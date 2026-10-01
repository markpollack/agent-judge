/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.serialization.diagnostics;

import io.github.markpollack.judge.completion.CompletionEvidence;

import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.judgment.BooleanFinding;
import io.github.markpollack.judge.judgment.Finding;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentStatus;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class V3InterpretationTest {

	private final ObjectMapper mapper = new ObjectMapper();

	@Test
	void liveRootsAreExplicitVersionFour() {
		Map<String, Object> wire = mapper.convertValue(Verdict.single("seat", Judgment.pass("ok")),
				new TypeReference<>() {
				});
		assertThat(wire).containsEntry("schemaVersion", 5);
		assertThat(((Map<?, ?>) wire.get("judgment")).get("schemaVersion")).isEqualTo(5);
	}

	@Test
	void richIdentityIsSupportedWithoutLoss() {
		Judgment judgment = new Judgment(JudgmentStatus.FAIL, new Finding(new BooleanFinding(false), null, null), null,
				null, null, "negative", java.util.List.of(), null, Map.of());
		StoredReading reading = StoredVerdicts.interpret(Verdict.single("seat", judgment));
		assertThat(reading.readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);
		assertThat(reading.schemaVersion()).isEqualTo(3);
		assertThat(reading.sourceVersion()).isEqualTo(5);
		assertThat(reading.outcome()).isEqualTo(RequirementOutcome.VIOLATED);
	}

	@Test
	void forgedReductionCannotContradictAllRetainedInputs() {
		var jury = io.github.markpollack.judge.jury.SimpleJury.builder()
			.judge(() -> Judgment.pass("a"))
			.judge(() -> Judgment.pass("b"))
			.votingStrategy(new io.github.markpollack.judge.jury.ConsensusStrategy())
			.build();
		var v = jury.vote();
		Map<String, Object> wire = mapper.convertValue(v, new TypeReference<>() {
		});
		var fail = mapper.convertValue(Judgment.fail("contradiction"), new TypeReference<Map<String, Object>>() {
		});
		wire.put("individual", java.util.List.of(fail, fail));
		Map<String, Object> names = new java.util.LinkedHashMap<>();
		v.individualByName().keySet().forEach(key -> names.put(key, fail));
		wire.put("individualByName", names);
		assertThat(StoredVerdicts.interpret(wire).readingSupport()).isEqualTo(ReadingSupport.UNDETERMINED);
	}

}
