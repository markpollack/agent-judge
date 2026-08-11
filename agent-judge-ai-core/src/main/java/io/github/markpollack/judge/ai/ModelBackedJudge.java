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
 * Executable examples are maintained in the Agent Judge Tutorial: https://github.com/markpollack/agent-judge-tutorial.
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

	/**
	 * Start a model-backed judge builder.
	 * @return a new builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/** Builds a model-backed judge from its four required collaborators. */
	public static class Builder {

		/** Create an empty builder. */
		public Builder() {
		}

		private String name;

		private String description = "";

		private JudgePromptTemplate promptTemplate;

		private JudgmentClassifier classifier;

		private JudgeModel model;

		/**
		 * Set the judge name.
		 * @param name judge name
		 * @return this builder
		 */
		public Builder name(String name) {
			this.name = name;
			return this;
		}

		/**
		 * Set the judge description.
		 * @param description judge description
		 * @return this builder
		 */
		public Builder description(String description) {
			this.description = description;
			return this;
		}

		/**
		 * Set the prompt template.
		 * @param promptTemplate prompt template
		 * @return this builder
		 */
		public Builder promptTemplate(JudgePromptTemplate promptTemplate) {
			this.promptTemplate = promptTemplate;
			return this;
		}

		/**
		 * Set the response classifier.
		 * @param classifier response classifier
		 * @return this builder
		 */
		public Builder judgmentClassifier(JudgmentClassifier classifier) {
			this.classifier = classifier;
			return this;
		}

		/**
		 * Set the model adapter.
		 * @param model model adapter
		 * @return this builder
		 */
		public Builder model(JudgeModel model) {
			this.model = model;
			return this;
		}

		/**
		 * Build the configured judge.
		 * @return a model-backed judge
		 */
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
