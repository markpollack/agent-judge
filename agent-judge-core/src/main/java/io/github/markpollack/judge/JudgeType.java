/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge;

/**
 * Types of judges based on their evaluation approach.
 *
 * @author Mark Pollack
 * @since 0.1.0
 */
public enum JudgeType {

	/**
	 * Deterministic judge - Rule-based evaluation without LLM. Examples: file checks,
	 * command execution, build success.
	 */
	DETERMINISTIC,

	/**
	 * LLM-powered judge - Uses large language model for evaluation. Examples:
	 * correctness, code quality, faithfulness.
	 */
	LLM_POWERED,

	/**
	 * Hybrid judge - Combines deterministic checks with LLM evaluation. Examples: jury
	 * with mixed judges.
	 */
	HYBRID,

	/**
	 * Agent judge - Uses another autonomous agent for judgment. Examples: code review
	 * agent, security audit agent.
	 */
	AGENT

}
