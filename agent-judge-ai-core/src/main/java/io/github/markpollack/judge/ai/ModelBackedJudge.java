/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.markpollack.judge.JudgeMetadata;
import io.github.markpollack.judge.JudgeType;
import io.github.markpollack.judge.JudgeWithMetadata;
import io.github.markpollack.judge.ai.model.EvalModel;
import io.github.markpollack.judge.ai.model.EvalModelRequest;
import io.github.markpollack.judge.ai.model.EvalModelResponse;
import io.github.markpollack.judge.ai.prompt.JudgePromptTemplate;
import java.util.function.Function;
import java.util.Objects;
import io.github.markpollack.judge.description.ConfiguredJudge;
import io.github.markpollack.judge.portable.ImplementationIdentity;
import io.github.markpollack.judge.judgment.Judgment;

/**
 * A judge backed by an AI model or agent session.
 *
 * <p>
 * Composes the full pipeline via builder — no subclassing needed:
 * <ol>
 * <li>{@link JudgePromptTemplate} renders the prompt from typed evidence</li>
 * <li>{@link EvalModel} invokes the AI backend</li>
 * <li>{@link JudgmentClassifier} maps the model response into a {@link Judgment}</li>
 * </ol>
 *
 * <p>
 * It is a {@link ConfiguredJudge}: {@link #configuration()} declares the prompt it
 * renders and the classifier that reads the answer. It declares no model, because a
 * {@link EvalModel} does not state which model it will call.
 *
 * <p>
 * Its metadata carries the exclusion capability, if any. A judge whose rubric is partly
 * conditional declares that through {@link Builder#notApplicableWhen(String)} while the
 * jury is being assembled; a judge that does not declare it cannot exclude a criterion
 * after seeing the subject.
 *
 * <p>
 * Example: Executable examples are maintained in the Agent Judge Tutorial:
 * https://github.com/markpollack/agent-judge-tutorial.
 *
 * @param <E> evidence type
 * @author Mark Pollack
 * @since 0.10.0
 */
public final class ModelBackedJudge<E> implements JudgeWithMetadata, ConfiguredJudge {

	/**
	 * Final construction stage for a model producer.
	 *
	 * @param <E> evidence type captured during construction
	 */
	@FunctionalInterface
	public interface Ready<E> extends io.github.markpollack.judge.construction.ReadyJudge {

		@Override
		ModelBackedJudge<E> build();

	}

	/**
	 * Configuration key for the prompt template's {@linkplain JudgePromptTemplate#name()
	 * name}.
	 *
	 * @since 0.17.0
	 */
	public static final String PROMPT_TEMPLATE_KEY = "promptTemplate";

	/**
	 * Configuration key for the lowercase hexadecimal SHA-256 digest of the template
	 * text, encoded as UTF-8, before rendering.
	 *
	 * @since 0.17.0
	 */
	public static final String PROMPT_TEMPLATE_SHA256_KEY = "promptTemplateSha256";

	/**
	 * Configuration key for the template's
	 * {@linkplain JudgePromptTemplate#missingVariablePolicy() missing-variable policy},
	 * as its constant name.
	 *
	 * @since 0.17.0
	 */
	public static final String MISSING_VARIABLE_POLICY_KEY = "missingVariablePolicy";

	/**
	 * Configuration key for the judgment classifier's implementation, in the portable
	 * form of {@link ImplementationIdentity}, so a lambda classifier records no unstable
	 * class name.
	 *
	 * @since 0.17.0
	 */
	public static final String JUDGMENT_CLASSIFIER_KEY = "judgmentClassifier";

	private final JudgeMetadata metadata;

	private final JudgePromptTemplate promptTemplate;

	private final Function<? super E, Map<String, Object>> variables;

	private final JudgmentClassifier classifier;

	private final EvalModel model;

	private final java.util.function.Supplier<? extends E> evidence;

	private final io.github.markpollack.judge.requirement.Requirement<?> requirement;

	private ModelBackedJudge(JudgeMetadata metadata, JudgePromptTemplate promptTemplate, JudgmentClassifier classifier,
			EvalModel model, Function<? super E, Map<String, Object>> variables,
			java.util.function.Supplier<? extends E> evidence,
			io.github.markpollack.judge.requirement.Requirement<?> requirement) {
		this.metadata = metadata;
		this.promptTemplate = promptTemplate;
		this.classifier = classifier;
		this.model = model;
		this.variables = variables;
		this.evidence = evidence;
		this.requirement = requirement;
	}

	@Override
	public Judgment judge() {
		String prompt = promptTemplate
			.render(variables.apply(java.util.Objects.requireNonNull(evidence.get(), "acquired evidence")));
		var response = model.execute(EvalModelRequest.user(prompt));
		Judgment result;
		try {
			result = java.util.Objects.requireNonNull(classifier.classify(response.answer()),
					"Classifier returned null");
		}
		catch (java.util.concurrent.CancellationException cancelled) {
			throw cancelled;
		}
		catch (RuntimeException failure) {
			if (Thread.currentThread().isInterrupted())
				throw new java.util.concurrent.CancellationException("Classification interrupted");
			result = Judgment.error("Answer classification failed: " + failure.getClass().getName() + ": "
					+ java.util.Objects.toString(failure.getMessage(), ""));
		}
		result = result.withInvocation(response.invocation());
		return requirement == null ? result : result.forRequirement(requirement);
	}

	@Override
	public JudgeMetadata metadata() {
		return metadata;
	}

	/**
	 * The configuration this judge's verdicts depend on, as known before any call.
	 * <p>
	 * Declares the prompt template's name ({@value #PROMPT_TEMPLATE_KEY}), the SHA-256 of
	 * its text before rendering ({@value #PROMPT_TEMPLATE_SHA256_KEY}), its
	 * missing-variable policy ({@value #MISSING_VARIABLE_POLICY_KEY}) and the
	 * classifier's implementation ({@value #JUDGMENT_CLASSIFIER_KEY}).
	 * </p>
	 * <p>
	 * There is no model key. A {@link EvalModel} does not state which model it will
	 * call, and an adapter such as a chat client may choose at call time, so any value
	 * here would be a guess. The model a call actually used is reported afterwards, in
	 * {@link EvalModelResponse#model()}.
	 * </p>
	 * @return the declared configuration, in declaration order
	 *
	 * @since 0.17.0
	 */
	@Override
	public Map<String, Object> configuration() {
		Map<String, Object> configuration = new LinkedHashMap<>();
		configuration.put(PROMPT_TEMPLATE_KEY, promptTemplate.name());
		configuration.put(PROMPT_TEMPLATE_SHA256_KEY, sha256(promptTemplate.source().load()));
		configuration.put(MISSING_VARIABLE_POLICY_KEY, promptTemplate.missingVariablePolicy().name());
		configuration.put(JUDGMENT_CLASSIFIER_KEY, ImplementationIdentity.of(classifier.getClass()).toPortable());
		return configuration;
	}

	private static String sha256(String text) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is required on every Java platform", ex);
		}
	}

	/**
	 * Start a model-backed judge builder.
	 * @param <E> evidence type
	 * @return a new builder
	 */
	public static <E> Builder<E> builder() {
		return new Builder<>();
	}

	/**
	 * Builds a model-backed judge with explicit evidence rendering.
	 *
	 * @param <E> evidence type
	 */
	public static class Builder<E> implements io.github.markpollack.judge.construction.EvidenceStep<E>,
			io.github.markpollack.judge.construction.JudgeRecipe<String, E> {

		/** Create an empty builder. */
		public Builder() {
		}

		private String name;

		private String description = "";

		private JudgePromptTemplate promptTemplate;

		private JudgmentClassifier classifier;

		private EvalModel model;

		private String notApplicableWhen;

		private Function<? super E, Map<String, Object>> variables;

		@Override
		public Ready<E> evidence(E value) {
			Objects.requireNonNull(value);
			return evidenceSupplier(() -> value);
		}

		@Override
		public Ready<E> evidenceSupplier(java.util.function.Supplier<? extends E> provider) {
			Objects.requireNonNull(provider);
			Builder<E> captured = snapshot();
			return () -> captured.build(provider, null);
		}

		@Override
		public io.github.markpollack.judge.construction.EvidenceStep<E> requirement(
				io.github.markpollack.judge.requirement.Requirement<String> actual) {
			io.github.markpollack.judge.requirement.Requirement.validate(actual);
			Builder<E> captured = snapshot();
			return new io.github.markpollack.judge.construction.EvidenceStep<>() {
				public Ready<E> evidence(E value) {
					Objects.requireNonNull(value);
					return evidenceSupplier(() -> value);
				}

				public Ready<E> evidenceSupplier(java.util.function.Supplier<? extends E> provider) {
					Objects.requireNonNull(provider);
					return () -> captured.build(provider, actual);
				}
			};
		}

		private Builder<E> snapshot() {
			Builder<E> copy = new Builder<>();
			copy.name = name;
			copy.description = description;
			copy.promptTemplate = promptTemplate;
			copy.classifier = classifier;
			copy.model = model;
			copy.notApplicableWhen = notApplicableWhen;
			copy.variables = variables;
			return copy;
		}

		/**
		 * Selects the native generated-answer runtime.
		 * @param runtime native harness
		 * @return this builder
		 */
		public Builder<E> runtime(EvalModel runtime) {
			return model(runtime);
		}

		/**
		 * Define how this Judge renders its typed evidence, separately from model
		 * configuration.
		 * @param variables explicit evidence-to-template projection
		 * @return this builder
		 */
		public Builder<E> variables(Function<? super E, Map<String, Object>> variables) {
			this.variables = Objects.requireNonNull(variables);
			return this;
		}

		/**
		 * Set the judge name.
		 * @param name judge name
		 * @return this builder
		 */
		public Builder<E> name(String name) {
			this.name = name;
			return this;
		}

		/**
		 * Set the judge description.
		 * @param description judge description
		 * @return this builder
		 */
		public Builder<E> description(String description) {
			this.description = description;
			return this;
		}

		/**
		 * Set the prompt template.
		 * @param promptTemplate prompt template
		 * @return this builder
		 */
		public Builder<E> promptTemplate(JudgePromptTemplate promptTemplate) {
			this.promptTemplate = promptTemplate;
			return this;
		}

		/**
		 * Set the response classifier.
		 * @param classifier response classifier
		 * @return this builder
		 */
		public Builder<E> judgmentClassifier(JudgmentClassifier classifier) {
			this.classifier = classifier;
			return this;
		}

		/**
		 * Set the model adapter.
		 * @param model model adapter
		 * @return this builder
		 */
		public Builder<E> model(EvalModel model) {
			this.model = model;
			return this;
		}

		/**
		 * Declare that this judge may return
		 * {@link io.github.markpollack.judge.judgment.JudgmentStatus#NOT_APPLICABLE}, and
		 * under what condition.
		 * <p>
		 * Omit it and the judge declares no exclusion capability, which is the default: a
		 * jury then contains an exclusion from this seat as an error rather than
		 * honouring it. State a condition a reader could check against the subject, not
		 * the fact that the judge sometimes excludes.
		 * </p>
		 * @param notApplicableWhen the condition; must be non-blank
		 * @return this builder
		 *
		 * @since 0.17.0
		 */
		public Builder<E> notApplicableWhen(String notApplicableWhen) {
			this.notApplicableWhen = notApplicableWhen;
			return this;
		}

		/**
		 * Build the configured judge.
		 * @return a model-backed judge
		 */
		private ModelBackedJudge<E> build(java.util.function.Supplier<? extends E> evidence,
				io.github.markpollack.judge.requirement.Requirement<?> requirement) {
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
			model.requireInput(io.github.markpollack.judge.ai.model.GeneratedInput.PREPARED_EVIDENCE);
			JudgeMetadata metadata = new JudgeMetadata(name, description, JudgeType.LLM_POWERED, notApplicableWhen);
			return new ModelBackedJudge<>(metadata, promptTemplate, classifier, model,
					requirementVariables(Objects.requireNonNull(variables, "evidence variables"), requirement),
					evidence, requirement);
		}

		private Function<? super E, Map<String, Object>> requirementVariables(
				Function<? super E, Map<String, Object>> projection,
				io.github.markpollack.judge.requirement.Requirement<?> requirement) {
			if (requirement == null)
				return projection;
			return value -> {
				Map<String, Object> result = new LinkedHashMap<>(projection.apply(value));
				Map<String, Object> owned = Map.of("requirement", requirement.specification(), "requirementId",
						requirement.id(), "requirementRevision", requirement.revision());
				owned.forEach((key, fact) -> {
					if (result.containsKey(key))
						throw new IllegalArgumentException("Evidence projection duplicates configured " + key);
					result.put(key, fact);
				});
				return result;
			};
		}

	}

}
