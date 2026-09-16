/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury.interpretation;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.markpollack.judge.Judges;
import io.github.markpollack.judge.jury.CascadedJury;
import io.github.markpollack.judge.jury.ConsensusStrategy;
import io.github.markpollack.judge.jury.ErrorPolicy;
import io.github.markpollack.judge.jury.Jury;
import io.github.markpollack.judge.jury.NotApplicablePolicy;
import io.github.markpollack.judge.jury.SimpleJury;
import io.github.markpollack.judge.jury.TierPolicy;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.result.Judgment;

import static io.github.markpollack.judge.jury.interpretation.Fixtures.BOUNDARY_GOLDEN;
import static io.github.markpollack.judge.jury.interpretation.Fixtures.COMPOSITE_GOLDEN;
import static io.github.markpollack.judge.jury.interpretation.Fixtures.CONTEXT;
import static io.github.markpollack.judge.jury.interpretation.Fixtures.EXCLUSION;
import static io.github.markpollack.judge.jury.interpretation.Fixtures.MAPPER;
import static io.github.markpollack.judge.jury.interpretation.Fixtures.asMap;
import static io.github.markpollack.judge.jury.interpretation.Fixtures.golden;
import static io.github.markpollack.judge.jury.interpretation.Fixtures.opaqueExcludingTier;
import static io.github.markpollack.judge.jury.interpretation.Fixtures.passingTier;
import static io.github.markpollack.judge.jury.interpretation.Fixtures.readMap;
import static io.github.markpollack.judge.jury.interpretation.Fixtures.undecidedTier;
import static org.assertj.core.api.Assertions.assertThat;

/** A4–A7 and A9: the rules, on live verdicts and on their stored projections. */
@DisplayName("The rules on live verdicts")
class LiveRulesTest {

	private static Jury boundaryRejectingCascade() {
		return CascadedJury.builder()
			.tier("rubric", opaqueExcludingTier(Judgment.pass("a"), Judgment.fail("b")), TierPolicy.REJECT_ON_ANY_FAIL)
			.tier("semantic", passingTier("ok", "OK"), TierPolicy.FINAL_TIER)
			.build();
	}

	@Test
	@DisplayName("A4: a root error from a propagated judge error reads NOT_ASSESSED, never REJECTED")
	void aPropagatedJudgeErrorIsNotAssessed() {
		Verdict verdict = SimpleJury.builder()
			.judge(Judges.named(context -> Judgment.error("the index was unreachable"), "flaky"))
			.judge(Judges.named(context -> Judgment.pass("fine"), "ok"))
			.votingStrategy(new ConsensusStrategy())
			.build()
			.vote(CONTEXT);

		for (Interpretation interpretation : List.of(Verdicts.interpret(verdict), Verdicts.interpret(asMap(verdict)))) {
			assertThat(interpretation.reading()).isEqualTo(VerdictReading.NOT_ASSESSED);
			assertThat(interpretation.root().status()).isEqualTo("error");
			assertThat(interpretation.root().reasonCode()).isEqualTo("errors_propagated");
			assertThat(interpretation.root().judges()).extracting(JudgeSeat::name, JudgeSeat::status,
					JudgeSeat::reasonCode)
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
			.tier("only", undecidedTier(Judgment.pass("a"), Judgment.fail("b")), TierPolicy.FINAL_TIER)
			.build()
			.vote(CONTEXT);

		Interpretation interpretation = Verdicts.interpret(verdict);

		assertThat(interpretation.reading()).isEqualTo(VerdictReading.NOT_ASSESSED);
		assertThat(interpretation.root().reasonCode()).isEqualTo("no_tier_decided");
		assertThat(interpretation.decidedBy()).isNull();
		assertThat(interpretation.stages().get(0).usedByParent()).isFalse();
		assertThat(interpretation.stages().get(0).reason()).isEqualTo("child_undecided");
		assertThat(interpretation.defects()).isEmpty();
	}

	@Test
	@DisplayName("A5: an all-not-applicable roster reads NOT_APPLICABLE, and each exclusion is listed with its reason")
	void anAllNotApplicableRosterIsNotApplicable() {
		Verdict verdict = SimpleJury.builder()
			.judge(new Fixtures.Conditional("style", Judgment.notApplicable(EXCLUSION)))
			.judge(new Fixtures.Conditional("layout", Judgment.notApplicable("no layout to check")))
			.votingStrategy(new ConsensusStrategy(ErrorPolicy.PROPAGATE, NotApplicablePolicy.EXCLUDE))
			.build()
			.vote(CONTEXT);

		for (Interpretation interpretation : List.of(Verdicts.interpret(verdict), Verdicts.interpret(asMap(verdict)))) {
			assertThat(interpretation.reading()).isEqualTo(VerdictReading.NOT_APPLICABLE);
			assertThat(interpretation.root().status()).isEqualTo("not_applicable");
			assertThat(interpretation.root().judges()).extracting(JudgeSeat::name, JudgeSeat::status,
					JudgeSeat::reasoning)
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
			.judge(new Fixtures.Conditional("style", Judgment.notApplicable(EXCLUSION)))
			.judge(Judges.named(context -> Judgment.pass("compiled"), "build"))
			.votingStrategy(new ConsensusStrategy(ErrorPolicy.PROPAGATE, NotApplicablePolicy.EXCLUDE))
			.build()
			.vote(CONTEXT);

		Interpretation interpretation = Verdicts.interpret(verdict);

		assertThat(interpretation.reading()).isEqualTo(VerdictReading.ACCEPTED);
		assertThat(interpretation.root().judges().get(0).status()).isEqualTo("not_applicable");
		assertThat(interpretation.root().judges().get(0).reasoning()).isEqualTo(EXCLUSION);
		assertThat(interpretation.root().evidence().notApplicableCount()).isEqualTo(1);
		assertThat(interpretation.readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);
	}

	@Test
	@DisplayName("A6: the CHILD_UNDECIDED variant reads REJECTED, decided by the parent's tier")
	void theChildUndecidedVariant() {
		Verdict verdict = CascadedJury.builder()
			.tier("gate", undecidedTier(Judgment.pass("a"), Judgment.fail("b")), TierPolicy.REJECT_ON_ANY_FAIL)
			.tier("semantic", passingTier("ok", "OK"), TierPolicy.FINAL_TIER)
			.build()
			.vote(CONTEXT);

		for (Interpretation interpretation : List.of(Verdicts.interpret(verdict), Verdicts.interpret(asMap(verdict)))) {
			assertThat(interpretation.reading()).isEqualTo(VerdictReading.REJECTED);
			assertThat(interpretation.decidedBy()).isEqualTo(new DecidedBy("gate", List.of("gate"), "individual_rejection"));
			assertThat(interpretation.root().status()).as("no FAIL is manufactured").isEqualTo("error");
			assertThat(interpretation.root().reasonCode()).isEqualTo("aggregation_failed");
			assertThat(interpretation.stages()).extracting(Stage::stage).containsExactly("gate");
			assertThat(interpretation.stages().get(0).reason()).isEqualTo("child_undecided");
			assertThat(interpretation.readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);
			assertThat(interpretation.defects()).isEmpty();
		}
	}

	@Test
	@DisplayName("A6: the UNDECLARED_NOT_APPLICABLE variant reads REJECTED, decided by the parent's tier")
	void theUndeclaredNotApplicableVariant() {
		Verdict verdict = boundaryRejectingCascade().vote(CONTEXT);

		for (Interpretation interpretation : List.of(Verdicts.interpret(verdict), Verdicts.interpret(asMap(verdict)))) {
			assertThat(interpretation.reading()).isEqualTo(VerdictReading.REJECTED);
			assertThat(interpretation.decidedBy())
				.isEqualTo(new DecidedBy("rubric", List.of("rubric"), "individual_rejection"));
			assertThat(interpretation.root().reasonCode()).isEqualTo("stage_failed");
			assertThat(interpretation.stages().get(0).status()).as("the child's own claim is kept").isEqualTo("not_applicable");
			assertThat(interpretation.stages().get(0).reason()).isEqualTo("undeclared_not_applicable");
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
			builder.tier("inner", boundaryRejectingCascade(), TierPolicy.FINAL_TIER);
		}
		else {
			builder.tier("inner", boundaryRejectingCascade(), TierPolicy.REJECT_ON_ANY_FAIL)
				.tier("outer-final", passingTier("ok", "OK"), TierPolicy.FINAL_TIER);
		}
		Verdict verdict = builder.build().vote(CONTEXT);

		Interpretation interpretation = Verdicts.interpret(verdict);

		assertThat(interpretation.reading()).isEqualTo(VerdictReading.REJECTED);
		assertThat(interpretation.decidedBy())
			.isEqualTo(new DecidedBy("rubric", List.of("inner", "rubric"), "individual_rejection"));
		assertThat(interpretation.stages()).extracting(Stage::path).containsExactly(List.of("inner"),
				List.of("inner", "rubric"));
		assertThat(interpretation.stages().get(0).usedByParent()).isTrue();
		assertThat(interpretation.readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);
		assertThat(interpretation.defects()).isEmpty();
	}

	@Test
	@DisplayName("A7: a root individual_rejection reports the tier it names")
	void aRootIndividualRejectionNamesItsTier() {
		Interpretation interpretation = Verdicts.interpret(golden(BOUNDARY_GOLDEN));

		assertThat(interpretation.decidedBy())
			.isEqualTo(new DecidedBy("rubric", List.of("rubric"), "individual_rejection"));
		assertThat(interpretation.reading()).isEqualTo(VerdictReading.REJECTED);
	}

	@Test
	@DisplayName("A7: an own root reports decidedBy null with no defect")
	void anOwnRootNamesNoStage() {
		Interpretation interpretation = Verdicts.interpret(passingTier("ok", "OK").vote(CONTEXT));

		assertThat(interpretation.decidedBy()).isNull();
		assertThat(interpretation.defects()).isEmpty();
		assertThat(interpretation.reading()).isEqualTo(VerdictReading.ACCEPTED);
		assertThat(interpretation.readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);
	}

	@Test
	@DisplayName("an outcome adopted through two tiers is decided by the last edge, with basis tier_outcome")
	void anAdoptedOutcomeIsDecidedByTheLastEdge() {
		Jury inner = CascadedJury.builder().tier("leaf", passingTier("ok", "OK"), TierPolicy.FINAL_TIER).build();
		Verdict verdict = CascadedJury.builder().tier("outer-tier", inner, TierPolicy.FINAL_TIER).build().vote(CONTEXT);

		Interpretation interpretation = Verdicts.interpret(verdict);

		assertThat(interpretation.decidedBy()).isEqualTo(new DecidedBy("leaf", List.of("outer-tier", "leaf"), "tier_outcome"));
		assertThat(interpretation.reading()).isEqualTo(VerdictReading.ACCEPTED);
		assertThat(interpretation.readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);
	}

	@Test
	@DisplayName("a meta-jury whose member failed reads NOT_ASSESSED and lists the failed member")
	void aMetaJuryStageFailure() {
		Interpretation interpretation = Verdicts.interpret(golden(COMPOSITE_GOLDEN));

		assertThat(interpretation.reading()).isEqualTo(VerdictReading.NOT_ASSESSED);
		assertThat(interpretation.decidedBy()).isNull();
		assertThat(interpretation.stages()).extracting(Stage::path).containsExactly(List.of("pipeline"),
				List.of("pipeline", "broken-check"), List.of("pipeline", "semantic-check"), List.of("audit"));
		assertThat(interpretation.stages()).extracting(Stage::failure).containsExactly(null, "jury_execution_failed",
				null, "jury_execution_failed");
		assertThat(interpretation.stages()).extracting(Stage::usedByParent).containsExactly(true, false, true, false);
		assertThat(interpretation.readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);
		assertThat(interpretation.defects()).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { COMPOSITE_GOLDEN, BOUNDARY_GOLDEN })
	@DisplayName("A9: the live and stored paths agree on every 0.17 golden")
	void theTwoPathsAgree(String resource) throws Exception {
		Interpretation live = Verdicts.interpret(golden(resource));
		Interpretation fromMap = Verdicts.interpret(readMap(resource));

		assertThat(live).isEqualTo(fromMap);
		assertThat(MAPPER.writeValueAsString(live)).isEqualTo(MAPPER.writeValueAsString(fromMap));
		assertThat(live.sourceVersion()).isEqualTo(1);
		assertThat(live.defects()).isEmpty();
	}

}
