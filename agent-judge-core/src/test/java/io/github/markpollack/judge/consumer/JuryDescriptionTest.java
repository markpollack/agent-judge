/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.consumer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.JudgeTestFixtures;
import io.github.markpollack.judge.JudgeType;
import io.github.markpollack.judge.Judges;
import io.github.markpollack.judge.NamedJudge;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.description.CascadedJuryDescription;
import io.github.markpollack.judge.description.ConfiguredJudge;
import io.github.markpollack.judge.description.ImplementationIdentity;
import io.github.markpollack.judge.description.ImplementationIdentity.Form;
import io.github.markpollack.judge.description.JudgeDescription;
import io.github.markpollack.judge.description.JuryDescription;
import io.github.markpollack.judge.description.KeySource;
import io.github.markpollack.judge.description.MemberDescription;
import io.github.markpollack.judge.description.MetaJuryDescription;
import io.github.markpollack.judge.description.OpaqueJuryDescription;
import io.github.markpollack.judge.description.SeatDescription;
import io.github.markpollack.judge.description.SimpleJuryDescription;
import io.github.markpollack.judge.description.StrategyDescription;
import io.github.markpollack.judge.description.TierDescription;
import io.github.markpollack.judge.jury.AggregationEvidence;
import io.github.markpollack.judge.jury.AllMustPassStrategy;
import io.github.markpollack.judge.jury.AverageVotingStrategy;
import io.github.markpollack.judge.jury.CascadedJury;
import io.github.markpollack.judge.jury.CompositeAttempt;
import io.github.markpollack.judge.jury.ConjunctiveStrategy;
import io.github.markpollack.judge.jury.ConsensusStrategy;
import io.github.markpollack.judge.jury.ErrorPolicy;
import io.github.markpollack.judge.jury.Juries;
import io.github.markpollack.judge.jury.Jury;
import io.github.markpollack.judge.jury.MajorityVotingStrategy;
import io.github.markpollack.judge.jury.MedianVotingStrategy;
import io.github.markpollack.judge.jury.NotApplicablePolicy;
import io.github.markpollack.judge.jury.NamedJury;
import io.github.markpollack.judge.jury.SimpleJury;
import io.github.markpollack.judge.jury.TiePolicy;
import io.github.markpollack.judge.jury.TierPolicy;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.jury.VotingStrategy;
import io.github.markpollack.judge.jury.WeightedAverageStrategy;
import io.github.markpollack.judge.result.Judgment;

import static io.github.markpollack.judge.JudgeTestFixtures.simpleContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * R1–R3 of the jury description, obtained only through public API from a package outside
 * {@code io.github.markpollack.judge.jury} and {@code io.github.markpollack.judge.description}.
 */
@DisplayName("A jury describes its configuration before it votes")
class JuryDescriptionTest {

	private static final ObjectMapper JSON = new ObjectMapper();

	private static ImplementationIdentity named(Class<?> type) {
		return new ImplementationIdentity(Form.NAMED, type.getName(), null);
	}

	@Test
	void theDescriptionVersionIsPinnedAtTwo() {
		assertThat(JuryDescription.DESCRIPTION_VERSION)
			.as("increment only for a format change: a key added, removed or renamed at any level; a value "
					+ "vocabulary added or changed; or a changed derivation rule. Never for a change in what a "
					+ "judge declares. Re-pin the golden description in CrossJvmDescriptionStabilityTest with it.")
			.isEqualTo(2);
	}

	@Nested
	@DisplayName("SimpleJury seats")
	class SimpleJurySeats {

		private SimpleJury mixedJury() {
			return SimpleJury.builder()
				.judge(Judges.named(new KeywordJudge("done"), "keyword"), 2.0)
				.judge(ctx -> Judgment.pass("unnamed lambda"))
				.judge(Judges.named(ctx -> Judgment.fail("strict"), "strict", "a strict judge", JudgeType.LLM_POWERED),
						0.5)
				.votingStrategy(new WeightedAverageStrategy(0.6, ErrorPolicy.IGNORE))
				.parallel(false)
				.build();
		}

		@Test
		void everySeatPairsPositionVerdictKeyKeySourceAndWeight() {
			SimpleJuryDescription description = (SimpleJuryDescription) mixedJury().describe();

			assertThat(description.seats())
				.extracting(SeatDescription::position, SeatDescription::verdictKey, SeatDescription::keySource,
						SeatDescription::weight)
				.containsExactly(tuple(0, "keyword", KeySource.DECLARED, 2.0),
						tuple(1, "Judge#2", KeySource.POSITIONAL, 1.0), tuple(2, "strict", KeySource.DECLARED, 0.5));
			assertThat(description.strategy()).isEqualTo(new StrategyDescription("weightedAverage",
					named(WeightedAverageStrategy.class), ErrorPolicy.IGNORE, NotApplicablePolicy.REFUSE, 0.6,
					Map.of()));
		}

		@Test
		void aNamedJudgeIsImplementedByTheClassItWraps() {
			JudgeDescription judge = ((SimpleJuryDescription) mixedJury().describe()).seats().get(0).judge();

			assertThat(judge.name()).isEqualTo("keyword");
			assertThat(judge.type()).isEqualTo(JudgeType.DETERMINISTIC);
			assertThat(judge.delegateName()).isNull();
			assertThat(judge.delegateType()).isNull();
			assertThat(judge.implementation()).isEqualTo(named(KeywordJudge.class));
			assertThat(judge.configuration()).isNull();
		}

		@Test
		void anUnnamedLambdaIsPositionalAndHidden() {
			SeatDescription seat = ((SimpleJuryDescription) mixedJury().describe()).seats().get(1);

			assertThat(seat.judge().name()).isNull();
			assertThat(seat.judge().implementation()).isEqualTo(new ImplementationIdentity(Form.HIDDEN, null, null));
			Map<String, Object> portable = seat.judge().toPortable();
			assertThat(portable.get("metadata")).isEqualTo(Map.of("declared", false));
			assertThat(portable.get("implementation")).isEqualTo(Map.of("form", "HIDDEN"));
		}

		@Test
		void seatsJoinTheVerdictTheSameJuryReturns() {
			SimpleJury jury = mixedJury();
			List<SeatDescription> seats = ((SimpleJuryDescription) jury.describe()).seats();

			Verdict verdict = jury.vote(simpleContext("describe before voting"));

			assertThat(verdict.individualByName().keySet())
				.containsExactlyElementsOf(seats.stream().map(SeatDescription::verdictKey).toList());
			for (SeatDescription seat : seats) {
				assertThat(verdict.weights()).containsEntry(String.valueOf(seat.position()), seat.weight());
			}
			Map<?, ?> evidence = (Map<String, Object>) verdict.aggregated().metadata().get(Judgment.AGGREGATION_KEY);
			assertThat(evidence.get(AggregationEvidence.INPUT_COUNT)).isEqualTo(seats.size());
		}

		@Test
		void aDeduplicatedSeatStillShowsTheWrappedJudgesRealType() {
			Judge first = Judges.named(ctx -> Judgment.pass("first"), "check");
			Judge second = Judges.named(new KeywordJudge("x"), "check", "judged by a model", JudgeType.LLM_POWERED);
			Jury jury = Juries.fromJudges(new MajorityVotingStrategy(), first, second);

			List<SeatDescription> seats = ((SimpleJuryDescription) jury.describe()).seats();

			assertThat(seats).extracting(SeatDescription::verdictKey, SeatDescription::keySource)
				.containsExactly(tuple("check", KeySource.DECLARED), tuple("check-2", KeySource.DEDUPLICATED));
			JudgeDescription renamed = seats.get(1).judge();
			assertThat(renamed.name()).isEqualTo("check-2");
			assertThat(renamed.type()).as("the deduplicating wrapper's label").isEqualTo(JudgeType.DETERMINISTIC);
			assertThat(renamed.delegateName()).isEqualTo("check");
			assertThat(renamed.delegateType()).as("what the judge says it is").isEqualTo(JudgeType.LLM_POWERED);
			assertThat(renamed.implementation()).isEqualTo(named(KeywordJudge.class));
			assertThat(jury.vote(simpleContext("goal")).individualByName()).containsOnlyKeys("check", "check-2");
		}

		@Test
		void fromJudgesLeavesUnnamedJudgesPositional() {
			Jury jury = Juries.fromJudges(new ConsensusStrategy(), ctx -> Judgment.pass("a"), ctx -> Judgment.pass("b"));

			assertThat(((SimpleJuryDescription) jury.describe()).seats())
				.extracting(SeatDescription::verdictKey, SeatDescription::keySource)
				.containsExactly(tuple("Judge#1", KeySource.POSITIONAL), tuple("Judge#2", KeySource.POSITIONAL));
		}

		@Test
		void libraryCombinatorsAreHidden() {
			Judge a = new KeywordJudge("a");
			Judge b = new KeywordJudge("b");

			assertThat(List.of(Judges.and(a, b), Judges.or(a, b), Judges.allOf(a, b), Judges.anyOf(a, b),
					Judges.alwaysPass("p"), Judges.alwaysFail("f")))
				.allSatisfy(judge -> assertThat(Judges.describe(judge).implementation().form()).isEqualTo(Form.HIDDEN));
		}

		@Test
		void aWeightWithNoJsonRepresentationIsRefusedBeforeAJuryCanBeDescribed() {
			// Until 0.17.0 the builder accepted NaN and infinite weights, and only describe()
			// refused them. The seat description still refuses them; see
			// aSeatDescriptionRefusesImpossibleSeats.
			assertThatThrownBy(() -> SimpleJury.builder().judge(ctx -> Judgment.pass("a"), Double.NaN))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Weight must be finite");
		}

		@Test
		void aSeatWhoseMetadataIsNullCannotBeDescribed() {
			SimpleJury jury = SimpleJury.builder()
				.judge(Judges.named(new KeywordJudge("done"), "keyword"))
				.judge(JudgeTestFixtures.nullMetadata(Judgment.pass("never kept")))
				.votingStrategy(new MajorityVotingStrategy())
				.build();

			assertThatThrownBy(jury::describe).isInstanceOf(IllegalArgumentException.class)
				.hasMessageStartingWith("seats[1] ('Judge#2'): ")
				.hasMessageContaining("metadata() returned null");
		}

		@Test
		void aSeatWhoseMetadataThrowsCannotBeDescribed() {
			IllegalStateException failure = new IllegalStateException("registry offline");
			SimpleJury jury = SimpleJury.builder()
				.judge(JudgeTestFixtures.throwingMetadata(failure, Judgment.pass("never kept")))
				.votingStrategy(new MajorityVotingStrategy())
				.build();

			assertThatThrownBy(jury::describe).isInstanceOf(IllegalArgumentException.class)
				.hasMessageStartingWith("seats[0] ('Judge#1'): ")
				.hasMessageContaining("metadata() threw " + IllegalStateException.class.getName() + ": registry offline")
				.hasRootCause(failure);
		}

		@Test
		void aNamedJudgeWrappingAJudgeWithNullMetadataCannotBeDescribed() {
			// The wrapper's own name is readable, so the seat votes; the wrapped judge's metadata
			// is not, and describing it as undeclared would misstate what the judge declares.
			SimpleJury jury = SimpleJury.builder()
				.judge(Judges.named(JudgeTestFixtures.nullMetadata(Judgment.pass("kept")), "outer"))
				.votingStrategy(new MajorityVotingStrategy())
				.build();

			assertThatThrownBy(jury::describe).isInstanceOf(IllegalArgumentException.class)
				.hasMessageStartingWith("seats[0] ('outer'): ")
				.hasMessageContaining("metadata() returned null");
		}

	}

	@Nested
	@DisplayName("CascadedJury tiers")
	class Cascades {

		private CascadedJury threeTierCascade() {
			Jury gate = SimpleJury.builder()
				.judge(Judges.named(new KeywordJudge("BUILD SUCCESS"), "build"))
				.votingStrategy(new AllMustPassStrategy(ErrorPolicy.TREAT_AS_FAIL))
				.parallel(false)
				.build();
			Jury style = SimpleJury.builder()
				.judge(Judges.named(new KeywordJudge("style"), "style"))
				.votingStrategy(new ConsensusStrategy(ErrorPolicy.TREAT_AS_ABSTAIN))
				.parallel(false)
				.build();
			Jury docs = SimpleJury.builder()
				.judge(Judges.named(new KeywordJudge("docs"), "docs"))
				.votingStrategy(new MedianVotingStrategy(0.4))
				.parallel(false)
				.build();
			Jury review = Juries.meta(new AverageVotingStrategy(0.7), new NamedJury("style", style),
					new NamedJury("docs", docs));
			Jury last = SimpleJury.builder()
				.judge(Judges.named(new KeywordJudge("done"), "done"))
				.votingStrategy(new MajorityVotingStrategy(TiePolicy.ABSTAIN, ErrorPolicy.IGNORE))
				.parallel(false)
				.build();
			return CascadedJury.builder()
				.tier("gate", gate, TierPolicy.REJECT_ON_ANY_FAIL)
				.tier("review", review, TierPolicy.ACCEPT_ON_ALL_PASS)
				.tier("final", last, TierPolicy.FINAL_TIER)
				.build();
		}

		@Test
		void everyTierIsDescribedIncludingAMetaJuryTier() {
			CascadedJuryDescription cascade = (CascadedJuryDescription) threeTierCascade().describe();

			assertThat(cascade.tiers()).extracting(TierDescription::name, TierDescription::policy)
				.containsExactly(tuple("gate", TierPolicy.REJECT_ON_ANY_FAIL),
						tuple("review", TierPolicy.ACCEPT_ON_ALL_PASS), tuple("final", TierPolicy.FINAL_TIER));

			SimpleJuryDescription gate = (SimpleJuryDescription) cascade.tiers().get(0).jury();
			assertThat(gate.strategy().errorPolicy()).isEqualTo(ErrorPolicy.TREAT_AS_FAIL);
			assertThat(gate.strategy().threshold()).isNull();

			MetaJuryDescription review = (MetaJuryDescription) cascade.tiers().get(1).jury();
			assertThat(review.strategy().name()).isEqualTo("average");
			assertThat(review.strategy().threshold()).isEqualTo(0.7);
			assertThat(review.members()).extracting(MemberDescription::name).containsExactly("style", "docs");
			SimpleJuryDescription docs = (SimpleJuryDescription) review.members().get(1).jury();
			assertThat(docs.strategy().threshold()).isEqualTo(0.4);
			assertThat(docs.seats()).extracting(SeatDescription::verdictKey).containsExactly("docs");

			SimpleJuryDescription last = (SimpleJuryDescription) cascade.tiers().get(2).jury();
			assertThat(last.strategy().errorPolicy()).isEqualTo(ErrorPolicy.IGNORE);
			assertThat(last.strategy().parameters()).containsExactly(Map.entry("tiePolicy", "ABSTAIN"));
		}

		@Test
		void thePortableFormNamesEachTiersKind() {
			Map<String, Object> portable = threeTierCascade().describe().toPortable();

			assertThat(portable.get("kind")).isEqualTo("CASCADED");
			assertThat((List<Object>) portable.get("tiers"))
				.extracting(tier -> ((Map<String, Object>) ((Map<String, Object>) tier).get("jury")).get("kind"))
				.containsExactly("SIMPLE", "META", "SIMPLE");
		}

		@Test
		void onlyTheRootCarriesTheDescriptionVersion() {
			Map<String, Object> portable = threeTierCascade().describe().toPortable();

			assertThat(JuryDescription.DESCRIPTION_VERSION).isEqualTo(2);
			assertThat(portable.keySet()).first().isEqualTo("descriptionVersion");
			assertThat(portable.get("descriptionVersion")).isEqualTo(2);
			assertThat((List<Object>) portable.get("tiers"))
				.allSatisfy(tier -> assertThat((Map<String, Object>) ((Map<String, Object>) tier).get("jury"))
					.doesNotContainKey("descriptionVersion"));
			assertThat(Judges.describe(new KeywordJudge("k")).toPortable()).containsEntry("descriptionVersion", 2);
			assertThat(new ConsensusStrategy().describe().toPortable()).containsEntry("descriptionVersion", 2);
			assertThat(ImplementationIdentity.of(KeywordJudge.class).toPortable())
				.as("an identity is a fragment, not a description root")
				.doesNotContainKey("descriptionVersion");
		}

		@Test
		void anEarlyStopLeavesDescribedTiersWithoutAttempts() {
			CascadedJury cascade = threeTierCascade();
			CascadedJuryDescription description = (CascadedJuryDescription) cascade.describe();

			Verdict verdict = cascade.vote(simpleContext("no build output"));

			assertThat(verdict.compositeAttempts()).extracting(CompositeAttempt::name).containsExactly("gate");
			assertThat(description.tiers()).extracting(TierDescription::name).containsExactly("gate", "review", "final");
		}

	}

	@Nested
	@DisplayName("Juries.meta members")
	class MetaJuries {

		@Test
		void membersAndStrategyAreDescribedThoughGetJudgesIsEmpty() {
			Jury first = Juries.fromJudges(new ConsensusStrategy(), Judges.named(new KeywordJudge("a"), "a"));
			Jury second = SimpleJury.builder()
				.judge(new KeywordJudge("b"), 3.0)
				.votingStrategy(new WeightedAverageStrategy())
				.build();
			Jury meta = Juries.meta(new MajorityVotingStrategy(), new NamedJury("first", first),
					new NamedJury("second", second));

			assertThat(meta.getJudges()).as("a meta-jury's public roster").isEmpty();

			MetaJuryDescription description = (MetaJuryDescription) meta.describe();
			assertThat(description.strategy()).isEqualTo(new StrategyDescription("majority",
					named(MajorityVotingStrategy.class), ErrorPolicy.PROPAGATE, NotApplicablePolicy.REFUSE, null,
					Map.of("tiePolicy", "FAIL")));
			assertThat(description.members()).extracting(MemberDescription::name).containsExactly("first", "second");
			SimpleJuryDescription secondJury = (SimpleJuryDescription) description.members().get(1).jury();
			assertThat(secondJury.seats()).singleElement().satisfies(seat -> {
				assertThat(seat.weight()).isEqualTo(3.0);
				assertThat(seat.keySource()).isEqualTo(KeySource.POSITIONAL);
				assertThat(seat.judge().implementation()).isEqualTo(named(KeywordJudge.class));
			});

			Map<String, Object> portable = description.toPortable();
			assertThat(portable.get("kind")).isEqualTo("META");
			assertThat((List<Object>) portable.get("members")).extracting(member -> ((Map<String, Object>) member).get("name"))
				.containsExactly("first", "second");
		}

	}

	@Nested
	@DisplayName("Judge configuration")
	class JudgeConfiguration {

		@Test
		void notOptingInIsUndeclaredAndAnEmptyMapIsADeclaration() {
			JudgeDescription undeclared = Judges.describe(new KeywordJudge("x"));
			JudgeDescription declaredEmpty = Judges.describe(new DeclaringJudge(Map.of()));

			assertThat(undeclared.configuration()).isNull();
			assertThat(declaredEmpty.configuration()).isNotNull().isEmpty();
			assertThat(undeclared.toPortable().get("configuration")).isEqualTo(Map.of("declared", false));
			assertThat(declaredEmpty.toPortable().get("configuration"))
				.isEqualTo(Map.of("declared", true, "values", Map.of()));
		}

		@Test
		void undeclaredAndDeclaredEmptyHaveUnequalPortableForms() throws Exception {
			ImplementationIdentity same = named(KeywordJudge.class);
			JudgeDescription undeclared = new JudgeDescription("j", JudgeType.DETERMINISTIC, null, null, same, null);
			JudgeDescription declaredEmpty = new JudgeDescription("j", JudgeType.DETERMINISTIC, null, null, same,
					Map.of());

			assertThat(undeclared.toPortable()).isNotEqualTo(declaredEmpty.toPortable());
			assertThat(JSON.writeValueAsString(undeclared.toPortable()))
				.isNotEqualTo(JSON.writeValueAsString(declaredEmpty.toPortable()));
		}

		@Test
		void declaredKeysAreOrderedWhateverMapTheJudgeReturns() throws Exception {
			Map<String, Object> insertionOrder = new LinkedHashMap<>();
			insertionOrder.put("zeta", 1);
			insertionOrder.put("alpha", Map.of("y", true, "b", "text"));
			insertionOrder.put("mid", List.of(Map.of("k2", 2, "k1", 1)));
			Map<String, Object> otherOrder = Map.of("mid", List.of(Map.of("k1", 1, "k2", 2)), "zeta", 1, "alpha",
					Map.of("b", "text", "y", true));

			JudgeDescription first = Judges.describe(new DeclaringJudge(insertionOrder));
			JudgeDescription second = Judges.describe(new DeclaringJudge(otherOrder));

			assertThat(first.configuration()).containsOnlyKeys("alpha", "mid", "zeta");
			assertThat(first.configuration().keySet()).containsExactly("alpha", "mid", "zeta");
			String json = JSON.writeValueAsString(first.toPortable());
			assertThat(json).isEqualTo(JSON.writeValueAsString(second.toPortable()))
				.contains("{\"b\":\"text\",\"y\":true}")
				.contains("{\"k1\":1,\"k2\":2}");
		}

		@Test
		void aWrapperDescribesTheDelegatesConfigurationAndBothLayersOfMetadata() {
			ConfiguredJudge rubric = new DeclaringJudge(Map.of("rubric", "v3"));
			NamedJudge inner = Judges.named(rubric, "rubric", null, JudgeType.LLM_POWERED);
			NamedJudge outer = Judges.named(inner, "rubric-2");

			assertThat(outer.delegate()).isSameAs(inner);
			JudgeDescription description = Judges.describe(outer);
			assertThat(description.name()).isEqualTo("rubric-2");
			assertThat(description.type()).isEqualTo(JudgeType.DETERMINISTIC);
			assertThat(description.delegateName()).isEqualTo("rubric");
			assertThat(description.delegateType()).isEqualTo(JudgeType.LLM_POWERED);
			assertThat(description.implementation()).isEqualTo(named(DeclaringJudge.class));
			assertThat(description.configuration()).containsExactly(Map.entry("rubric", "v3"));
		}

		@Test
		void aConfiguredJudgeReturningNullIsRefused() {
			assertThatThrownBy(() -> Judges.describe(new DeclaringJudge(null))).isInstanceOf(NullPointerException.class)
				.hasMessageContaining("return an empty map");
		}

		@Test
		void metadataWithOnlyATypeDeclaresOnlyThatValue() {
			JudgeDescription description = new JudgeDescription(null, JudgeType.AGENT, null, null,
					named(KeywordJudge.class), null);

			assertThat(description.toPortable().get("metadata"))
				.isEqualTo(Map.of("declared", true, "values", Map.of("type", "AGENT")));
		}

	}

	@Nested
	@DisplayName("Consumer juries and strategies")
	class ConsumerJuries {

		@Test
		void aJuryThatDoesNotOverrideDescribeIsOpaqueButTruthful() {
			Jury custom = new FirstVoteJury(List.of(Judges.named(new KeywordJudge("a"), "a"), ctx -> Judgment.pass("b")),
					new ConsensusStrategy(ErrorPolicy.IGNORE));

			JuryDescription description = custom.describe();

			assertThat(description).isInstanceOf(OpaqueJuryDescription.class);
			OpaqueJuryDescription opaque = (OpaqueJuryDescription) description;
			assertThat(opaque.implementation()).isEqualTo(named(FirstVoteJury.class));
			assertThat(opaque.strategy()).isNotNull();
			assertThat(opaque.strategy().errorPolicy()).isEqualTo(ErrorPolicy.IGNORE);
			assertThat(opaque.judges()).extracting(judge -> judge.implementation().form())
				.containsExactly(Form.NAMED, Form.HIDDEN);
			assertThat(opaque.judges().get(0).name()).isEqualTo("a");

			Map<String, Object> portable = opaque.toPortable();
			assertThat(portable.get("kind")).isEqualTo("OPAQUE");
			Map<?, ?> strategy = (Map<String, Object>) portable.get("strategy");
			assertThat(strategy.get("declared")).isEqualTo(true);
			assertThat(((Map<String, Object>) strategy.get("value")).get("name")).isEqualTo("consensus");
		}

		@Test
		void anOpaqueJuryWithNoStrategySaysSo() {
			Jury custom = new FirstVoteJury(List.of(new KeywordJudge("a")), null);

			assertThat(custom.describe().toPortable().get("strategy")).isEqualTo(Map.of("declared", false));
		}

		@Test
		void aStrategyThatDoesNotOverrideDescribeIsUndeclared() {
			VotingStrategy custom = new VotingStrategy() {
				@Override
				public Judgment aggregate(List<Judgment> judgments, Map<String, Double> weights) {
					return judgments.get(0);
				}

				@Override
				public String getName() {
					return "first";
				}
			};

			StrategyDescription description = custom.describe();

			assertThat(description).isEqualTo(new StrategyDescription("first",
					new ImplementationIdentity(Form.ANONYMOUS, null, JuryDescriptionTest.class.getName()), null, null,
					null, null));
			assertThat(description.toPortable().get("parameters")).isEqualTo(Map.of("declared", false));
		}

		@Test
		void aConsumerJuryCanDescribeItselfStructurally() {
			Jury inner = Juries.fromJudges(new ConsensusStrategy(), new KeywordJudge("a"));
			Jury wrapper = new Jury() {
				@Override
				public List<Judge> getJudges() {
					return inner.getJudges();
				}

				@Override
				public VotingStrategy getVotingStrategy() {
					return inner.getVotingStrategy();
				}

				@Override
				public Verdict vote(JudgmentContext context) {
					return inner.vote(context);
				}

				@Override
				public JuryDescription describe() {
					return new CascadedJuryDescription(
							List.of(new TierDescription("only", TierPolicy.FINAL_TIER, inner.describe())));
				}
			};

			assertThat(wrapper.describe()).isInstanceOf(CascadedJuryDescription.class);
			assertThat(wrapper.describe().toPortable().get("kind")).isEqualTo("CASCADED");
		}

	}

	@Nested
	@DisplayName("Built-in strategies")
	class BuiltInStrategies {

		private StrategyDescription declared(String name, Class<?> type, ErrorPolicy errorPolicy, Double threshold,
				Map<String, Object> parameters) {
			return new StrategyDescription(name, named(type), errorPolicy, NotApplicablePolicy.REFUSE, threshold,
					parameters);
		}

		@Test
		void everyBuiltInDeclaresItsErrorPolicyThresholdAndTiePolicy() {
			assertThat(new AllMustPassStrategy(ErrorPolicy.TREAT_AS_ABSTAIN).describe()).isEqualTo(
					declared("allMustPass", AllMustPassStrategy.class, ErrorPolicy.TREAT_AS_ABSTAIN, null, Map.of()));
			assertThat(new AverageVotingStrategy(0.25, ErrorPolicy.IGNORE).describe())
				.isEqualTo(declared("average", AverageVotingStrategy.class, ErrorPolicy.IGNORE, 0.25, Map.of()));
			assertThat(new MedianVotingStrategy(0.9, ErrorPolicy.TREAT_AS_FAIL).describe())
				.isEqualTo(declared("median", MedianVotingStrategy.class, ErrorPolicy.TREAT_AS_FAIL, 0.9, Map.of()));
			assertThat(new WeightedAverageStrategy(0.3).describe()).isEqualTo(
					declared("weightedAverage", WeightedAverageStrategy.class, ErrorPolicy.PROPAGATE, 0.3, Map.of()));
			assertThat(new ConjunctiveStrategy(0.8, ErrorPolicy.IGNORE).describe())
				.isEqualTo(declared("conjunctive", ConjunctiveStrategy.class, ErrorPolicy.IGNORE, 0.8, Map.of()));
			assertThat(new ConsensusStrategy(ErrorPolicy.TREAT_AS_FAIL).describe())
				.isEqualTo(declared("consensus", ConsensusStrategy.class, ErrorPolicy.TREAT_AS_FAIL, null, Map.of()));
			assertThat(new MajorityVotingStrategy(TiePolicy.PASS, ErrorPolicy.TREAT_AS_ABSTAIN).describe())
				.isEqualTo(declared("majority", MajorityVotingStrategy.class, ErrorPolicy.TREAT_AS_ABSTAIN, null,
						Map.of("tiePolicy", "PASS")));
		}

		@Test
		void portableValuesMergeErrorPolicyThresholdAndParametersInKeyOrder() throws Exception {
			assertThat(JSON.writeValueAsString(
					new MajorityVotingStrategy(TiePolicy.ABSTAIN, ErrorPolicy.IGNORE).describe().toPortable()))
				.isEqualTo("{\"descriptionVersion\":2,\"name\":\"majority\",\"implementation\":{\"form\":\"NAMED\",\"className\":"
						+ "\"io.github.markpollack.judge.jury.MajorityVotingStrategy\"},\"parameters\":{\"declared\":true,"
						+ "\"values\":{\"errorPolicy\":\"ignore\",\"notApplicablePolicy\":\"refuse\","
						+ "\"tiePolicy\":\"ABSTAIN\"}}}");
			assertThat(JSON.writeValueAsString(new AverageVotingStrategy().describe().toPortable()))
				.endsWith("\"parameters\":{\"declared\":true,\"values\":{\"errorPolicy\":\"propagate\","
						+ "\"notApplicablePolicy\":\"refuse\",\"threshold\":0.5}}}");
		}

		@Test
		void aSubclassInheritsTheDeclarationButNamesItself() {
			class StricterAverage extends AverageVotingStrategy {

				StricterAverage() {
					super(0.9);
				}

			}

			StrategyDescription description = new StricterAverage().describe();

			assertThat(description.threshold()).isEqualTo(0.9);
			assertThat(description.implementation())
				.isEqualTo(new ImplementationIdentity(Form.LOCAL, null, JuryDescriptionTest.class.getName()));
		}

	}

	@Nested
	@DisplayName("Implementation identity")
	class Identity {

		@Test
		void anAnonymousClassRecordsOnlyItsEnclosingTopLevelClass() {
			Judge anonymous = new Judge() {
				@Override
				public Judgment judge(JudgmentContext context) {
					return Judgment.pass("anonymous");
				}
			};

			assertThat(ImplementationIdentity.of(anonymous.getClass()))
				.isEqualTo(new ImplementationIdentity(Form.ANONYMOUS, null, JuryDescriptionTest.class.getName()));
			assertThat(ImplementationIdentity.of(anonymous.getClass()).toPortable())
				.containsExactly(Map.entry("form", "ANONYMOUS"),
						Map.entry("enclosingClassName", JuryDescriptionTest.class.getName()));
		}

		@Test
		void aLocalClassAndAClassNestedInItRecordOnlyTheEnclosingTopLevelClass() {
			class LocalJudge implements Judge {

				class Nested {

				}

				@Override
				public Judgment judge(JudgmentContext context) {
					return Judgment.pass("local");
				}

			}

			ImplementationIdentity expected = new ImplementationIdentity(Form.LOCAL, null,
					JuryDescriptionTest.class.getName());
			assertThat(ImplementationIdentity.of(LocalJudge.class)).isEqualTo(expected);
			assertThat(ImplementationIdentity.of(LocalJudge.Nested.class)).isEqualTo(expected);
		}

		@Test
		void aMemberClassRecordsItsBinaryName() {
			assertThat(ImplementationIdentity.of(FirstVoteJury.class).toPortable()).containsExactly(
					Map.entry("form", "NAMED"),
					Map.entry("className", "io.github.markpollack.judge.consumer.JuryDescriptionTest$FirstVoteJury"));
		}

		@Test
		void aLambdaIsHiddenWithNoName() {
			Judge lambda = ctx -> Judgment.pass("lambda");

			assertThat(lambda.getClass().getName()).as("the name that must not leak").contains("$$Lambda");
			assertThat(ImplementationIdentity.of(lambda.getClass()).toPortable()).isEqualTo(Map.of("form", "HIDDEN"));
		}

		@Test
		void theConstructorRefusesNamesTheFormForbids() {
			assertThatThrownBy(() -> new ImplementationIdentity(Form.NAMED, null, null))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> new ImplementationIdentity(Form.NAMED, " ", null))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> new ImplementationIdentity(Form.NAMED, "a.B", "a.C"))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> new ImplementationIdentity(Form.ANONYMOUS, "a.B$1", "a.B"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("positional ordinal");
			assertThatThrownBy(() -> new ImplementationIdentity(Form.LOCAL, null, null))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> new ImplementationIdentity(Form.LOCAL, null, " "))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> new ImplementationIdentity(Form.HIDDEN, "a.B$$Lambda", null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("no stable name");
			assertThatThrownBy(() -> new ImplementationIdentity(Form.HIDDEN, null, "a.B"))
				.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		void wireNamesAreStable() {
			assertThat(Form.values()).extracting(Form::wireName)
				.containsExactly("NAMED", "ANONYMOUS", "LOCAL", "HIDDEN");
			assertThat(KeySource.values()).extracting(KeySource::wireName)
				.containsExactly("DECLARED", "DEDUPLICATED", "POSITIONAL");
		}

	}

	@Nested
	@DisplayName("Description invariants")
	class Invariants {

		@Test
		void aStrategyDescriptionRefusesInconsistentDeclarations() {
			ImplementationIdentity identity = named(AverageVotingStrategy.class);

			assertThatThrownBy(() -> new StrategyDescription("s", identity, ErrorPolicy.PROPAGATE, null, null, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("empty parameter map");
			assertThatThrownBy(() -> new StrategyDescription("s", identity, null, NotApplicablePolicy.EXCLUDE, null, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("empty parameter map");
			assertThatThrownBy(() -> new StrategyDescription("s", identity, null, null, 0.5, null))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> new StrategyDescription("s", identity, null, null, Double.NaN, Map.of()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("finite");
			assertThatThrownBy(() -> new StrategyDescription("s", identity, null, null, null, Map.of("threshold", 0.5)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("their own components");
			assertThatThrownBy(
					() -> new StrategyDescription("s", identity, null, null, null, Map.of("errorPolicy", "ignore")))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> new StrategyDescription("s", identity, null, null, null,
					Map.of("notApplicablePolicy", "exclude"))).isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		void aSeatDescriptionRefusesImpossibleSeats() {
			JudgeDescription judge = Judges.describe(new KeywordJudge("k"));

			assertThatThrownBy(() -> new SeatDescription(-1, "k", KeySource.DECLARED, 1.0, judge))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> new SeatDescription(0, "k", KeySource.DECLARED, -0.5, judge))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> new SeatDescription(0, "k", KeySource.DECLARED, Double.POSITIVE_INFINITY, judge))
				.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		void seatPositionsMustMatchSeatOrder() {
			StrategyDescription strategy = new ConsensusStrategy().describe();
			SeatDescription misplaced = new SeatDescription(1, "k", KeySource.DECLARED, 1.0,
					Judges.describe(new KeywordJudge("k")));

			assertThatThrownBy(() -> new SimpleJuryDescription(strategy, List.of(misplaced)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("positions must match seat order");
		}

		@Test
		void descriptionsCopyTheListsTheyAreGiven() {
			List<SeatDescription> seats = new ArrayList<>(List.of(new SeatDescription(0, "k", KeySource.DECLARED, 1.0,
					Judges.describe(new KeywordJudge("k")))));
			SimpleJuryDescription description = new SimpleJuryDescription(new ConsensusStrategy().describe(), seats);

			seats.clear();

			assertThat(description.seats()).hasSize(1);
			assertThatThrownBy(() -> description.seats().clear()).isInstanceOf(UnsupportedOperationException.class);
		}

	}

	/** A consumer jury that votes with its first judge only and does not override describe(). */
	static final class FirstVoteJury implements Jury {

		private final List<Judge> judges;

		private final VotingStrategy strategy;

		FirstVoteJury(List<Judge> judges, VotingStrategy strategy) {
			this.judges = judges;
			this.strategy = strategy;
		}

		@Override
		public List<Judge> getJudges() {
			return this.judges;
		}

		@Override
		public VotingStrategy getVotingStrategy() {
			return this.strategy;
		}

		@Override
		public Verdict vote(JudgmentContext context) {
			Judgment first = this.judges.get(0).judge(context);
			return Verdict.builder().aggregated(first).individual(List.of(first)).build();
		}

	}

}
