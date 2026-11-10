/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.ai.requirements;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The requirement-document guide at the repository root shows the format by example. Each
 * example marked valid must lint clean, and the refused example must produce exactly the
 * report printed under it, so the guide cannot drift from the readers.
 */
class RequirementDocumentsGuideTest {

	private static final Pattern EXAMPLE = Pattern
		.compile("(?s)<!-- lint: (valid|refused) -->\\n```markdown\\n(.*?)```(?:\\n\\nThe linter reports:\\n\\n```text\\n(.*?)```)?");

	@TempDir
	Path spec;

	private static String guide() throws IOException {
		Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		while (!Files.exists(root.resolve("agent-judge-core"))) {
			root = root.getParent();
		}
		return Files.readString(root.resolve("REQUIREMENT_DOCUMENTS.md"));
	}

	private RequirementDocuments.Report lint(String document) throws IOException {
		Files.writeString(spec.resolve("example.md"), document);
		return RequirementDocuments.lint(spec);
	}

	@Test
	void everyGuideExampleLintsAsTheGuideSays() throws IOException {
		Matcher examples = EXAMPLE.matcher(guide());
		List<String> kinds = new ArrayList<>();
		while (examples.find()) {
			kinds.add(examples.group(1));
			RequirementDocuments.Report report = lint(examples.group(2));
			if (examples.group(1).equals("valid")) {
				assertThat(report.problems()).as(examples.group(2)).isEmpty();
				assertThat(report.criteria() + report.constraints()).isPositive();
			}
			else {
				assertThat(examples.group(3)).as("the refused example's printed report").isNotNull();
				assertThat(report.problems()).extracting(RequirementDocumentProblem::describe)
					.containsExactlyElementsOf(examples.group(3).lines().toList());
			}
		}
		assertThat(kinds).containsExactly("valid", "valid", "refused");
	}

	@Test
	void theValidExamplesReadWrappedSentencesWhole() throws IOException {
		Matcher examples = EXAMPLE.matcher(guide());
		assertThat(examples.find()).isTrue();
		var criteria = EarsCriterion.parse(examples.group(2), "example.md").items();
		assertThat(criteria).extracting(EarsCriterion::requirement)
			.containsExactly(
					"When a customer places an order, the system shall record the order and send exactly one confirmation.",
					"While the shop is closed, the system shall show only orders in the `OPEN` state.");
		assertThat(criteria.get(1).applicability()).isEqualTo("the change touches the order list view");

		assertThat(examples.find()).isTrue();
		var rules = Rfc2119Constraint.parse(examples.group(2), "example.md").items();
		assertThat(rules).extracting(Rfc2119Constraint::requirement)
			.containsExactly("call the payment gateway from inside a database transaction.",
					"log every refused payment with its order identifier.");
		assertThat(rules).extracting(Rfc2119Constraint::keyword).containsExactly("MUST NOT", "SHOULD");
		assertThat(rules.get(1).applicability()).isEqualTo("the change touches payment code");
	}

	@Test
	void theRefusedExampleLoadsPermissivelyAsTheGuideSays() throws IOException {
		Matcher examples = EXAMPLE.matcher(guide());
		while (examples.find() && !examples.group(1).equals("refused")) {
			// skip to the refused example
		}
		Path document = spec.resolve("example.md");
		Files.writeString(document, examples.group(2));

		assertThat(EarsRequirement.from(document, "1")).extracting(EarsRequirement::id)
			.containsExactly("UC2-AC1", "UC2-AC1");
		assertThat(Rfc2119Requirement.from(document, "1")).isEmpty();
		assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> EarsRequirement.fromStrict(document, "1")))
			.isInstanceOf(RequirementDocumentException.class);
	}

}
