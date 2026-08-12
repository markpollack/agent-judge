/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge;

/**
 * Metadata about a judge including its name, description, and type.
 *
 * @param name the judge name (e.g., "FileExistsJudge", "CorrectnessJudge")
 * @param description human-readable description of what this judge evaluates
 * @param type the judge type (deterministic, LLM-powered, hybrid, or agent)
 * @author Mark Pollack
 * @since 0.1.0
 */
public record JudgeMetadata(String name, String description, JudgeType type) {

}
