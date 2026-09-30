/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.description;

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
import io.github.markpollack.judge.jury.AllMustPassStrategy;
import io.github.markpollack.judge.jury.ErrorPolicy;
import io.github.markpollack.judge.jury.Juries;
import io.github.markpollack.judge.jury.Jury;
import io.github.markpollack.judge.jury.NamedJury;
import io.github.markpollack.judge.jury.NotApplicablePolicy;
import io.github.markpollack.judge.jury.SimpleJury;
import io.github.markpollack.judge.jury.VotingStrategy;
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
 * {@link VotingStrategy#notApplicablePolicy()} and to leave {@code describe()} at its
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
	private record Conditional(String name) implements JudgeWithMetadata<CompletionEvidence> {

		@Override
		public Judgment judge(CompletionEvidence context) {
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

		private final VotingStrategy delegate = new AllMustPassStrategy(ErrorPolicy.PROPAGATE,
				NotApplicablePolicy.EXCLUDE);

		@Override
		public Judgment aggregate(List<Judgment> judgments, Map<String, Double> weights) {
			return this.delegate.aggregate(judgments, weights);
		}

		@Override
		public String getName() {
			return "delegatingExcluder";
		}

		@Override
		public NotApplicablePolicy notApplicablePolicy() {
			return NotApplicablePolicy.EXCLUDE;
		}

	}

	private static SimpleJury<CompletionEvidence> capableJury() {
		return SimpleJury.<CompletionEvidence>builder()
			.judge(new Conditional("conditional"))
			.votingStrategy(new DelegatingExcluder())
			.build();
	}

	@Nested
	@DisplayName("A custom strategy that declares its policy only through the interface")
	class CustomStrategy {

		@Test
		@DisplayName("the jury and its description agree about the capability")
		void theJuryAndItsDescriptionAgree() {
			SimpleJury<CompletionEvidence> jury = capableJury();

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
			assertThat(capableJury().vote(CONTEXT).judgment().notApplicable()).isTrue();
		}

		@Test
		@DisplayName("a meta-jury over it agrees too")
		void aMetaJuryAgrees() {
			Jury<CompletionEvidence> meta = Juries.meta(new DelegatingExcluder(),
					new NamedJury<CompletionEvidence>("panel", capableJury()));

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
			SimpleJury<CompletionEvidence> excluding = SimpleJury.<CompletionEvidence>builder()
				.judge(new Conditional("conditional"))
				.votingStrategy(new AllMustPassStrategy(ErrorPolicy.PROPAGATE, NotApplicablePolicy.EXCLUDE))
				.build();
			SimpleJury<CompletionEvidence> failing = SimpleJury.<CompletionEvidence>builder()
				.judge(new Conditional("conditional"))
				.votingStrategy(new AllMustPassStrategy(ErrorPolicy.PROPAGATE, NotApplicablePolicy.TREAT_AS_FAIL))
				.build();

			assertThat(excluding.describe().aggregateMayBeNotApplicable()).isTrue();
			assertThat(failing.describe().aggregateMayBeNotApplicable())
				.as("one declared seat retains its exclusion by identity")
				.isTrue();
			assertThat(failing.aggregateMayBeNotApplicable()).isTrue();
		}

		@Test
		@DisplayName("a description that contradicts a declared policy is refused, not stored")
		void aContradictionIsRefused() {
			StrategyDescription declaredExcluder = new AllMustPassStrategy(ErrorPolicy.PROPAGATE,
					NotApplicablePolicy.EXCLUDE)
				.describe();
			List<SeatDescription> capableSeat = List.of(new SeatDescription(0, "conditional", KeySource.DECLARED, 1.0,
					Judges.describe(new Conditional("conditional"))));

			assertThatThrownBy(() -> new SimpleJuryDescription(declaredExcluder, capableSeat, false))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("aggregateMayBeNotApplicable");
			assertThatThrownBy(() -> new SimpleJuryDescription(declaredExcluder, List.of(), true))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("aggregateMayBeNotApplicable");
			assertThatCode(() -> new SimpleJuryDescription(declaredExcluder, capableSeat, true))
				.doesNotThrowAnyException();
		}

	}

}
