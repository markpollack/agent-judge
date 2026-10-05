/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.verdict;

/** Internal refusal raised before a composite depth or attempt limit is exceeded. */
public final class CompositeLimitExceededException extends RuntimeException {

 /** Record a refused bound without pretending a child ran.
  * @param message violated bound
  */
	public CompositeLimitExceededException(String message) {
		super(message);
	}

}
