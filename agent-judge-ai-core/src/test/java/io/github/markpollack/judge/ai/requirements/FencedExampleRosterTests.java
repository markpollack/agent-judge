/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.ai.requirements;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An example inside a fenced code block is documentation, not a requirement. Fences nest by
 * length and character the way CommonMark says they do, so a four-backtick fence holds a
 * three-backtick example, a shorter line does not close a longer fence, and a backtick line
 * does not close a tilde fence. Each test asserts the exact roster and the exact diagnostics.
 */
class FencedExampleRosterTests {

	@TempDir
	Path directory;

	private static final String REAL_RULE_1 = """
			### RULE-1
			**MUST** be a real rule before the example.
			**Reason:** real
			""";

	private static final String REAL_RULE_2 = """
			### RULE-2
			**MUST** be a real rule after the example.
			**Reason:** real
			""";

	private static final String EXAMPLE_RULE = """
			### RULE-7
			**MUST** be an example only.
			**Reason:** example
			**Applies when:** examples are mistaken for rules
			""";

	private static final String REAL_CRITERION_1 = """
			### UC1-AC1: A real criterion before the example

			When read, the system shall return this criterion.
			""";

	private static final String REAL_CRITERION_2 = """
			### UC1-AC2: A real criterion after the example

			When read, the system shall return this criterion too.
			""";

	private static final String EXAMPLE_CRITERION = """
			### UC9-AC9: An example only

			When fenced, the system shall never be read.

			Applies when: examples are mistaken for criteria
			""";

	@Test
	void aFourBacktickFenceHoldsAThreeBacktickRfcExample() {
		Path rules = write(REAL_RULE_1 + "\nAn example rules document, as documentation:\n\n````markdown\n```\n"
				+ EXAMPLE_RULE + "```\n````\n\n" + REAL_RULE_2);

		assertRealRulesOnly(rules);
	}

	@Test
	void aFourTildeFenceHoldsAThreeTildeRfcExample() {
		Path rules = write(REAL_RULE_1 + "\n~~~~\n~~~\n" + EXAMPLE_RULE + "~~~\n~~~~\n\n" + REAL_RULE_2);

		assertRealRulesOnly(rules);
	}

	@Test
	void aShorterLineDoesNotCloseALongerRfcFence() {
		// Three backticks inside a four-backtick fence are content; the fence closes at the
		// four-backtick line, and the real rule after it is read.
		Path rules = write(REAL_RULE_1 + "\n````\n" + EXAMPLE_RULE + "```\n" + EXAMPLE_RULE.replace("RULE-7", "RULE-8")
				+ "````\n\n" + REAL_RULE_2);

		assertRealRulesOnly(rules);
	}

	@Test
	void aBacktickLineDoesNotCloseATildeRfcFence() {
		Path rules = write(REAL_RULE_1 + "\n~~~\n" + EXAMPLE_RULE + "```\n" + EXAMPLE_RULE.replace("RULE-7", "RULE-8")
				+ "~~~\n\n" + REAL_RULE_2);

		assertRealRulesOnly(rules);
	}

	@Test
	void aFourBacktickFenceHoldsAThreeBacktickEarsExample() {
		Path criteria = write(REAL_CRITERION_1 + "\n````markdown\n```\n" + EXAMPLE_CRITERION + "```\n````\n\n"
				+ REAL_CRITERION_2);

		assertRealCriteriaOnly(criteria);
	}

	@Test
	void aFourTildeFenceHoldsAThreeTildeEarsExample() {
		Path criteria = write(REAL_CRITERION_1 + "\n~~~~\n~~~\n" + EXAMPLE_CRITERION + "~~~\n~~~~\n\n" + REAL_CRITERION_2);

		assertRealCriteriaOnly(criteria);
	}

	@Test
	void aShorterLineDoesNotCloseALongerEarsFence() {
		Path criteria = write(REAL_CRITERION_1 + "\n````\n" + EXAMPLE_CRITERION + "```\n"
				+ EXAMPLE_CRITERION.replace("UC9-AC9", "UC9-AC8") + "````\n\n" + REAL_CRITERION_2);

		assertRealCriteriaOnly(criteria);
	}

	@Test
	void aBacktickLineDoesNotCloseATildeEarsFence() {
		Path criteria = write(REAL_CRITERION_1 + "\n~~~\n" + EXAMPLE_CRITERION + "```\n"
				+ EXAMPLE_CRITERION.replace("UC9-AC9", "UC9-AC8") + "~~~\n\n" + REAL_CRITERION_2);

		assertRealCriteriaOnly(criteria);
	}

	@Test
	void anUnterminatedFenceSwallowsToTheEndOfTheDocumentAsCommonMarkSays() {
		// Not a defect to repair: the document is malformed Markdown, and the reader follows
		// the specification rather than guessing where the author meant the fence to end.
		Path rules = write(REAL_RULE_1 + "\n````\n" + EXAMPLE_RULE + "```\n" + REAL_RULE_2);

		assertThat(Rfc2119Constraint.from(rules)).extracting(Rfc2119Constraint::id).containsExactly("RULE-1");
		assertThat(Rfc2119Constraint.problems(rules)).isEmpty();
	}

	private static void assertRealRulesOnly(Path rules) {
		List<Rfc2119Constraint> permissive = Rfc2119Constraint.from(rules);
		assertThat(permissive).extracting(Rfc2119Constraint::id).containsExactly("RULE-1", "RULE-2");
		assertThat(permissive).allSatisfy(rule -> {
			assertThat(rule.conditional()).as(rule.id() + " takes no applicability from an example").isFalse();
			assertThat(rule.requirement()).startsWith("be a real rule");
		});
		assertThat(Rfc2119Constraint.problems(rules)).isEmpty();
		assertThat(Rfc2119Constraint.fromStrict(rules)).isEqualTo(permissive);
		assertThat(Rfc2119Requirement.fromStrict(rules, "v")).extracting(Rfc2119Requirement::id)
			.containsExactly("RULE-1", "RULE-2");
	}

	private static void assertRealCriteriaOnly(Path criteria) {
		List<EarsCriterion> permissive = EarsCriterion.from(criteria);
		assertThat(permissive).extracting(EarsCriterion::id).containsExactly("UC1-AC1", "UC1-AC2");
		assertThat(permissive).allSatisfy(criterion -> {
			assertThat(criterion.conditional()).as(criterion.id() + " takes no applicability from an example").isFalse();
			assertThat(criterion.requirement()).startsWith("When read, the system shall return");
		});
		assertThat(EarsCriterion.problems(criteria)).isEmpty();
		assertThat(EarsCriterion.fromStrict(criteria)).isEqualTo(permissive);
		assertThat(EarsRequirement.fromStrict(criteria, "v")).extracting(EarsRequirement::id)
			.containsExactly("UC1-AC1", "UC1-AC2");
	}

	private Path write(String content) {
		try {
			Path file = Files.createTempFile(directory, "requirements", ".md");
			Files.writeString(file, content);
			return file;
		}
		catch (java.io.IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}
