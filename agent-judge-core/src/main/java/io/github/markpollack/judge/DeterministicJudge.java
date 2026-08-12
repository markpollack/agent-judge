/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.JudgeMetadata;
import io.github.markpollack.judge.JudgeType;

/**
 * Base class for deterministic (rule-based) judges.
 *
 * <p>
 * Deterministic judges use programmatic rules without LLMs. Examples: file existence
 * checks, command execution validation, build success verification, test result parsing.
 * </p>
 *
 * <p>
 * Subclasses implement the
 * {@link #judge(io.github.markpollack.judge.context.JudgmentContext)} method with their
 * specific evaluation logic.
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
 * @author Mark Pollack
 * @since 0.1.0
 */
public abstract class DeterministicJudge implements io.github.markpollack.judge.JudgeWithMetadata {

	private final JudgeMetadata metadata;

	/**
	 * Create a deterministic judge with discoverable metadata.
	 * @param name judge name
	 * @param description human-readable purpose
	 */
	protected DeterministicJudge(String name, String description) {
		this.metadata = new JudgeMetadata(name, description, JudgeType.DETERMINISTIC);
	}

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
