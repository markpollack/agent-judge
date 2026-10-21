/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.ai.requirements;

import java.util.List;
import java.util.Objects;

import static java.util.stream.Collectors.joining;

/**
 * A requirement document was refused by a strict reader.
 *
 * <p>
 * Every problem the reader found is carried, so one load reports the whole document rather
 * than its first defect. The permissive readers never throw this; they log the same
 * problems and return what they could build.
 *
 * @author Mark Pollack
 * @since 0.19.0
 */
public final class RequirementDocumentException extends IllegalArgumentException {

	/** The refused document's file name. */
	private final String source;

	/** Every problem found, in document order. */
	private final List<RequirementDocumentProblem> problems;

	/**
	 * Refuse a document.
	 * @param source the document's file name
	 * @param problems every problem found, in document order; nonempty
	 */
	public RequirementDocumentException(String source, List<RequirementDocumentProblem> problems) {
		super(message(source, problems));
		this.source = Objects.requireNonNull(source, "source");
		this.problems = List.copyOf(problems);
	}

	private static String message(String source, List<RequirementDocumentProblem> problems) {
		Objects.requireNonNull(problems, "problems");
		if (problems.isEmpty()) {
			throw new IllegalArgumentException("a refused document has at least one problem");
		}
		return problems.size() + (problems.size() == 1 ? " problem in " : " problems in ") + source + ":\n  "
				+ problems.stream().map(RequirementDocumentProblem::describe).collect(joining("\n  "));
	}

	/**
	 * The refused document's file name.
	 * @return the source name
	 */
	public String source() {
		return this.source;
	}

	/**
	 * Every problem found, in document order.
	 * @return nonempty immutable list
	 */
	public List<RequirementDocumentProblem> problems() {
		return this.problems;
	}
}
