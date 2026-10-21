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
 * CommonMark decides the structure; the readers decide what is a requirement. These pin the
 * consequences that differ from reading the document line by line, so they are deliberate.
 */
class MarkdownStructureTests {

	@TempDir
	Path directory;

	@Test
	void anIndentedCodeBlockIsAnExampleToo() {
		Path rules = write("""
				### RULE-1
				**MUST** be real.
				**Reason:** r

				Shown as an indented example:

				    ### RULE-2
				    **MUST** be an example.
				    **Reason:** e

				### RULE-3
				**MUST** be real as well.
				**Reason:** r
				""");

		assertThat(Rfc2119Constraint.from(rules)).extracting(Rfc2119Constraint::id).containsExactly("RULE-1", "RULE-3");
		assertThat(Rfc2119Constraint.problems(rules)).isEmpty();
	}

	@Test
	void paragraphsInsideListsAndBlockQuotesAreReadLikeAnyOther() {
		Path criteria = write("""
				### UC1-AC1: In a list

				- When listed, the system shall still return the sentence.

				### UC1-AC2: In a quote

				> When quoted, the system shall still return the sentence.
				> Applies when: the quote is read
				""");

		List<EarsCriterion> parsed = EarsCriterion.from(criteria);

		assertThat(parsed).extracting(EarsCriterion::requirement)
			.containsExactly("- When listed, the system shall still return the sentence.",
					"> When quoted, the system shall still return the sentence.");
		assertThat(parsed.get(1).applicability()).isNull();
		assertThat(EarsCriterion.problems(criteria)).isEmpty();
	}

	@Test
	void htmlBlocksAndThematicBreaksAreNotRead() {
		Path rules = write("""
				<!--
				### RULE-9
				**MUST** be a commented-out draft.
				**Reason:** draft
				-->

				### RULE-1
				**MUST** be real.
				**Reason:** r

				---

				### RULE-2
				**MUST** be real after the rule.
				**Reason:** r
				""");

		assertThat(Rfc2119Constraint.from(rules)).extracting(Rfc2119Constraint::id).containsExactly("RULE-1", "RULE-2");
		assertThat(Rfc2119Constraint.problems(rules)).isEmpty();
	}

	@Test
	void aHeadingWithClosingHashesOrASetextUnderlineIsStillAHeadingButNotARule() {
		Path rules = write("""
				### RULE-1 ###
				**MUST** not be read as RULE-1, because the heading line is not exactly `### RULE-1`.
				**Reason:** r

				RULE-2
				------
				**MUST** not be read either; a setext heading is a heading, not a rule.
				**Reason:** r

				### RULE-3
				**MUST** be the one real rule.
				**Reason:** r
				""");

		assertThat(Rfc2119Constraint.from(rules)).extracting(Rfc2119Constraint::id).containsExactly("RULE-3");
		assertThat(Rfc2119Constraint.problems(rules)).singleElement().satisfies(problem -> {
			assertThat(problem.id()).isEqualTo("RULE-1");
			assertThat(problem.line()).isEqualTo(1);
			assertThat(problem.reason()).contains("resembles a rule");
		});
	}

	@Test
	void theStatementIsTheDocumentsOwnLineNotARendering() {
		// Emphasis, code spans and escapes stay exactly as the author wrote them.
		Path rules = write("""
				### RULE-1
				**MUST** keep `Instant` values, *never* `LocalDateTime`, and write 2\\*3 as written.
				**Reason:** r
				""");

		assertThat(Rfc2119Constraint.from(rules)).singleElement().extracting(Rfc2119Constraint::requirement)
			.isEqualTo("keep `Instant` values, *never* `LocalDateTime`, and write 2\\*3 as written.");
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
