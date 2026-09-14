/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.result;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.markpollack.judge.Judges;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.jury.AllMustPassStrategy;
import io.github.markpollack.judge.jury.CascadedJury;
import io.github.markpollack.judge.jury.ConsensusStrategy;
import io.github.markpollack.judge.jury.Decision;
import io.github.markpollack.judge.jury.DecisionBasis;
import io.github.markpollack.judge.jury.ErrorPolicy;
import io.github.markpollack.judge.jury.Jury;
import io.github.markpollack.judge.jury.NotApplicablePolicy;
import io.github.markpollack.judge.jury.SimpleJury;
import io.github.markpollack.judge.jury.TierPolicy;
import io.github.markpollack.judge.jury.Verdict;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Where a cause about the <em>subject</em> may live, and where it may not.
 *
 * <p>
 * There are three structural buckets, and telling them apart is the whole point of having a code
 * at all: an ERROR with an instrument code means the instrument failed; a FAIL with a subject
 * code is a rejection with a named cause; a FAIL with no code is a rejection explained in prose.
 * A reader counts the first as an instrument failure and the other two as non-passes, and gets
 * the denominator wrong if the three are conflated.
 * </p>
 *
 * <p>
 * The one rule that needs a test rather than a sentence is where a subject cause may live. It
 * belongs on a leaf, because a leaf is the thing that looked at the subject. A newly computed
 * multi-input aggregate never carries one: it would claim a cause its own reduction did not
 * observe, and the same leaf would then be counted twice — once where it happened and once in the
 * aggregate above it. A cascade copying a tier's verdict is not a new computation, so a copy
 * keeps the code and the leaf is still counted once.
 * </p>
 */
@DisplayName("Subject causes")
class SubjectCauseTest {

	private static final JudgmentContext CONTEXT = JudgmentContext.builder().goal("assess the artifact").build();

	/** What a judge that looked and found nothing produces. */
	private static Judgment emptySubject() {
		return Judgment.builder()
			.fail()
			.reasonCode(JudgmentReasonCode.SUBJECT_EMPTY)
			.reasoning("The diff contained no changed files, so there was nothing to assess")
			.build();
	}

	@Test
	@DisplayName("the three structural buckets are distinguishable from the stored fields alone")
	void theThreeBuckets() {
		Judgment instrumentFailure = Judgment.error(JudgmentReasonCode.JUDGE_FAILED, "the judge threw");
		Judgment codedRejection = emptySubject();
		Judgment uncodedRejection = Judgment.fail("the answer contradicted the retrieved context");

		assertThat(instrumentFailure.reasonCode().family()).isEqualTo(JudgmentReasonCode.Family.INSTRUMENT);
		assertThat(codedRejection.reasonCode().family()).isEqualTo(JudgmentReasonCode.Family.SUBJECT);
		assertThat(uncodedRejection.reasonCode()).isNull();
		assertThat(uncodedRejection.status()).as("an uncoded rejection is still a rejection")
			.isEqualTo(JudgmentStatus.FAIL);
	}

	@Test
	@DisplayName("subject_empty means the judge looked; absence arranged elsewhere is not this code")
	void whatTheCodeMeans() {
		assertThat(JudgmentReasonCode.SUBJECT_EMPTY.family()).isEqualTo(JudgmentReasonCode.Family.SUBJECT);
		assertThat(JudgmentReasonCode.SUBJECT_EMPTY.originFamily()).as("it describes the subject, not an instrument")
			.isNull();
		assertThat(emptySubject().reasoning()).as("the bounded definition of empty travels with the code")
			.contains("no changed files");
	}

	@Test
	@DisplayName("a cascade copying a tier keeps the leaf's cause, and the leaf is counted once")
	void aCopiedLeafKeepsItsCause() {
		Jury tier = SimpleJury.builder()
			.judge(Judges.named(context -> emptySubject(), "diff-size"))
			.votingStrategy(new AllMustPassStrategy())
			.build();

		Verdict verdict = CascadedJury.builder()
			.tier("gate", tier, TierPolicy.REJECT_ON_ANY_FAIL)
			.tier("semantic", passing(), TierPolicy.FINAL_TIER)
			.build()
			.vote(CONTEXT);

		assertThat(verdict.decision()).isEqualTo(Decision.tier("gate", DecisionBasis.TIER_OUTCOME));
		assertThat(verdict.individual()).singleElement()
			.satisfies(leaf -> assertThat(leaf.reasonCode()).isEqualTo(JudgmentReasonCode.SUBJECT_EMPTY));
		assertThat(verdict.aggregated().reasonCode()).as("the reduction observed no cause of its own").isNull();
		assertThat(verdict.compositeAttempts().get(0).verdict().individual()).as("the same leaf, copied not counted")
			.isEqualTo(verdict.individual());
	}

	@Test
	@DisplayName("an empty subject alongside a quality failure: two leaves, one aggregate, no cause on the aggregate")
	void anEmptySubjectBesideAQualityFailure() {
		Verdict verdict = SimpleJury.builder()
			.judge(Judges.named(context -> emptySubject(), "diff-size"))
			.judge(Judges.named(context -> Judgment.fail("the answer contradicted its sources"), "faithfulness"))
			.votingStrategy(new AllMustPassStrategy())
			.build()
			.vote(CONTEXT);

		assertThat(verdict.individual()).extracting(Judgment::reasonCode)
			.containsExactly(JudgmentReasonCode.SUBJECT_EMPTY, null);
		assertThat(verdict.aggregated().status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(verdict.aggregated().reasonCode())
			.as("a computed aggregate never adopts a cause one of its inputs observed")
			.isNull();
	}

	@Test
	@DisplayName("a one-judge verdict is a copy, not a computation, so it keeps the code")
	void singleKeepsTheCode() {
		Verdict verdict = Verdict.single("diff-size", emptySubject());

		assertThat(verdict.aggregated().reasonCode()).isEqualTo(JudgmentReasonCode.SUBJECT_EMPTY);
		assertThat(verdict.individual()).containsExactly(verdict.aggregated());
	}

	@Test
	@DisplayName("an exclusion treated as a failure produces an uncoded rejection, not a manufactured cause")
	void exclusionsTreatedAsFailuresAreUncoded() {
		Judgment aggregate = new ConsensusStrategy(ErrorPolicy.PROPAGATE, NotApplicablePolicy.TREAT_AS_FAIL)
			.aggregate(List.of(Judgment.notApplicable("the repository contains no Java")), Map.of());

		assertThat(aggregate.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(aggregate.reasonCode()).as("the strategy observed no cause; it applied a policy").isNull();
		assertThat(evidenceOf(aggregate)).containsEntry("notApplicableTreatedAsFailCount", 1);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> evidenceOf(Judgment judgment) {
		return (Map<String, Object>) judgment.metadata().get(Judgment.AGGREGATION_KEY);
	}

	private static Jury passing() {
		return SimpleJury.builder()
			.judge(Judges.named(context -> Judgment.pass("OK"), "semantic"))
			.votingStrategy(new ConsensusStrategy())
			.build();
	}

}
