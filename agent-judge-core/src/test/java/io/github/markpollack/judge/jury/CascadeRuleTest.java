/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import io.github.markpollack.judge.verdict.AttemptDisposition;
import io.github.markpollack.judge.verdict.CompositeAttempt;
import io.github.markpollack.judge.verdict.CompositeRelation;
import io.github.markpollack.judge.verdict.DispositionReason;
import io.github.markpollack.judge.verdict.RoutingDecision;
import io.github.markpollack.judge.verdict.RoutingRule;
import io.github.markpollack.judge.verdict.Seat;
import io.github.markpollack.judge.verdict.Verdict;
import io.github.markpollack.judge.verdict.VerdictProvenance;
import io.github.markpollack.judge.verdict.VerdictProvenanceBasis;
import io.github.markpollack.judge.verdict.VerdictProvenanceKind;
import io.github.markpollack.judge.voting.AggregationEvidence;
import io.github.markpollack.judge.voting.AllEligiblePassStrategy;
import io.github.markpollack.judge.voting.ConsensusStrategy;
import io.github.markpollack.judge.voting.ErrorHandling;
import io.github.markpollack.judge.voting.ExclusionHandling;
import io.github.markpollack.judge.voting.VotingStrategy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.markpollack.judge.JudgeMetadata;
import io.github.markpollack.judge.JudgeType;
import io.github.markpollack.judge.JudgeWithMetadata;
import io.github.markpollack.judge.Judges;
import io.github.markpollack.judge.completion.CompletionEvidence;
import io.github.markpollack.judge.verdict.KeySource;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentReasonCode;
import io.github.markpollack.judge.judgment.JudgmentStatus;

import static io.github.markpollack.judge.jury.ContainmentTest.returning;
import static io.github.markpollack.judge.jury.ContainmentTest.throwing;
import static io.github.markpollack.judge.jury.ContainmentTest.undecidedVerdict;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The single cascade rule.
 *
 * <p>
 * A tier can do three things: throw, return a verdict the cascade cannot use, or return
 * one it can. The rule for the middle case is asymmetric, and that asymmetry is the
 * design's central claim. A tier that did not finish may still have established a genuine
 * individual FAIL along the way, and one established violation is enough to reject. It is
 * never enough to accept: a broken reduction cannot demonstrate that a subject is fine.
 * </p>
 *
 * <p>
 * When the cascade does stop that way, it manufactures no FAIL and no score. The
 * rejection is carried by the <em>provenance</em> —
 * {@code TIER(name, INDIVIDUAL_REJECTION)} — while the root aggregate stays an instrument
 * failure. A reader therefore counts one machinery failure and classifies the item as
 * non-pass, rather than reading an error as though the subject had been assessed and
 * found wanting.
 * </p>
 */
@DisplayName("The cascade rule")
class CascadeRuleTest {

	private static final CompletionEvidence CONTEXT = CompletionEvidence.builder().request("cascade").build();

	private static final String EXCLUSION = "the change set contains no Java sources";

	/** A judge that declares it may exclude, so a jury built on it is capable. */
	private record Conditional(String name, Judgment result) implements JudgeWithMetadata {

		@Override
		public Judgment judge() {
			return this.result;
		}

		@Override
		public JudgeMetadata metadata() {
			return new JudgeMetadata(this.name, "conditional", JudgeType.DETERMINISTIC, EXCLUSION);
		}

	}

	/** A leaf jury whose reduction throws, so the tier returns an undecided verdict. */
	private static Jury undecidedTier(Judgment... judgments) {
		SimpleJury.Builder builder = SimpleJury.builder().votingStrategy(new VotingStrategy() {
			@Override
			public Judgment aggregate(List<io.github.markpollack.judge.voting.Ballot> ballots) {
				var input = io.github.markpollack.judge.voting.Ballots.judgments(ballots);
				throw new IllegalStateException("the reduction broke");
			}

			@Override
			public String getName() {
				return "broken";
			}
		});
		for (int index = 0; index < judgments.length; index++) {
			Judgment judgment = judgments[index];
			builder.judge(Judges.named(() -> judgment, "judge-" + (index + 1)));
		}
		return builder.build();
	}

	/** A leaf jury whose reduction returns null. */
	private static Jury nullReducingTier(Judgment... judgments) {
		SimpleJury.Builder builder = SimpleJury.builder().votingStrategy(new VotingStrategy() {
			@Override
			public Judgment aggregate(List<io.github.markpollack.judge.voting.Ballot> ballots) {
				var input = io.github.markpollack.judge.voting.Ballots.judgments(ballots);
				return null;
			}

			@Override
			public String getName() {
				return "silent";
			}
		});
		for (int index = 0; index < judgments.length; index++) {
			Judgment judgment = judgments[index];
			builder.judge(Judges.named(() -> judgment, "judge-" + (index + 1)));
		}
		return builder.build();
	}

	/**
	 * An opaque tier that returns an excluded aggregate over real individuals while
	 * declaring no capability, so a parent must refuse it.
	 */
	private static Jury opaqueExcludingTier(Judgment... individuals) {
		return returning(Verdict.of(Judgment.notApplicable(EXCLUSION), namedOf(individuals)));
	}

	private static Map<String, Judgment> namedOf(Judgment... individuals) {
		java.util.Map<String, Judgment> byName = new java.util.LinkedHashMap<>();
		for (int index = 0; index < individuals.length; index++) {
			byName.put("judge-" + (index + 1), individuals[index]);
		}
		return byName;
	}

	private static List<Seat> seatsFor(int count) {
		List<Seat> seats = new java.util.ArrayList<>(count);
		for (int index = 0; index < count; index++) {
			seats.add(new Seat(index, "judge-" + (index + 1), KeySource.DECLARED));
		}
		return seats;
	}

	private static Jury passingTier(String reasoning) {
		return SimpleJury.builder()
			.judge(Judges.named(() -> Judgment.pass(reasoning), "ok"))
			.votingStrategy(new ConsensusStrategy())
			.build();
	}

	/**
	 * The cascade tests 10 and 11 share: a "rubric" tier that excludes without declaring
	 * it may, over a genuine leaf FAIL, so the cascade stops on that established
	 * rejection with a parent-built {@code ERROR stage_failed} root and never reaches its
	 * final tier.
	 * @return the inner cascade
	 */
	private static Jury acceptedRejectingCascade(Jury finalTier) {
		return CascadedJury.builder()
			.tier("rubric", undecidedTier(Judgment.pass("a"), Judgment.fail("b")), RoutingRule.STOP_ON_ANY_OPINION_FAIL)
			.tier("semantic", finalTier, RoutingRule.FINAL_TIER)
			.build();
	}

	/**
	 * Assert that a recorded child really did stop on a boundary-rejected D1.
	 * <p>
	 * A parent that only inspects its own outcome cannot tell this apart from a cascade
	 * that refused the exclusion and then walked on to a passing final tier, because both
	 * leave the parent a usable child. So the rule is asserted where it happens: the D1
	 * provenance, the parent-built machinery root, the refused child kept unchanged, and
	 * the genuine FAIL that established the rejection.
	 * </p>
	 * @param inner the child cascade's verdict, as the parent recorded it
	 */
	private static void assertIsAcceptedUndecidedRejection(Verdict inner) {
		assertThat(inner.provenance()).as("the inner cascade stopped on the rejection the refused tier had established")
			.isEqualTo(new VerdictProvenance(VerdictProvenanceKind.TIER, "rubric",
					VerdictProvenanceBasis.INDIVIDUAL_REJECTION));
		assertThat(inner.judgment().status()).as("no FAIL is manufactured").isEqualTo(JudgmentStatus.ERROR);
		assertThat(inner.judgment().reasonCode()).as("the root is the parent-built machinery error")
			.isEqualTo(JudgmentReasonCode.AGGREGATION_FAILED);
		assertThat(inner.judgment().score()).as("and no score of zero either").isNull();

		assertThat(inner.compositeAttempts()).extracting(CompositeAttempt::name)
			.as("the cascade stopped at the rubric tier, so its final tier never ran")
			.containsExactly("rubric");
		CompositeAttempt refused = inner.compositeAttempts().get(0);
		assertThat(refused.disposition()).isEqualTo(AttemptDisposition.USED);
		assertThat(refused.dispositionReason()).isNull();
		assertThat(refused.verdict().judgment().status()).as("the child's own claim is kept unchanged")
			.isEqualTo(JudgmentStatus.ERROR);
		assertThat(inner.individual()).extracting(Judgment::status)
			.as("the rejecting tier's individuals are copied, FAIL included")
			.containsExactly(JudgmentStatus.PASS, JudgmentStatus.FAIL);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> evidenceOf(Judgment judgment) {
		Object block = judgment.metadata().get(Judgment.AGGREGATION_KEY);
		assertThat(block).as("the aggregate carries an evidence block").isInstanceOf(Map.class);
		return (Map<String, Object>) block;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> originOf(Judgment judgment) {
		Object origin = evidenceOf(judgment).get(AggregationEvidence.ERROR_CODE_COUNTS);
		assertThat(origin).as("the aggregate names the causes it counted").isInstanceOf(Map.class);
		return (Map<String, Object>) origin;
	}

	@Test
	@DisplayName("1. a genuine FAIL plus a broken reduction stops the cascade with a rejection")
	void genuineFailPlusBrokenReductionStops() {
		Verdict verdict = CascadedJury.builder()
			.tier("gate", undecidedTier(Judgment.pass("a"), Judgment.fail("b")), RoutingRule.STOP_ON_ANY_OPINION_FAIL)
			.tier("semantic", passingTier("OK"), RoutingRule.FINAL_TIER)
			.build()
			.vote();

		assertThat(verdict.provenance()).isEqualTo(
				new VerdictProvenance(VerdictProvenanceKind.TIER, "gate", VerdictProvenanceBasis.INDIVIDUAL_REJECTION));
		assertThat(verdict.judgment().reasonCode()).as("the child's own machinery error is kept")
			.isEqualTo(JudgmentReasonCode.AGGREGATION_FAILED);
		assertThat(verdict.judgment().status()).isNotEqualTo(JudgmentStatus.FAIL);
		assertThat(verdict.individual()).extracting(Judgment::status)
			.containsExactly(JudgmentStatus.PASS, JudgmentStatus.FAIL);
		assertThat(verdict.compositeAttempts()).extracting(CompositeAttempt::name).containsExactly("gate");
		assertThat(verdict.compositeAttempts().get(0).dispositionReason()).isNull();
	}

	@Test
	@DisplayName("2. the same tier under STOP_ON_ALL_OPINIONS_PASS escalates: a broken stage accepts nothing")
	void acceptOnAllPassNeverStopsOnAFailedStage() {
		Verdict verdict = CascadedJury.builder()
			.tier("structural", undecidedTier(Judgment.pass("a"), Judgment.fail("b")),
					RoutingRule.STOP_ON_ALL_OPINIONS_PASS)
			.tier("semantic", passingTier("OK"), RoutingRule.FINAL_TIER)
			.build()
			.vote();

		assertThat(verdict.provenance())
			.isEqualTo(VerdictProvenance.tier("semantic", VerdictProvenanceBasis.TIER_OUTCOME));
		assertThat(verdict.judgment().status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(verdict.compositeAttempts()).extracting(CompositeAttempt::name)
			.containsExactly("structural", "semantic");
		assertThat(verdict.compositeAttempts().get(0).disposition()).as("the failure is still counted")
			.isEqualTo(AttemptDisposition.USED);
	}

	@Test
	@DisplayName("3. an undecided final tier decides nothing at all")
	void anUndecidedFinalTierDecidesNothing() {
		Verdict verdict = CascadedJury.builder()
			.tier("only", undecidedTier(Judgment.pass("a"), Judgment.fail("b")), RoutingRule.FINAL_TIER)
			.build()
			.vote();

		assertThat(verdict.judgment().reasonCode()).isEqualTo(JudgmentReasonCode.AGGREGATION_FAILED);
		assertThat(verdict.provenance()).isEqualTo(VerdictProvenance.tier("only", VerdictProvenanceBasis.TIER_OUTCOME));
		assertThat(verdict.individual()).hasSize(2);
		assertThat(verdict.seats()).hasSize(2);
		assertThat(verdict.compositeAttempts()).hasSize(1);
	}

	@Test
	@DisplayName("4. a meta tier with one good failing member and one broken member stops on the rejection")
	void nestedMetaWithAGenuineFailStops() {
		Jury failingMember = SimpleJury.builder()
			.judge(Judges.named(() -> Judgment.fail("a requirement was not met"), "strict"))
			.votingStrategy(new ConsensusStrategy())
			.build();
		Jury tier = Juries.meta(new ConsensusStrategy(ErrorHandling.IGNORE, ExclusionHandling.EXCLUDE),
				new NamedJury("strict", failingMember),
				new NamedJury("broken", throwing(new IllegalStateException("boom"))));

		Verdict verdict = CascadedJury.builder()
			.tier("review", tier, RoutingRule.STOP_ON_ANY_OPINION_FAIL)
			.tier("semantic", passingTier("OK"), RoutingRule.FINAL_TIER)
			.build()
			.vote();

		assertThat(verdict.provenance()).isEqualTo(new VerdictProvenance(VerdictProvenanceKind.TIER, "review",
				VerdictProvenanceBasis.INDIVIDUAL_REJECTION));
		assertThat(verdict.judgment().reasonCode()).isEqualTo(JudgmentReasonCode.STAGE_FAILED);
		assertThat(verdict.individual()).extracting(Judgment::status).containsExactly(JudgmentStatus.FAIL);
	}

	@Nested
	@DisplayName("5. an opaque tier that excludes without declaring it may")
	class BoundaryRefusedExclusion {

		@Test
		@DisplayName("as the sole final tier, nothing is decided, and the refusal is countable")
		void soleFinalTier() {
			Verdict verdict = CascadedJury.builder()
				.tier("only", opaqueExcludingTier(Judgment.pass("a")), RoutingRule.FINAL_TIER)
				.build()
				.vote();

			assertThat(verdict.judgment().reasonCode()).isEqualTo(JudgmentReasonCode.NO_TIER_DECIDED);
			assertThat(verdict.provenance()).isEqualTo(VerdictProvenance.undecided());
			assertThat(verdict.compositeAttempts().get(0).dispositionReason())
				.isEqualTo(DispositionReason.INVALID_TIER_RESULT);
		}

		@Test
		@DisplayName("as a non-final tier it escalates, and the refusal stays countable when a later tier passes")
		void nonFinalTierFollowedByAPass() {
			Verdict verdict = CascadedJury.builder()
				.tier("rubric", opaqueExcludingTier(Judgment.pass("a")), RoutingRule.STOP_ON_ANY_OPINION_FAIL)
				.tier("semantic", passingTier("OK"), RoutingRule.FINAL_TIER)
				.build()
				.vote();

			assertThat(verdict.judgment().status()).isEqualTo(JudgmentStatus.PASS);
			assertThat(verdict.judgment().reasoning())
				.as("the root reasoning is the later tier's, not a note about the refusal")
				.isEqualTo("OK")
				.doesNotContain("NOT_APPLICABLE");
			CompositeAttempt refused = verdict.compositeAttempts().get(0);
			assertThat(refused.disposition()).isEqualTo(AttemptDisposition.STAGE_FAILED);
			assertThat(refused.dispositionReason()).isEqualTo(DispositionReason.INVALID_TIER_RESULT);
			assertThat(refused.verdict().judgment().status()).as("the child's own claim is unchanged")
				.isEqualTo(JudgmentStatus.NOT_APPLICABLE);
		}

		@Test
		@DisplayName("a tier that did declare the capability is honoured, not refused")
		void aDeclaredExclusionIsUsed() {
			Jury capable = SimpleJury.builder()
				.seat(declared(new Conditional("conditional", Judgment.notApplicable(EXCLUSION))))
				.votingStrategy(new ConsensusStrategy(ErrorHandling.PROPAGATE, ExclusionHandling.EXCLUDE))
				.build();

			Verdict verdict = CascadedJury.builder().tier("rubric", capable, RoutingRule.FINAL_TIER).build().vote();

			assertThat(verdict.judgment().status()).isEqualTo(JudgmentStatus.NOT_APPLICABLE);
			assertThat(verdict.provenance())
				.isEqualTo(VerdictProvenance.tier("rubric", VerdictProvenanceBasis.TIER_OUTCOME));
			assertThat(verdict.compositeAttempts().get(0).disposition()).isEqualTo(AttemptDisposition.USED);
		}

	}

	@Nested
	@DisplayName("6. a cascade inside a cascade")
	class NestedCascades {

		@Test
		@DisplayName("an inner rejection is a determination, adopted as a tier outcome without re-rejecting")
		void anInnerRejectionIsAdopted() {
			Jury inner = CascadedJury.builder()
				.tier("gate", undecidedTier(Judgment.pass("a"), Judgment.fail("b")),
						RoutingRule.STOP_ON_ANY_OPINION_FAIL)
				.tier("unused", passingTier("never reached"), RoutingRule.FINAL_TIER)
				.build();

			Verdict verdict = CascadedJury.builder().tier("inner", inner, RoutingRule.FINAL_TIER).build().vote();

			assertThat(verdict.provenance())
				.isEqualTo(VerdictProvenance.tier("inner", VerdictProvenanceBasis.TIER_OUTCOME));
			assertThat(verdict.compositeAttempts().get(0).disposition()).as("a determination, not a stage failure")
				.isEqualTo(AttemptDisposition.USED);
			Verdict innerVerdict = verdict.compositeAttempts().get(0).verdict();
			assertThat(innerVerdict.provenance().basis()).isEqualTo(VerdictProvenanceBasis.INDIVIDUAL_REJECTION);
			assertThat(verdict.judgment()).isEqualTo(innerVerdict.judgment());
		}

		@Test
		@DisplayName("an inner propagated outcome is likewise a determination")
		void anInnerPropagatedOutcomeIsAdopted() {
			Jury erroring = SimpleJury.builder()
				.judge(Judges.named(() -> Judgment.error("the index was unreachable"), "flaky"))
				.judge(Judges.named(() -> Judgment.pass("another seat"), "other"))
				.votingStrategy(new ConsensusStrategy())
				.build();
			Jury inner = CascadedJury.builder().tier("leaf", erroring, RoutingRule.FINAL_TIER).build();

			Verdict verdict = CascadedJury.builder().tier("inner", inner, RoutingRule.FINAL_TIER).build().vote();

			assertThat(verdict.judgment().reasonCode()).isEqualTo(JudgmentReasonCode.ERRORS_PROPAGATED);
			assertThat(verdict.provenance())
				.isEqualTo(VerdictProvenance.tier("inner", VerdictProvenanceBasis.TIER_OUTCOME));
		}

		@Test
		@DisplayName("names are local: each level names only its own direct tier")
		void namesAreLocal() {
			Jury inner = CascadedJury.builder().tier("leaf", passingTier("OK"), RoutingRule.FINAL_TIER).build();
			Verdict verdict = CascadedJury.builder().tier("outer-tier", inner, RoutingRule.FINAL_TIER).build().vote();

			assertThat(verdict.provenance().tier()).isEqualTo("outer-tier");
			assertThat(verdict.compositeAttempts().get(0).verdict().provenance().tier()).isEqualTo("leaf");
		}

	}

	@Test
	@DisplayName("7. a refused exclusion holding a real FAIL stops, and the root is parent-authored")
	void refusedExclusionWithARealFailStops() {
		Verdict verdict = CascadedJury.builder()
			.tier("rubric", opaqueExcludingTier(Judgment.pass("a"), Judgment.fail("b")),
					RoutingRule.STOP_ON_ANY_OPINION_FAIL)
			.tier("semantic", passingTier("OK"), RoutingRule.FINAL_TIER)
			.build()
			.vote();
		assertThat(verdict.provenance())
			.isEqualTo(VerdictProvenance.tier("semantic", VerdictProvenanceBasis.TIER_OUTCOME));
		assertThat(verdict.conclusion()).isEqualTo(Verdict.Conclusion.PASS);
		CompositeAttempt refused = verdict.compositeAttempts().get(0);
		assertThat(refused.dispositionReason()).isEqualTo(DispositionReason.INVALID_TIER_RESULT);
		assertThat(refused.verdict().judgment().status()).isEqualTo(JudgmentStatus.NOT_APPLICABLE);
		assertThat(refused.verdict().individual()).extracting(Judgment::status)
			.containsExactly(JudgmentStatus.PASS, JudgmentStatus.FAIL);
	}

	@Test
	@DisplayName("8. a refused exclusion tier holding a real FAIL also stops when its aggregate came from a refusal")
	void aRefusedPolicyTierHoldingARealFailStops() {
		// The tier's own strategy refused an exclusion, so its aggregate is a machinery
		// error and
		// the tier is undecided for control flow. The FAIL beneath it is still real, and
		// one
		// established violation is enough to reject.
		Jury refusing = returning(refusedVerdict());
		assertThat(refusing.aggregateMayBeNotApplicable()).isFalse();

		Verdict verdict = CascadedJury.builder()
			.tier("rubric", refusing, RoutingRule.STOP_ON_ANY_OPINION_FAIL)
			.tier("semantic", passingTier("OK"), RoutingRule.FINAL_TIER)
			.build()
			.vote();

		assertThat(verdict.provenance()).isEqualTo(new VerdictProvenance(VerdictProvenanceKind.TIER, "rubric",
				VerdictProvenanceBasis.INDIVIDUAL_REJECTION));
		assertThat(verdict.judgment().reasonCode()).as("a CHILD_UNDECIDED rejection keeps the child's own code")
			.isEqualTo(JudgmentReasonCode.NOT_APPLICABLE_REFUSED);
		assertThat(verdict.judgment().status()).as("no FAIL and no score is manufactured")
			.isNotEqualTo(JudgmentStatus.FAIL);
		assertThat(verdict.judgment().score()).isNull();
		assertThat(verdict.individual()).extracting(Judgment::status)
			.as("the rejecting tier's individuals are copied, including the FAIL that established it")
			.containsExactly(JudgmentStatus.NOT_APPLICABLE, JudgmentStatus.FAIL);
		CompositeAttempt attempt = verdict.compositeAttempts().get(0);
		assertThat(attempt.dispositionReason()).isNull();
		assertThat(attempt.verdict().judgment()).as("the child's verdict is unchanged").isEqualTo(verdict.judgment());
		assertThat(verdict.compositeAttempts()).extracting(CompositeAttempt::name)
			.as("the cascade stopped, so the later tier never ran")
			.containsExactly("rubric");
	}

	private static Verdict refusedVerdict() {
		Judgment excluded = Judgment.notApplicable(EXCLUSION);
		Judgment failed = Judgment.fail("a requirement was not met");
		return Verdict.advancedBuilder()
			.judgment(Judgment.error(JudgmentReasonCode.NOT_APPLICABLE_REFUSED,
					"1 of 2 judgment(s) were not applicable and the not-applicable policy is refuse"))
			.individual(List.of(excluded, failed))
			.individualByName(namedOf(excluded, failed))
			.seats(seatsFor(2))
			.provenance(VerdictProvenance.undecided())
			.build();
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	@DisplayName("9. a null-returning reduction behaves exactly like a throwing one")
	void nullReductionsBehaveLikeThrows(boolean nested) {
		Jury tier = nullReducingTier(Judgment.pass("a"), Judgment.fail("b"));
		Jury cascadeTier = nested ? Juries.meta(new ConsensusStrategy(ErrorHandling.IGNORE, ExclusionHandling.EXCLUDE),
				new NamedJury("inner", tier)) : tier;

		Verdict verdict = CascadedJury.builder()
			.tier("gate", cascadeTier, RoutingRule.STOP_ON_ANY_OPINION_FAIL)
			.tier("semantic", passingTier("OK"), RoutingRule.FINAL_TIER)
			.build()
			.vote();

		assertThat(verdict.provenance().basis()).isEqualTo(VerdictProvenanceBasis.INDIVIDUAL_REJECTION);
		assertThat(verdict.judgment().reasonCode()).isEqualTo(JudgmentReasonCode.AGGREGATION_FAILED);
		assertThat(verdict.compositeAttempts().get(0).disposition()).isEqualTo(AttemptDisposition.USED);
	}

	@Nested
	@DisplayName("10. a rejecting cascade nested in another cascade")
	class RejectingCascadeNested {

		/**
		 * Test 9's cascade: an opaque tier that excludes without declaring it may, over a
		 * genuine leaf FAIL, so the inner cascade stops on that established rejection
		 * with a parent-built {@code ERROR stage_failed} root.
		 * <p>
		 * {@code innerCapable} changes where the inner cascade's aggregate capability
		 * comes from, <em>not</em> whether it rejects. A cascade's capability is the
		 * union over its tiers, so a capable tier configured behind the rejecting one
		 * makes the inner cascade capable while leaving the D1 stop exactly where it was
		 * — which is what makes pinning both configurations worth anything. Making the
		 * rejecting tier itself capable instead removes the rejection: its exclusion is
		 * then honoured, no tier has a FAIL, and the cascade walks on to a passing final
		 * tier. That is the shape this test used to have, and it is why the guard stayed
		 * green when D1 stopping was disabled.
		 * </p>
		 * @param innerCapable whether the inner cascade declares its aggregate may be
		 * excluded
		 * @return the inner cascade
		 */
		private Jury rejectingCascade(boolean innerCapable) {
			return acceptedRejectingCascade(innerCapable ? capableTier() : passingTier("OK"));
		}

		/**
		 * A tier that declares its aggregate may be excluded, so the cascade holding it
		 * is capable.
		 */
		private Jury capableTier() {
			return SimpleJury.builder()
				.seat(declared(new Conditional("conditional", Judgment.pass("OK"))))
				.votingStrategy(new ConsensusStrategy(ErrorHandling.PROPAGATE, ExclusionHandling.EXCLUDE))
				.build();
		}

		/**
		 * The §6.5 selected determination: follow named {@code TIER_OUTCOME} edges, and
		 * stop at {@code OWN}, {@code UNDECIDED}, or a tier reached by
		 * {@code INDIVIDUAL_REJECTION}.
		 * @param root the item's root verdict
		 * @return the verdict that classifies the item
		 */
		private Verdict selectedDetermination(Verdict root) {
			Verdict current = root;
			while (current.provenance().kind() == VerdictProvenanceKind.TIER
					&& current.provenance().basis() == VerdictProvenanceBasis.TIER_OUTCOME) {
				String named = current.provenance().tier();
				current = current.compositeAttempts()
					.stream()
					.filter(attempt -> attempt.relation() == CompositeRelation.CASCADE_TIER
							&& attempt.name().equals(named))
					.map(CompositeAttempt::verdict)
					.findFirst()
					.orElseThrow();
			}
			return current;
		}

		@ParameterizedTest
		@ValueSource(booleans = { true, false })
		@DisplayName("the outer cascade adopts it as a tier outcome and never re-rejects")
		void asASoleFinalTier(boolean innerCapable) {
			Jury inner = rejectingCascade(innerCapable);
			assertThat(inner.aggregateMayBeNotApplicable()).isEqualTo(innerCapable);

			Verdict verdict = CascadedJury.builder().tier("inner", inner, RoutingRule.FINAL_TIER).build().vote();

			assertThat(verdict.provenance())
				.isEqualTo(VerdictProvenance.tier("inner", VerdictProvenanceBasis.TIER_OUTCOME));
			assertThat(verdict.compositeAttempts().get(0).disposition()).isEqualTo(AttemptDisposition.USED);

			// What the outer copy marker alone never established: that there was a D1 to
			// adopt.
			assertIsAcceptedUndecidedRejection(verdict.compositeAttempts().get(0).verdict());

			Verdict selected = selectedDetermination(verdict);
			assertThat(selected.provenance().basis()).as("the chain ends at the inner rejection")
				.isEqualTo(VerdictProvenanceBasis.INDIVIDUAL_REJECTION);
			assertThat(selected.provenance().tier()).isEqualTo("rubric");
			assertThat(selected.judgment().status()).as("non-pass: in the denominator, not the numerator")
				.isNotEqualTo(JudgmentStatus.PASS);
			assertThat(selected.judgment().reasonCode()).as("and one machinery failure, counted once")
				.isEqualTo(JudgmentReasonCode.AGGREGATION_FAILED);
		}

		@ParameterizedTest
		@ValueSource(booleans = { true, false })
		@DisplayName("as a rejecting non-final tier, its determination is adopted rather than refused")
		void asARejectingNonFinalTier(boolean innerCapable) {
			Verdict verdict = CascadedJury.builder()
				.tier("inner", rejectingCascade(innerCapable), RoutingRule.STOP_ON_ANY_OPINION_FAIL)
				.tier("outer-final", passingTier("OK"), RoutingRule.FINAL_TIER)
				.build()
				.vote();

			assertThat(verdict.provenance())
				.isEqualTo(VerdictProvenance.tier("inner", VerdictProvenanceBasis.TIER_OUTCOME));
			assertThat(verdict.judgment().reasonCode()).as("still one machinery failure, counted once")
				.isEqualTo(JudgmentReasonCode.AGGREGATION_FAILED);
			assertThat(verdict.compositeAttempts()).extracting(CompositeAttempt::name).containsExactly("inner");

			assertIsAcceptedUndecidedRejection(verdict.compositeAttempts().get(0).verdict());

			Verdict selected = selectedDetermination(verdict);
			assertThat(selected.provenance().basis()).isEqualTo(VerdictProvenanceBasis.INDIVIDUAL_REJECTION);
			assertThat(selected.judgment().status()).isNotEqualTo(JudgmentStatus.PASS);
		}

	}

	/**
	 * 11. R-D's cascade&rarr;meta case, under every error policy.
	 *
	 * <p>
	 * Two rules meet here, and the healthy sibling hides them both from an outcome
	 * assertion. The member must actually <em>be</em> a boundary-rejected D1 — not a
	 * cascade that refused the exclusion and walked on to its passing final tier — and
	 * its machinery ERROR must be a non-vote under {@code IGNORE} and
	 * {@code TREAT_AS_ABSTAIN} rather than a contribution. With one healthy PASS beside
	 * it, a correctly excluded error and an error wrongly counted as a passing vote
	 * produce the same aggregate PASS, so the aggregate cannot witness D4 at all.
	 * </p>
	 *
	 * <p>
	 * So the member is inspected where the D1 happens, and the non-vote is asserted on
	 * the population and its counters: one error submitted, one error counted under the
	 * matching treatment, exactly one eligible contribution, and the machinery cause
	 * named in the origin.
	 * </p>
	 * @param errorPolicy the meta-strategy's configured error policy
	 */
	@ParameterizedTest
	@EnumSource(ErrorHandling.class)
	@DisplayName("11. a rejecting cascade as a meta member is a determination, and is never scored")
	void aRejectingCascadeAsAMetaMember(ErrorHandling errorPolicy) {
		Jury inner = acceptedRejectingCascade(passingTier("OK"));

		Verdict verdict = Juries
			.meta(new AllEligiblePassStrategy(errorPolicy, ExclusionHandling.EXCLUDE), new NamedJury("inner", inner),
					new NamedJury("healthy", returning(Verdict.single("b", Judgment.pass("ok")))))
			.vote();

		CompositeAttempt member = verdict.compositeAttempts().get(0);
		assertThat(member.name()).isEqualTo("inner");
		assertThat(member.disposition()).as("a determined verdict, not a stage failure")
			.isEqualTo(AttemptDisposition.USED);
		assertIsAcceptedUndecidedRejection(member.verdict());

		assertThat(verdict.individual()).extracting(Judgment::status)
			.as("the machinery error reached the reduction, so how it was treated is a decision, not an absence")
			.containsExactly(JudgmentStatus.ERROR, JudgmentStatus.PASS);
		assertThat(verdict.judgment().status()).as("the meta-jury never rejects the subject on machinery's behalf")
			.isNotEqualTo(JudgmentStatus.FAIL);

		if (errorPolicy == ErrorHandling.PROPAGATE || errorPolicy == ErrorHandling.TREAT_AS_FAIL) {
			assertThat(verdict.judgment().reasonCode()).isEqualTo(JudgmentReasonCode.ERRORS_PROPAGATED);
			assertThat(originOf(verdict.judgment())).as("the machinery cause is carried up by name, counted once")
				.containsOnlyKeys(JudgmentReasonCode.AGGREGATION_FAILED.wireName())
				.containsEntry(JudgmentReasonCode.AGGREGATION_FAILED.wireName(), 1);
			assertThat(evidenceOf(verdict.judgment()))
				.as("a policy exit reduces nothing, so no treatment was performed")
				.containsEntry(AggregationEvidence.ELIGIBLE_COUNT, 0)
				.containsEntry(AggregationEvidence.ERRORS_TREATED_AS_FAIL_COUNT, 0);
		}
		else {
			assertThat(verdict.judgment().status()).isEqualTo(JudgmentStatus.PASS);
			String treatment = errorPolicy == ErrorHandling.IGNORE ? AggregationEvidence.IGNORED_ERROR_COUNT
					: AggregationEvidence.ERRORS_TREATED_AS_ABSTAIN_COUNT;
			assertThat(evidenceOf(verdict.judgment()))
				.as("D4: the error was counted and excluded, leaving the healthy member alone eligible")
				.containsEntry(AggregationEvidence.INPUT_COUNT, 2)
				.containsEntry(AggregationEvidence.ERROR_COUNT, 1)
				.containsEntry(treatment, 1)
				.containsEntry(AggregationEvidence.ELIGIBLE_COUNT, 1)
				.containsEntry(AggregationEvidence.PASS_COUNT, 1)
				.containsEntry(AggregationEvidence.FAIL_COUNT, 0);
			assertThat(originOf(verdict.judgment())).as("and the excluded cause is named, not merely missing")
				.containsOnlyKeys(JudgmentReasonCode.AGGREGATION_FAILED.wireName())
				.containsEntry(JudgmentReasonCode.AGGREGATION_FAILED.wireName(), 1);
		}
	}

	@Test
	@DisplayName("a throwing final tier decides nothing; a throwing non-final tier escalates")
	void throwingTiers() {
		Verdict undecided = CascadedJury.builder()
			.tier("only", throwing(new IllegalStateException("boom")), RoutingRule.FINAL_TIER)
			.build()
			.vote();
		assertThat(undecided.judgment().reasonCode()).isEqualTo(JudgmentReasonCode.NO_TIER_DECIDED);
		assertThat(undecided.compositeAttempts().get(0).dispositionReason())
			.isEqualTo(DispositionReason.EXECUTION_FAILED);

		Verdict escalated = CascadedJury.builder()
			.tier("broken", throwing(new IllegalStateException("boom")), RoutingRule.STOP_ON_ANY_OPINION_FAIL)
			.tier("semantic", passingTier("OK"), RoutingRule.FINAL_TIER)
			.build()
			.vote();
		assertThat(escalated.judgment().status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(escalated.compositeAttempts().get(0).verdict()).as("a throw leaves a code, not a verdict").isNull();
	}

	@Test
	@DisplayName("a genuine FAIL may be a completed member reduction, including one an error policy produced")
	void genuineFailWitnesses() {
		// A member whose own strategy turned a judge error into a failing contribution:
		// the
		// member completed a reduction, so its FAIL is real.
		Jury errorDerived = SimpleJury.builder()
			.judge(Judges.named(() -> Judgment.error("the index was unreachable"), "flaky"))
			.judge(Judges.named(() -> Judgment.pass("another seat"), "other"))
			.votingStrategy(new AllEligiblePassStrategy(ErrorHandling.TREAT_AS_FAIL, ExclusionHandling.EXCLUDE))
			.build();
		// A member whose exclusion policy turned an exclusion into a failing
		// contribution.
		Jury exclusionDerived = SimpleJury.builder()
			.seat(declared(new Conditional("conditional", Judgment.notApplicable(EXCLUSION))))
			.judge(Judges.named(() -> Judgment.pass("another seat"), "other"))
			.votingStrategy(new AllEligiblePassStrategy(ErrorHandling.PROPAGATE, ExclusionHandling.TREAT_AS_FAIL))
			.build();

		for (Jury member : List.of(errorDerived, exclusionDerived)) {
			Jury tier = Juries.meta(new ConsensusStrategy(ErrorHandling.IGNORE, ExclusionHandling.EXCLUDE),
					new NamedJury("member", member),
					new NamedJury("broken", throwing(new IllegalStateException("boom"))));

			Verdict verdict = CascadedJury.builder()
				.tier("review", tier, RoutingRule.STOP_ON_ANY_OPINION_FAIL)
				.tier("semantic", passingTier("OK"), RoutingRule.FINAL_TIER)
				.build()
				.vote();

			assertThat(verdict.provenance().basis()).as("a completed member reduction is a genuine FAIL")
				.isEqualTo(VerdictProvenanceBasis.INDIVIDUAL_REJECTION);
		}
	}

	@Test
	@DisplayName("a machinery error is never a genuine FAIL, so it stops nothing on its own")
	void machineryErrorsAreNotGenuineFails() {
		// The tier must actually hold a machinery error where a genuine FAIL would sit,
		// or the
		// claim is untested: a tier whose individuals are empty escalates whatever the
		// rule says.
		// So its one usable member is a determined D1, whose aggregate is a machinery
		// ERROR, and a
		// second member breaks the tier so the cascade reaches the rule at all.
		Jury tier = Juries.meta(new ConsensusStrategy(ErrorHandling.IGNORE, ExclusionHandling.EXCLUDE),
				new NamedJury("rejected", acceptedRejectingCascade(passingTier("OK"))),
				new NamedJury("broken", throwing(new IllegalStateException("boom"))));

		Verdict verdict = CascadedJury.builder()
			.tier("review", tier, RoutingRule.STOP_ON_ANY_OPINION_FAIL)
			.tier("semantic", passingTier("OK"), RoutingRule.FINAL_TIER)
			.build()
			.vote();

		CompositeAttempt reviewed = verdict.compositeAttempts().get(0);
		assertThat(reviewed.dispositionReason()).isNull();
		assertThat(reviewed.verdict().individual()).extracting(Judgment::status)
			.as("a machinery error is standing exactly where a genuine FAIL would stop the cascade")
			.containsExactly(JudgmentStatus.ERROR);
		assertThat(reviewed.verdict().individual().get(0).reasonCode())
			.isEqualTo(JudgmentReasonCode.AGGREGATION_FAILED);

		assertThat(verdict.provenance()).as("and the cascade escalated past it rather than rejecting on it")
			.isEqualTo(VerdictProvenance.tier("semantic", VerdictProvenanceBasis.TIER_OUTCOME));
		assertThat(verdict.judgment().status()).isEqualTo(JudgmentStatus.PASS);
	}

	@Test
	@DisplayName("nor does an errored individual in a tier that did complete: only a FAIL stops one")
	void anErroredIndividualIsNotAFail() {
		Jury tier = SimpleJury.builder()
			.judge(Judges.named(() -> Judgment.error("the index was unreachable"), "flaky"))
			.judge(Judges.named(() -> Judgment.pass("fine"), "ok"))
			.votingStrategy(new ConsensusStrategy(ErrorHandling.IGNORE, ExclusionHandling.EXCLUDE))
			.build();

		Verdict verdict = CascadedJury.builder()
			.tier("review", tier, RoutingRule.STOP_ON_ANY_OPINION_FAIL)
			.tier("semantic", passingTier("OK"), RoutingRule.FINAL_TIER)
			.build()
			.vote();

		CompositeAttempt reviewed = verdict.compositeAttempts().get(0);
		assertThat(reviewed.disposition()).as("this tier finished; it simply has an errored individual")
			.isEqualTo(AttemptDisposition.USED);
		assertThat(reviewed.verdict().individual()).extracting(Judgment::status)
			.containsExactly(JudgmentStatus.ERROR, JudgmentStatus.PASS);
		assertThat(verdict.provenance()).as("an error is not a rejection, so the cascade walked on")
			.isEqualTo(VerdictProvenance.tier("semantic", VerdictProvenanceBasis.TIER_OUTCOME));
	}

	@Test
	@DisplayName("an undecided tier holding nothing at all escalates: there is no rejection to adopt")
	void anEmptyUndecidedTierEscalates() {
		Jury tier = Juries.meta(new ConsensusStrategy(ErrorHandling.IGNORE, ExclusionHandling.EXCLUDE),
				new NamedJury("broken", returning(undecidedVerdict())));

		Verdict verdict = CascadedJury.builder()
			.tier("review", tier, RoutingRule.STOP_ON_ANY_OPINION_FAIL)
			.tier("semantic", passingTier("OK"), RoutingRule.FINAL_TIER)
			.build()
			.vote();

		assertThat(verdict.compositeAttempts().get(0).routingDecision().reason())
			.isEqualTo(RoutingDecision.Reason.NO_ROOT_OPINIONS);
		assertThat(verdict.provenance())
			.isEqualTo(VerdictProvenance.tier("semantic", VerdictProvenanceBasis.TIER_OUTCOME));
		assertThat(verdict.judgment().status()).isEqualTo(JudgmentStatus.PASS);
	}

	private static io.github.markpollack.judge.jury.JudgeSeat declared(io.github.markpollack.judge.Judge producer) {
		return io.github.markpollack.judge.jury.JudgeSeat
			.named(((io.github.markpollack.judge.JudgeWithMetadata) producer).metadata().name(), producer)
			.notApplicableWhen(io.github.markpollack.judge.Judges.notApplicableCapability(producer).orElseThrow());
	}

}
