/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.fs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Inputs on which this judge cannot fail.
 *
 * <p>
 * Degeneracy suite item (a): can it return FAIL at all? On a genuinely absent file, yes —
 * that is covered by {@link FileExistsJudgeTest}. These are the inputs where it returns
 * PASS having established nothing, which is the failure mode that matters for a judge:
 * a broken test fails closed and is loud, a broken judge fails open and is plausible.
 *
 * <p>
 * This judge is used ten times across four experiment repositories, always over a
 * configured {@code expectedPaths} list, so every one of these is reachable by a
 * mis-entered path rather than by an attack. One is live today: a catalogue entry of
 * {@code "src/main/java"} names a directory Maven creates in every scaffold, so that
 * check passes before the agent under test does anything at all.
 */
class FileExistsJudgeDegeneracyTest {

	@TempDir
	Path workspace;

	private Judgment judge(String path) {
		return new FileExistsJudge(path).judge(JudgmentContext.builder().goal("g").workspace(workspace).build());
	}

	@Test
	@DisplayName("An empty path does not pass — it resolves to the workspace, which always exists")
	void emptyPathIsNotAFile() {
		assertThat(judge("").status()).isNotEqualTo(JudgmentStatus.PASS);
	}

	@Test
	@DisplayName("A dot path does not pass, for the same reason")
	void dotPathIsNotAFile() {
		assertThat(judge(".").status()).isNotEqualTo(JudgmentStatus.PASS);
	}

	@Test
	@DisplayName("A directory does not satisfy a FILE existence check")
	void directoryIsNotAFile() throws IOException {
		Files.createDirectories(workspace.resolve("src/main/java"));

		// The live case. Maven creates this in every scaffold, so a catalogue listing it
		// as an expected path adds a judge to the roster that can never fail.
		assertThat(judge("src/main/java").status()).isNotEqualTo(JudgmentStatus.PASS);
	}

	@Test
	@DisplayName("An absolute path outside the workspace does not pass")
	void absolutePathOutsideWorkspaceIsRefused() throws IOException {
		Path outside = Files.createTempDirectory("fej-outside");
		Files.writeString(outside.resolve("secret.txt"), "not produced by the agent");

		// resolve() with an absolute argument returns the argument, so the judge silently
		// leaves the workspace and can pass on a file the subject never created.
		assertThat(judge(outside.resolve("secret.txt").toString()).status()).isNotEqualTo(JudgmentStatus.PASS);
	}

	@Test
	@DisplayName("A parent traversal out of the workspace does not pass")
	void parentTraversalIsRefused() throws IOException {
		Path outside = Files.createTempDirectory("fej-outside");
		Files.writeString(outside.resolve("secret.txt"), "not produced by the agent");

		String escape = "../" + outside.getFileName() + "/secret.txt";

		assertThat(judge(escape).status()).isNotEqualTo(JudgmentStatus.PASS);
	}

	@Test
	@DisplayName("A real file in the workspace still passes")
	void theHappyPathIsUnchanged() throws IOException {
		Files.writeString(workspace.resolve("Application.java"), "class Application {}");

		assertThat(judge("Application.java").status()).isEqualTo(JudgmentStatus.PASS);
	}

}
