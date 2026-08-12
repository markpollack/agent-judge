/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge;

import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.Judgment;

/**
 * Wrapper that adds metadata to a Judge through composition.
 *
 * <p>
 * This class implements the composition-over-inheritance pattern to attach metadata
 * (name, description, type) to any Judge implementation without polluting the core Judge
 * interface with default methods. This preserves functional interface purity while
 * enabling rich metadata when needed for logging, monitoring, or display.
 * </p>
 *
 * <p>
 * This class implements {@link JudgeWithMetadata} marker interface, enabling semantic
 * pattern matching for infrastructure code, monitoring tools, and dashboards.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 * Executable examples are maintained in the Agent Judge Tutorial: https://github.com/markpollack/agent-judge-tutorial.
 *
 * @author Mark Pollack
 * @since 0.1.0
 * @see Judge
 * @see JudgeWithMetadata
 * @see JudgeMetadata
 * @see Judges
 */
public final class NamedJudge implements JudgeWithMetadata {

	private final Judge delegate;

	private final JudgeMetadata metadata;

	/**
	 * Create a named judge wrapping the given delegate with metadata.
	 * @param delegate the judge to wrap
	 * @param metadata the metadata for this judge
	 */
	public NamedJudge(Judge delegate, JudgeMetadata metadata) {
		this.delegate = delegate;
		this.metadata = metadata;
	}

	@Override
	public Judgment judge(JudgmentContext context) {
		return this.delegate.judge(context);
	}

	/**
	 * Get the metadata for this judge.
	 * @return the judge metadata
	 */
	public JudgeMetadata metadata() {
		return this.metadata;
	}

}
