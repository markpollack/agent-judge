/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.ai.prompt;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.markpollack.judge.completion.CompletionEvidence;

/**
 * Extracts template variables from a {@link CompletionEvidence} for use in prompt
 * rendering.
 *
 * <p>
 * Standard variables:
 * <ul>
 * <li>{@code goal} — the agent's task description</li>
 * <li>{@code output} — the agent's output text (empty string if absent)</li>
 *
 * <li>{@code status} — the execution status</li>
 * <li>{@code metadata.<key>} — each metadata entry as a dotted variable</li>
 * </ul>
 *
 * @author Mark Pollack
 * @since 0.10.0
 */
public final class CompletionVariables {

	private CompletionVariables() {
	}

	/**
	 * Extract template variables from a judgment context.
	 * @param context the judgment context
	 * @return a mutable map of variable name to value
	 */
	public static Map<String, Object> from(CompletionEvidence context) {
		Map<String, Object> variables = new LinkedHashMap<>();
		variables.put("goal", context.request() != null ? context.request() : "");
		variables.put("output", java.util.Optional.ofNullable(context.response()).orElse(""));
		variables.put("status", context.status() != null ? context.status().name() : "");

		if (context.metadata() != null) {
			for (var entry : context.metadata().entrySet()) {
				variables.put("metadata." + entry.getKey(), entry.getValue());
			}
		}

		return variables;
	}

}
