/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.consumer;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.description.JuryDescription;
import io.github.markpollack.judge.result.Judgment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R5: the same jury describes to the same bytes in separate JVMs, so a consumer can hash the
 * description.
 *
 * <p>
 * Each run is a fresh {@code java} process on this test's classpath. Hidden-class addresses and
 * {@code Map.of} iteration salts are chosen per process, so agreement between processes is the
 * property; agreement within one process would prove nothing.
 * </p>
 */
@DisplayName("A jury description is byte-stable across JVM runs")
class CrossJvmDescriptionStabilityTest {

	private static final String GOLDEN = "/description/jury-description-v" + JuryDescription.DESCRIPTION_VERSION
			+ ".json";

	@Test
	void theSameJuryDescribesToIdenticalBytesInSeparateJvms(@TempDir Path directory) throws Exception {
		byte[] first = describeInFreshJvm(directory, "first");
		byte[] second = describeInFreshJvm(directory, "second");
		byte[] inThisJvm = DescriptionFixture.describe();

		assertThat(second).as("a second JVM").isEqualTo(first);
		assertThat(inThisJvm).as("the test JVM").isEqualTo(first);

		String json = new String(first, StandardCharsets.UTF_8);
		assertThat(json).doesNotContain("$$Lambda")
			.doesNotContain("/0x")
			.contains("{\"form\":\"HIDDEN\"}")
			.contains("{\"form\":\"ANONYMOUS\",\"enclosingClassName\":\"" + DescriptionFixture.class.getName() + "\"}")
			.contains("\"values\":{\"criteria\":[\"correct\",\"complete\"],\"levels\":{\"high\":1.0,\"low\":0.0,"
					+ "\"mid\":0.5},\"passMark\":0.75,\"strict\":true,\"version\":3}");
	}

	@Test
	void theFixtureMatchesThePinnedFormatForThisDescriptionVersion() throws Exception {
		String golden;
		try (InputStream resource = getClass().getResourceAsStream(GOLDEN)) {
			assertThat(resource).as("a golden description for descriptionVersion %d", JuryDescription.DESCRIPTION_VERSION)
				.isNotNull();
			golden = new String(resource.readAllBytes(), StandardCharsets.UTF_8).strip();
		}

		assertThat(new String(DescriptionFixture.describe(), StandardCharsets.UTF_8))
			.as("a difference is either a derivation regression or a format change; a format change increments "
					+ "JuryDescription.DESCRIPTION_VERSION and adds a new golden file")
			.isEqualTo(golden);
	}

	@Test
	void theRuntimeNamesTheDescriptionAvoidsAreReallyUnstable() {
		Judge lambda = ctx -> Judgment.pass("probe");

		assertThat(lambda.getClass().getName()).contains("$$Lambda").contains("/0x");
	}

	private static byte[] describeInFreshJvm(Path directory, String run) throws Exception {
		Path output = directory.resolve(run + ".json");
		Path log = directory.resolve(run + ".log");
		String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
		Process process = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
				DescriptionFixture.class.getName(), output.toString())
			.redirectErrorStream(true)
			.redirectOutput(log.toFile())
			.start();

		boolean finished = process.waitFor(2, TimeUnit.MINUTES);
		if (!finished) {
			process.destroyForcibly();
		}
		assertThat(finished).as("the %s JVM finished", run).isTrue();
		assertThat(process.exitValue()).as("the %s JVM exit status; its output was:%n%s", run, Files.readString(log))
			.isZero();
		return Files.readAllBytes(output);
	}

}
