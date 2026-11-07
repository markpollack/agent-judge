/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.ai.requirements;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.markpollack.judge.ai.model.EvalModel;
import io.github.markpollack.judge.ai.model.EvalModelRequest;
import io.github.markpollack.judge.ai.model.EvalModelResponse;
import io.github.markpollack.judge.provenance.ArtifactRef;
import io.github.markpollack.judge.provenance.Invocation;
import io.github.markpollack.judge.serialization.VerdictCodec;
import io.github.markpollack.judge.verdict.Verdict;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A generated-answer roster over large prepared evidence must store and reopen. The
 * request is reproducible from requirement and evidence; the stored invocation keeps its
 * digest, not a second copy of its bytes. No live inference.
 */
class GeneratedRequestStorageTest {

	/** Prepared evidence large enough that an inline copy of the request overflows the bound. */
	private static final int EVIDENCE_BYTES = 1_040_000;

	/** Measured size of the saved answer text. */
	private static final int REASONING_BYTES = 16_000;

	private final VerdictCodec codec = NativeRequirementCodecs.codec();

	@Test
	void rfc2119RosterOverLargeEvidenceStoresAndReopensWithRequestDigest() {
		var requests = new ArrayList<EvalModelRequest>();
		var requirements = List.of(Rfc2119Requirement.of("A", "rev7", "MUST", "retain facts", "audit", null),
				Rfc2119Requirement.of("B", "rev7", "SHOULD", "retain support", "audit", null));
		Verdict original = Rfc2119Jury.builder()
			.runtime(saved(requests, "A: FAIL - " + reasoning() + "\nB: PASS - holds"))
			.requirements(requirements)
			.evidence(evidence())
			.build()
			.vote();
		assertStoredWithDigest(original, requests);
	}

	@Test
	void earsRosterOverLargeEvidenceStoresAndReopensWithRequestDigest() {
		var requests = new ArrayList<EvalModelRequest>();
		var requirements = List.of(EarsRequirement.of("UC1-AC1", "rev9", "facts",
				"When requested, the system shall retain facts.", null));
		Verdict original = EarsJury.builder()
			.runtime(saved(requests, "UC1-AC1: FAIL - " + reasoning()))
			.requirements(requirements)
			.evidence(evidence())
			.build()
			.vote();
		assertStoredWithDigest(original, requests);
	}

	private void assertStoredWithDigest(Verdict original, List<EvalModelRequest> requests) {
		assertThat(requests).hasSize(1);
		String prompt = requests.getFirst().messages().getFirst().content();
		assertThat(prompt.getBytes(StandardCharsets.UTF_8)).hasSizeGreaterThan(VerdictCodec.MAXIMUM_BYTES - 10_000);

		String json = codec.write(original);
		assertThat(json.getBytes(StandardCharsets.UTF_8)).hasSizeLessThan(VerdictCodec.MAXIMUM_BYTES / 4);
		assertThat(json).doesNotContain(evidence().substring(0, 4096));

		Verdict read = codec.read(json);
		assertThat(read).isEqualTo(original);
		assertThat(read.conclusion()).isEqualTo(original.conclusion());
		Invocation invocation = read.invocations().getFirst();
		assertThat(invocation.protocol()).isEqualTo("generated-answer:v3");
		assertThat(invocation.nativeFacts()).doesNotContainKey("messages");
		assertThat(invocation.nativeFacts().get("text")).asString().contains(reasoning());
		String digest = ArtifactRef.ofBytes("request", prompt.getBytes(StandardCharsets.UTF_8), null).sha256();
		assertThat(invocation.nativeFacts().get("messageDigests")).isEqualTo(List.of(Map.of("role", "USER", "utf8Bytes",
				prompt.getBytes(StandardCharsets.UTF_8).length, "sha256", digest)));
	}

	private static EvalModel saved(List<EvalModelRequest> requests, String answer) {
		return request -> {
			requests.add(request);
			return new EvalModelResponse(answer, "saved", null, Map.of());
		};
	}

	private static String evidence() {
		return "x".repeat(EVIDENCE_BYTES);
	}

	private static String reasoning() {
		return "r".repeat(REASONING_BYTES);
	}

}
