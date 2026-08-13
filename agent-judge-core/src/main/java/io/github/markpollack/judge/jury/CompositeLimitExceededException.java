/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

/** Internal refusal raised before a composite depth or attempt limit is exceeded. */
final class CompositeLimitExceededException extends RuntimeException {

	CompositeLimitExceededException(String message) {
		super(message);
	}

}
