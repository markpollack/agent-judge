/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.rag;
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

class RagAnswerContractTest {

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
		var evidence = new RagEvidence("question", "context", "answer");
		{
			var calls = new AtomicInteger();
			ChatModel model = prompt -> {
				calls.incrementAndGet();
				return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
			};
			Judge ready = FaithfulnessJudge.builder(ChatClient.builder(model)).evidence(evidence).build();
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
		{
			var calls = new AtomicInteger();
			ChatModel model = prompt -> {
				calls.incrementAndGet();
				return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
			};
			Judge ready = ContextualRelevanceJudge.builder(ChatClient.builder(model)).evidence(evidence).build();
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
		{
			var calls = new AtomicInteger();
			ChatModel model = prompt -> {
				calls.incrementAndGet();
				return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
			};
			Judge ready = HallucinationJudge.builder(ChatClient.builder(model)).evidence(evidence).build();
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
	void relevanceRequiresQuestionButNotAnswer() {
		var calls = new AtomicInteger();
		ChatModel model = prompt -> {
			calls.incrementAndGet();
			return new ChatResponse(List.of(new Generation(new AssistantMessage("Answer: YES"))));
		};
		var missing = ContextualRelevanceJudge.builder(ChatClient.builder(model))
			.evidence(new RagEvidence(" ", "context", "answer"))
			.build()
			.judge();
		assertThat(missing.status()).isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(missing.invocations()).isEmpty();
		assertThat(calls).hasValue(0);
		var sufficient = ContextualRelevanceJudge.builder(ChatClient.builder(model))
			.evidence(new RagEvidence("question", "context", ""))
			.build()
			.judge();
		assertThat(sufficient.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(calls).hasValue(1);
	}

	@Test
	void faithfulnessAndHallucinationNeedContextAndAnswerButNotQuestion() {
		for (boolean faithful : List.of(true, false)) {
			for (var evidence : List.of(new RagEvidence("q", "", "a"), new RagEvidence("q", "c", " "))) {
				ChatModel model = prompt -> {
					throw new AssertionError("missing input must not invoke model");
				};
				Judge judge = faithful ? FaithfulnessJudge.builder(ChatClient.builder(model)).evidence(evidence).build()
						: HallucinationJudge.builder(ChatClient.builder(model)).evidence(evidence).build();
				assertThat(judge.judge().status()).isEqualTo(JudgmentStatus.ABSTAIN);
			}
		}
	}

}
