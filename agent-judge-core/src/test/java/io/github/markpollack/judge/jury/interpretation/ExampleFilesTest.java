/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury.interpretation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Generates the two example files: the full stored item with the original verdict, unchanged,
 * beside its {@code interpretation}.
 *
 * <p>Example one comes from the generated 0.17 cascade copied into the test resources. Example
 * two comes from the {@code bud-ddd} archive on this machine ({@code -Daj26.example.two} names
 * the run file) and is skipped where that archive is absent. Both are written under
 * {@code -Daj26.examples.dir}, by default {@code target/aj26-examples} at the reactor root.
 */
@DisplayName("The generated example files")
class ExampleFilesTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final String EXAMPLE_TWO_SOURCE = System.getProperty("user.home")
			+ "/tuvium/projects/bud-ddd-optimize-experiment/experiments/runs/bud-ddd/"
			+ "9b68576e-57f8-41e6-af1d-b1610752455d.json";

	private static Path outputDir() throws Exception {
		Path dir = Path.of(System.getProperty("aj26.examples.dir",
				Path.of(System.getProperty("basedir", "."), "..", "target", "aj26-examples").toString()));
		Files.createDirectories(dir);
		return dir.toAbsolutePath().normalize();
	}

	/** Every item with a verdict gains an {@code interpretation} member directly after it. */
	private static ObjectNode withInterpretations(JsonNode run) {
		ObjectNode out = run.deepCopy();
		ArrayNode items = (ArrayNode) out.get("items");
		for (int index = 0; index < items.size(); index++) {
			ObjectNode item = (ObjectNode) items.get(index);
			JsonNode verdict = item.get("verdict");
			if (verdict == null || !verdict.isObject()) {
				continue;
			}
			Interpretation interpretation = Verdicts.interpret(MAPPER.convertValue(verdict, Fixtures.MAP));
			ObjectNode rebuilt = MAPPER.createObjectNode();
			Iterator<Map.Entry<String, JsonNode>> fields = item.fields();
			while (fields.hasNext()) {
				Map.Entry<String, JsonNode> field = fields.next();
				rebuilt.set(field.getKey(), field.getValue());
				if (field.getKey().equals("verdict")) {
					rebuilt.set("interpretation", MAPPER.valueToTree(interpretation));
				}
			}
			items.set(index, rebuilt);
		}
		return out;
	}

	private static Path write(JsonNode run, String name) throws Exception {
		Path file = outputDir().resolve(name);
		Files.writeString(file, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(run) + "\n",
				StandardCharsets.UTF_8);
		return file;
	}

	@Test
	@DisplayName("example one: the 0.17 cascade, original verdict beside its interpretation")
	void exampleOne() throws Exception {
		JsonNode run = Fixtures.readTree("/interpretation/examples/example-0-17-cascade.json");
		ObjectNode generated = withInterpretations(run);

		Path file = write(generated, "example-one-0-17-cascade.json");

		JsonNode written = MAPPER.readTree(file.toFile());
		JsonNode item = written.get("items").get(0);
		assertThat(item.get("verdict")).as("the original verdict is unchanged").isEqualTo(run.get("items").get(0).get("verdict"));
		assertThat(item.get("interpretation").get("reading").asText()).isEqualTo("UNDECIDED");
		assertThat(item.get("interpretation").get("decidedBy").get("stage").asText()).isEqualTo("structure");
		assertThat(item.fieldNames()).toIterable().containsSequence("verdict", "interpretation");
	}

	@Test
	@DisplayName("example two: the 0.13 record, original verdict beside its interpretation")
	void exampleTwo() throws Exception {
		Path source = Path.of(System.getProperty("aj26.example.two", EXAMPLE_TWO_SOURCE));
		assumeTrue(Files.isRegularFile(source), "the bud-ddd archive is not on this machine: " + source);
		JsonNode run = MAPPER.readTree(source.toFile());
		ObjectNode generated = withInterpretations(run);

		Path file = write(generated, "example-two-0-13-review-derived-brief.json");

		JsonNode written = MAPPER.readTree(file.toFile());
		JsonNode item = written.get("items").get(0);
		assertThat(item.get("itemSlug").asText()).isEqualTo("review-derived-brief:spring-batch");
		assertThat(item.get("verdict")).as("the original verdict is unchanged").isEqualTo(run.get("items").get(0).get("verdict"));
		assertThat(item.get("interpretation").get("reading").asText()).isEqualTo("REJECTED");
		assertThat(item.get("interpretation").get("decidedBy").isNull()).isTrue();
		assertThat(item.get("interpretation").get("sourceVersion").asInt()).isEqualTo(0);
		assertThat(item.get("interpretation").get("defects").size()).isEqualTo(10);
	}

}
