/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge;

import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.Judgment;

/**
 * Pure functional interface for judging agent execution results.
 *
 * <p>
 * This interface defines the core judging contract as a single abstract method, enabling
 * functional programming patterns like lambdas, method references, and composition.
 * </p>
 *
 * <p>
 * <strong>Functional Purity:</strong> Judge is intentionally minimal - a single method
 * with no default methods or metadata concerns. This preserves functional interface
 * discipline and clean separation of concerns. For judges that need metadata (name,
 * description, type), use {@link NamedJudge} which wraps a Judge with metadata through
 * composition.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 * Executable examples are maintained in the Agent Judge Tutorial: https://github.com/markpollack/agent-judge-tutorial.
 *
 * <p>
 * <strong>Design Inspiration:</strong> This interface draws from the "judges" framework's
 * clean BaseJudge abstraction and Spring AI's Evaluator pattern - a single abstract
 * method with rich context. The composition-over-inheritance approach (NamedJudge
 * wrapper) avoids default method pollution while maintaining functional purity.
 * </p>
 *
 * @author Mark Pollack
 * @since 0.1.0
 * @see NamedJudge
 * @see Judges
 */
@FunctionalInterface
public interface Judge {

	/**
	 * Evaluate an agent execution result.
	 * @param context the judgment context containing all information about the agent
	 * execution
	 * @return the judgment with required status, optional score/label, reasoning, and checks
	 */
	Judgment judge(JudgmentContext context);

}
