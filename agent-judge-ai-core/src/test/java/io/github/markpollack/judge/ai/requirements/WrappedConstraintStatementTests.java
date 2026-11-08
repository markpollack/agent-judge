/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.ai.requirements;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An RFC 2119 statement is its keyword line and the rest of that paragraph, joined as
 * Markdown renders a soft wrap. Reading only the keyword line drops the statement's final
 * clause.
 */
class WrappedConstraintStatementTests {

	private static final String STATEMENT = "record the booking and send exactly five confirmations.";

	private static Rfc2119Constraint only(String document) {
		var parsed = Rfc2119Constraint.parse(document, "rules.md");
		assertThat(parsed.problems()).isEmpty();
		assertThat(parsed.items()).hasSize(1);
		return parsed.items().getFirst();
	}

	@Test
	void wrappedStatementRendersExactlyAsItsSingleLineForm() {
		Rfc2119Constraint single = only("""
				### RULE-1

				**MUST** %s

				**Reason:** audit
				""".formatted(STATEMENT));
		Rfc2119Constraint wrapped = only("""
				### RULE-1

				**MUST** record the booking
				and send exactly five confirmations.

				**Reason:** audit
				""");

		assertThat(single.requirement()).isEqualTo(STATEMENT);
		assertThat(wrapped.requirement()).isEqualTo(single.requirement()).endsWith("five confirmations.");
		assertThat(wrapped.asPrompt()).isEqualTo(single.asPrompt());
		assertThat(wrapped.keyword()).isEqualTo("MUST");
		assertThat(wrapped.reason()).isEqualTo("audit");
	}

	@Test
	void reasonAndApplicabilityLinesEndTheStatementEvenWithoutABlankLine() {
		Rfc2119Constraint constraint = only("""
				### RULE-1
				**MUST** record the booking
				and send exactly five confirmations.
				**Covers:** UC1-B32
				**Reason:** audit
				Applies when: the clinic sends confirmations
				""");

		assertThat(constraint.requirement()).isEqualTo(STATEMENT);
		assertThat(constraint.reason()).isEqualTo("audit");
		assertThat(constraint.applicability()).isEqualTo("the clinic sends confirmations");
	}

	@Test
	void hardBreaksJoinAndLaterParagraphsAreNotRead() {
		Rfc2119Constraint backslash = only("### RULE-1\n\n**MUST** record the booking\\\n"
				+ "and send exactly five confirmations.\n\nLater prose is not read.\n\n**Reason:** audit\n");
		Rfc2119Constraint spaces = only("### RULE-1\n\n**MUST** record the booking  \n"
				+ "and send exactly five confirmations.\n\n**Reason:** audit\n");

		assertThat(backslash.requirement()).isEqualTo(STATEMENT);
		assertThat(spaces.requirement()).isEqualTo(STATEMENT);
	}

	@Test
	void aSecondKeywordLineInTheSameParagraphIsStillASecondStatement() {
		var parsed = Rfc2119Constraint.parse("""
				### RULE-1

				**MUST** record the booking.
				**SHOULD** send confirmations.

				**Reason:** audit
				""", "rules.md");

		assertThat(parsed.problems()).extracting(RequirementDocumentProblem::reason)
			.containsExactly("a second keyword statement; one obligation per heading");
		assertThat(parsed.problems()).extracting(RequirementDocumentProblem::line).containsExactly(4);
		assertThat(List.of(parsed.items().getFirst().requirement())).containsExactly("send confirmations.");
	}

	@Test
	void keywordStatementsInsideListItemsAndBlockQuotesAreRecognised() {
		Rfc2119Constraint listed = only("""
				### RULE-1

				- **MUST** record the booking
				  and send exactly five confirmations.
				- **Reason:** audit
				""");
		Rfc2119Constraint quoted = only("""
				### RULE-1

				> **MUST** record the booking
				> and send exactly five confirmations.
				>
				> **Reason:** audit
				> Applies when: the clinic sends confirmations
				""");

		assertThat(listed.requirement()).isEqualTo(STATEMENT);
		assertThat(listed.reason()).isEqualTo("audit");
		assertThat(quoted.requirement()).isEqualTo(STATEMENT);
		assertThat(quoted.reason()).isEqualTo("audit");
		assertThat(quoted.applicability()).isEqualTo("the clinic sends confirmations");
	}

}
