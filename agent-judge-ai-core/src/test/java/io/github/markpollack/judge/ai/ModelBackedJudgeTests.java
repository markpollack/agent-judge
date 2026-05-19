package io.github.markpollack.judge.ai;

import java.nio.file.Path;

import io.github.markpollack.judge.ai.model.JudgeModel;
import io.github.markpollack.judge.ai.model.JudgeModelRequest;
import io.github.markpollack.judge.ai.model.JudgeModelResponse;
import io.github.markpollack.judge.ai.prompt.JudgePromptTemplate;
import io.github.markpollack.judge.context.ExecutionStatus;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelBackedJudgeTests {

	@Test
	void passJudgment() {
		JudgeModel model = stubModel("relevant");

		var judge = ModelBackedJudge.builder()
			.name("relevance")
			.promptTemplate(JudgePromptTemplate.fromString("relevance", "Is {{output}} relevant to {{goal}}?"))
			.judgmentClassifier(JudgmentClassifiers.passFail("relevant", "irrelevant"))
			.model(model)
			.build();

		JudgmentContext context = JudgmentContext.builder()
			.goal("summarize the document")
			.agentOutput("Here is a summary of the document.")
			.status(ExecutionStatus.SUCCESS)
			.build();

		Judgment judgment = judge.judge(context);

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(judgment.reasoning()).isEqualTo("relevant");
	}

	@Test
	void failJudgment() {
		JudgeModel model = stubModel("irrelevant");

		var judge = ModelBackedJudge.builder()
			.name("relevance")
			.promptTemplate(JudgePromptTemplate.fromString("relevance", "Is {{output}} relevant to {{goal}}?"))
			.judgmentClassifier(JudgmentClassifiers.passFail("relevant", "irrelevant"))
			.model(model)
			.build();

		JudgmentContext context = JudgmentContext.builder()
			.goal("summarize the document")
			.agentOutput("I like pizza.")
			.status(ExecutionStatus.SUCCESS)
			.build();

		Judgment judgment = judge.judge(context);

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.FAIL);
	}

	@Test
	void abstainOnUnrecognizedLabel() {
		JudgeModel model = stubModel("maybe");

		var judge = ModelBackedJudge.builder()
			.name("relevance")
			.promptTemplate(JudgePromptTemplate.fromString("relevance", "Is {{output}} relevant to {{goal}}?"))
			.judgmentClassifier(JudgmentClassifiers.passFail("relevant", "irrelevant"))
			.model(model)
			.build();

		JudgmentContext context = JudgmentContext.builder()
			.goal("test")
			.agentOutput("test output")
			.status(ExecutionStatus.SUCCESS)
			.build();

		Judgment judgment = judge.judge(context);

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(judgment.reasoning()).contains("maybe");
	}

	@Test
	void classpathTemplate() {
		JudgeModel model = stubModel("relevant");

		var judge = ModelBackedJudge.builder()
			.name("relevance")
			.promptTemplate(JudgePromptTemplate.fromClasspath("judges/test-relevance.md"))
			.judgmentClassifier(JudgmentClassifiers.passFail("relevant", "irrelevant"))
			.model(model)
			.build();

		JudgmentContext context = JudgmentContext.builder()
			.goal("test goal")
			.agentOutput("test output")
			.status(ExecutionStatus.SUCCESS)
			.build();

		Judgment judgment = judge.judge(context);

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
	}

	@Test
	void metadataExposed() {
		var judge = ModelBackedJudge.builder()
			.name("test-judge")
			.description("A test judge")
			.promptTemplate(JudgePromptTemplate.fromString("test", "{{goal}}"))
			.judgmentClassifier(JudgmentClassifiers.passFail("yes", "no"))
			.model(stubModel("yes"))
			.build();

		assertThat(judge.metadata().name()).isEqualTo("test-judge");
		assertThat(judge.metadata().description()).isEqualTo("A test judge");
	}

	@Test
	void builderValidation() {
		assertThatThrownBy(() -> ModelBackedJudge.builder().build()).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("name");
	}

	private JudgeModel stubModel(String response) {
		return request -> new JudgeModelResponse(response, "test-model", null, null);
	}

}
