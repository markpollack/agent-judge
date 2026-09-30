/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.file;

import java.nio.file.Path;
import java.util.Objects;

/**
 * The two typed subjects of a filesystem comparison.
 *
 * @param expectedDirectory reference directory
 * @param actualDirectory evaluated directory
 */
public record DirectoryComparison(Path expectedDirectory, Path actualDirectory) {
	/** Require every comparison input. */
	/** Validate the required evidence values. */
	public DirectoryComparison {
		Objects.requireNonNull(expectedDirectory, "expectedDirectory");
		Objects.requireNonNull(actualDirectory, "actualDirectory");
	}
}
