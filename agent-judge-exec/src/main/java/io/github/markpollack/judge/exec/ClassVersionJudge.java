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

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import io.github.markpollack.judge.DeterministicJudge;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.Check;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;
import io.github.markpollack.judge.score.BooleanScore;

/**
 * Judge that verifies compiled {@code .class} files have the expected major version.
 *
 * <p>
 * Walks {@code target/classes/} recursively, reads bytes 6-7 of each {@code .class} file
 * (the major version per JVM spec §4.1), and compares against the expected version from
 * {@code metadata("targetClassVersion")}.
 * </p>
 *
 * <p>
 * Common major versions: Java 8 = 52, Java 11 = 55, Java 17 = 61, Java 21 = 65.
 * </p>
 *
 * @author Mark Pollack
 * @since 0.9.0
 */
public class ClassVersionJudge extends DeterministicJudge {

	private static final int CLASS_MAGIC = 0xCAFEBABE;

	public ClassVersionJudge() {
		super("ClassVersionJudge", "Verifies .class file major versions match target Java version");
	}

	@Override
	public Judgment judge(JudgmentContext context) {
		Object targetVersionObj = context.metadata().get("targetClassVersion");
		if (targetVersionObj == null) {
			return Judgment.abstain("No targetClassVersion in metadata");
		}

		int expectedVersion;
		if (targetVersionObj instanceof Integer i) {
			expectedVersion = i;
		}
		else if (targetVersionObj instanceof Number n) {
			expectedVersion = n.intValue();
		}
		else {
			return Judgment.abstain("targetClassVersion is not a number: " + targetVersionObj.getClass().getName());
		}

		Path classesDir = context.workspace().resolve("target/classes");
		if (!Files.isDirectory(classesDir)) {
			return Judgment.abstain("No target/classes directory found");
		}

		List<Path> classFiles;
		try (Stream<Path> walk = Files.walk(classesDir)) {
			classFiles = walk.filter(p -> p.toString().endsWith(".class")).toList();
		}
		catch (IOException ex) {
			return Judgment.error("Failed to walk target/classes: " + ex.getMessage(), ex);
		}

		if (classFiles.isEmpty()) {
			return Judgment.abstain("No .class files found in target/classes");
		}

		List<Check> checks = new ArrayList<>();
		List<String> mismatches = new ArrayList<>();

		for (Path classFile : classFiles) {
			String relativeName = classesDir.relativize(classFile).toString();
			try {
				int majorVersion = readMajorVersion(classFile);
				if (majorVersion == expectedVersion) {
					checks.add(Check.pass(relativeName,
							"Version " + majorVersion + " matches expected " + expectedVersion));
				}
				else {
					checks.add(Check.fail(relativeName,
							"Version " + majorVersion + " does not match expected " + expectedVersion));
					mismatches.add(relativeName + " (found " + majorVersion + ", expected " + expectedVersion + ")");
				}
			}
			catch (IOException ex) {
				checks.add(Check.fail(relativeName, "Failed to read: " + ex.getMessage()));
				mismatches.add(relativeName + " (read error: " + ex.getMessage() + ")");
			}
		}

		boolean pass = mismatches.isEmpty();
		String reasoning = pass
				? String.format("All %d .class files have major version %d", classFiles.size(), expectedVersion)
				: String.format("%d of %d .class files have wrong version: %s", mismatches.size(), classFiles.size(),
						String.join(", ", mismatches));

		return Judgment.builder()
			.score(new BooleanScore(pass))
			.status(pass ? JudgmentStatus.PASS : JudgmentStatus.FAIL)
			.reasoning(reasoning)
			.checks(checks)
			.build();
	}

	/**
	 * Read the major version from a .class file (bytes 6-7 per JVM spec §4.1).
	 * @param classFile path to the .class file
	 * @return the major version number
	 * @throws IOException if the file cannot be read or has invalid format
	 */
	static int readMajorVersion(Path classFile) throws IOException {
		try (InputStream is = Files.newInputStream(classFile); DataInputStream dis = new DataInputStream(is)) {
			int magic = dis.readInt();
			if (magic != CLASS_MAGIC) {
				throw new IOException("Not a valid .class file: bad magic number 0x" + Integer.toHexString(magic));
			}
			dis.readUnsignedShort(); // minor version
			return dis.readUnsignedShort(); // major version
		}
	}

}
