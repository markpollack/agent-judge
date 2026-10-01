/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.serialization.diagnostics;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.judgment.JudgmentReasonCode;
import io.github.markpollack.judge.judgment.JudgmentStatus;

import static io.github.markpollack.judge.serialization.diagnostics.Fixtures.BOUNDARY_GOLDEN;
import static io.github.markpollack.judge.serialization.diagnostics.Fixtures.COMPOSITE_GOLDEN;
import static io.github.markpollack.judge.serialization.diagnostics.Fixtures.EXAMPLE_ONE;
import static io.github.markpollack.judge.serialization.diagnostics.Fixtures.EXAMPLE_TWO;
import static io.github.markpollack.judge.serialization.diagnostics.Fixtures.stored;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A14: the summary is deterministic, and it agrees with the fields.
 *
 * <p>
 * The agreement test is the oracle for {@link Summaries}: every stage name, judge name,
 * status, reason code, reading, support value and deciding stage the summary mentions is
 * present in the fields, and every one present in the fields is mentioned. Names are
 * mentioned in single quotes, statuses as a fixed phrase, reason codes in square
 * brackets, and recorded reasoning is quoted verbatim in parentheses — so the test strips
 * the reasoning first and reads the rest.
 */
@DisplayName("Summaries")
class SummariesTest {

	@Test
	void versionSummaryDistinguishesCurrentFromUnsupportedSchemas() {
		var current = StoredVerdicts
			.interpret(Verdict.single("known", io.github.markpollack.judge.judgment.Judgment.pass("known")));
		assertThat(current.readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);
		assertThat(current.summary()).contains("Source: explicit version-5").doesNotContain("unsupported version");
		for (int version : List.of(2, 4, 99)) {
			var unsupported = StoredVerdicts.interpret(Map.of("schemaVersion", version));
			assertThat(unsupported.readingSupport()).isEqualTo(ReadingSupport.UNDETERMINED);
			assertThat(unsupported.summary()).contains("Source: unsupported version " + version + ".");
		}
	}

	private static final Pattern QUOTED = Pattern.compile("'([^']*)'");

	/**
	 * A reason code is rendered as {@code [code]} after whitespace; a defect path's own
	 * brackets follow a name.
	 */
	private static final Pattern BRACKETED = Pattern.compile("(?<=\\s)\\[([a-z_]+)]");

	static Stream<org.junit.jupiter.params.provider.Arguments> interpretations() {
		List<org.junit.jupiter.params.provider.Arguments> all = new ArrayList<>();
		for (String name : Fixtures.ALL_STORED) {
			all.add(org.junit.jupiter.params.provider.Arguments.of(name, StoredVerdicts.interpret(stored(name))));
		}
		for (String resource : List.of(COMPOSITE_GOLDEN, BOUNDARY_GOLDEN)) {
			all.add(org.junit.jupiter.params.provider.Arguments.of(resource,
					StoredVerdicts.interpret(Fixtures.readMap(resource))));
		}
		Verdict d1 = LiveFixturesForSummaries.childUndecidedRejection();
		all.add(org.junit.jupiter.params.provider.Arguments.of("child-undecided D1", StoredVerdicts.interpret(d1)));
		all.add(org.junit.jupiter.params.provider.Arguments.of("nested D1",
				StoredVerdicts.interpret(LiveFixturesForSummaries.nestedRejection())));
		all.add(org.junit.jupiter.params.provider.Arguments.of("propagated error",
				StoredVerdicts.interpret(LiveFixturesForSummaries.propagatedError())));
		all.add(org.junit.jupiter.params.provider.Arguments.of("wrong shape",
				StoredVerdicts.interpret(Map.of("x", 1))));
		return all.stream();
	}

	/**
	 * The phrase the summary uses for each status; the test's copy is the oracle for the
	 * generator's.
	 */
	static String phraseFor(String status) {
		return switch (status) {
			case "pass" -> "passed";
			case "fail" -> "failed";
			case "abstain" -> "abstained";
			case "not_applicable" -> "was not applicable";
			case "error" -> "errored";
			default -> "recorded status " + status;
		};
	}

	private static boolean mentions(String text, String phrase) {
		return Pattern.compile("(?<![\\w])" + Pattern.quote(phrase) + "(?![\\w])").matcher(text).find();
	}

	private static String withoutReasoning(StoredReading interpretation) {
		Set<String> reasons = new LinkedHashSet<>();
		for (Stage stage : allStages(interpretation)) {
			if (stage.reasoning() != null && !stage.reasoning().isEmpty()) {
				reasons.add(stage.reasoning());
			}
			for (JudgeSeat judge : stage.judges()) {
				if (!judge.reasoning().isEmpty()) {
					reasons.add(judge.reasoning());
				}
			}
		}
		String text = interpretation.summary();
		for (String reason : reasons.stream().sorted((a, b) -> b.length() - a.length()).toList()) {
			text = text.replace(" (" + reason + ")", "");
		}
		return text;
	}

	private static List<Stage> allStages(StoredReading interpretation) {
		List<Stage> all = new ArrayList<>();
		all.add(interpretation.root());
		all.addAll(interpretation.stages());
		return all;
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("interpretations")
	@DisplayName("A14: same input, same text; and the stored summary is what Summaries.of produces")
	void deterministic(String name, StoredReading interpretation) {
		assertThat(Summaries.of(interpretation)).isEqualTo(Summaries.of(interpretation));
		assertThat(interpretation.summary()).isEqualTo(Summaries.of(interpretation));
		assertThat(interpretation.summary()).isNotBlank();
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("interpretations")
	@DisplayName("A14: every name, status, reason code, reading, support value and deciding stage agrees with the fields")
	void agreesWithTheFields(String name, StoredReading interpretation) {
		String text = withoutReasoning(interpretation);

		Set<String> fieldNames = new LinkedHashSet<>();
		Set<String> fieldStatuses = new LinkedHashSet<>();
		Set<String> fieldCodes = new LinkedHashSet<>();
		for (Stage stage : allStages(interpretation)) {
			if (stage.stage() != null) {
				fieldNames.add(stage.stage());
			}
			fieldNames.addAll(stage.path());
			if (stage.status() != null) {
				fieldStatuses.add(stage.status());
			}
			if (stage.reasonCode() != null) {
				fieldCodes.add(stage.reasonCode());
			}
			for (JudgeSeat judge : stage.judges()) {
				fieldNames.add(judge.name());
				if (judge.status() != null) {
					fieldStatuses.add(judge.status());
				}
				if (judge.reasonCode() != null) {
					fieldCodes.add(judge.reasonCode());
				}
			}
		}
		if (interpretation.decidedBy() != null) {
			fieldNames.add(interpretation.decidedBy().stage());
			fieldNames.addAll(interpretation.decidedBy().path());
		}

		Set<String> mentionedNames = new LinkedHashSet<>();
		Matcher quoted = QUOTED.matcher(text);
		while (quoted.find()) {
			mentionedNames.add(quoted.group(1));
		}
		assertThat(mentionedNames).as("names the summary mentions vs the fields").isEqualTo(fieldNames);

		Set<String> mentionedCodes = new LinkedHashSet<>();
		Matcher bracketed = BRACKETED.matcher(text);
		while (bracketed.find()) {
			mentionedCodes.add(bracketed.group(1));
		}
		assertThat(mentionedCodes).as("reason codes the summary mentions vs the fields").isEqualTo(fieldCodes);

		for (JudgmentStatus status : JudgmentStatus.values()) {
			String token = status.wireName();
			assertThat(mentions(text, phraseFor(token))).as("status %s mentioned iff present", token)
				.isEqualTo(fieldStatuses.contains(token));
		}
		for (String unknown : fieldStatuses) {
			assertThat(mentions(text, phraseFor(unknown))).as("status %s", unknown).isTrue();
		}
		for (RequirementOutcome reading : RequirementOutcome.values()) {
			assertThat(mentions(text, reading.name())).as("reading %s mentioned iff it is the reading", reading)
				.isEqualTo(reading == interpretation.outcome());
		}
		for (ReadingSupport support : ReadingSupport.values()) {
			assertThat(mentions(text, support.name())).as("support %s mentioned iff it is the support", support)
				.isEqualTo(support == interpretation.readingSupport());
		}
		for (JudgmentReasonCode code : JudgmentReasonCode.values()) {
			assertThat(mentionedCodes.contains(code.wireName())).isEqualTo(fieldCodes.contains(code.wireName()));
		}
	}

	@Test
	@DisplayName("the summary of example one is pinned")
	void exampleOneGolden() {
		assertThat(StoredVerdicts.interpret(stored(EXAMPLE_ONE)).summary()).isEqualTo(SummaryGoldens.EXAMPLE_ONE);
	}

	@Test
	@DisplayName("the summary of example two is pinned")
	void exampleTwoGolden() {
		assertThat(StoredVerdicts.interpret(stored(EXAMPLE_TWO)).summary()).isEqualTo(SummaryGoldens.EXAMPLE_TWO);
	}

}
