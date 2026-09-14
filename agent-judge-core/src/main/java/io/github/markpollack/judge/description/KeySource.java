/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.description;

/**
 * Where a seat's verdict key came from.
 *
 * <p>
 * A verdict key is the name a judge's judgment is stored under in
 * {@link io.github.markpollack.judge.jury.Verdict#individualByName()}. It is a join key, and
 * only a {@link #DECLARED} key is also an identity.
 * </p>
 *
 * @author Mark Pollack
 * @since 0.17.0
 */
public enum KeySource {

	/** The judge declared this name through its metadata. */
	DECLARED("DECLARED"),

	/**
	 * The judge's name collided with an earlier seat's, so {@code Juries.fromJudges} added a
	 * {@code -2}, {@code -3}, ... suffix. The key depends on the order of the judges.
	 */
	DEDUPLICATED("DEDUPLICATED"),

	/**
	 * The judge declared no name, so the key is {@code "Judge#" + (position + 1)}. It
	 * identifies a position, not a judge: inserting a judge above it changes the key.
	 */
	POSITIONAL("POSITIONAL");

	private final String wireName;

	KeySource(String wireName) {
		this.wireName = wireName;
	}

	/**
	 * The stable token used in the portable form.
	 * @return the wire token
	 */
	public String wireName() {
		return wireName;
	}

}
