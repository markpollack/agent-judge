/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.consumer;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.Judges;
import io.github.markpollack.judge.completion.CompletionEvidence;
import io.github.markpollack.judge.voting.AllMustPassStrategy;
import io.github.markpollack.judge.verdict.AttemptDisposition;
import io.github.markpollack.judge.jury.CascadedJury;
import io.github.markpollack.judge.verdict.CompositeAttempt;
import io.github.markpollack.judge.verdict.CompositeRelation;
import io.github.markpollack.judge.voting.ConsensusStrategy;
import io.github.markpollack.judge.verdict.VerdictProvenance;
import io.github.markpollack.judge.verdict.VerdictProvenanceBasis;
import io.github.markpollack.judge.verdict.VerdictProvenanceKind;
import io.github.markpollack.judge.verdict.DispositionReason;
import io.github.markpollack.judge.jury.Jury;
import io.github.markpollack.judge.verdict.RoutingRule;
import io.github.markpollack.judge.verdict.Verdict;
import io.github.markpollack.judge.voting.VotingStrategy;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentReasonCode;
import io.github.markpollack.judge.judgment.JudgmentStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The library facts a reader needs to classify one item, exercised end to end.
 *
 * <p>
 * Agent Judge does not own the pass-rate formula — agent-experiment does, and its numeric
 * oracles live there. What Agent Judge owes is that the <em>facts</em> a reader needs are
 * present and consistent in a stored verdict, and the hardest of those is the selected
 * determination: which node in a tree of copies actually decided this item.
 * </p>
 *
 * <p>
 * The rule is a walk. Start at the root; while the provenance is a tier adopted as
 * {@code TIER_OUTCOME}, move into that named attempt's verdict; stop at {@code OWN},
 * {@code UNDECIDED}, or a tier adopted as {@code INDIVIDUAL_REJECTION}. Only named copy
 * edges are followed, because a later completed determination has a meaning of its own
 * and searching for one would invent an answer the cascade never gave.
 * </p>
 *
 * <p>
 * The walk below is the reader's, written here so the structures can be checked against
 * it. It is deliberately not library API: the formula belongs to the consumer that owns
 * the denominator.
 * </p>
 */
@DisplayName("Item accounting")
class ItemAccountingTest {

	private static final CompletionEvidence CONTEXT = CompletionEvidence.builder().request("accounting").build();

	/** §6.5's selected determination, implemented exactly as a reader would. */
	private static Verdict selectedDetermination(Verdict root) {
		Verdict current = root;
		while (current.provenance().kind() == VerdictProvenanceKind.TIER
				&& current.provenance().basis() == VerdictProvenanceBasis.TIER_OUTCOME) {
			current = namedAttempt(current, current.provenance().tier()).verdict();
		}
		return current;
	}

	private static CompositeAttempt namedAttempt(Verdict verdict, String name) {
		return verdict.compositeAttempts()
			.stream()
			.filter(attempt -> attempt.relation() == CompositeRelation.CASCADE_TIER && attempt.name().equals(name))
			.findFirst()
			.orElseThrow();
	}

	/** How many parent-side stage failures the tree records, at any depth. */
	private static long boundaryDispositions(Verdict root) {
		long count = root.compositeAttempts()
			.stream()
			.filter(attempt -> attempt.disposition() == AttemptDisposition.STAGE_FAILED)
			.count();
		for (CompositeAttempt attempt : root.compositeAttempts()) {
			Verdict child = attempt.verdict();
			if (child != null) {
				count += boundaryDispositions(child);
			}
		}
		return count;
	}

	@Test
	@DisplayName("a direct rejection: the root decided it itself, and there is nothing to follow")
	void aDirectRejection() {
		Verdict root = SimpleJury.of(Judgment.fail("a requirement was not met")).vote();

		Verdict selected = selectedDetermination(root);

		assertThat(selected).isSameAs(root);
		assertThat(selected.provenance().kind()).isEqualTo(VerdictProvenanceKind.OWN);
		assertThat(selected.judgment().status()).as("non-pass, counted in the denominator")
			.isEqualTo(JudgmentStatus.FAIL);
		assertThat(boundaryDispositions(root)).isZero();
	}

	@Test
	@DisplayName("a direct instrument failure: undecided, so it is excluded from the subject denominator")
	void aDirectInstrumentFailure() {
		Verdict root = brokenReduction(Judgment.pass("a"), Judgment.pass("b")).vote();

		Verdict selected = selectedDetermination(root);

		assertThat(selected.provenance().kind()).isEqualTo(VerdictProvenanceKind.UNDECIDED);
		assertThat(selected.judgment().reasonCode()).isEqualTo(JudgmentReasonCode.AGGREGATION_FAILED);
	}

	@Test
	@DisplayName("a rejection on a boundary-refused tier: the decision carries it, the aggregate stays an error")
	void aBoundaryRejection() {
		Verdict root = boundaryRejectingCascade().vote();

		Verdict selected = selectedDetermination(root);

		assertThat(selected).isSameAs(root);
		assertThat(selected.provenance().basis()).as("the chain stops here: a rejection is a determination")
			.isEqualTo(VerdictProvenanceBasis.INDIVIDUAL_REJECTION);
		assertThat(selected.judgment().reasonCode()).isEqualTo(JudgmentReasonCode.AGGREGATION_FAILED);
		assertThat(selected.judgment().status()).as("no FAIL was manufactured; the rejection is in the decision")
			.isNotEqualTo(JudgmentStatus.FAIL);
		assertThat(boundaryDispositions(root)).as("one machinery failure, counted where it was emitted").isZero();
	}

	@Test
	@DisplayName("a rejection copied through two cascade levels is still one rejection and one machinery failure")
	void aRejectionCopiedThroughTwoLevels() {
		Jury inner = boundaryRejectingCascade();
		Jury middle = CascadedJury.builder().tier("inner", inner, RoutingRule.FINAL_TIER).build();
		Verdict root = CascadedJury.builder().tier("middle", middle, RoutingRule.FINAL_TIER).build().vote();

		Verdict selected = selectedDetermination(root);

		assertThat(root.provenance()).isEqualTo(VerdictProvenance.tier("middle", VerdictProvenanceBasis.TIER_OUTCOME));
		assertThat(selected).isNotSameAs(root);
		assertThat(selected.provenance().basis()).isEqualTo(VerdictProvenanceBasis.INDIVIDUAL_REJECTION);
		assertThat(selected.judgment()).as("the copies are equal, so counting the root as well would double-count")
			.isEqualTo(root.judgment());
		assertThat(boundaryDispositions(root)).as("the refusal happened once, three levels down").isZero();
	}

	@Test
	@DisplayName("a refused stage followed by a passing tier: the item passes, and the refusal is still counted")
	void aRefusedStageFollowedByAPass() {
		Verdict root = CascadedJury.builder()
			.tier("rubric", excludingTier(), RoutingRule.STOP_ON_ANY_OPINION_FAIL)
			.tier("semantic", SimpleJury.of(Judgment.pass("OK")), RoutingRule.FINAL_TIER)
			.build()
			.vote();

		Verdict selected = selectedDetermination(root);

		assertThat(selected.judgment().status()).as("the item passes").isEqualTo(JudgmentStatus.PASS);
		assertThat(boundaryDispositions(root)).as("and the composition failure is still there to count").isEqualTo(1);
		assertThat(namedAttempt(root, "rubric").dispositionReason())
			.isEqualTo(DispositionReason.UNDECLARED_NOT_APPLICABLE);
		assertThat(namedAttempt(root, "rubric").verdict().judgment().status())
			.as("the child's claim is unchanged, so it stays legible")
			.isEqualTo(JudgmentStatus.NOT_APPLICABLE);
	}

	@Test
	@DisplayName("an excluded aggregate leaves the denominator rather than passing or failing")
	void anExcludedItem() {
		Verdict root = CascadedJury.builder()
			.tier("rubric", capableExcludingTier(), RoutingRule.FINAL_TIER)
			.build()
			.vote();

		Verdict selected = selectedDetermination(root);

		assertThat(selected.judgment().status()).isEqualTo(JudgmentStatus.NOT_APPLICABLE);
		assertThat(selected.judgment().notApplicable()).isTrue();
	}

	@Test
	@DisplayName("only named copy edges are followed: a later completed determination is not searched for")
	void onlyNamedEdgesAreFollowed() {
		Verdict root = CascadedJury.builder()
			.tier("first", brokenReduction(Judgment.fail("a requirement was not met"), Judgment.pass("other")),
					RoutingRule.STOP_ON_ANY_OPINION_FAIL)
			.tier("second", SimpleJury.of(Judgment.pass("OK")), RoutingRule.FINAL_TIER)
			.build()
			.vote();

		Verdict selected = selectedDetermination(root);

		assertThat(selected.provenance().tier()).isEqualTo("first");
		assertThat(root.compositeAttempts()).extracting(CompositeAttempt::name)
			.as("the second tier was never entered, so there is nothing later to find")
			.containsExactly("first");
	}

	// ==================== Fixtures ====================

	/** A leaf jury over one judgment. */
	private interface SimpleJury {

		static Jury of(Judgment judgment) {
			return io.github.markpollack.judge.jury.SimpleJury.builder()
				.judge(Judges.named(() -> judgment, "leaf"))
				.votingStrategy(new AllMustPassStrategy())
				.build();
		}

	}

	/** A leaf jury whose reduction breaks, over the judgments given. */
	private static Jury brokenReduction(Judgment... judgments) {
		io.github.markpollack.judge.jury.SimpleJury.Builder builder = io.github.markpollack.judge.jury.SimpleJury
			.<CompletionEvidence>builder()
			.votingStrategy(new VotingStrategy() {
				@Override
				public Judgment aggregate(List<Judgment> input, java.util.Map<String, Double> weights) {
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

	/** An opaque tier that excludes the subject without ever declaring it may. */
	private static Jury excludingTier() {
		return opaque(Verdict.single("excluded", Judgment.notApplicable("nothing here applies")));
	}

	/**
	 * The same shape, but holding a genuine individual FAIL the cascade can reject on.
	 */
	private static Jury rejectableExcludingTier() {
		Judgment failing = Judgment.fail("a requirement was not met");
		return opaque(Verdict.of(Judgment.notApplicable("nothing here applies"), java.util.Map.of("strict", failing)));
	}

	/** A judge that declared, in advance, that it may exclude a subject. */
	private record ConditionalJudge() implements io.github.markpollack.judge.JudgeWithMetadata {

		@Override
		public Judgment judge() {
			return Judgment.notApplicable("the change set contains no Java sources");
		}

		@Override
		public io.github.markpollack.judge.JudgeMetadata metadata() {
			return new io.github.markpollack.judge.JudgeMetadata("conditional", "a conditional judge",
					io.github.markpollack.judge.JudgeType.DETERMINISTIC, "the change set contains Java sources");
		}

	}

	/** A tier whose judge declared it may exclude, so the exclusion is honoured. */
	private static Jury capableExcludingTier() {
		return io.github.markpollack.judge.jury.SimpleJury.builder()
			.seat(declared(new ConditionalJudge()))
			.votingStrategy(new ConsensusStrategy(io.github.markpollack.judge.voting.ErrorHandling.PROPAGATE,
					io.github.markpollack.judge.voting.ExclusionHandling.EXCLUDE))
			.build();
	}

	private static Jury boundaryRejectingCascade() {
		return CascadedJury.builder()
			.tier("rubric", brokenReduction(Judgment.fail("a requirement was not met"), Judgment.pass("other")),
					RoutingRule.STOP_ON_ANY_OPINION_FAIL)
			.tier("semantic", SimpleJury.of(Judgment.pass("OK")), RoutingRule.FINAL_TIER)
			.build();
	}

	private static Jury opaque(Verdict verdict) {
		return new io.github.markpollack.judge.jury.VotingJury() {
			@Override
			public List<Judge> getJudges() {
				return List.of();
			}

			@Override
			public @Nullable VotingStrategy getVotingStrategy() {
				return new ConsensusStrategy();
			}

			@Override
			public Verdict vote() {
				return verdict;
			}
		};
	}

	private static io.github.markpollack.judge.jury.JudgeSeat declared(io.github.markpollack.judge.Judge producer) {
		return io.github.markpollack.judge.jury.JudgeSeat
			.named(((io.github.markpollack.judge.JudgeWithMetadata) producer).metadata().name(), producer)
			.notApplicableWhen(io.github.markpollack.judge.Judges.notApplicableCapability(producer).orElseThrow());
	}

}
