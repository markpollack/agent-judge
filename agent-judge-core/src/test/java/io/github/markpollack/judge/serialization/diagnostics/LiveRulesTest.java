/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.serialization.diagnostics;

import io.github.markpollack.judge.completion.CompletionEvidence;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.markpollack.judge.Judges;
import io.github.markpollack.judge.jury.CascadedJury;
import io.github.markpollack.judge.jury.ConsensusStrategy;
import io.github.markpollack.judge.jury.ErrorHandling;
import io.github.markpollack.judge.jury.Jury;
import io.github.markpollack.judge.jury.ExclusionHandling;
import io.github.markpollack.judge.jury.SimpleJury;
import io.github.markpollack.judge.jury.RoutingRule;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.judgment.Judgment;

import static io.github.markpollack.judge.serialization.diagnostics.Fixtures.BOUNDARY_GOLDEN;
import static io.github.markpollack.judge.serialization.diagnostics.Fixtures.COMPOSITE_GOLDEN;
import static io.github.markpollack.judge.serialization.diagnostics.Fixtures.CONTEXT;
import static io.github.markpollack.judge.serialization.diagnostics.Fixtures.EXCLUSION;
import static io.github.markpollack.judge.serialization.diagnostics.Fixtures.MAPPER;
import static io.github.markpollack.judge.serialization.diagnostics.Fixtures.asMap;
import static io.github.markpollack.judge.serialization.diagnostics.Fixtures.opaqueExcludingTier;
import static io.github.markpollack.judge.serialization.diagnostics.Fixtures.passingTier;
import static io.github.markpollack.judge.serialization.diagnostics.Fixtures.readMap;
import static io.github.markpollack.judge.serialization.diagnostics.Fixtures.undecidedTier;
import static org.assertj.core.api.Assertions.assertThat;

/** A4–A7 and A9: the rules, on live verdicts and on their stored projections. */
@DisplayName("The rules on live verdicts")
class LiveRulesTest {

	private static Jury boundaryRejectingCascade() {
		return CascadedJury.builder()
			.tier("rubric", undecidedTier(Judgment.pass("a"), Judgment.fail("b")), RoutingRule.STOP_ON_ANY_OPINION_FAIL)
			.tier("semantic", passingTier("ok", "OK"), RoutingRule.FINAL_TIER)
			.build();
	}

	@Test
	@DisplayName("A4: a root error from a propagated judge error reads NOT_ASSESSED, never REJECTED")
	void aPropagatedJudgeErrorIsNotAssessed() {
		Verdict verdict = SimpleJury.builder()
			.judge(Judges.named(() -> Judgment.error("the index was unreachable"), "flaky"))
			.judge(Judges.named(() -> Judgment.pass("fine"), "ok"))
			.votingStrategy(new ConsensusStrategy())
			.build()
			.vote();

		for (StoredReading interpretation : List.of(StoredVerdicts.interpret(verdict),
				StoredVerdicts.interpret(asMap(verdict)))) {
			assertThat(interpretation.outcome()).isEqualTo(RequirementOutcome.NOT_ASSESSED);
			assertThat(interpretation.root().status()).isEqualTo("error");
			assertThat(interpretation.root().reasonCode()).isEqualTo("errors_propagated");
			assertThat(interpretation.root().judges())
				.extracting(JudgeSeat::name, JudgeSeat::status, JudgeSeat::reasonCode)
				.containsExactly(org.assertj.core.groups.Tuple.tuple("flaky", "error", "judge_reported"),
						org.assertj.core.groups.Tuple.tuple("ok", "pass", null));
			assertThat(interpretation.readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);
			assertThat(interpretation.defects()).isEmpty();
		}
	}

	@Test
	@DisplayName("A4: a cascade that decided nothing reads NOT_ASSESSED, not UNDECIDED")
	void noTierDecidedIsNotAssessed() {
		Verdict verdict = CascadedJury.builder()
			.tier("only", undecidedTier(Judgment.pass("a"), Judgment.fail("b")), RoutingRule.FINAL_TIER)
			.build()
			.vote();

		StoredReading interpretation = StoredVerdicts.interpret(verdict);

		assertThat(interpretation.outcome()).isEqualTo(RequirementOutcome.NOT_ASSESSED);
		assertThat(interpretation.root().reasonCode()).isEqualTo("aggregation_failed");
		assertThat(interpretation.decidedBy()).isEqualTo(new DecidedBy("only", List.of("only"), "tier_outcome"));
		assertThat(interpretation.stages().get(0).usedByParent()).isTrue();
		assertThat(interpretation.stages().get(0).reason()).isNull();
		assertThat(interpretation.defects()).isEmpty();
	}

	@Test
	@DisplayName("A5: an all-not-applicable roster reads NOT_APPLICABLE, and each exclusion is listed with its reason")
	void anAllNotApplicableRosterIsNotApplicable() {
		Verdict verdict = SimpleJury.builder()
			.seat(io.github.markpollack.judge.jury.JudgeSeat
				.named("style", new Fixtures.Conditional("style", Judgment.notApplicable(EXCLUSION)))
				.notApplicableWhen(EXCLUSION))
			.seat(io.github.markpollack.judge.jury.JudgeSeat
				.named("layout", new Fixtures.Conditional("layout", Judgment.notApplicable("no layout to check")))
				.notApplicableWhen(EXCLUSION))
			.votingStrategy(new ConsensusStrategy(ErrorHandling.PROPAGATE, ExclusionHandling.EXCLUDE))
			.build()
			.vote();

		for (StoredReading interpretation : List.of(StoredVerdicts.interpret(verdict),
				StoredVerdicts.interpret(asMap(verdict)))) {
			assertThat(interpretation.outcome()).isEqualTo(RequirementOutcome.NOT_APPLICABLE);
			assertThat(interpretation.root().status()).isEqualTo("not_applicable");
			assertThat(interpretation.root().judges())
				.extracting(JudgeSeat::name, JudgeSeat::status, JudgeSeat::reasoning)
				.containsExactly(org.assertj.core.groups.Tuple.tuple("style", "not_applicable", EXCLUSION),
						org.assertj.core.groups.Tuple.tuple("layout", "not_applicable", "no layout to check"));
			assertThat(interpretation.readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);
			assertThat(interpretation.defects()).isEmpty();
		}
	}

	@Test
	@DisplayName("A5: an excluded judge beside a passing one is listed as not applicable, not as a failure")
	void anExcludedJudgeIsNotAFailure() {
		Verdict verdict = SimpleJury.builder()
			.seat(io.github.markpollack.judge.jury.JudgeSeat
				.named("style", new Fixtures.Conditional("style", Judgment.notApplicable(EXCLUSION)))
				.notApplicableWhen(EXCLUSION))
			.judge(Judges.named(() -> Judgment.pass("compiled"), "build"))
			.votingStrategy(new ConsensusStrategy(ErrorHandling.PROPAGATE, ExclusionHandling.EXCLUDE))
			.build()
			.vote();

		StoredReading interpretation = StoredVerdicts.interpret(verdict);

		assertThat(interpretation.outcome()).isEqualTo(RequirementOutcome.SATISFIED);
		assertThat(interpretation.root().judges().get(0).status()).isEqualTo("not_applicable");
		assertThat(interpretation.root().judges().get(0).reasoning()).isEqualTo(EXCLUSION);
		assertThat(interpretation.root().evidence().notApplicableCount()).isEqualTo(1);
		assertThat(interpretation.readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);
	}

	@Test
	@DisplayName("A6: the CHILD_UNDECIDED variant reads REJECTED, decided by the parent's tier")
	void theChildUndecidedVariant() {
		Verdict verdict = CascadedJury.builder()
			.tier("gate", undecidedTier(Judgment.pass("a"), Judgment.fail("b")), RoutingRule.STOP_ON_ANY_OPINION_FAIL)
			.tier("semantic", passingTier("ok", "OK"), RoutingRule.FINAL_TIER)
			.build()
			.vote();

		for (StoredReading interpretation : List.of(StoredVerdicts.interpret(verdict),
				StoredVerdicts.interpret(asMap(verdict)))) {
			assertThat(interpretation.outcome()).isEqualTo(RequirementOutcome.VIOLATED);
			assertThat(interpretation.decidedBy())
				.isEqualTo(new DecidedBy("gate", List.of("gate"), "individual_rejection"));
			assertThat(interpretation.root().status()).as("no FAIL is manufactured").isEqualTo("error");
			assertThat(interpretation.root().reasonCode()).isEqualTo("aggregation_failed");
			assertThat(interpretation.stages()).extracting(Stage::stage).containsExactly("gate");
			assertThat(interpretation.stages().get(0).reason()).isNull();
			assertThat(interpretation.readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);
			assertThat(interpretation.defects()).isEmpty();
		}
	}

	@Test
	@DisplayName("A6: the UNDECLARED_NOT_APPLICABLE variant reads REJECTED, decided by the parent's tier")
	void theUndeclaredNotApplicableVariant() {
		Verdict verdict = boundaryRejectingCascade().vote();

		for (StoredReading interpretation : List.of(StoredVerdicts.interpret(verdict),
				StoredVerdicts.interpret(asMap(verdict)))) {
			assertThat(interpretation.outcome()).isEqualTo(RequirementOutcome.VIOLATED);
			assertThat(interpretation.decidedBy())
				.isEqualTo(new DecidedBy("rubric", List.of("rubric"), "individual_rejection"));
			assertThat(interpretation.root().reasonCode()).isEqualTo("aggregation_failed");
			assertThat(interpretation.stages().get(0).status()).as("the child's own claim is kept").isEqualTo("error");
			assertThat(interpretation.stages().get(0).reason()).isNull();
			assertThat(interpretation.readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);
			assertThat(interpretation.defects()).isEmpty();
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	@DisplayName("A6: a rejecting cascade nested in another is decided by the inner tier, at its full path")
	void aNestedRejection(boolean asFinalTier) {
		CascadedJury.Builder builder = CascadedJury.builder();
		if (asFinalTier) {
			builder.tier("inner", boundaryRejectingCascade(), RoutingRule.FINAL_TIER);
		}
		else {
			builder.tier("inner", boundaryRejectingCascade(), RoutingRule.STOP_ON_ANY_OPINION_FAIL)
				.tier("outer-final", passingTier("ok", "OK"), RoutingRule.FINAL_TIER);
		}
		Verdict verdict = builder.build().vote();

		StoredReading interpretation = StoredVerdicts.interpret(verdict);

		assertThat(interpretation.outcome()).isEqualTo(RequirementOutcome.VIOLATED);
		assertThat(interpretation.decidedBy())
			.isEqualTo(new DecidedBy("rubric", List.of("inner", "rubric"), "individual_rejection"));
		assertThat(interpretation.stages()).extracting(Stage::path)
			.containsExactly(List.of("inner"), List.of("inner", "rubric"));
		assertThat(interpretation.stages().get(0).usedByParent()).isTrue();
		assertThat(interpretation.readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);
		assertThat(interpretation.defects()).isEmpty();
	}

	@Test
	@DisplayName("A7: a root individual_rejection reports the tier it names")
	void aRootIndividualRejectionNamesItsTier() {
		StoredReading interpretation = StoredVerdicts.interpret(Fixtures.readMap(BOUNDARY_GOLDEN));

		assertThat(interpretation.decidedBy())
			.isEqualTo(new DecidedBy("rubric", List.of("rubric"), "individual_rejection"));
		assertThat(interpretation.outcome()).isEqualTo(RequirementOutcome.VIOLATED);
	}

	@Test
	@DisplayName("A7: an own root reports decidedBy null with no defect")
	void anOwnRootNamesNoStage() {
		StoredReading interpretation = StoredVerdicts.interpret(twoSeatPassingReduction().vote());

		assertThat(interpretation.decidedBy()).isNull();
		assertThat(interpretation.defects()).isEmpty();
		assertThat(interpretation.outcome()).isEqualTo(RequirementOutcome.SATISFIED);
		assertThat(interpretation.readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);
	}

	@Test
	@DisplayName("an outcome adopted through two tiers is decided by the last edge, with basis tier_outcome")
	void anAdoptedOutcomeIsDecidedByTheLastEdge() {
		Jury inner = CascadedJury.builder().tier("leaf", twoSeatPassingReduction(), RoutingRule.FINAL_TIER).build();
		Verdict verdict = CascadedJury.builder().tier("outer-tier", inner, RoutingRule.FINAL_TIER).build().vote();

		StoredReading interpretation = StoredVerdicts.interpret(verdict);

		assertThat(interpretation.decidedBy())
			.isEqualTo(new DecidedBy("leaf", List.of("outer-tier", "leaf"), "tier_outcome"));
		assertThat(interpretation.outcome()).isEqualTo(RequirementOutcome.SATISFIED);
		assertThat(interpretation.readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);
	}

	@Test
	@DisplayName("a meta-jury whose member failed reads NOT_ASSESSED and lists the failed member")
	void aMetaJuryStageFailure() {
		StoredReading interpretation = StoredVerdicts.interpret(Fixtures.readMap(COMPOSITE_GOLDEN));

		assertThat(interpretation.outcome()).isEqualTo(RequirementOutcome.NOT_ASSESSED);
		assertThat(interpretation.decidedBy()).isNull();
		assertThat(interpretation.stages()).extracting(Stage::path)
			.containsExactly(List.of("pipeline"), List.of("pipeline", "broken-check"),
					List.of("pipeline", "semantic-check"), List.of("audit"));
		assertThat(interpretation.stages()).extracting(Stage::failure)
			.containsExactly(null, "jury_execution_failed", null, "jury_execution_failed");
		assertThat(interpretation.stages()).extracting(Stage::usedByParent).containsExactly(true, false, true, false);
		assertThat(interpretation.readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);
		assertThat(interpretation.defects()).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { COMPOSITE_GOLDEN, BOUNDARY_GOLDEN })
	@DisplayName("A9: the live and stored paths agree on every 0.17 golden")
	void theTwoPathsAgree(String resource) throws Exception {
		StoredReading live = StoredVerdicts.interpret(Fixtures.mutableCopy(readMap(resource)));
		StoredReading fromMap = StoredVerdicts.interpret(readMap(resource));

		assertThat(live).isEqualTo(fromMap);
		assertThat(MAPPER.writeValueAsString(live)).isEqualTo(MAPPER.writeValueAsString(fromMap));
		assertThat(live.sourceVersion()).isEqualTo(1);
		assertThat(live.defects()).isEmpty();
	}

	/** These legacy-reader tests exercise an explicit reduction, not modern identity. */
	private static Jury twoSeatPassingReduction() {
		return io.github.markpollack.judge.jury.SimpleJury.builder()
			.judge(io.github.markpollack.judge.Judges.named(() -> Judgment.pass("first"), "first"))
			.judge(io.github.markpollack.judge.Judges.named(() -> Judgment.pass("second"), "second"))
			.votingStrategy(new io.github.markpollack.judge.jury.ConsensusStrategy())
			.build();
	}

}
