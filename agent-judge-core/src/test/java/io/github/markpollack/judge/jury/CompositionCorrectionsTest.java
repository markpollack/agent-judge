/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;
import io.github.markpollack.judge.verdict.RoutingRule;
import io.github.markpollack.judge.verdict.SeatExecution;
import io.github.markpollack.judge.verdict.Verdict;
import io.github.markpollack.judge.verdict.VerdictProvenance;
import io.github.markpollack.judge.voting.AllMustPassStrategy;
import io.github.markpollack.judge.voting.ConsensusStrategy;
import io.github.markpollack.judge.voting.ErrorHandling;
import io.github.markpollack.judge.voting.ExclusionHandling;
import io.github.markpollack.judge.voting.VotingStrategy;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import io.github.markpollack.judge.judgment.*;
import io.github.markpollack.judge.description.*;
import io.github.markpollack.judge.requirement.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CompositionCorrectionsTest {

	VotingStrategy broken() {
		return new VotingStrategy() {
			public Judgment aggregate(List<Judgment> j, Map<String, Double> w) {
				throw new IllegalStateException("reduction unavailable");
			}

			public String getName() {
				return "broken";
			}
		};
	}

	@Test
	void identityPreservesErrorWithEstablishedViolation() {
		Jury first = SimpleJury.builder()
			.judge(() -> Judgment.fail("violation"))
			.judge(() -> Judgment.pass("yes"))
			.parallel(false)
			.votingStrategy(broken())
			.build();
		Verdict child = CascadedJury.builder()
			.tier("first", first, RoutingRule.STOP_ON_ANY_OPINION_FAIL)
			.tier("fallback", () -> Verdict.single("fallback", Judgment.pass("passed")), RoutingRule.FINAL_TIER)
			.build()
			.vote();
		assertThat(child.conclusion()).isEqualTo(Verdict.Conclusion.FAIL);
		Verdict wrapped = Juries.meta(new ConsensusStrategy(), new NamedJury("only", () -> child)).vote();
		assertThat(wrapped.conclusion()).isEqualTo(child.conclusion());
		assertThat(wrapped.judgment()).isSameAs(child.judgment());
		assertThat(wrapped.compositeAttempts().getFirst().verdict()).isSameAs(child);
	}

	@Test
	void originalUndeclaredExclusionSurvivesGuard() {
		Judgment original = Judgment.notApplicable("absent feature");
		Verdict v = SimpleJury.builder()
			.judge(() -> original)
			.parallel(false)
			.votingStrategy(new AllMustPassStrategy(ErrorHandling.PROPAGATE, ExclusionHandling.EXCLUDE))
			.build()
			.vote();
		assertThat(v.individual()).containsExactly(original);
		assertThat(v.seats().getFirst().execution()).isEqualTo(SeatExecution.RETURNED_REJECTED);
		assertThat(v.seats().getFirst().rejection().reasonCode())
			.isEqualTo(JudgmentReasonCode.UNDECLARED_NOT_APPLICABLE);
		assertThat(v.conclusion()).isEqualTo(Verdict.Conclusion.INCONCLUSIVE);
	}

	@Test
	void emptyOpinionsContinueAndRemainReadable() {
		Verdict empty = Verdict.builder()
			.judgment(Judgment.error(JudgmentReasonCode.AGGREGATION_FAILED, "no opinions"))
			.provenance(VerdictProvenance.undecided())
			.build();
		AtomicInteger count = new AtomicInteger();
		Verdict v = CascadedJury.builder()
			.tier("opaque", () -> empty, RoutingRule.STOP_ON_ALL_OPINIONS_PASS)
			.tier("fallback", () -> {
				count.incrementAndGet();
				return Verdict.single("fallback", Judgment.pass("passed"));
			}, RoutingRule.FINAL_TIER)
			.build()
			.vote();
		assertThat(count.get()).isEqualTo(1);
		assertThat(v.conclusion()).isEqualTo(Verdict.Conclusion.PASS);
		assertThat(v.compositeAttempts().getFirst().verdict()).isSameAs(empty);
	}

	@Test
	void knownAuditOpinionTierIsRejectedBeforeExecution() {
		AtomicInteger calls = new AtomicInteger();
		Jury audit = new Jury() {
			public Verdict vote() {
				calls.incrementAndGet();
				throw new AssertionError("no execution");
			}

			public JuryDescription describe() {
				return new AuditJuryDescription("audit", List.of(Requirement.text("r", "1", "r")), false);
			}
		};
		assertThatThrownBy(() -> CascadedJury.builder()
			.tier("audit", audit, RoutingRule.STOP_ON_ALL_OPINIONS_PASS)
			.tier("final", audit, RoutingRule.FINAL_TIER)
			.build()).hasMessageContaining("opinions");
		assertThat(calls).hasValue(0);
	}

}
