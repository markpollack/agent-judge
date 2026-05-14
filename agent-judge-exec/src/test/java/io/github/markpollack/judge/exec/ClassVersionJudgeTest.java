/*
 * Copyright 2024 Spring AI Community
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.markpollack.judge.exec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import io.github.markpollack.judge.context.ExecutionStatus;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ClassVersionJudge}.
 *
 * @author Mark Pollack
 * @since 0.9.0
 */
class ClassVersionJudgeTest {

	@TempDir
	Path workspace;

	private final ClassVersionJudge judge = new ClassVersionJudge();

	@Test
	void correctVersionReturnsPass() throws IOException {
		writeClassFile(workspace.resolve("target/classes/com/example/Foo.class"), 61);

		Judgment judgment = judge.judge(contextWithVersion(61));

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(judgment.checks()).hasSize(1);
		assertThat(judgment.checks().get(0).passed()).isTrue();
	}

	@Test
	void wrongVersionReturnsFail() throws IOException {
		writeClassFile(workspace.resolve("target/classes/com/example/Foo.class"), 55);

		Judgment judgment = judge.judge(contextWithVersion(61));

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(judgment.reasoning()).contains("55").contains("61");
		assertThat(judgment.checks()).hasSize(1);
		assertThat(judgment.checks().get(0).passed()).isFalse();
	}

	@Test
	void multipleFilesAllCorrect() throws IOException {
		writeClassFile(workspace.resolve("target/classes/com/example/Foo.class"), 61);
		writeClassFile(workspace.resolve("target/classes/com/example/Bar.class"), 61);

		Judgment judgment = judge.judge(contextWithVersion(61));

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(judgment.checks()).hasSize(2);
		assertThat(judgment.reasoning()).contains("2 .class files");
	}

	@Test
	void mixedVersionsReturnsFail() throws IOException {
		writeClassFile(workspace.resolve("target/classes/com/example/Good.class"), 61);
		writeClassFile(workspace.resolve("target/classes/com/example/Bad.class"), 52);

		Judgment judgment = judge.judge(contextWithVersion(61));

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(judgment.reasoning()).contains("1 of 2");
	}

	@Test
	void noClassFilesReturnsAbstain() throws IOException {
		Files.createDirectories(workspace.resolve("target/classes"));

		Judgment judgment = judge.judge(contextWithVersion(61));

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.ABSTAIN);
	}

	@Test
	void noTargetClassesDirReturnsAbstain() {
		Judgment judgment = judge.judge(contextWithVersion(61));

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(judgment.reasoning()).contains("No target/classes");
	}

	@Test
	void noMetadataReturnsAbstain() {
		Judgment judgment = judge.judge(contextWithMetadata(Map.of()));

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(judgment.reasoning()).contains("No targetClassVersion");
	}

	// ==================== Helpers ====================

	private JudgmentContext contextWithVersion(int version) {
		return contextWithMetadata(Map.of("targetClassVersion", version));
	}

	private JudgmentContext contextWithMetadata(Map<String, Object> metadata) {
		return JudgmentContext.builder()
			.goal("Test migration")
			.workspace(workspace)
			.agentOutput("output")
			.status(ExecutionStatus.SUCCESS)
			.startedAt(Instant.now())
			.executionTime(Duration.ofSeconds(1))
			.metadata(metadata)
			.build();
	}

	/**
	 * Write a minimal valid .class file with the given major version.
	 */
	static void writeClassFile(Path path, int majorVersion) throws IOException {
		Files.createDirectories(path.getParent());
		try (OutputStream os = Files.newOutputStream(path); DataOutputStream dos = new DataOutputStream(os)) {
			dos.writeInt(0xCAFEBABE); // magic
			dos.writeShort(0); // minor version
			dos.writeShort(majorVersion); // major version
		}
	}

}
