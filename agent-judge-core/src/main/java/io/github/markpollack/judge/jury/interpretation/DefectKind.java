/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury.interpretation;

/**
 * What is wrong with a recorded fact.
 *
 * @author Mark Pollack
 * @since 0.17.0
 */
public enum DefectKind {

	/** A judge-owned fact the vocabulary requires is not recorded. */
	ABSENT,

	/** The fact is recorded in a form this version cannot read, and was ignored. */
	UNPARSEABLE,

	/**
	 * The fact carries a token this version does not define. The token is carried as the string
	 * it was rather than refused.
	 */
	UNKNOWN_VOCABULARY,

	/** Two recorded facts contradict each other, or a recorded fact contradicts the reading. */
	INCONSISTENT

}
