/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.llm;
import io.github.markpollack.judge.verdict.InvocationRecords;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.completion.CompletionEvidence;
import io.github.markpollack.judge.judgment.*;
import io.github.markpollack.judge.evaluation.Evaluations;
import io.github.markpollack.judge.jury.*;
import io.github.markpollack.judge.verdict.*;
import io.github.markpollack.judge.voting.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import static org.assertj.core.api.Assertions.*;

class CorrectnessAnswerContractTest {

	static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> answers() {
		return java.util.stream.Stream.of(
				org.junit.jupiter.params.provider.Arguments.of("Answer: YES\nReasoning: Supported",
						JudgmentStatus.PASS),
				org.junit.jupiter.params.provider.Arguments
					.of("Answer: NO\nReasoning: Yesterday's output was unrelated", JudgmentStatus.FAIL),
				org.junit.jupiter.params.provider.Arguments.of("Answer: NO\nReasoning: YES was never returned",
						JudgmentStatus.FAIL),
				org.junit.jupiter.params.provider.Arguments.of("Answer: UNKNOWN\nReasoning: cannot decide",
						JudgmentStatus.ABSTAIN),
				org.junit.jupiter.params.provider.Arguments.of("Reasoning: YES seems plausible",
						JudgmentStatus.ABSTAIN),
				org.junit.jupiter.params.provider.Arguments.of("Answer: YESterday\nReasoning: malformed",
						JudgmentStatus.ABSTAIN),
				org.junit.jupiter.params.provider.Arguments.of("Answer: NONE\nReasoning: malformed",
						JudgmentStatus.ABSTAIN),
				org.junit.jupiter.params.provider.Arguments.of("Reasoning: illustrative example follows\nAnswer: YES",
						JudgmentStatus.ABSTAIN),
				org.junit.jupiter.params.provider.Arguments.of("Answer: YES\nAnswer: NO", JudgmentStatus.ABSTAIN),
				org.junit.jupiter.params.provider.Arguments.of("Answer: YES or NO", JudgmentStatus.ABSTAIN),
				org.junit.jupiter.params.provider.Arguments.of("Answer: YES\nAnswer: YES", JudgmentStatus.ABSTAIN),
				org.junit.jupiter.params.provider.Arguments.of("Answer: NO\nReasoning: example\nAnswer: YES",
						JudgmentStatus.ABSTAIN));
	}

	@ParameterizedTest
	@org.junit.jupiter.params.provider.MethodSource("answers")
	void exactAnswerAndNativeRetention(String escaped, JudgmentStatus expected) {
		String text = escaped.replace("\\n", "\n");
		var evidence = CompletionEvidence.builder().request("goal").response("output").build();
		{
			var calls = new AtomicInteger();
			ChatModel model = prompt -> {
				calls.incrementAndGet();
				return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
			};
			Judge ready = CorrectnessJudge.builder(ChatClient.builder(model)).evidence(evidence).build();
			var result = Evaluations.evaluate(ready);
			assertThat(result.verdict().judgment().status()).isEqualTo(expected);
			assertThat(InvocationRecords.of(result.verdict())).hasSize(1);
			assertThat(InvocationRecords.of(result.verdict()).getFirst().nativeFacts().toString()).contains(text);
			var codec = new io.github.markpollack.judge.serialization.VerdictCodec();
			var read = codec.read(codec.write(result.verdict()));
			assertThat(read).isEqualTo(result.verdict());
			assertThat(read.judgment().status()).isEqualTo(expected);
			io.github.markpollack.judge.reporting.VerdictReport.of(read).summary();
			assertThat(calls).hasValue(1);
		}
	}

	@Test
	void correctnessRequiresGoalAndOutputBeforeCallingModel() {
		for (var evidence : List.of(CompletionEvidence.builder().request(" ").response("output").build(),
				CompletionEvidence.builder().request("goal").build(),
				CompletionEvidence.builder().request("goal").response(" ").build())) {
			ChatModel model = prompt -> {
				throw new AssertionError("missing input must not invoke model");
			};
			var result = CorrectnessJudge.builder(ChatClient.builder(model)).evidence(evidence).build().judge();
			assertThat(result.status()).isEqualTo(JudgmentStatus.ABSTAIN);
			assertThat(result.invocations()).isEmpty();
		}
	}

}
