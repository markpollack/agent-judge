/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury.interpretation;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.github.markpollack.judge.jury.interpretation.Fixtures.BUD_EVAL_7E423DE9;
import static io.github.markpollack.judge.jury.interpretation.Fixtures.EXAMPLE_ONE;
import static io.github.markpollack.judge.jury.interpretation.Fixtures.EXAMPLE_TWO;
import static io.github.markpollack.judge.jury.interpretation.Fixtures.MAPPER;
import static io.github.markpollack.judge.jury.interpretation.Fixtures.stored;
import static org.assertj.core.api.Assertions.assertThat;

/** A12: the serialised shape, and the consumer's {@code describe} over it. */
@DisplayName("Serialisation")
class InterpretationSerializationTest {

	private static JsonNode json(String fixture) {
		try {
			return MAPPER.readTree(MAPPER.writeValueAsString(Verdicts.interpret(stored(fixture))));
		}
		catch (Exception ex) {
			throw new AssertionError(ex);
		}
	}

	/**
	 * §7.4's {@code describe}, transliterated: it reads the interpretation node only and never
	 * touches the stored verdict.
	 */
	private static List<String> describe(JsonNode i) {
		List<String> lines = new ArrayList<>();
		JsonNode d = i.get("decidedBy");
		lines.add(i.get("reading").asText() + " (" + i.get("readingSupport").asText() + "); decided by "
				+ (d.isNull() ? "not recorded" : d.get("stage").asText()));
		List<JsonNode> stages = new ArrayList<>();
		stages.add(i.get("root"));
		i.get("stages").forEach(stages::add);
		for (JsonNode s : stages) {
			List<String> path = new ArrayList<>();
			s.get("path").forEach(p -> path.add(p.asText()));
			String where = path.isEmpty() ? "<root>" : String.join("/", path);
			lines.add("  " + where + " [" + (s.get("policy").isNull() ? "-" : s.get("policy").asText()) + "] "
					+ s.get("status").asText() + (s.get("reasonCode").isNull() ? "" : " " + s.get("reasonCode").asText())
					+ ": " + s.get("reasoning").asText());
			for (JsonNode j : s.get("judges")) {
				lines.add("    seat " + j.get("position").asInt() + " " + j.get("name").asText() + ": "
						+ j.get("status").asText() + (j.get("reasonCode").isNull() ? "" : " (" + j.get("reasonCode").asText() + ")")
						+ " — " + j.get("reasoning").asText());
				for (JsonNode c : j.get("checks")) {
					lines.add("      [" + (c.get("passed").asBoolean() ? "x" : " ") + "] " + c.get("name").asText() + ": "
							+ c.get("detail").asText());
				}
			}
		}
		for (JsonNode x : i.get("defects")) {
			lines.add("  ! " + x.get("kind").asText() + " " + x.get("path").asText() + "." + x.get("field").asText() + ": "
					+ x.get("note").asText());
		}
		lines.add("  " + i.get("summary").asText());
		return lines;
	}

	@Test
	@DisplayName("the block has the §1.1 members in order, with nulls written explicitly")
	void theBlockShape() {
		JsonNode i = json(EXAMPLE_ONE);

		assertThat(i.fieldNames()).toIterable()
			.containsExactly("schemaVersion", "sourceVersion", "reading", "readingSupport", "decidedBy", "root", "stages",
					"defects", "summary");
		assertThat(i.get("decidedBy").toString()).isEqualTo("{\"stage\":\"structure\",\"path\":[\"structure\"],\"basis\":\"tier_outcome\"}");
		assertThat(i.get("stages").get(0).fieldNames()).toIterable()
			.containsExactly("stage", "path", "relation", "policy", "disposition", "reason", "failure", "usedByParent",
					"status", "reasonCode", "reasoning", "evidence", "judges");
		assertThat(i.get("stages").get(0).get("reasonCode").isNull()).as("an absent reason code is an explicit null").isTrue();
		assertThat(i.get("stages").get(0).get("judges").get(0).fieldNames()).toIterable()
			.containsExactly("position", "name", "keySource", "status", "reasonCode", "score", "reasoning", "checks");
		assertThat(i.get("stages").get(0).get("evidence").get("passCount").asInt()).isEqualTo(1);
		assertThat(i.get("stages").get(0).get("evidence").get("errorCodeCounts").toString()).isEqualTo("{}");
		assertThat(i.get("defects").toString()).isEqualTo("[]");
	}

	@Test
	@DisplayName("a null decidedBy, a null keySource and a null evidence are written as nulls; a null scoreScale is omitted")
	void nullsAreExplicitExceptTheScale() {
		JsonNode two = json(EXAMPLE_TWO);

		assertThat(two.get("decidedBy").isNull()).isTrue();
		assertThat(two.get("root").get("evidence").isNull()).isTrue();
		assertThat(two.get("root").get("judges").get(0).get("keySource").isNull()).isTrue();
		assertThat(two.get("root").get("judges").get(0).has("scoreScale")).isFalse();
		assertThat(two.get("stages").get(0).get("stage").isNull()).isTrue();

		JsonNode graded = json(BUD_EVAL_7E423DE9).get("root").get("judges").get(0);
		assertThat(graded.get("scoreScale").toString()).isEqualTo("{\"min\":0.0,\"max\":1.0}");
		assertThat(graded.get("score").asDouble()).isEqualTo(0.7777777777777778);
	}

	@Test
	@DisplayName("A12: describe prints every recorded check without touching the stored verdict")
	void describePrintsEveryCheck() {
		JsonNode stored = Fixtures.readTree("/interpretation/stored/" + BUD_EVAL_7E423DE9 + ".verdict.json");
		int recorded = stored.findValues("checks").stream().mapToInt(JsonNode::size).sum();
		int distinctCheckNodes = stored.findParents("checks").size();
		assertThat(recorded).isPositive();

		List<String> lines = describe(json(BUD_EVAL_7E423DE9));

		long printed = lines.stream().filter(line -> line.startsWith("      [")).count();
		// The root's judge is one of the judgments; sub-verdict judges are the rest. Every check
		// recorded on a judgment that is seated somewhere is printed exactly once per seat.
		assertThat(printed).as("%d judgments carry checks", distinctCheckNodes).isEqualTo(recordedOnSeats(stored));
		assertThat(lines.get(0)).startsWith("ACCEPTED (UNDETERMINED); decided by not recorded");
		assertThat(lines).anyMatch(line -> line.contains("[x] error_handling: error_handling=3/3"));
	}

	/** Checks recorded on judgments that the interpretation seats: every {@code individualByName} entry. */
	private static int recordedOnSeats(JsonNode verdict) {
		int total = 0;
		for (JsonNode judgment : verdict.get("individualByName")) {
			total += judgment.get("checks").size();
		}
		for (JsonNode sub : verdict.get("subVerdicts")) {
			total += recordedOnSeats(sub);
		}
		return total;
	}

	@Test
	@DisplayName("A12: describe on example one prints both judges' reasons")
	void describeExampleOne() {
		List<String> lines = describe(json(EXAMPLE_ONE));

		assertThat(lines.get(0)).isEqualTo("UNDECIDED (SUPPORTED); decided by structure");
		assertThat(lines).contains("    seat 0 structure:ddd-review.md: pass — report present",
				"    seat 1 reportStructure: fail — report has no bounded contexts");
		assertThat(lines).anyMatch(line -> line.startsWith("  structure [REJECT_ON_ANY_FAIL] abstain: No consensus"));
	}

}
