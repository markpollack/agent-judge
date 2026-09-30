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
 * @param relativePath file identity within the comparison
 * @param expectedFile reference file
 * @param actualFile evaluated file
 */
public record FileComparison(String relativePath, Path expectedFile, Path actualFile) {
	/** Require every comparison input. */
	/** Validate the required evidence values. */
	public FileComparison {
		Objects.requireNonNull(relativePath, "relativePath");
		Objects.requireNonNull(expectedFile, "expectedFile");
		Objects.requireNonNull(actualFile, "actualFile");
	}
}
