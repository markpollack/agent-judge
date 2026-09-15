/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.jury.ConsensusStrategy;
import io.github.markpollack.judge.jury.ErrorPolicy;
import io.github.markpollack.judge.jury.Jury;
import io.github.markpollack.judge.jury.NotApplicablePolicy;
import io.github.markpollack.judge.jury.SimpleJury;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentReasonCode;
import io.github.markpollack.judge.result.JudgmentStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The three-argument constructor is the shape a judge that never excludes a subject is written in.
 *
 * <p>
 * It exists so that the overwhelmingly common judge — one that answers its question every time —
 * does not have to write a trailing {@code null} for a feature it does not use. What matters is
 * that the convenience is only a convenience: it must produce exactly the record the four-argument
 * form produces with {@code null}, it must be read as <em>no declaration</em> everywhere the
 * capability is looked up, and it must not become a way round the validation the canonical
 * constructor performs.
 * </p>
 */
@DisplayName("JudgeMetadata's three-argument constructor")
class JudgeMetadataConvenienceConstructorTest {

	private static final JudgeMetadata THREE_ARG = new JudgeMetadata("style", "a judge", JudgeType.DETERMINISTIC);

	private static JudgmentContext context() {
		return JudgmentContext.builder().goal("assess the change set").build();
	}

	/** A judge whose metadata is written in the three-argument style. */
	private record NeverExcludes(String name, Judgment result) implements JudgeWithMetadata {

		@Override
		public Judgment judge(JudgmentContext judgmentContext) {
			return this.result;
		}

		@Override
		public JudgeMetadata metadata() {
			return new JudgeMetadata(this.name, "a judge that never excludes", JudgeType.DETERMINISTIC);
		}

	}

	@Nested
	@DisplayName("It delegates, and nothing else")
	class Delegation {

		@Test
		@DisplayName("the fourth component is absent")
		void notApplicableWhenIsNull() {
			assertThat(THREE_ARG.notApplicableWhen()).isNull();
		}

		@Test
		@DisplayName("it produces the same record as the four-argument form with null")
		void itEqualsTheCanonicalFormWithNull() {
			JudgeMetadata fourArg = new JudgeMetadata("style", "a judge", JudgeType.DETERMINISTIC, null);

			assertThat(THREE_ARG).isEqualTo(fourArg).hasSameHashCodeAs(fourArg);
			assertThat(THREE_ARG.toString()).isEqualTo(fourArg.toString());
		}

		@Test
		@DisplayName("the first three components are carried through unchanged")
		void theOtherComponentsAreUnchanged() {
			assertThat(THREE_ARG.name()).isEqualTo("style");
			assertThat(THREE_ARG.description()).isEqualTo("a judge");
			assertThat(THREE_ARG.type()).isEqualTo(JudgeType.DETERMINISTIC);
		}

	}

	@Nested
	@DisplayName("Absence is not a declaration")
	class NotACapabilityDeclaration {

		@Test
		@DisplayName("the one lookup reads it as no capability")
		void theLookupReadsNoCapability() {
			assertThat(Judges.notApplicableCapability(new NeverExcludes("plain", Judgment.pass("ok")))).isEmpty();
		}

		@Test
		@DisplayName("wrapping it in a NamedJudge does not manufacture one")
		void aWrapperDoesNotManufactureOne() {
			Judge wrapped = Judges.named(new NeverExcludes("inner", Judgment.pass("ok")), "renamed");

			assertThat(Judges.notApplicableCapability(wrapped)).isEmpty();
			assertThat(Judges.notApplicableCapability(new NamedJudge(new NeverExcludes("inner", Judgment.pass("ok")),
					new JudgeMetadata("outer", "", JudgeType.DETERMINISTIC))))
				.as("an outer wrapper written the same way declares nothing either")
				.isEmpty();
		}

		@Test
		@DisplayName("the description publishes it as undeclared")
		void theDescriptionSaysUndeclared() {
			assertThat(Judges.describe(new NeverExcludes("plain", Judgment.pass("ok"))).notApplicableWhen()).isNull();
			assertThat(Judges.describe(new NeverExcludes("plain", Judgment.pass("ok"))).toPortable())
				.containsEntry("notApplicableWhen", Map.of("declared", false));
		}

		@Test
		@DisplayName("a jury of such judges cannot exclude, and builds under the refusing default")
		void aJuryOfThemCannotExclude() {
			assertThatCode(() -> SimpleJury.builder()
				.judge(new NeverExcludes("plain", Judgment.pass("ok")))
				.votingStrategy(new ConsensusStrategy())
				.build()).doesNotThrowAnyException();

			Jury jury = SimpleJury.builder()
				.judge(new NeverExcludes("plain", Judgment.pass("ok")))
				.votingStrategy(new ConsensusStrategy(ErrorPolicy.PROPAGATE, NotApplicablePolicy.EXCLUDE))
				.build();
			assertThat(jury.aggregateMayBeNotApplicable()).as("nothing was declared, so nothing may be excluded")
				.isFalse();
		}

		@Test
		@DisplayName("the seat guard contains an exclusion from a seat built on it")
		void theSeatGuardContainsAnExclusion() {
			Jury jury = SimpleJury.builder()
				.judge(new NeverExcludes("sneaky", Judgment.notApplicable("the change set contains no Java sources")))
				.judge(Judges.named(judgmentContext -> Judgment.pass("ok"), "honest"))
				.votingStrategy(new ConsensusStrategy(ErrorPolicy.IGNORE, NotApplicablePolicy.EXCLUDE))
				.build();

			Verdict verdict = jury.vote(context());
			Judgment sneaky = verdict.individualByName().get("sneaky");

			assertThat(sneaky.status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(sneaky.reasonCode()).isEqualTo(JudgmentReasonCode.UNDECLARED_NOT_APPLICABLE);
			assertThat(verdict.individualByName().get("honest").status()).as("the other judge is untouched")
				.isEqualTo(JudgmentStatus.PASS);
		}

	}

	@Nested
	@DisplayName("It delegates to the validation rather than round it")
	class Validation {

		@ParameterizedTest
		@ValueSource(strings = { "", " ", "   ", "\t", "\n" })
		@DisplayName("a blank name is refused exactly as the four-argument form refuses it")
		void aBlankNameIsRefused(String blank) {
			assertThatThrownBy(() -> new JudgeMetadata(blank, "a judge", JudgeType.DETERMINISTIC))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("name must be non-blank");

			assertThatThrownBy(() -> new JudgeMetadata(blank, "a judge", JudgeType.DETERMINISTIC, null))
				.as("the same rejection, with the same message, from the canonical form")
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage(messageOf(() -> new JudgeMetadata(blank, "a judge", JudgeType.DETERMINISTIC)));
		}

		@Test
		@DisplayName("a null name is refused exactly as the four-argument form refuses it")
		void aNullNameIsRefused() {
			assertThatThrownBy(() -> new JudgeMetadata(null, "a judge", JudgeType.DETERMINISTIC))
				.isInstanceOf(NullPointerException.class)
				.hasMessage("name must not be null");

			assertThatThrownBy(() -> new JudgeMetadata(null, "a judge", JudgeType.DETERMINISTIC, null))
				.isInstanceOf(NullPointerException.class)
				.hasMessage("name must not be null");
		}

		@Test
		@DisplayName("an ordinary name is unaffected")
		void anOrdinaryNameIsUnaffected() {
			assertThatCode(() -> new JudgeMetadata("style", "a judge", JudgeType.DETERMINISTIC))
				.doesNotThrowAnyException();
		}

		private static String messageOf(Runnable construction) {
			try {
				construction.run();
			}
			catch (RuntimeException ex) {
				return ex.getMessage();
			}
			throw new AssertionError("expected the construction to be refused");
		}

	}

}
