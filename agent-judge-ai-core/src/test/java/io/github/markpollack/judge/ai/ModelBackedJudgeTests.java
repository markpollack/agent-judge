/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.ai;

import io.github.markpollack.judge.ai.model.JudgeModel;
import io.github.markpollack.judge.ai.model.JudgeModelResponse;
import io.github.markpollack.judge.ai.prompt.JudgePromptTemplate;
import io.github.markpollack.judge.completion.CompletionStatus;
import io.github.markpollack.judge.completion.CompletionEvidence;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelBackedJudgeTests {

	@Test
	void passJudgment() {
		JudgeModel model = stubModel("relevant");

		var judge = ModelBackedJudge.<io.github.markpollack.judge.completion.CompletionEvidence>builder()
			.variables(io.github.markpollack.judge.ai.prompt.CompletionVariables::from)
			.name("relevance")
			.promptTemplate(JudgePromptTemplate.fromString("relevance", "Is {{output}} relevant to {{goal}}?"))
			.judgmentClassifier(JudgmentClassifiers.passFail("relevant", "irrelevant"))
			.model(model);

		CompletionEvidence context = CompletionEvidence.builder()
			.request("summarize the document")
			.response("Here is a summary of the document.")
			.status(CompletionStatus.SUCCESS)
			.build();

		Judgment judgment = judge.evidence(context).build().judge();

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(judgment.reasoning()).isEqualTo("relevant");
	}

	@Test
	void failJudgment() {
		JudgeModel model = stubModel("irrelevant");

		var judge = ModelBackedJudge.<io.github.markpollack.judge.completion.CompletionEvidence>builder()
			.variables(io.github.markpollack.judge.ai.prompt.CompletionVariables::from)
			.name("relevance")
			.promptTemplate(JudgePromptTemplate.fromString("relevance", "Is {{output}} relevant to {{goal}}?"))
			.judgmentClassifier(JudgmentClassifiers.passFail("relevant", "irrelevant"))
			.model(model);

		CompletionEvidence context = CompletionEvidence.builder()
			.request("summarize the document")
			.response("I like pizza.")
			.status(CompletionStatus.SUCCESS)
			.build();

		Judgment judgment = judge.evidence(context).build().judge();

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.FAIL);
	}

	@Test
	void abstainOnUnrecognizedLabel() {
		JudgeModel model = stubModel("maybe");

		var judge = ModelBackedJudge.<io.github.markpollack.judge.completion.CompletionEvidence>builder()
			.variables(io.github.markpollack.judge.ai.prompt.CompletionVariables::from)
			.name("relevance")
			.promptTemplate(JudgePromptTemplate.fromString("relevance", "Is {{output}} relevant to {{goal}}?"))
			.judgmentClassifier(JudgmentClassifiers.passFail("relevant", "irrelevant"))
			.model(model);

		CompletionEvidence context = CompletionEvidence.builder()
			.request("test")
			.response("test output")
			.status(CompletionStatus.SUCCESS)
			.build();

		Judgment judgment = judge.evidence(context).build().judge();

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(judgment.reasoning()).contains("maybe");
	}

	@Test
	void classpathTemplate() {
		JudgeModel model = stubModel("relevant");

		var judge = ModelBackedJudge.<io.github.markpollack.judge.completion.CompletionEvidence>builder()
			.variables(io.github.markpollack.judge.ai.prompt.CompletionVariables::from)
			.name("relevance")
			.promptTemplate(JudgePromptTemplate.fromClasspath("judges/test-relevance.md"))
			.judgmentClassifier(JudgmentClassifiers.passFail("relevant", "irrelevant"))
			.model(model);

		CompletionEvidence context = CompletionEvidence.builder()
			.request("test goal")
			.response("test output")
			.status(CompletionStatus.SUCCESS)
			.build();

		Judgment judgment = judge.evidence(context).build().judge();

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
	}

	@Test
	void metadataExposed() {
		var judge = ModelBackedJudge.<io.github.markpollack.judge.completion.CompletionEvidence>builder()
			.variables(io.github.markpollack.judge.ai.prompt.CompletionVariables::from)
			.name("test-judge")
			.description("A test judge")
			.promptTemplate(JudgePromptTemplate.fromString("test", "{{goal}}"))
			.judgmentClassifier(JudgmentClassifiers.passFail("yes", "no"))
			.model(stubModel("yes"))
			.evidence(io.github.markpollack.judge.completion.CompletionEvidence.builder()
				.request("metadata fixture")
				.build())
			.build();

		assertThat(judge.metadata().name()).isEqualTo("test-judge");
		assertThat(judge.metadata().description()).isEqualTo("A test judge");
	}

	@Test
	void builderValidation() {
		assertThatThrownBy(() -> ModelBackedJudge.<io.github.markpollack.judge.completion.CompletionEvidence>builder()
			.variables(io.github.markpollack.judge.ai.prompt.CompletionVariables::from)
			.evidence(io.github.markpollack.judge.completion.CompletionEvidence.builder()
				.request("validation fixture")
				.build())
			.build()).isInstanceOf(IllegalStateException.class).hasMessageContaining("name");
	}

	private JudgeModel stubModel(String response) {
		return request -> new JudgeModelResponse(response, "test-model", null, null);
	}

}
