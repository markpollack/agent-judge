/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.fs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A path that leaves the workspace cannot be judged against it.
 *
 * <p>
 * The sibling of {@link FileExistsJudgeDegeneracyTest}. {@code FileContentJudge} carried the
 * same {@code workspace().resolve(filePath)} as {@code FileExistsJudge}, so it had the same
 * escape: {@code Path.resolve} returns its argument unchanged when that argument is
 * absolute. Fixing one and leaving the other is how a defect survives its own correction.
 */
class FileContentJudgeContainmentTest {

	@TempDir
	Path workspace;

	private Judgment judge(String path, String expected) {
		return new FileContentJudge(path, expected)
			.judge(JudgmentContext.builder().goal("g").workspace(workspace).build());
	}

	@Test
	@DisplayName("An absolute path outside the workspace errors rather than matching content")
	void absolutePathOutsideWorkspaceErrors() throws IOException {
		Path outside = Files.createTempDirectory("fcj-outside");
		Path planted = outside.resolve("planted.txt");
		Files.writeString(planted, "content the subject never produced");

		Judgment result = judge(planted.toString(), "content the subject never produced");

		assertThat(result.status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(result.reasoning()).contains("escapes the workspace");
	}

	@Test
	@DisplayName("A parent traversal out of the workspace errors")
	void parentTraversalErrors() throws IOException {
		Path outside = Files.createTempDirectory("fcj-outside");
		Files.writeString(outside.resolve("planted.txt"), "x");

		Judgment result = judge("../" + outside.getFileName() + "/planted.txt", "x");

		assertThat(result.status()).isEqualTo(JudgmentStatus.ERROR);
	}

	@Test
	@DisplayName("A file inside the workspace is unaffected")
	void containedPathStillEvaluates() throws IOException {
		Files.writeString(workspace.resolve("app.txt"), "hello");

		assertThat(judge("app.txt", "hello").status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(judge("app.txt", "goodbye").status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(judge("missing.txt", "hello").status()).isEqualTo(JudgmentStatus.FAIL);
	}

}
