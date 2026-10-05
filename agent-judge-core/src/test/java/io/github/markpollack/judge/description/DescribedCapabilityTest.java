/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.description;

import io.github.markpollack.judge.verdict.KeySource;
import io.github.markpollack.judge.voting.StrategyDescription;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.markpollack.judge.JudgeMetadata;
import io.github.markpollack.judge.Judges;
import io.github.markpollack.judge.JudgeType;
import io.github.markpollack.judge.JudgeWithMetadata;
import io.github.markpollack.judge.completion.CompletionEvidence;
import io.github.markpollack.judge.voting.AllEligiblePassStrategy;
import io.github.markpollack.judge.voting.ErrorHandling;
import io.github.markpollack.judge.jury.Juries;
import io.github.markpollack.judge.jury.Jury;
import io.github.markpollack.judge.jury.NamedJury;
import io.github.markpollack.judge.voting.ExclusionHandling;
import io.github.markpollack.judge.jury.SimpleJury;
import io.github.markpollack.judge.voting.VotingStrategy;
import io.github.markpollack.judge.judgment.Judgment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A description must not state a capability its own jury contradicts.
 *
 * <p>
 * {@code aggregateMayBeNotApplicable} was derived from the strategy
 * <em>description</em>'s not-applicable policy. A custom strategy is entitled to override
 * {@link VotingStrategy#exclusionHandling()} and to leave {@code describe()} at its
 * supported default, and then that policy is absent from the description — so the
 * derivation read absent as {@code REFUSE} and published {@code false} for a jury that
 * reports {@code true} and can emit {@code NOT_APPLICABLE}.
 * </p>
 *
 * <p>
 * A false {@code false} is worse here than an absent value. Absence says "not recorded",
 * and a reader can go and find out; a confident {@code false} is indistinguishable from a
 * jury that really cannot exclude, and the whole point of publishing the capability is
 * that a reader does not have to run the jury to learn it. So the description carries
 * what the jury actually is, and a description that disagrees with a strategy that
 * <em>did</em> declare its policy is refused outright.
 * </p>
 */
@DisplayName("Described capability")
class DescribedCapabilityTest {

	private static final String CONDITION = "the change set contains no Java sources";

	private static final CompletionEvidence CONTEXT = CompletionEvidence.builder().request("describe").build();

	/** A judge that declares, in advance, that it may exclude a subject. */
	private record Conditional(String name) implements JudgeWithMetadata {

		@Override
		public Judgment judge() {
			return Judgment.notApplicable(CONDITION);
		}

		@Override
		public JudgeMetadata metadata() {
			return new JudgeMetadata(this.name, "a conditional judge", JudgeType.DETERMINISTIC, CONDITION);
		}

	}

	/**
	 * A compliant custom strategy: it delegates the reduction to a built-in configured to
	 * exclude, states that policy through the interface method composition validation
	 * reads, and leaves {@code describe()} at the supported default.
	 */
	private static final class DelegatingExcluder implements VotingStrategy {

		private final VotingStrategy delegate = new AllEligiblePassStrategy(ErrorHandling.PROPAGATE,
				ExclusionHandling.EXCLUDE);

		@Override
		public Judgment aggregate(List<io.github.markpollack.judge.voting.Ballot> ballots) {
			var judgments = io.github.markpollack.judge.voting.Ballots.judgments(ballots);
			return this.delegate.aggregate(ballots);
		}

		@Override
		public String getName() {
			return "delegatingExcluder";
		}

		@Override
		public ExclusionHandling exclusionHandling() {
			return ExclusionHandling.EXCLUDE;
		}

		@Override
		public StrategyDescription describe() {
			return StrategyDescription.declared(this, null, ExclusionHandling.EXCLUDE, null, Map.of());
		}

	}

	private static SimpleJury capableJury() {
		return SimpleJury.builder()
			.seat(declared(new Conditional("conditional")))
			.votingStrategy(new DelegatingExcluder())
			.build();
	}

	@Nested
	@DisplayName("A custom strategy that declares its policy only through the interface")
	class CustomStrategy {

		@Test
		@DisplayName("the jury and its description agree about the capability")
		void theJuryAndItsDescriptionAgree() {
			SimpleJury jury = capableJury();

			assertThat(jury.aggregateMayBeNotApplicable()).as("the jury can emit NOT_APPLICABLE").isTrue();
			assertThat(jury.describe().aggregateMayBeNotApplicable()).as("and says so").isTrue();
		}

		@Test
		@DisplayName("the portable form a reader sees says the same thing")
		void thePortableFormAgrees() {
			assertThat(capableJury().describe().toPortable()).containsEntry("aggregateMayBeNotApplicable", true);
		}

		@Test
		@DisplayName("the jury really does produce the excluded aggregate the description promises")
		void theCapabilityIsReal() {
			assertThat(capableJury().vote().judgment().notApplicable()).isTrue();
		}

		@Test
		@DisplayName("a meta-jury over it agrees too")
		void aMetaJuryAgrees() {
			Jury meta = Juries.meta(new DelegatingExcluder(), new NamedJury("panel", capableJury()));

			assertThat(meta.aggregateMayBeNotApplicable()).isTrue();
			assertThat(meta.describe().aggregateMayBeNotApplicable()).isTrue();
			assertThat(meta.describe().toPortable()).containsEntry("aggregateMayBeNotApplicable", true);
		}

	}

	@Nested
	@DisplayName("A built-in strategy")
	class BuiltInStrategy {

		@Test
		@DisplayName("still describes its capability exactly as before")
		void builtInsAreUnchanged() {
			SimpleJury excluding = SimpleJury.builder()
				.seat(declared(new Conditional("conditional")))
				.votingStrategy(new AllEligiblePassStrategy(ErrorHandling.PROPAGATE, ExclusionHandling.EXCLUDE))
				.build();
			SimpleJury failing = SimpleJury.builder()
				.seat(declared(new Conditional("conditional")))
				.votingStrategy(new AllEligiblePassStrategy(ErrorHandling.PROPAGATE, ExclusionHandling.TREAT_AS_FAIL))
				.build();

			assertThat(excluding.describe().aggregateMayBeNotApplicable()).isTrue();
			assertThat(failing.describe().aggregateMayBeNotApplicable())
				.as("one declared seat retains its exclusion by identity")
				.isTrue();
			assertThat(failing.aggregateMayBeNotApplicable()).isTrue();
		}

		@Test
		void applicabilityHasOneStructuredSource() {
			var strategy = new AllEligiblePassStrategy(ErrorHandling.PROPAGATE, ExclusionHandling.EXCLUDE).describe();
			var seats = List.of(new SeatDescription(0, "conditional", KeySource.DECLARED, 1.0,
					io.github.markpollack.judge.description.JudgeDescription.of(new Conditional("conditional"))));
			assertThat(new SimpleJuryDescription(strategy, seats).aggregateMayBeNotApplicable()).isTrue();
			assertThat(new SimpleJuryDescription(strategy, List.of()).aggregateMayBeNotApplicable()).isFalse();
		}

	}

	private static io.github.markpollack.judge.jury.JudgeSeat declared(io.github.markpollack.judge.Judge producer) {
		return io.github.markpollack.judge.jury.JudgeSeat
			.named(((io.github.markpollack.judge.JudgeWithMetadata) producer).metadata().name(), producer)
			.notApplicableWhen(io.github.markpollack.judge.Judges.notApplicableCapability(producer).orElseThrow());
	}

}
