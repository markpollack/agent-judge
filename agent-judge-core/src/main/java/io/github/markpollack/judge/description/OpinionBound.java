/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.description;

/** Construction knowledge about root routing opinions, independent of call count. */
public enum OpinionBound {

	/** Structure guarantees no root opinions. */
	KNOWN_NONE,
	/** Structure may produce root opinions. */
	MAY,
	/** Custom structure has not declared an opinion bound. */
	UNKNOWN

}
