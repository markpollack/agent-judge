/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.evaluation;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import io.github.markpollack.judge.*;
import io.github.markpollack.judge.construction.*;
import io.github.markpollack.judge.jury.*;
import io.github.markpollack.judge.judgment.*;
import io.github.markpollack.judge.requirement.*;
import io.github.markpollack.judge.policy.*;
import io.github.markpollack.judge.reporting.VerdictReport;
import io.github.markpollack.judge.serialization.VerdictCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;

class EvaluationContractTest {

	static final Requirement<String> SECURITY = Requirement.text("security", "1", "No unsafe access");
	static final Requirement<String> COMPATIBILITY = Requirement.text("compatibility", "1", "API compatible");
	static final Requirement<String> OBSERVABILITY = Requirement.text("observability", "1", "Errors visible");

	static Requirement<AllOf> parent(List<Requirement<?>> children) {
		return new GeneralRequirement<>("readiness", "1", "Ready for production", new AllOf(children),
				SECURITY.source());
	}

	static Judgment result(JudgmentStatus status) {
		return switch (status) {
			case PASS -> Judgment.pass("yes");
			case FAIL -> Judgment.fail("no");
			case ABSTAIN -> Judgment.abstain("unknown");
			case ERROR -> Judgment.error("instrument unavailable");
			case NOT_APPLICABLE -> Judgment.notApplicable("excluded");
		};
	}

	@ParameterizedTest
	@EnumSource(JudgmentStatus.class)
	void everyOutcomeReceivesEveryRequestedActionWithoutMutation(JudgmentStatus status) {
		Verdict original = Verdict.single("judge", result(status));
		Verdict.Conclusion conclusion = original.conclusion();
		for (PolicyAction action : PolicyAction.values()) {
			AtomicInteger calls = new AtomicInteger();
			PolicyDecision decision = new PolicyDecision(action, "caller consequence");
			var evaluated = Evaluations.apply(original, v -> {
				calls.incrementAndGet();
				assertThat(v).isSameAs(original);
				return decision;
			});
			assertThat(calls.get()).isEqualTo(1);
			assertThat(evaluated.verdict()).isSameAs(original);
			assertThat(((PolicyResult.Decided) evaluated.policyResult()).decision()).isSameAs(decision);
			assertThat(evaluated.verdict().conclusion()).isEqualTo(conclusion);
			VerdictReport.of(original).summary();
			VerdictReport.of(original).summary();
			assertThat(calls.get()).isEqualTo(1);
		}
	}

	@Test
	void ordinaryJudgeNeedsNeitherRequirementNorFindingNorPolicy() {
		AtomicInteger calls = new AtomicInteger();
		int n = 4;
		Judge judge = () -> {
			calls.incrementAndGet();
			return n == 4 ? Judgment.pass("four") : Judgment.fail("different");
		};
		var result = Evaluations.evaluate(judge);
		assertThat(result.verdict().requirement()).isNull();
		assertThat(result.verdict().judgment().finding()).isNull();
		assertThat(result.verdict().conclusion()).isEqualTo(Verdict.Conclusion.PASS);
		assertThat(result.policyResult()).isInstanceOf(PolicyResult.NotRequested.class);
		assertThat(calls.get()).isEqualTo(1);
	}

	@Test
	void policyExceptionAndNullReturnRetainOriginalVerdictAndException() {
		Verdict original = Verdict.single("judge", Judgment.pass("yes"));
		var exception = new IllegalStateException("configuration unavailable");
		var failed = Evaluations.apply(original, v -> {
			throw exception;
		});
		assertThat(failed.verdict()).isSameAs(original);
		assertThat(((PolicyResult.Failed) failed.policyResult()).cause()).isSameAs(exception);
		var invalid = Evaluations.apply(original, v -> null);
		assertThat(((PolicyResult.Failed) invalid.policyResult()).cause()).isInstanceOf(NullPointerException.class)
			.hasMessageContaining("Policy returned null");
		assertThatThrownBy(() -> new PolicyResult.Decided(null)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new PolicyResult.Failed(null)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new EvaluationResult(null, new PolicyResult.NotRequested()))
			.isInstanceOf(NullPointerException.class);
	}

	@Test
	void fatalErrorsAndCancellationAreNotOrdinaryPolicyFailures() {
		Verdict v = Verdict.single("judge", Judgment.pass("yes"));
		var fatal = new OutOfMemoryError("sentinel");
		assertThatThrownBy(() -> Evaluations.apply(v, x -> {
			throw fatal;
		})).isSameAs(fatal);
		assertThatThrownBy(() -> Evaluations.apply(v, x -> {
			throw new CancellationException("cancelled");
		})).isInstanceOf(CancellationException.class);
		try {
			Thread.currentThread().interrupt();
			assertThatThrownBy(() -> Evaluations.apply(v, x -> {
				throw new AssertionError("must not execute");
			})).isInstanceOf(CancellationException.class);
		}
		finally {
			Thread.interrupted();
		}
	}

	@Test
	void mixedOpinionsAndDifferentConstituentsAreNeverFlattened() {
		var readiness = parent(List.of(SECURITY, COMPATIBILITY, OBSERVABILITY));
		List<JudgeRecipe<String, String>> opinions = List.of(TestRecipes.judge((r, e) -> Judgment.pass("one")),
				TestRecipes.judge((r, e) -> Judgment.pass("two")),
				TestRecipes.judge((r, e) -> Judgment.fail("dissent")));
		var security = TestRecipes.voting(new MajorityVotingStrategy(), opinions);
		var prepared = Assignments.<String>forRequirement(readiness)
			.jury(SECURITY, security)
			.judge(COMPATIBILITY, TestRecipes.judge((r, e) -> Judgment.pass("compatible")))
			.judge(OBSERVABILITY, TestRecipes.judge((r, e) -> Judgment.fail("logs absent")))
			.validate();
		Verdict verdict = prepared.evidence("release").build().vote();
		assertThat(verdict.conclusion()).isEqualTo(Verdict.Conclusion.FAIL);
		assertThat(verdict.requirement()).isSameAs(readiness);
		assertThat(verdict.individual()).isEmpty();
		assertThat(verdict.compositeAttempts()).hasSize(3);
		Verdict child = verdict.compositeAttempts().getFirst().verdict();
		assertThat(child.requirement()).isSameAs(SECURITY);
		assertThat(child.conclusion()).isEqualTo(Verdict.Conclusion.PASS);
		assertThat(child.individual()).hasSize(3);
		assertThat(child.individual().get(2).status()).isEqualTo(JudgmentStatus.FAIL);
		var codec = new VerdictCodec();
		Verdict stored = codec.read(codec.write(verdict));
		assertThat(stored).isEqualTo(verdict);
		assertThat(stored.conclusion()).isEqualTo(verdict.conclusion());
		assertThat(VerdictReport.of(stored).attempts()).hasSize(3);
	}

	@ParameterizedTest
	@CsvSource({ "PASS,PASS,PASS", "PASS,FAIL,FAIL", "PASS,ABSTAIN,INCONCLUSIVE", "FAIL,ERROR,FAIL",
			"PASS,ERROR,INCONCLUSIVE", "PASS,NOT_APPLICABLE,INCONCLUSIVE", "ERROR,ERROR,INCONCLUSIVE" })
	void allOfTruthTable(JudgmentStatus first, JudgmentStatus second, Verdict.Conclusion expected) {
		var prepared = Assignments.<String>forRequirement(parent(List.of(SECURITY, COMPATIBILITY)))
			.judge(SECURITY, TestRecipes.judge((r, e) -> result(first)))
			.judge(COMPATIBILITY, TestRecipes.judge((r, e) -> result(second)))
			.validate();
		Verdict verdict = prepared.evidence("case").build().vote();
		assertThat(verdict.conclusion()).isEqualTo(expected);
		AtomicInteger calls = new AtomicInteger();
		var evaluated = Evaluations.apply(verdict, v -> {
			calls.incrementAndGet();
			return new PolicyDecision(PolicyAction.RELY, "read all attempts");
		});
		assertThat(calls.get()).isEqualTo(1);
		assertThat(evaluated.verdict().conclusion()).isEqualTo(expected);
		assertThat(new VerdictCodec().read(new VerdictCodec().write(verdict)).conclusion()).isEqualTo(expected);
	}

	@Test
	void invalidAssignmentsCannotExecuteAnythingAndValidatedPlanIsStable() {
		AtomicInteger calls = new AtomicInteger();
		JudgeRecipe<String, String> judge = TestRecipes.judge((r, e) -> {
			calls.incrementAndGet();
			return Judgment.pass("yes");
		});
		var roster = new ArrayList<Requirement<?>>(List.of(SECURITY, COMPATIBILITY));
		var readiness = parent(roster);
		roster.clear();
		var assignments = Assignments.<String>forRequirement(readiness).judge(SECURITY, judge);
		assertThatThrownBy(assignments::validate).hasMessageContaining("Missing assignment: compatibility");
		assertThatThrownBy(() -> assignments.judge(SECURITY, judge)).hasMessageContaining("Duplicate");
		assertThatThrownBy(() -> assignments.judge(OBSERVABILITY, judge)).hasMessageContaining("Unrelated");
		assertThatThrownBy(() -> assignments.judge(COMPATIBILITY, (JudgeRecipe<String, String>) null))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> assignments.judge(Requirement.text("security", "2", "changed"), judge))
			.hasMessageContaining("differs");
		assertThatThrownBy(() -> new AllOf(List.of(SECURITY, Requirement.text("security", "2", "another"))))
			.hasMessageContaining("Ambiguous");
		assertThat(calls.get()).isZero();
		var prepared = assignments.judge(COMPATIBILITY, judge).validate();
		assertThat(prepared.evidence("release").build().vote().conclusion()).isEqualTo(Verdict.Conclusion.PASS);
		assertThat(calls.get()).isEqualTo(2);
	}

	record ReleaseEvidence(Integer apiDiff, String logs) {
	}

	public record ApiLimit(int maximum) {
	}

	@Test
	void typedSelectorsUseTheParentInstancesAndNativeSpecificationsRoundTrip() {
		Requirement<ApiLimit> api = new GeneralRequirement<>("api", "7", "Max changed API count", new ApiLimit(2),
				SECURITY.source());
		var readiness = parent(List.of(api, OBSERVABILITY));
		Requirement<ApiLimit> equalReference = new GeneralRequirement<>(api.id(), api.revision(), api.text(),
				api.specification(), api.source());
		JudgeRecipe<ApiLimit, Integer> checker = TestRecipes.judge((r, diff) -> {
			assertThat(r).isSameAs(api);
			return diff <= r.specification().maximum() ? Judgment.pass("within bound") : Judgment.fail("too many");
		});
		JuryRecipe<String, String> logs = TestRecipes
			.jury((r, e) -> Verdict.single("logs", e.isEmpty() ? Judgment.fail("empty") : Judgment.pass("present")));
		var prepared = Assignments.<ReleaseEvidence>forRequirement(readiness)
			.judge(equalReference, ReleaseEvidence::apiDiff, checker)
			.jury(OBSERVABILITY, ReleaseEvidence::logs, logs)
			.validate();
		Verdict verdict = prepared.evidence(new ReleaseEvidence(1, "log")).build().vote();
		assertThat(verdict.conclusion()).isEqualTo(Verdict.Conclusion.PASS);
		assertThatThrownBy(() -> new VerdictCodec().write(verdict)).hasMessageContaining("No registered specification");
		var codec = new VerdictCodec(Map.of("apiLimit", ApiLimit.class));
		Verdict stored = codec.read(codec.write(verdict));
		assertThat(stored).isEqualTo(verdict);
	}

	@Test
	void selectorFailureRetainsTheRequiredChildAndOriginalCause() {
		var failure = new IllegalStateException("diff unavailable");
		var providerCalls = new AtomicInteger();
		var prepared = Assignments.<ReleaseEvidence>forRequirement(parent(List.of(COMPATIBILITY, OBSERVABILITY)))
			.judge(COMPATIBILITY, (ReleaseEvidence e) -> {
				throw failure;
			}, TestRecipes.<String, Integer>judge((r, e) -> {
				providerCalls.incrementAndGet();
				return Judgment.pass("never");
			}))
			.judge(OBSERVABILITY, ReleaseEvidence::logs, TestRecipes.judge((r, e) -> Judgment.fail("logs inadequate")))
			.validate();
		Verdict verdict = prepared.evidence(new ReleaseEvidence(1, "log")).build().vote();
		assertThat(verdict.conclusion()).isEqualTo(Verdict.Conclusion.FAIL);
		assertThat(verdict.compositeAttempts()).hasSize(2);
		assertThat(verdict.compositeAttempts().getFirst().failure().cause()).isSameAs(failure);
		assertThat(providerCalls.get()).isZero();
		assertThat(new VerdictCodec().read(new VerdictCodec().write(verdict)).conclusion())
			.isEqualTo(Verdict.Conclusion.FAIL);
	}

	@Test
	void parentNonApplicabilityDoesNotMeanInconclusiveAndRunsNoChildren() {
		var applicable = parent(List.of(SECURITY));
		var parent = new GeneralRequirement<>(applicable.id(), applicable.revision(), applicable.text(),
				new AllOf(List.of(SECURITY), false), applicable.source());
		var verdict = Assignments.<String>forRequirement(parent).judge(SECURITY, TestRecipes.judge((r, e) -> {
			throw new AssertionError("must not run");
		})).validate().evidence("release").build().vote();
		assertThat(verdict.conclusion()).isEqualTo(Verdict.Conclusion.NOT_APPLICABLE);
		assertThat(verdict.compositeAttempts()).isEmpty();
	}

	@Test
	void policySeesIndividualRejectionAndFailedStageDespiteCollectiveError() {
		Jury negative = SimpleJury.<String>builder()
			.judge(() -> Judgment.fail("violation"))
			.votingStrategy(new ConsensusStrategy())
			.build();
		Jury broken = () -> {
			throw new IllegalStateException("instrument failed");
		};
		Jury stage = Juries.meta(new ConsensusStrategy(), new NamedJury("negative", negative),
				new NamedJury("broken", broken));
		Verdict v = CascadedJury.<String>builder()
			.tier("gate", stage, RoutingRule.STOP_ON_ANY_OPINION_FAIL)
			.tier("final", () -> {
				throw new AssertionError("must stop at rejection");
			}, RoutingRule.FINAL_TIER)
			.build()
			.vote();
		assertThat(v.judgment().status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(v.conclusion()).isEqualTo(Verdict.Conclusion.FAIL);
		var result = Evaluations.apply(v, complete -> {
			assertThat(complete.compositeAttempts().getFirst().verdict().compositeAttempts()).hasSize(2);
			return new PolicyDecision(PolicyAction.RELY, "Trust established rejection");
		});
		assertThat(result.policyResult()).isInstanceOf(PolicyResult.Decided.class);
		assertThat(result.verdict()).isSameAs(v);
		assertThat(new VerdictCodec().read(new VerdictCodec().write(v)).conclusion())
			.isEqualTo(Verdict.Conclusion.FAIL);
	}

	@Test
	void allFailedCascadeIsUsableAndPolicyStillRuns() {
		Jury broken = () -> {
			throw new IllegalStateException("down");
		};
		Verdict v = CascadedJury.<String>builder()
			.tier("first", broken, RoutingRule.STOP_ON_ALL_OPINIONS_PASS)
			.tier("last", broken, RoutingRule.FINAL_TIER)
			.build()
			.vote();
		assertThat(v.conclusion()).isEqualTo(Verdict.Conclusion.INCONCLUSIVE);
		AtomicInteger calls = new AtomicInteger();
		Evaluations.apply(v, x -> {
			calls.incrementAndGet();
			return new PolicyDecision(PolicyAction.ESCALATE, "instrument repair");
		});
		assertThat(calls.get()).isEqualTo(1);
	}

	@Test
	void allPolicyResultFormsRoundTripWithoutExecutingAnything() {
		Verdict v = Verdict.single("judge", Judgment.pass("yes")).forRequirement(SECURITY);
		var codec = new VerdictCodec();
		for (PolicyResult policy : List.of(new PolicyResult.NotRequested(),
				new PolicyResult.Decided(new PolicyDecision(PolicyAction.RELY, "yes")),
				new PolicyResult.Failed(new IllegalStateException("missing configuration")))) {
			EvaluationResult restored = codec.readEvaluation(codec.write(new EvaluationResult(v, policy)));
			assertThat(restored.verdict()).isEqualTo(v);
			assertThat(restored.policyResult().getClass()).isEqualTo(policy.getClass());
			if (policy instanceof PolicyResult.Failed) {
				assertThat(((PolicyResult.Failed) restored.policyResult()).cause())
					.isInstanceOf(VerdictCodec.StoredPolicyFailure.class)
					.hasMessage("missing configuration");
				assertThat(codec.write(new EvaluationResult(v, policy))).doesNotContain("stackTrace", "suppressed");
			}
			else
				assertThat(restored.policyResult()).isEqualTo(policy);
		}
	}

	@Test
	void unsupportedOrContradictoryRecordsCannotReachPolicy() {
		var codec = new VerdictCodec();
		String good = codec.write(Verdict.single("judge", Judgment.pass("yes")));
		AtomicInteger calls = new AtomicInteger();
		Policy policy = v -> {
			calls.incrementAndGet();
			return new PolicyDecision(PolicyAction.RELY, "yes");
		};
		for (String invalid : List.of(good.replace("\"schemaVersion\":5", "\"schemaVersion\":99"),
				good.replaceFirst("\"producerStatus\":\"pass\"", "\"producerStatus\":\"fail\""),
				good.replace("\"declaredCardinality\":1", "\"declaredCardinality\":2")))
			assertThatThrownBy(() -> Evaluations.apply(codec.read(invalid), policy))
				.isInstanceOf(IllegalArgumentException.class);
		var unknown = Verdict.of(Judgment.pass("fabricated"),
				new LinkedHashMap<>(Map.of("a", Judgment.pass("yes"), "b", Judgment.pass("yes"))));
		assertThatThrownBy(() -> Evaluations.apply(unknown, policy)).hasMessageContaining("aggregation");
		assertThat(calls.get()).isZero();
	}

}
