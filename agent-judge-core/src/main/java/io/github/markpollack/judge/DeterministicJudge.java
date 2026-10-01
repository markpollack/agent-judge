/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge;

/**
 * Base class for deterministic (rule-based) judges.
 *
 * <p>
 * Deterministic judges use programmatic rules without LLMs. Examples: file existence
 * checks, command execution validation, build success verification, test result parsing.
 * </p>
 *
 * <p>
 * Subclasses implement the {@link Judge#judge()} method for their evidence type with
 * their specific evaluation logic.
 * </p>
 *
 * <p>
 * This class implements {@link io.github.markpollack.judge.JudgeWithMetadata} marker
 * interface, enabling infrastructure code to discover judge metadata via semantic pattern
 * matching.
 * </p>
 *
 * <p>
 * <strong>Design Rationale:</strong> Deterministic and AI-powered judges receive equal
 * first-class support, a key principle from our research. While frameworks like deepeval
 * focus heavily on LLM metrics, we recognize that deterministic judges (file checks,
 * build validation, test parsing) are often faster, cheaper, and more reliable for
 * specific evaluation criteria. Base classes provide convenience but are not required -
 * judges can implement the Judge interface directly.
 * </p>
 *
 * @param <E> evidence type
 * @author Mark Pollack
 * @since 0.1.0
 */
public abstract class DeterministicJudge<E> implements io.github.markpollack.judge.JudgeWithMetadata {

	private final JudgeMetadata metadata;

	private final java.util.function.Supplier<? extends E> evidence;

	/**
	 * Create a deterministic judge with discoverable metadata.
	 * @param name judge name
	 * @param description human-readable purpose
	 * @param evidence fresh evidence acquisition provider, invoked once per direct
	 * execution
	 */
	protected DeterministicJudge(java.util.function.Supplier<? extends E> evidence, String name, String description) {
		this.evidence = java.util.Objects.requireNonNull(evidence);
		// No exclusion capability: a deterministic judge that cannot evaluate abstains.
		this.metadata = new JudgeMetadata(name, description, JudgeType.DETERMINISTIC);
	}

	/**
	 * Executes with one freshly acquired real evidence snapshot.
	 * @return original judgment
	 */
	@Override
	public final io.github.markpollack.judge.judgment.Judgment judge() {
		return evaluate(java.util.Objects.requireNonNull(evidence.get(), "acquired evidence"));
	}

	/**
	 * Evaluates the configured snapshot.
	 * @param evidence actual evidence
	 * @return judgment
	 */
	protected abstract io.github.markpollack.judge.judgment.Judgment evaluate(E evidence);

	/**
	 * Get metadata for this judge.
	 * <p>
	 * This method implements
	 * {@link io.github.markpollack.judge.JudgeWithMetadata#metadata()}, enabling
	 * infrastructure code to discover this judge's metadata via semantic pattern
	 * matching.
	 * </p>
	 * @return the judge metadata
	 */
	@Override
	public JudgeMetadata metadata() {
		return this.metadata;
	}

}
