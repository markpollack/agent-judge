/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.markpollack.judge.judgment.Check;
import io.github.markpollack.judge.judgment.Judgment;

/**
 * Judge that verifies compiled {@code .class} files have the expected major version.
 *
 * <p>
 * Walks {@code target/classes/} recursively, reads bytes 6-7 of each {@code .class} file
 * (the major version per JVM spec §4.1), and compares against the expected version
 * supplied to the constructor.
 * </p>
 *
 * <p>
 * Common major versions: Java 8 = 52, Java 11 = 55, Java 17 = 61, Java 21 = 65.
 * </p>
 *
 * @author Mark Pollack
 * @since 0.9.0
 */
public class ClassVersionJudge extends DeterministicJudge<Path> {

	private static final Logger logger = LoggerFactory.getLogger(ClassVersionJudge.class);

	private static final int CLASS_MAGIC = 0xCAFEBABE;

	/** Create a class-file version judge. */
	private final int expectedVersion;

	/**
	 * Require the given JVM class-file major version.
	 * @param expectedVersion required JVM major version
	 */
	public ClassVersionJudge(int expectedVersion) {
		super("ClassVersionJudge", "Verifies .class file major versions match target Java version");
		if (expectedVersion < 45 || expectedVersion > 65535)
			throw new IllegalArgumentException("Invalid class major version");
		this.expectedVersion = expectedVersion;
	}

	@Override
	public Judgment judge(Path workspace) {
		Path classesDir = workspace.resolve("target/classes");
		if (!Files.isDirectory(classesDir)) {
			return Judgment.abstain("No target/classes directory found");
		}

		List<Path> classFiles;
		try (Stream<Path> walk = Files.walk(classesDir)) {
			classFiles = walk.filter(p -> p.toString().endsWith(".class")).toList();
		}
		catch (IOException ex) {
			logger.error("Failed to walk target/classes at {}", classesDir, ex);
			return Judgment.error("Failed to walk target/classes: " + ex.getMessage());
		}

		if (classFiles.isEmpty()) {
			return Judgment.abstain("No .class files found in target/classes");
		}

		List<Check> checks = new ArrayList<>();
		List<String> mismatches = new ArrayList<>();
		List<String> readErrors = new ArrayList<>();

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
				checks.add(new Check(relativeName, Judgment.error("Failed to read: " + ex.getMessage())));
				readErrors.add(relativeName + " (read error: " + ex.getMessage() + ")");
			}
		}

		if (!readErrors.isEmpty()) {
			return Judgment.builder()
				.error()
				.reasoning(String.format(
						"Could not determine the version of %d of %d .class files: %s; %d known mismatches",
						readErrors.size(), classFiles.size(), String.join(", ", readErrors), mismatches.size()))
				.checks(checks)
				.build();
		}

		boolean pass = mismatches.isEmpty();
		String reasoning = pass
				? String.format("All %d .class files have major version %d", classFiles.size(), expectedVersion)
				: String.format("%d of %d .class files have wrong version: %s", mismatches.size(), classFiles.size(),
						String.join(", ", mismatches));

		return (pass ? Judgment.builder().pass() : Judgment.builder().fail()).reasoning(reasoning)
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
