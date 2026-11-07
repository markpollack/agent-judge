/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.rag;

import io.github.markpollack.judge.judgment.Judgment;

/**
 * The input checks shared by the context-grounding judges.
 */
final class RagInputs {

	private RagInputs() {
	}

	/**
	 * The abstention when context or answer is missing, so there is nothing to judge.
	 * @param context the evidence
	 * @param action what the judge would do, such as "evaluate faithfulness"
	 * @return the abstention, or null when both are present
	 */
	static Judgment missing(RagEvidence context, String action) {
		if (context.retrievedContext().isBlank()) {
			return Judgment.abstain("No context provided — cannot " + action);
		}
		if (context.answer().isBlank()) {
			return Judgment.abstain("No answer provided — cannot " + action);
		}
		return null;
	}

	/**
	 * A field as the prompt shows it: blank text is shown as empty.
	 * @param value the field
	 * @return the value, or the empty string when it is blank
	 */
	static String shown(String value) {
		return value.isBlank() ? "" : value;
	}

}
