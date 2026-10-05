/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import io.github.markpollack.judge.verdict.RoutingRule;
import io.github.markpollack.judge.verdict.Seat;
import io.github.markpollack.judge.verdict.Verdict;
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

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.JudgeMetadata;
import io.github.markpollack.judge.JudgeType;
import io.github.markpollack.judge.JudgeWithMetadata;
import io.github.markpollack.judge.Judges;
import io.github.markpollack.judge.NamedJudge;
import io.github.markpollack.judge.completion.CompletionEvidence;
import io.github.markpollack.judge.description.JudgeDescription;
import io.github.markpollack.judge.description.SimpleJuryDescription;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentReasonCode;
import io.github.markpollack.judge.judgment.JudgmentStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Part G: a judge may only exclude a subject if it said so first.
 *
 * <p>
 * Exclusion is the one outcome that takes a criterion out of its own denominator, which
 * makes it the one outcome an instrument could use to dodge a question it does not like
 * the look of. The declaration moves that provenance to where the jury is assembled — a
 * sentence a reviewer can disagree with — and everything here exists to keep the two ends
 * honest: what the description says, what construction validates, and what the guard
 * actually honours must be the same fact.
 * </p>
 */
@DisplayName("The exclusion capability")
class ExclusionCapabilityTest {

	private static final String CONDITION = "the change set contains no Java sources";

	/** A judge that declares, in advance, that it may exclude a subject. */
	private record Conditional(String name, Judgment result) implements JudgeWithMetadata {

		@Override
		public Judgment judge() {
			return this.result;
		}

		@Override
		public JudgeMetadata metadata() {
			return new JudgeMetadata(this.name, "a conditional judge", JudgeType.DETERMINISTIC, CONDITION);
		}

	}

	/** A judge that carries metadata and declares no capability at all. */
	private record Unconditional(String name, Judgment result) implements JudgeWithMetadata {

		@Override
		public Judgment judge() {
			return this.result;
		}

		@Override
		public JudgeMetadata metadata() {
			return new JudgeMetadata(this.name, "an unconditional judge", JudgeType.DETERMINISTIC, null);
		}

	}

	private static CompletionEvidence context() {
		return CompletionEvidence.builder().request("assess the change set").build();
	}

	private static Judgment excluded() {
		return Judgment.notApplicable(CONDITION);
	}

	@Nested
	@DisplayName("One lookup, through the wrapper chain")
	class Lookup {

		@Test
		@DisplayName("a judge that carries only metadata declares no capability")
		void metadataWithoutADeclaration() {
			assertThat(Judges.notApplicableCapability(new Unconditional("plain", Judgment.pass("ok")))).isEmpty();
			assertThat(Judges.notApplicableCapability(() -> Judgment.pass("lambda"))).isEmpty();
		}

		@Test
		@DisplayName("a declaration is found through a wrapper that declares nothing")
		void aTransparentWrapperPreservesTheCapability() {
			Judge wrapped = Judges.named(new Conditional("inner", excluded()), "renamed");

			assertThat(Judges.notApplicableCapability(wrapped)).contains(CONDITION);
		}

		@Test
		@DisplayName("the outermost declaration wins, because it is the one the jury seated")
		void theOutermostDeclarationWins() {
			Judge wrapped = new NamedJudge(new Conditional("inner", excluded()),
					new JudgeMetadata("outer", "", JudgeType.DETERMINISTIC, "the subject is a binary artifact"));

			assertThat(Judges.notApplicableCapability(wrapped)).contains("the subject is a binary artifact");
		}

		@Test
		@DisplayName("a wrapper naming an incapable judge does not manufacture a capability")
		void namingDoesNotManufactureACapability() {
			assertThat(Judges.notApplicableCapability(Judges.named(() -> Judgment.pass("ok"), "named"))).isEmpty();
		}

		@Test
		@DisplayName("the deduplicating rename Juries.fromJudges applies keeps the capability underneath")
		void deduplicationPreservesTheCapability() {
			Jury jury = SimpleJury.builder()
				.seat(declared(new Conditional("same", excluded())))
				.seat(io.github.markpollack.judge.jury.JudgeSeat.named("same-2", new Conditional("same", excluded()))
					.notApplicableWhen(CONDITION))
				.votingStrategy(new ConsensusStrategy(ErrorHandling.PROPAGATE, ExclusionHandling.EXCLUDE))
				.build();

			SimpleJuryDescription description = (SimpleJuryDescription) jury.describe();
			assertThat(description.seats()).extracting(seat -> seat.judge().notApplicableWhen())
				.as("the second seat was renamed, not stripped")
				.containsExactly(CONDITION, CONDITION);
			assertThat(jury.aggregateMayBeNotApplicable()).isTrue();
		}

		@Test
		@DisplayName("a judge whose metadata cannot be read is refused rather than read as incapable")
		void unreadableMetadataIsNotAbsence() {
			Judge throwing = new JudgeWithMetadata() {
				@Override
				public Judgment judge() {
					return Judgment.pass("ok");
				}

				@Override
				public JudgeMetadata metadata() {
					throw new IllegalStateException("registry offline");
				}
			};

			assertThatThrownBy(() -> Judges.notApplicableCapability(throwing))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("metadata() threw");
		}

	}

	@Nested
	@DisplayName("Invalid declarations are refused before any judge runs")
	class InvalidDeclarations {

		@Test
		@DisplayName("a blank declaration claims a capability while saying nothing about when it applies")
		void blankIsRefused() {
			assertThatThrownBy(() -> new JudgeMetadata("j", "", JudgeType.DETERMINISTIC, "   "))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("notApplicableWhen must be non-blank");
			assertThatCode(() -> new JudgeMetadata("j", "", JudgeType.DETERMINISTIC, null)).doesNotThrowAnyException();
		}

		@Test
		@DisplayName("a judge whose metadata cannot be read has declared nothing, and never runs to use it")
		void unreadableMetadataDeclaresNothing() {
			Judge throwing = new JudgeWithMetadata() {
				@Override
				public Judgment judge() {
					return excluded();
				}

				@Override
				public JudgeMetadata metadata() {
					throw new IllegalStateException("registry offline");
				}
			};

			Jury jury = SimpleJury.builder()
				.judge(throwing)
				.votingStrategy(new ConsensusStrategy(ErrorHandling.IGNORE, ExclusionHandling.EXCLUDE))
				.build();

			assertThat(jury.aggregateMayBeNotApplicable()).as("nothing was declared, so nothing may be excluded")
				.isFalse();
			Judgment seat = jury.vote().individual().get(0);
			assertThat(seat.reasonCode()).as("the jury cannot name the judge, so it does not run it")
				.isEqualTo(JudgmentReasonCode.JUDGE_METADATA_UNREADABLE);
			assertThatThrownBy(jury::describe, "describing it still fails loudly")
				.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		@DisplayName("a declaration that differs between build and description is refused")
		void anUnstableDeclarationIsRefused() {
			Judge unstable = new JudgeWithMetadata() {

				private int calls;

				@Override
				public Judgment judge() {
					return Judgment.pass("ok");
				}

				@Override
				public JudgeMetadata metadata() {
					return new JudgeMetadata("unstable", "", JudgeType.DETERMINISTIC,
							this.calls++ == 0 ? null : CONDITION);
				}
			};

			Jury jury = SimpleJury.builder().judge(unstable).votingStrategy(new ConsensusStrategy()).build();

			assertThat(jury.describe().aggregateMayBeNotApplicable()).isFalse();
			assertThat(((SimpleJuryDescription) jury.describe()).seats().getFirst().judge().notApplicableWhen())
				.isNull();
		}

	}

	@Nested
	@DisplayName("Composition is validated at build time")
	class Composition {

		@Test
		@DisplayName("a capable seat under a refusing strategy is a construction error")
		void aCapableSeatUnderRefuseIsRejected() {
			assertThatThrownBy(() -> SimpleJury.builder()
				.seat(declared(new Conditional("conditional", excluded())))
				.votingStrategy(new ConsensusStrategy())
				.build()).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("refuses exclusions");
		}

		@ParameterizedTest
		@EnumSource(value = ExclusionHandling.class, names = { "EXCLUDE", "TREAT_AS_FAIL" })
		@DisplayName("the same seat builds once the strategy says what to do with an exclusion")
		void aCapableSeatBuildsUnderAPolicyThatDecides(ExclusionHandling policy) {
			assertThatCode(() -> SimpleJury.builder()
				.seat(declared(new Conditional("conditional", excluded())))
				.votingStrategy(new ConsensusStrategy(ErrorHandling.PROPAGATE, policy))
				.build()).doesNotThrowAnyException();
		}

		@Test
		@DisplayName("an incapable jury builds under the default, which is the common case")
		void anIncapableJuryBuildsUnderTheDefault() {
			assertThatCode(() -> SimpleJury.builder()
				.judge(new Unconditional("plain", Judgment.pass("ok")))
				.votingStrategy(new ConsensusStrategy())
				.build()).doesNotThrowAnyException();
		}

		@Test
		@DisplayName("a possibly-excluding member under a refusing meta-strategy is a construction error")
		void aCapableMemberUnderRefuseIsRejected() {
			Jury capable = capableJury();

			assertThatThrownBy(() -> Juries.meta(new ConsensusStrategy(), new NamedJury("rubric", capable)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("refuses exclusions");
		}

		@Test
		@DisplayName("an opaque jury makes no pre-spend guarantee, so it composes anywhere and is checked at runtime")
		void anOpaqueJuryIsNotCapableByDefault() {
			Jury opaque = new io.github.markpollack.judge.jury.VotingJury() {
				@Override
				public List<Judge> getJudges() {
					return List.of();
				}

				@Override
				public VotingStrategy getVotingStrategy() {
					return new ConsensusStrategy();
				}

				@Override
				public Verdict vote() {
					return Verdict.single("sole", excluded());
				}
			};

			assertThat(opaque.aggregateMayBeNotApplicable()).isFalse();
			assertThatCode(() -> Juries.meta(new ConsensusStrategy(), new NamedJury("opaque", opaque)))
				.doesNotThrowAnyException();
		}

	}

	@Nested
	@DisplayName("The conservative bound")
	class Bound {

		@Test
		@DisplayName("a simple jury is capable only when a seat declares it and the policy honours it")
		void simpleJuryBound() {
			assertThat(capableJury().aggregateMayBeNotApplicable()).isTrue();
			assertThat(SimpleJury.builder()
				.seat(declared(new Conditional("conditional", excluded())))
				.votingStrategy(new ConsensusStrategy(ErrorHandling.PROPAGATE, ExclusionHandling.TREAT_AS_FAIL))
				.build()
				.aggregateMayBeNotApplicable()).as("one declared seat retains its exclusion by identity").isTrue();
			assertThat(SimpleJury.builder()
				.judge(new Unconditional("plain", Judgment.pass("ok")))
				.votingStrategy(new ConsensusStrategy(ErrorHandling.PROPAGATE, ExclusionHandling.EXCLUDE))
				.build()
				.aggregateMayBeNotApplicable()).as("nothing to exclude").isFalse();
		}

		@Test
		@DisplayName("a meta-jury and a cascade propagate the bound from what they compose")
		void compositeBounds() {
			Jury capable = capableJury();
			Jury meta = Juries.meta(new ConsensusStrategy(ErrorHandling.PROPAGATE, ExclusionHandling.EXCLUDE),
					new NamedJury("rubric", capable));
			Jury cascade = CascadedJury.builder().tier("only", meta, RoutingRule.FINAL_TIER).build();

			assertThat(meta.aggregateMayBeNotApplicable()).isTrue();
			assertThat(cascade.aggregateMayBeNotApplicable()).isTrue();

			Jury refusingMeta = Juries.meta(new ConsensusStrategy(),
					new NamedJury("plain",
							SimpleJury.builder()
								.judge(new Unconditional("plain", Judgment.pass("ok")))
								.votingStrategy(new ConsensusStrategy())
								.build()));
			assertThat(refusingMeta.aggregateMayBeNotApplicable()).isFalse();
		}

		@Test
		@DisplayName("the description states the same bound the jury computes, custom strategies included")
		void theDescriptionAgreesWithTheJury() {
			// The custom-strategy cases are the ones that matter. A built-in declares its
			// not-applicable policy, so a description derived from that declaration can
			// only
			// agree; a strategy that states its policy through the interface alone is
			// where a
			// derived description used to publish a confident, wrong false.
			for (Jury jury : List.of(capableJury(), customCapableJury(),
					Juries.meta(new ConsensusStrategy(ErrorHandling.PROPAGATE, ExclusionHandling.EXCLUDE),
							new NamedJury("rubric", capableJury())),
					Juries.meta(new DelegatingExcluder(), new NamedJury("rubric", customCapableJury())),
					CascadedJury.builder().tier("only", capableJury(), RoutingRule.FINAL_TIER).build(),
					CascadedJury.builder().tier("only", customCapableJury(), RoutingRule.FINAL_TIER).build())) {
				assertThat(jury.aggregateMayBeNotApplicable()).as("every jury here really can exclude").isTrue();
				assertThat(jury.describe().aggregateMayBeNotApplicable()).isEqualTo(jury.aggregateMayBeNotApplicable());
				assertThat(jury.describe().toPortable()).containsEntry("aggregateMayBeNotApplicable",
						jury.aggregateMayBeNotApplicable());
			}
		}

	}

	@Nested
	@DisplayName("The seat guard")
	class SeatGuard {

		@Test
		@DisplayName("an exclusion from an undeclared seat becomes a coded error, not an honoured exclusion")
		void undeclaredExclusionIsContained() {
			Jury jury = SimpleJury.builder()
				.judge(Judges.named(() -> excluded(), "sneaky"))
				.judge(Judges.named(() -> Judgment.pass("ok"), "honest"))
				.votingStrategy(new ConsensusStrategy(ErrorHandling.IGNORE, ExclusionHandling.EXCLUDE))
				.build();

			Verdict verdict = jury.vote();
			Judgment sneaky = verdict.individualByName().get("sneaky");

			assertThat(sneaky.status()).isEqualTo(JudgmentStatus.NOT_APPLICABLE);
			assertThat(verdict.seats().get(0).rejection().reasonCode())
				.isEqualTo(JudgmentReasonCode.UNDECLARED_NOT_APPLICABLE);
			assertThat(verdict.seats().get(0).rejection().reasoning()).contains("without declaring")
				.contains(CONDITION);
		}

		@Test
		@DisplayName("it is a judge-origin error, so the configured error policy governs it")
		void theErrorHandlingGovernsIt() {
			Judgment propagated = jurySeatingAnUndeclaredExclusion(ErrorHandling.PROPAGATE).judgment();
			assertThat(propagated.reasonCode()).isEqualTo(JudgmentReasonCode.ERRORS_PROPAGATED);

			// A gate, so the failing contribution shows in the aggregate rather than
			// being
			// absorbed into a "the judges disagree" abstention.
			Judgment treatedAsFail = SimpleJury.builder()
				.judge(Judges.named(() -> excluded(), "sneaky"))
				.judge(Judges.named(() -> Judgment.pass("ok"), "honest"))
				.votingStrategy(new AllEligiblePassStrategy(ErrorHandling.TREAT_AS_FAIL, ExclusionHandling.EXCLUDE))
				.build()
				.vote()
				.judgment();
			assertThat(treatedAsFail.status()).as("a judge's own error may be scored; machinery's may not")
				.isEqualTo(JudgmentStatus.FAIL);
		}

		@Test
		@DisplayName("a declared seat's exclusion is honoured untouched")
		void aDeclaredExclusionIsHonoured() {
			Jury jury = SimpleJury.builder()
				.seat(declared(new Conditional("conditional", excluded())))
				.judge(Judges.named(() -> Judgment.pass("ok"), "honest"))
				.votingStrategy(new ConsensusStrategy(ErrorHandling.IGNORE, ExclusionHandling.EXCLUDE))
				.build();

			Verdict verdict = jury.vote();

			assertThat(verdict.individualByName().get("conditional").status()).isEqualTo(JudgmentStatus.NOT_APPLICABLE);
			assertThat(verdict.judgment().status()).isEqualTo(JudgmentStatus.PASS);
		}

		private Verdict jurySeatingAnUndeclaredExclusion(ErrorHandling errorPolicy) {
			return SimpleJury.builder()
				.judge(Judges.named(() -> excluded(), "sneaky"))
				.judge(Judges.named(() -> Judgment.pass("ok"), "honest"))
				.votingStrategy(new ConsensusStrategy(errorPolicy, ExclusionHandling.EXCLUDE))
				.build()
				.vote();
		}

	}

	@Nested
	@DisplayName("requireDeclaredNames")
	class DeclaredNames {

		@Test
		@DisplayName("a positional seat identifies a position rather than a judge, and is rejected")
		void positionalSeatsAreRejected() {
			assertThatThrownBy(() -> SimpleJury.builder()
				.judge(Judges.named(() -> Judgment.pass("ok"), "named"))
				.judge(() -> Judgment.pass("lambda"))
				.votingStrategy(new ConsensusStrategy())
				.requireDeclaredNames()
				.build()).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("seats[1] has no declared name")
				.hasMessageContaining("Judge#2");
		}

		@Test
		@DisplayName("two seats declaring the same name would overwrite one another")
		void duplicateDeclaredNamesAreRejected() {
			assertThatThrownBy(() -> SimpleJury.builder()
				.judge(Judges.named(() -> Judgment.pass("a"), "same"))
				.judge(Judges.named(() -> Judgment.fail("b"), "same"))
				.votingStrategy(new ConsensusStrategy())
				.requireDeclaredNames()
				.build()).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("share the verdict key 'same'");
		}

		@Test
		@DisplayName("a declared name that collides with a positional key is rejected")
		void declaredPositionalCollisionsAreRejected() {
			// Seat 0 declares the literal name a positional key would take at seat 1.
			// Without the
			// guard, the unnamed seat's judgment lands under the same key and one of the
			// two
			// disappears.
			assertThatThrownBy(() -> SimpleJury.builder()
				.judge(Judges.named(() -> Judgment.pass("a"), "Judge#2"))
				.judge(() -> Judgment.fail("b"))
				.votingStrategy(new ConsensusStrategy())
				.requireDeclaredNames()
				.build()).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("seats[1]");
		}

		@Test
		@DisplayName("a fully declared jury builds, and it is opt-in so an unnamed jury still builds without it")
		void declaredNamesBuild() {
			assertThatCode(() -> SimpleJury.builder()
				.judge(Judges.named(() -> Judgment.pass("a"), "first"))
				.judge(Judges.named(() -> Judgment.fail("b"), "second"))
				.votingStrategy(new ConsensusStrategy())
				.requireDeclaredNames()
				.build()).doesNotThrowAnyException();
			assertThatCode(() -> SimpleJury.builder()
				.judge(() -> Judgment.pass("lambda"))
				.votingStrategy(new ConsensusStrategy())
				.build()).doesNotThrowAnyException();
		}

		@Test
		@DisplayName("the collision it exists for is real: without it, one judgment overwrites the other")
		void theCollisionItGuardsAgainstIsReal() {
			Verdict verdict = SimpleJury.builder()
				.judge(Judges.named(() -> Judgment.pass("a"), "Judge#2"))
				.judge(() -> Judgment.fail("b"))
				.votingStrategy(new ConsensusStrategy())
				.build()
				.vote();

			assertThat(verdict.individual()).hasSize(2);
			assertThat(verdict.individualByName()).as("two judgments, one key").hasSize(1);
		}

	}

	@Test
	@DisplayName("the description carries the effective capability, so a reader need not run the jury")
	void theDescriptionCarriesTheCapability() {
		JudgeDescription described = io.github.markpollack.judge.description.JudgeDescription
			.of(Judges.named(new Conditional("inner", excluded()), "renamed"));

		assertThat(described.notApplicableWhen()).isEqualTo(CONDITION);
		assertThat(described.toPortable()).containsEntry("notApplicableWhen",
				Map.of("declared", true, "value", CONDITION));
		assertThat(io.github.markpollack.judge.description.JudgeDescription
			.of(new Unconditional("plain", Judgment.pass("ok")))
			.toPortable()).containsEntry("notApplicableWhen", Map.of("declared", false));
	}

	private static Jury capableJury() {
		return SimpleJury.builder()
			.seat(declared(new Conditional("conditional", excluded())))
			.votingStrategy(new ConsensusStrategy(ErrorHandling.PROPAGATE, ExclusionHandling.EXCLUDE))
			.build();
	}

	/**
	 * The same capable jury, under a custom strategy that declares its policy only
	 * through the interface.
	 */
	private static Jury customCapableJury() {
		return SimpleJury.builder()
			.seat(declared(new Conditional("conditional", excluded())))
			.votingStrategy(new DelegatingExcluder())
			.build();
	}

	/**
	 * A compliant custom strategy: it delegates the reduction to a built-in configured to
	 * exclude, states that policy through the method composition validation reads, and
	 * leaves {@code describe()} at its supported default.
	 */
	private static final class DelegatingExcluder implements VotingStrategy {

		private final VotingStrategy delegate = new ConsensusStrategy(ErrorHandling.PROPAGATE,
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

	}

	private static io.github.markpollack.judge.jury.JudgeSeat declared(io.github.markpollack.judge.Judge producer) {
		return io.github.markpollack.judge.jury.JudgeSeat
			.named(((io.github.markpollack.judge.JudgeWithMetadata) producer).metadata().name(), producer)
			.notApplicableWhen(io.github.markpollack.judge.Judges.notApplicableCapability(producer).orElseThrow());
	}

}
