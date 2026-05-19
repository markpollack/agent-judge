package io.github.markpollack.judge.ai;

import io.github.markpollack.judge.JudgeMetadata;
import io.github.markpollack.judge.JudgeType;
import io.github.markpollack.judge.JudgeWithMetadata;
import io.github.markpollack.judge.ai.model.JudgeModel;
import io.github.markpollack.judge.ai.model.JudgeModelRequest;
import io.github.markpollack.judge.ai.model.JudgeModelResponse;
import io.github.markpollack.judge.ai.prompt.JudgePromptTemplate;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.Judgment;

/**
 * A judge backed by an AI model or agent session.
 *
 * <p>Composes the full pipeline via builder — no subclassing needed:
 * <ol>
 *   <li>{@link JudgePromptTemplate} renders the prompt from {@link JudgmentContext}</li>
 *   <li>{@link JudgeModel} invokes the AI backend</li>
 *   <li>{@link JudgmentClassifier} maps the model response into a {@link Judgment}</li>
 * </ol>
 *
 * <p>Example:
 * <pre>{@code
 * Judge judge = ModelBackedJudge.builder()
 *     .name("relevance")
 *     .promptTemplate(JudgePromptTemplate.fromClasspath("judges/relevance.md"))
 *     .judgmentClassifier(JudgmentClassifiers.passFail("relevant", "irrelevant"))
 *     .model(springAiJudgeModel)
 *     .build();
 * }</pre>
 *
 * @author Mark Pollack
 * @since 0.10.0
 */
public final class ModelBackedJudge implements JudgeWithMetadata {

	private final JudgeMetadata metadata;

	private final JudgePromptTemplate promptTemplate;

	private final JudgmentClassifier classifier;

	private final JudgeModel model;

	private ModelBackedJudge(JudgeMetadata metadata, JudgePromptTemplate promptTemplate,
			JudgmentClassifier classifier, JudgeModel model) {
		this.metadata = metadata;
		this.promptTemplate = promptTemplate;
		this.classifier = classifier;
		this.model = model;
	}

	@Override
	public Judgment judge(JudgmentContext context) {
		String prompt = promptTemplate.render(context);
		JudgeModelResponse response = model.generate(JudgeModelRequest.user(prompt));
		return classifier.classify(response);
	}

	@Override
	public JudgeMetadata metadata() {
		return metadata;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private String name;

		private String description = "";

		private JudgePromptTemplate promptTemplate;

		private JudgmentClassifier classifier;

		private JudgeModel model;

		public Builder name(String name) {
			this.name = name;
			return this;
		}

		public Builder description(String description) {
			this.description = description;
			return this;
		}

		public Builder promptTemplate(JudgePromptTemplate promptTemplate) {
			this.promptTemplate = promptTemplate;
			return this;
		}

		public Builder judgmentClassifier(JudgmentClassifier classifier) {
			this.classifier = classifier;
			return this;
		}

		public Builder model(JudgeModel model) {
			this.model = model;
			return this;
		}

		public ModelBackedJudge build() {
			if (name == null) {
				throw new IllegalStateException("Judge name is required");
			}
			if (promptTemplate == null) {
				throw new IllegalStateException("Prompt template is required");
			}
			if (classifier == null) {
				throw new IllegalStateException("Judgment classifier is required");
			}
			if (model == null) {
				throw new IllegalStateException("Judge model is required");
			}
			JudgeMetadata metadata = new JudgeMetadata(name, description, JudgeType.LLM_POWERED);
			return new ModelBackedJudge(metadata, promptTemplate, classifier, model);
		}

	}

}
