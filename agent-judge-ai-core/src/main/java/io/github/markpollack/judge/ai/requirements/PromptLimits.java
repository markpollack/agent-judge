/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.ai.requirements;

/**
 * The size limit on one generated requirement audit. A generated EARS or RFC 2119 judge or
 * roster jury sends its whole roster in one call, and refuses before calling when the
 * rendered prompt is larger than this limit: the result is an {@code ERROR} that states the
 * size, the limit and where the characters are, never a {@code FAIL}. Each generated
 * builder accepts its own limit through {@code maxPromptCharacters(int)}.
 *
 * @since 0.19.0
 */
public final class PromptLimits {

	/**
	 * The default limit, in characters per call.
	 *
	 * <p>
	 * Derivation: one call per roster. The largest real roster measured, a 36-criterion
	 * booking specification, renders its requirement headers, criteria and instructions in
	 * about 16,000 characters, and its whole specification is about 19,000. The judge reads
	 * the implementation with its tools from the workspace it is given, so the workspace
	 * slot is meant for a path, not for pasted source. 100,000 characters holds a roster
	 * several times that size with room for prepared evidence, while a prompt carrying a
	 * pasted code base (one measured at 1,286,385 characters, beyond a backend's
	 * 1,048,576-character limit) is refused before it costs a call.
	 */
	public static final int DEFAULT_MAX_PROMPT_CHARACTERS = 100_000;

	private PromptLimits() {
	}

	static int require(int maxPromptCharacters) {
		if (maxPromptCharacters < 1)
			throw new IllegalArgumentException("maxPromptCharacters must be positive, but was " + maxPromptCharacters);
		return maxPromptCharacters;
	}

}
