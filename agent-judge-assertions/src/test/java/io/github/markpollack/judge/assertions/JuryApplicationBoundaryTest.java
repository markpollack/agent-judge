/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.assertions;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.JudgeMetadata;
import io.github.markpollack.judge.JudgeType;
import io.github.markpollack.judge.JudgeWithMetadata;
import io.github.markpollack.judge.Judges;
import io.github.markpollack.judge.NamedJudge;
import io.github.markpollack.judge.PolicyJudges;
import io.github.markpollack.judge.assertions.AssertionResult.PolicySource;
import io.github.markpollack.judge.jury.AllMustPassStrategy;
import io.github.markpollack.judge.jury.AttemptDisposition;
import io.github.markpollack.judge.jury.CascadedJury;
import io.github.markpollack.judge.jury.ConsensusStrategy;
import io.github.markpollack.judge.jury.DecisionBasis;
import io.github.markpollack.judge.jury.ErrorPolicy;
import io.github.markpollack.judge.jury.Juries;
import io.github.markpollack.judge.jury.Jury;
import io.github.markpollack.judge.jury.NamedJury;
import io.github.markpollack.judge.jury.NotApplicablePolicy;
import io.github.markpollack.judge.jury.SimpleJury;
import io.github.markpollack.judge.jury.TierPolicy;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.jury.VotingStrategy;
import io.github.markpollack.judge.jury.interpretation.ReadingSupport;
import io.github.markpollack.judge.jury.interpretation.VerdictReading;
import io.github.markpollack.judge.jury.interpretation.Verdicts;
import io.github.markpollack.judge.requirement.Requirement;
import io.github.markpollack.judge.requirement.RequirementEvidence;
import io.github.markpollack.judge.result.Acceptance;
import io.github.markpollack.judge.result.AcceptanceAction;
import io.github.markpollack.judge.result.AppliedPolicy;
import io.github.markpollack.judge.result.ArtifactRef;
import io.github.markpollack.judge.result.Assessment;
import io.github.markpollack.judge.result.AssessmentTarget;
import io.github.markpollack.judge.result.Certainty;
import io.github.markpollack.judge.result.Check;
import io.github.markpollack.judge.result.Distribution;
import io.github.markpollack.judge.result.EvaluationProvenance;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentReasonCode;
import io.github.markpollack.judge.result.JudgmentStatus;
import io.github.markpollack.judge.result.Policies;
import io.github.markpollack.judge.result.PolicyBinding;
import io.github.markpollack.judge.result.PolicyFailure;
import io.github.markpollack.judge.result.PolicyRef;
import io.github.markpollack.judge.result.ProbabilityMass;
import io.github.markpollack.judge.result.Proposition;
import io.github.markpollack.judge.result.SupportOrigin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JuryApplicationBoundaryTest {

    private static final Requirement<String> REQUIREMENT = Requirement.text("ready", "1", "The answer is READY");
    private static final String EVIDENCE = "READY";
    private static final ObjectMapper JSON = new ObjectMapper();

    enum Topology { FLAT, NESTED, INTERNALLY_ESCALATING, INTERNALLY_DECISIVE }

    static Stream<Arguments> configurations() {
        return Arrays.stream(Topology.values()).flatMap(topology -> Arrays.stream(PolicySource.values())
            .flatMap(source -> Arrays.stream(AcceptanceAction.values())
                .map(action -> Arguments.of(topology, source, action))));
    }

    @ParameterizedTest(name = "{0}, {1}, final {2}")
    @MethodSource("configurations")
    void finalPolicyNeverReplacesHeterogeneousInternalPolicies(Topology topology, PolicySource source,
            AcceptanceAction action) throws Exception {
        var baseline = fixture(topology);
        var direct = baseline.jury().vote(new RequirementEvidence<>(REQUIREMENT, EVIDENCE));
        var fixture = fixture(topology);
        var retained = new AtomicReference<Verdict>();
        var expectedRequirement = new AtomicReference<Requirement<String>>();
        Jury<RequirementEvidence<Requirement<String>, String>> supplied = recording(fixture.jury(), retained,
            expectedRequirement, fixture.calls().events);
        var finalPolicy = new PolicyBinding(reference("final-" + action), raw -> {
            assertThat(retained.get()).as("the supplied Jury returned before final policy").isNotNull();
            assertThat(fixture.calls().events).last().isEqualTo("jury-returned");
            assertThat(raw.policyApplication()).as("AcceptancePolicy receives its existing raw view").isNull();
            assertThat(raw.assessment()).isEqualTo(retained.get().aggregated().assessment());
            fixture.calls().events.add("final-policy");
            fixture.calls().policy("final");
            return new Acceptance(action, "application consequence");
        });
        var unused = new PolicyBinding(reference("unused"), raw -> {
            throw new AssertionError("Lower-priority policy must not run");
        });
        Requirement<String> requirement = switch (source) {
            case DEFAULT -> REQUIREMENT;
            case ASSOCIATED -> REQUIREMENT.under(finalPolicy);
            case EXPLICIT -> REQUIREMENT.under(unused);
        };
        expectedRequirement.set(requirement);
        var assertions = new RequirementAssertions(source == PolicySource.DEFAULT ? finalPolicy : unused);
        var result = assertions.evaluate(requirement, supplied, EVIDENCE,
            source == PolicySource.EXPLICIT ? finalPolicy : null);

        assertThat(result.verdict()).as("retain the actual supplied Jury result").isSameAs(retained.get());
        assertThat(result.verdict()).as("complete evaluation matches unchanged direct execution").isEqualTo(direct);
        assertThat(JSON.writeValueAsBytes(result.verdict())).isEqualTo(JSON.writeValueAsBytes(direct));
        assertThat(result.interpretation()).isEqualTo(Verdicts.interpret(direct));
        assertThat(result.interpretation().readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);
        assertThat(result.interpretation().reading()).isEqualTo(topology == Topology.INTERNALLY_ESCALATING
            ? VerdictReading.REJECTED : VerdictReading.ACCEPTED);
        assertThat(fixture.calls().judges).isEqualTo(baseline.calls().judges);
        assertThat(fixture.calls().policies).containsAllEntriesOf(baseline.calls().policies)
            .containsEntry("final", 1).hasSize(baseline.calls().policies.size() + 1);
        assertThat(result.policySource()).isEqualTo(source);
        assertThat(result.applicationDecision().application()).isEqualTo(
            new AppliedPolicy(finalPolicy.reference(), action, "application consequence"));
        assertThat(result.applicationDecision().bypass()).isNull();

        if (topology == Topology.INTERNALLY_ESCALATING) {
            assertThat(result.verdict().compositeAttempts()).extracting(attempt -> attempt.name())
                .containsExactly("first", "fallback");
        }
        if (topology == Topology.INTERNALLY_DECISIVE) {
            assertThat(result.verdict().compositeAttempts()).hasSize(1);
            assertThat(fixture.calls().judges).doesNotContainKey("withheld-negative");
        }
        var beforeInspection = List.copyOf(fixture.calls().events);
        for (int repeat = 0; repeat < 2; repeat++) {
            if (action != AcceptanceAction.USE_ASSESSMENT) {
                assertThatThrownBy(() -> SemanticAssertions.requireSatisfied(result))
                    .isInstanceOf(SemanticAssertionError.Inconclusive.class);
            }
            else if (topology == Topology.INTERNALLY_ESCALATING) {
                assertThatThrownBy(() -> SemanticAssertions.requireSatisfied(result))
                    .isInstanceOf(SemanticAssertionError.Rejected.class);
            }
            else {
                assertThatCode(() -> SemanticAssertions.requireSatisfied(result)).doesNotThrowAnyException();
            }
        }
        assertThat(fixture.calls().events).isEqualTo(beforeInspection);
    }

    @Test
    void approvedStaticDefaultHonorsInternalEscalation() {
        var fixture = fixture(Topology.INTERNALLY_ESCALATING);
        var result = RequirementAssertions.usingAssessment().evaluate(REQUIREMENT, fixture.jury(), EVIDENCE, null);
        assertThat(result.policySource()).isEqualTo(PolicySource.DEFAULT);
        assertThat(result.policy().id()).isEqualTo("agent-eval.assertion-default");
        assertThat(result.verdict().compositeAttempts()).hasSize(2);
        assertThat(fixture.calls().judges).containsEntry("first", 1).containsEntry("fallback", 1);
        assertThat(result.interpretation().reading()).isEqualTo(VerdictReading.REJECTED);
        assertThatThrownBy(() -> SemanticAssertions.requireSatisfied(result))
            .isInstanceOf(SemanticAssertionError.Rejected.class);
    }

    @ParameterizedTest
    @EnumSource(AcceptanceAction.class)
    void judgeAndOneSeatJuryGiveFinalPolicyTheSameScope(AcceptanceAction internalAction) {
        var internal = Policies.apply(rich("single", Judgment.pass("producer finding")), reference("internal"),
            raw -> new Acceptance(internalAction, "internal consequence"));
        var judgeCalls = new AtomicInteger();
        Judge<RequirementEvidence<Requirement<String>, String>> judge = Judges.named(pair -> {
            judgeCalls.incrementAndGet();
            return internal;
        }, "single");
        var finalCalls = new AtomicInteger();
        var finalPolicy = new PolicyBinding(reference("final"), raw -> {
            assertThat(raw.producerStatus()).isEqualTo(JudgmentStatus.PASS);
            assertThat(raw.policyApplication()).isNull();
            finalCalls.incrementAndGet();
            return new Acceptance(AcceptanceAction.USE_ASSESSMENT, "use determination");
        });
        var assertions = new RequirementAssertions(finalPolicy);
        var viaJudge = assertions.evaluate(REQUIREMENT, judge, EVIDENCE, null);
        var viaJury = assertions.evaluate(REQUIREMENT, single(judge), EVIDENCE, null);
        assertThat(viaJury).isEqualTo(viaJudge);
        assertThat(viaJudge.verdict().aggregated()).isSameAs(internal);
        assertThat(judgeCalls.get()).isEqualTo(2);
        assertThat(finalCalls.get()).isEqualTo(2);
        if (internalAction == AcceptanceAction.USE_ASSESSMENT) {
            assertThatCode(() -> SemanticAssertions.requireSatisfied(viaJudge)).doesNotThrowAnyException();
        }
        else {
            assertThat(viaJudge.interpretation().reading()).isEqualTo(VerdictReading.UNDECIDED);
            assertThatThrownBy(() -> SemanticAssertions.requireSatisfied(viaJudge))
                .isInstanceOf(SemanticAssertionError.Inconclusive.class);
        }
    }

    @Test
    void internalPolicyFailureCannotBeRepairedThroughEitherEntry() {
        Judgment failed = Policies.apply(Judgment.pass("producer finding"), reference("internal"), raw -> {
            throw new IllegalStateException("internal policy unavailable");
        });
        Judge<RequirementEvidence<Requirement<String>, String>> judge = Judges.named(pair -> failed, "single");
        var assertions = new RequirementAssertions(forbiddenFinalPolicy());
        var viaJudge = assertions.evaluate(REQUIREMENT, judge, EVIDENCE, null);
        var viaJury = assertions.evaluate(REQUIREMENT, single(judge), EVIDENCE, null);
        assertThat(viaJury).isEqualTo(viaJudge);
        assertThat(viaJudge.verdict().aggregated()).isSameAs(failed);
        assertThat(failed.producerStatus()).isEqualTo(JudgmentStatus.PASS);
        assertThat(failed.policyApplication()).isInstanceOf(PolicyFailure.class);
        assertInstrumentFailure(viaJudge);
    }

    @ParameterizedTest
    @EnumSource(AcceptanceAction.class)
    void finalPolicyCannotRepairDisagreement(AcceptanceAction action) {
        var calls = new Calls();
        var jury = panel(seat("positive", Judgment.pass("yes"), AcceptanceAction.USE_ASSESSMENT, calls),
            seat("negative", Judgment.fail("no"), AcceptanceAction.USE_ASSESSMENT, calls));
        var result = new RequirementAssertions(policy("final", action, calls))
            .evaluate(REQUIREMENT, jury, EVIDENCE, null);
        assertThat(result.interpretation().reading()).isEqualTo(VerdictReading.UNDECIDED);
        assertThat(result.verdict().individual()).extracting(Judgment::producerStatus)
            .containsExactly(JudgmentStatus.PASS, JudgmentStatus.FAIL);
        assertThat(result.verdict().seats()).extracting(seat -> seat.verdictKey())
            .containsExactly("positive", "negative");
        assertThat(result.interpretation().root().judges()).hasSize(2);
        assertThatThrownBy(() -> SemanticAssertions.requireSatisfied(result))
            .isInstanceOf(SemanticAssertionError.Inconclusive.class);
    }

    @Test
    void unsupportedCustomVerdictBypassesFinalPolicy() {
        var valid = Verdict.single("custom", Judgment.pass("positive"));
        var unsupported = new Verdict(2, valid.aggregated(), valid.individual(), valid.individualByName(),
            valid.weights(), valid.seats(), valid.decision(), valid.compositeAttempts(), 0);
        var result = new RequirementAssertions(forbiddenFinalPolicy()).evaluate(REQUIREMENT,
            returning(unsupported), EVIDENCE, null);
        assertThat(result.verdict()).isSameAs(unsupported);
        assertThat(result.applicationDecision().bypass()).isEqualTo(ApplicationDecision.Bypass.UNSUPPORTED_READING);
        assertThatThrownBy(() -> SemanticAssertions.requireSatisfied(result))
            .isInstanceOf(SemanticAssertionError.UnsupportedReading.class);
    }

    @Test
    void individualRejectionWithMachineryAggregateRemainsRejectedWithoutFinalPolicy() {
        var brokenReduction = new VotingStrategy() {
            @Override
            public Judgment aggregate(List<Judgment> judgments, Map<String, Double> weights) {
                throw new IllegalStateException("reduction unavailable");
            }
            @Override
            public String getName() { return "broken-reduction"; }
        };
        var first = SimpleJury.<RequirementEvidence<Requirement<String>, String>>builder()
            .judge(Judges.named(pair -> Judgment.pass("positive finding"), "positive"))
            .judge(Judges.named(pair -> Judgment.fail("verified violation"), "negative"))
            .votingStrategy(brokenReduction).parallel(false).build();
        Judge<RequirementEvidence<Requirement<String>, String>> fallback = pair -> {
            throw new AssertionError("Established individual rejection must stop the cascade");
        };
        var jury = CascadedJury.<RequirementEvidence<Requirement<String>, String>>builder()
            .tier("gate", first, TierPolicy.REJECT_ON_ANY_FAIL)
            .tier("fallback", single(fallback), TierPolicy.FINAL_TIER).build();
        var result = new RequirementAssertions(forbiddenFinalPolicy())
            .evaluate(REQUIREMENT, jury, EVIDENCE, null);
        assertThat(result.verdict().decision().basis()).isEqualTo(DecisionBasis.INDIVIDUAL_REJECTION);
        assertThat(result.verdict().aggregated().producerStatus()).isEqualTo(JudgmentStatus.ERROR);
        assertThat(result.verdict().aggregated().reasonCode()).isEqualTo(JudgmentReasonCode.AGGREGATION_FAILED);
        assertThat(result.verdict().compositeAttempts()).singleElement().satisfies(attempt -> {
            assertThat(attempt.name()).isEqualTo("gate");
            assertThat(attempt.disposition()).isEqualTo(AttemptDisposition.STAGE_FAILED);
        });
        assertThat(result.verdict().individual()).extracting(Judgment::producerStatus)
            .containsExactly(JudgmentStatus.PASS, JudgmentStatus.FAIL);
        assertThat(result.interpretation().readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);
        assertThat(result.interpretation().reading()).isEqualTo(VerdictReading.REJECTED);
        assertThat(result.applicationDecision().bypass()).isEqualTo(ApplicationDecision.Bypass.AGGREGATE_ERROR);
        assertThat(result.applicationDecision().application()).isNull();
        assertThatThrownBy(() -> SemanticAssertions.requireSatisfied(result))
            .isInstanceOf(SemanticAssertionError.Rejected.class);
    }

    @Test
    void declaredExclusionRetainsItsDistinctOutcome() {
        Judge<RequirementEvidence<Requirement<String>, String>> judge = new NamedJudge<>(
            pair -> Judgment.notApplicable("No relevant artifact"),
            new JudgeMetadata("conditional", "conditional check", JudgeType.DETERMINISTIC, "No relevant artifact"));
        var result = new RequirementAssertions(forbiddenFinalPolicy())
            .evaluate(REQUIREMENT, single(judge), EVIDENCE, null);
        assertThat(result.interpretation().reading()).isEqualTo(VerdictReading.NOT_APPLICABLE);
        assertThat(result.applicationDecision().bypass()).isEqualTo(ApplicationDecision.Bypass.NOT_APPLICABLE);
        assertThatThrownBy(() -> SemanticAssertions.requireSatisfied(result))
            .isInstanceOf(SemanticAssertionError.NotApplicable.class);
    }

    @Test
    void metadataCapabilityDriftCannotAcquireAnExclusionAtAssertionTime() {
        var metadataCalls = new AtomicInteger();
        Judge<RequirementEvidence<Requirement<String>, String>> judge = new JudgeWithMetadata<>() {
            @Override
            public JudgeMetadata metadata() {
                return new JudgeMetadata("changing", "conditional", JudgeType.DETERMINISTIC,
                    metadataCalls.getAndIncrement() == 0 ? null : "No relevant artifact");
            }
            @Override
            public Judgment judge(RequirementEvidence<Requirement<String>, String> pair) {
                return Judgment.notApplicable("No relevant artifact");
            }
        };
        var jury = single(judge);
        var result = new RequirementAssertions(forbiddenFinalPolicy()).evaluate(REQUIREMENT, jury, EVIDENCE, null);
        assertThat(result.verdict().individual()).singleElement().satisfies(judgment ->
            assertThat(judgment.reasonCode()).isEqualTo(JudgmentReasonCode.UNDECLARED_NOT_APPLICABLE));
        assertInstrumentFailure(result);
    }

    @Test
    void unreadableMetadataPreventsJudgeAndFinalPolicyExecution() {
        Judge<RequirementEvidence<Requirement<String>, String>> judge = new JudgeWithMetadata<>() {
            @Override
            public JudgeMetadata metadata() { throw new IllegalStateException("metadata unavailable"); }
            @Override
            public Judgment judge(RequirementEvidence<Requirement<String>, String> pair) {
                throw new AssertionError("Unreadable seat must not execute");
            }
        };
        var result = new RequirementAssertions(forbiddenFinalPolicy())
            .evaluate(REQUIREMENT, single(judge), EVIDENCE, null);
        assertThat(result.verdict().individual()).singleElement().satisfies(judgment ->
            assertThat(judgment.reasonCode()).isEqualTo(JudgmentReasonCode.JUDGE_METADATA_UNREADABLE));
        assertInstrumentFailure(result);
    }

    @ParameterizedTest
    @EnumSource(PolicySource.class)
    void finalPolicyDoesNotSupplyMissingInternalAssessmentTierPolicy(PolicySource source) {
        var calls = new Calls();
        Judge<RequirementEvidence<Requirement<String>, String>> bare = pair -> {
            calls.judge("bare");
            return Judgment.pass("No internal policy was configured");
        };
        var jury = cascade(single(bare), single(seat("fallback", Judgment.fail("violation"),
            AcceptanceAction.USE_ASSESSMENT, calls)));
        var binding = forbiddenFinalPolicy();
        var requirement = source == PolicySource.ASSOCIATED ? REQUIREMENT.under(binding) : REQUIREMENT;
        var result = new RequirementAssertions(binding).evaluate(requirement, jury, EVIDENCE,
            source == PolicySource.EXPLICIT ? binding : null);
        assertThat(result.policySource()).isEqualTo(source);
        assertThat(calls.judges).containsOnlyKeys("bare");
        assertThat(result.verdict().compositeAttempts()).singleElement().satisfies(attempt -> {
            assertThat(attempt.disposition()).isEqualTo(AttemptDisposition.STAGE_FAILED);
            assertThat(attempt.verdict().individual().get(0).producerStatus()).isEqualTo(JudgmentStatus.PASS);
            assertThat(attempt.verdict().individual().get(0).policyApplication()).isNull();
        });
        assertInstrumentFailure(result);
    }

    @Test
    void assessmentTierStillRefusesMultipleSeatsNestedJuriesAndCustomWrappersBeforeExecution() {
        Judge<RequirementEvidence<Requirement<String>, String>> uncalled = pair -> {
            throw new AssertionError("Invalid assessment tier must not spend a Judge call");
        };
        var twoSeats = panel(uncalled, uncalled);
        var nested = Juries.meta(new ConsensusStrategy(), new NamedJury<>("nested", single(uncalled)));
        var custom = returning(Verdict.single("forged", Judgment.pass("positive")));
        for (var invalid : List.of(twoSeats, nested, custom)) {
            var result = new RequirementAssertions(forbiddenFinalPolicy()).evaluate(REQUIREMENT,
                cascade(invalid, single(uncalled)), EVIDENCE, null);
            assertThat(result.verdict().compositeAttempts()).singleElement().satisfies(attempt -> {
                assertThat(attempt.disposition()).isEqualTo(AttemptDisposition.STAGE_FAILED);
                assertThat(attempt.failure()).isNotNull();
            });
            assertInstrumentFailure(result);
        }
    }

    private static void assertInstrumentFailure(AssertionResult result) {
        assertThat(result.interpretation().reading()).isEqualTo(VerdictReading.NOT_ASSESSED);
        assertThat(result.applicationDecision().application()).isNull();
        assertThat(result.applicationDecision().bypass()).isEqualTo(ApplicationDecision.Bypass.NOT_ASSESSED);
        assertThatThrownBy(() -> SemanticAssertions.requireSatisfied(result))
            .isInstanceOf(SemanticAssertionError.InstrumentFailure.class);
    }

    private static PolicyBinding forbiddenFinalPolicy() {
        return new PolicyBinding(reference("final"), raw -> {
            throw new AssertionError("Invalid evaluation must bypass final policy");
        });
    }

    private static PolicyRef reference(String id) {
        return new PolicyRef(id, "1", ArtifactRef.ofBytes("policy", id.getBytes(StandardCharsets.UTF_8), null).sha256());
    }

    private static PolicyBinding policy(String id, AcceptanceAction action, Calls calls) {
        return new PolicyBinding(reference(id), raw -> {
            assertThat(raw.policyApplication()).isNull();
            calls.policy(id);
            return new Acceptance(action, id);
        });
    }

    private static Judge<RequirementEvidence<Requirement<String>, String>> seat(String name, Judgment finding,
            AcceptanceAction action, Calls calls) {
        Judgment raw = rich(name, finding);
        Judge<RequirementEvidence<Requirement<String>, String>> judge = pair -> {
            assertThat(pair.requirement().id()).isEqualTo(REQUIREMENT.id());
            assertThat(pair.evidence()).isSameAs(EVIDENCE);
            calls.judge(name);
            return raw;
        };
        var binding = policy("internal-" + name, action, calls);
        return Judges.named(PolicyJudges.apply(judge, binding.reference(), binding.policy()), name);
    }

    private static Judgment rich(String name, Judgment finding) {
        var artifact = ArtifactRef.ofBytes("evidence", EVIDENCE.getBytes(StandardCharsets.UTF_8), null);
        return new Judgment(finding.producerStatus(), new Assessment(new Proposition(finding.pass()), null, null),
            new Certainty(0.7, "reported-support", SupportOrigin.REPORTED, AssessmentTarget.PROPOSITION, null),
            new Distribution(AssessmentTarget.PROPOSITION, "truth", List.of(
                new ProbabilityMass("true", 0.7), new ProbabilityMass("false", 0.3))),
            finding.reasonCode(), finding.reasoning(), List.of(new Check("detail", finding)),
            new EvaluationProvenance(name, "1", artifact.sha256(), List.of(artifact), null, List.of()),
            null, Map.of("source", name));
    }

    @SafeVarargs
    private static SimpleJury<RequirementEvidence<Requirement<String>, String>> panel(
            Judge<RequirementEvidence<Requirement<String>, String>>... judges) {
        var builder = SimpleJury.<RequirementEvidence<Requirement<String>, String>>builder()
            .votingStrategy(new ConsensusStrategy()).parallel(false);
        for (var judge : judges) builder.judge(judge);
        return builder.build();
    }

    private static SimpleJury<RequirementEvidence<Requirement<String>, String>> single(
            Judge<RequirementEvidence<Requirement<String>, String>> judge) {
        return SimpleJury.<RequirementEvidence<Requirement<String>, String>>builder().judge(judge)
            .votingStrategy(new AllMustPassStrategy(ErrorPolicy.PROPAGATE, NotApplicablePolicy.EXCLUDE))
            .parallel(false).build();
    }

    private static Jury<RequirementEvidence<Requirement<String>, String>> cascade(
            Jury<RequirementEvidence<Requirement<String>, String>> first,
            Jury<RequirementEvidence<Requirement<String>, String>> fallback) {
        return CascadedJury.<RequirementEvidence<Requirement<String>, String>>builder()
            .tier("first", first, TierPolicy.STOP_ON_USABLE_ASSESSMENT)
            .tier("fallback", fallback, TierPolicy.FINAL_TIER).build();
    }

    private static Fixture fixture(Topology topology) {
        var calls = new Calls();
        var positive = seat("positive", Judgment.pass("positive finding"), AcceptanceAction.USE_ASSESSMENT, calls);
        var withheld = seat("withheld-negative", Judgment.fail("negative finding"), AcceptanceAction.ABSTAIN, calls);
        var panel = panel(positive, withheld);
        var jury = switch (topology) {
            case FLAT -> panel;
            case NESTED -> Juries.meta(new ConsensusStrategy(), new NamedJury<>("panel", panel),
                new NamedJury<>("other", single(seat("other", Judgment.fail("another negative"),
                    AcceptanceAction.ABSTAIN, calls))));
            case INTERNALLY_ESCALATING -> cascade(single(seat("first", Judgment.pass("tentative"),
                AcceptanceAction.ESCALATE, calls)), single(seat("fallback", Judgment.fail("verified violation"),
                    AcceptanceAction.USE_ASSESSMENT, calls)));
            case INTERNALLY_DECISIVE -> cascade(single(positive), single(withheld));
        };
        return new Fixture(jury, calls);
    }

    private static Jury<RequirementEvidence<Requirement<String>, String>> recording(
            Jury<RequirementEvidence<Requirement<String>, String>> delegate, AtomicReference<Verdict> retained,
            AtomicReference<Requirement<String>> expectedRequirement, List<String> events) {
        return new Jury<>() {
            @Override
            public Verdict vote(RequirementEvidence<Requirement<String>, String> pair) {
                assertThat(retained.get()).as("one call to the supplied Jury").isNull();
                assertThat(pair.requirement()).isSameAs(expectedRequirement.get());
                assertThat(pair.evidence()).isSameAs(EVIDENCE);
                var verdict = delegate.vote(pair);
                retained.set(verdict);
                events.add("jury-returned");
                return verdict;
            }
            @Override
            public List<Judge<RequirementEvidence<Requirement<String>, String>>> getJudges() {
                throw new AssertionError("Assertions must not reconstruct a supplied Jury");
            }
            @Override
            public VotingStrategy getVotingStrategy() {
                throw new AssertionError("Assertions must not reconstruct a supplied Jury");
            }
        };
    }

    private static Jury<RequirementEvidence<Requirement<String>, String>> returning(Verdict verdict) {
        return new Jury<>() {
            @Override
            public Verdict vote(RequirementEvidence<Requirement<String>, String> pair) { return verdict; }
            @Override
            public List<Judge<RequirementEvidence<Requirement<String>, String>>> getJudges() { return List.of(); }
            @Override
            public VotingStrategy getVotingStrategy() { return new ConsensusStrategy(); }
        };
    }

    private record Fixture(Jury<RequirementEvidence<Requirement<String>, String>> jury, Calls calls) {}

    private static final class Calls {
        final Map<String, Integer> judges = new LinkedHashMap<>();
        final Map<String, Integer> policies = new LinkedHashMap<>();
        final List<String> events = new ArrayList<>();
        void judge(String name) { judges.merge(name, 1, Integer::sum); events.add("judge:" + name); }
        void policy(String name) { policies.merge(name, 1, Integer::sum); events.add("policy:" + name); }
    }
}
