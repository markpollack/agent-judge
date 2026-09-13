/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.consumer;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.markpollack.judge.JudgeType;
import io.github.markpollack.judge.Judges;
import io.github.markpollack.judge.description.ImplementationIdentity;
import io.github.markpollack.judge.description.JudgeDescription;
import io.github.markpollack.judge.description.JuryDescription;
import io.github.markpollack.judge.description.StrategyDescription;
import io.github.markpollack.judge.jury.AllMustPassStrategy;
import io.github.markpollack.judge.jury.CascadedJury;
import io.github.markpollack.judge.jury.Juries;
import io.github.markpollack.judge.jury.Jury;
import io.github.markpollack.judge.jury.MajorityVotingStrategy;
import io.github.markpollack.judge.jury.NamedJury;
import io.github.markpollack.judge.jury.SimpleJury;
import io.github.markpollack.judge.jury.TierPolicy;
import io.github.markpollack.judge.jury.WeightedAverageStrategy;
import io.github.markpollack.judge.result.Judgment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R4: a description is made of portable values, validated by the same algebra as judgment
 * metadata, and refused with a path-aware diagnostic when it is not.
 */
@DisplayName("A description is portable, or it is refused with the path that is not")
class DescriptionPortabilityTest {

	private static final ObjectMapper JSON = new ObjectMapper();

	private static final String DECLARING_JUDGE = "{form=NAMED, className=" + DeclaringJudge.class.getName() + "}";

	@Test
	void aLiveObjectIsRefusedWithItsPathAndSeat() {
		Jury jury = SimpleJury.builder()
			.judge(Judges.named(new DeclaringJudge(Map.of("rubric", Map.of("levels", List.of("pass", new Object())))),
					"rubric-judge"))
			.votingStrategy(new MajorityVotingStrategy())
			.build();

		assertThatThrownBy(jury::describe).isInstanceOf(IllegalArgumentException.class)
			.hasMessageStartingWith("seats[0] ('rubric-judge'): Judge 'rubric-judge' declared a configuration that is "
					+ "not portable: configuration.rubric.levels[1]: java.lang.Object is not a portable")
			.hasMessageNotContaining("metadata.");
	}

	@Test
	void notANumberIsRefusedWithItsPath() {
		Map<String, Object> configuration = new LinkedHashMap<>();
		configuration.put("passMark", Double.NaN);

		assertThatThrownBy(() -> Judges.describe(new DeclaringJudge(configuration)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Judge implemented by " + DECLARING_JUDGE + " declared a configuration that is not portable: "
					+ "configuration.passMark: NaN has no JSON representation. Numbers must be finite.");
	}

	@Test
	void aNullElementIsRefusedWithItsPath() {
		assertThatThrownBy(
				() -> Judges.describe(new DeclaringJudge(Map.of("criteria", Arrays.asList("correct", null)))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("configuration.criteria[1]: null is not a portable metadata element");
	}

	@Test
	void aNullValueIsRefusedWithItsPath() {
		Map<String, Object> configuration = new HashMap<>();
		configuration.put("model", null);

		assertThatThrownBy(() -> Judges.describe(new DeclaringJudge(configuration)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("configuration.model: null is not a portable metadata value");
	}

	@Test
	void strategyParametersAreHeldToTheSameRules() {
		assertThatThrownBy(() -> new StrategyDescription("s", ImplementationIdentity.of(MajorityVotingStrategy.class),
				null, null, Map.of("clock", Duration.ZERO)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageStartingWith("parameters.clock: java.time.Duration is not a portable");
	}

	@Test
	void theFailingTierMemberAndJudgeAreAllNamed() {
		Jury bad = SimpleJury.builder()
			.judge(new DeclaringJudge(Map.of("x", new Object())))
			.votingStrategy(new MajorityVotingStrategy())
			.build();
		Jury cascade = CascadedJury.builder()
			.tier("only", Juries.meta(new MajorityVotingStrategy(), new NamedJury("member", bad)), TierPolicy.FINAL_TIER)
			.build();

		assertThatThrownBy(cascade::describe).isInstanceOf(IllegalArgumentException.class)
			.hasMessageStartingWith("tier 'only': member 'member': seats[0] ('Judge#1'): Judge implemented by "
					+ DECLARING_JUDGE + " declared a configuration that is not portable: configuration.x: "
					+ "java.lang.Object is not a portable");
	}

	@Test
	void anOpaqueJuryNamesTheFailingJudgeByPosition() {
		Jury custom = new JuryDescriptionTest.FirstVoteJury(List.of(new DeclaringJudge(Map.of("x", new Object()))),
				null);

		assertThatThrownBy(custom::describe).isInstanceOf(IllegalArgumentException.class)
			.hasMessageStartingWith("judges[0]: Judge implemented by " + DECLARING_JUDGE);
	}

	@Test
	void anAcceptedDescriptionRoundTripsThroughOrdinaryJson() throws Exception {
		Map<String, Object> rubric = new LinkedHashMap<>();
		rubric.put("version", 3);
		rubric.put("passMark", 0.75);
		rubric.put("strict", true);
		rubric.put("criteria", List.of("correct", "complete"));
		rubric.put("levels", Map.of("high", 1.0, "low", 0.0));
		Jury review = SimpleJury.builder()
			.judge(Judges.named(new DeclaringJudge(rubric), "rubric", null, JudgeType.LLM_POWERED), 2.0)
			.judge(ctx -> Judgment.pass("lambda"))
			.votingStrategy(new WeightedAverageStrategy(0.6))
			.build();
		Jury cascade = CascadedJury.builder()
			.tier("gate", Juries.fromJudges(new AllMustPassStrategy(), new KeywordJudge("ok")),
					TierPolicy.REJECT_ON_ANY_FAIL)
			.tier("review", Juries.meta(new MajorityVotingStrategy(), new NamedJury("review", review)),
					TierPolicy.FINAL_TIER)
			.build();

		Map<String, Object> portable = cascade.describe().toPortable();
		String json = JSON.writeValueAsString(portable);
		Map<String, Object> parsed = JSON.readValue(json, new TypeReference<Map<String, Object>>() {
		});

		assertThat(parsed).isEqualTo(portable);
		assertThat(JSON.writeValueAsString(parsed)).isEqualTo(json);
	}

	@Test
	@SuppressWarnings("unchecked")
	void thePortableFormIsImmutableAtEveryDepth() {
		JuryDescription description = SimpleJury.builder()
			.judge(new DeclaringJudge(Map.of("nested", Map.of("k", 1))))
			.votingStrategy(new MajorityVotingStrategy())
			.build()
			.describe();

		Map<String, Object> portable = description.toPortable();
		List<Object> seats = (List<Object>) portable.get("seats");
		Map<String, Object> judge = (Map<String, Object>) ((Map<String, Object>) seats.get(0)).get("judge");
		Map<String, Object> values = (Map<String, Object>) ((Map<String, Object>) judge.get("configuration"))
			.get("values");

		assertThatThrownBy(() -> portable.put("kind", "OTHER")).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> seats.add("seat")).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> ((Map<String, Object>) values.get("nested")).put("k", 2))
			.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void aDescriptionIsDetachedFromTheMapTheJudgeReturned() {
		Map<String, Object> configuration = new LinkedHashMap<>();
		configuration.put("version", 1);
		JudgeDescription description = Judges.describe(new DeclaringJudge(configuration));

		configuration.put("version", 2);

		assertThat(description.configuration()).containsEntry("version", 1);
	}

}
