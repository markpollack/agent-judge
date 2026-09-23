package io.github.markpollack.judge.jury.interpretation;

import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.result.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class V2InterpretationTest {

	private final ObjectMapper mapper = new ObjectMapper();

	@Test
	void liveRootsAreExplicitVersionTwo() {
		Map<String, Object> wire = mapper.convertValue(Verdict.single("seat", Judgment.pass("ok")),
				new TypeReference<>() {
				});
		assertThat(wire).containsEntry("schemaVersion", 2);
		assertThat(((Map<?, ?>) wire.get("aggregated")).get("schemaVersion")).isEqualTo(2);
	}

	@Test
	void richIdentityIsSupportedWithoutLoss() {
		Judgment judgment = new Judgment(JudgmentStatus.FAIL, new Assessment(new Proposition(false), null, null), null,
				null, null, "negative", java.util.List.of(), null, null, Map.of());
		Interpretation reading = Verdicts.interpret(Verdict.single("seat", judgment));
		assertThat(reading.readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);
		assertThat(reading.schemaVersion()).isEqualTo(2);
		assertThat(reading.sourceVersion()).isEqualTo(2);
		assertThat(reading.reading()).isEqualTo(VerdictReading.REJECTED);
	}

	@Test
	void forgedReductionCannotContradictAllRetainedInputs() {
		var jury = io.github.markpollack.judge.jury.SimpleJury.builder()
			.judge(c -> Judgment.pass("a"))
			.judge(c -> Judgment.pass("b"))
			.votingStrategy(new io.github.markpollack.judge.jury.ConsensusStrategy())
			.build();
		var v = jury.vote(io.github.markpollack.judge.context.JudgmentContext.builder().goal("test").build());
		Map<String, Object> wire = mapper.convertValue(v, new TypeReference<>() {
		});
		var fail = mapper.convertValue(Judgment.fail("contradiction"), new TypeReference<Map<String, Object>>() {
		});
		wire.put("individual", java.util.List.of(fail, fail));
		Map<String, Object> names = new java.util.LinkedHashMap<>();
		v.individualByName().keySet().forEach(key -> names.put(key, fail));
		wire.put("individualByName", names);
		assertThat(Verdicts.interpret(wire).readingSupport()).isEqualTo(ReadingSupport.UNDETERMINED);
	}

}
