package io.github.markpollack.judge.ai.requirements;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reading the architectural constraints out of the design document.
 *
 * <p>A structurally different document from the acceptance criteria — different headings, a
 * keyword, a stated reason — parsed by the same shape into the same kind of roster.
 */
class Rfc2119ConstraintTests {

	@TempDir
	Path directory;

	@Test
	void everyConstraintInTheDocumentIsRead() {
		Path file = write("""
			    ## Rules

			    ### RULE-1
			    **Covers:** UC3-AC47, UC4-AC33
			    **MUST** implement mutations as explicit application-service operations.
			    **Reason:** The explicit-policy design keeps every transition inspectable.

			    ### RULE-2
			    **MUST NOT** perform independent workflow transitions in controllers.
			    **Reason:** Controllers would hide behaviour in presentation code.
			    """);

		List<Rfc2119Constraint> constraints = Rfc2119Constraint.from(file);

		assertThat(constraints).hasSize(2);
		assertThat(constraints.get(0).id()).isEqualTo("RULE-1");
		assertThat(constraints.get(0).keyword()).isEqualTo("MUST");
		assertThat(constraints.get(0).requirement())
			.isEqualTo("implement mutations as explicit application-service operations.");
		assertThat(constraints.get(0).reason()).isEqualTo("The explicit-policy design keeps every transition inspectable.");
		assertThat(constraints.get(1).keyword()).isEqualTo("MUST NOT");
	}

	@Test
	void theKeywordIsCapturedRatherThanDiscarded() {
		// A document whose rules are all MUST does not depend on this yet. It is recorded so a
		// later SHOULD does not have to be guessed at.
		Path file = write("""
			    ### RULE-1
			    **SHOULD** name repositories after the aggregate they serve.
			    **Reason:** Readability.

			    ### RULE-2
			    **MAY** cache the effective calendar per request.
			    **Reason:** Cost.
			    """);

		assertThat(Rfc2119Constraint.from(file)).extracting(Rfc2119Constraint::keyword)
			.containsExactly("SHOULD", "MAY");
	}

	@Test
	void aUseCaseScopedRuleIdentifierIsRead() {
		Path file = write("""
			    ### UC6-RULE1
			    **MUST** reject owner cancellation at the exact start instant.
			    **Reason:** The boundary is inclusive.
			    """);

		assertThat(Rfc2119Constraint.from(file)).singleElement()
			.extracting(Rfc2119Constraint::id).isEqualTo("UC6-RULE1");
	}

	@Test
	void aRuleWithNoStatedReasonIsNotYetAConstraint() {
		// The reason travels with the rule into the prompt, so a rule without one is incomplete
		// rather than silently admitted to the roster on weaker terms.
		Path file = write("""
			    ### RULE-1
			    **MUST** implement mutations as explicit application-service operations.

			    ### RULE-2
			    **MUST NOT** perform independent workflow transitions in controllers.
			    **Reason:** Controllers would hide behaviour in presentation code.
			    """);

		assertThat(Rfc2119Constraint.from(file)).singleElement()
			.extracting(Rfc2119Constraint::id).isEqualTo("RULE-2");
	}

	@Test
	void anEmptyDocumentYieldsNoConstraintsRatherThanFailing() {
		assertThat(Rfc2119Constraint.from(write("# Technical Design\n\n## Overview\n\nProse only.\n"))).isEmpty();
	}

	@Test
	void theConstraintIsAskedWithItsKeywordAndReason() {
		Rfc2119Constraint constraint =
			new Rfc2119Constraint("RULE-4", "MUST", "protect reservation-changing transactions", "lost updates");

		assertThat(constraint.asPrompt())
			.isEqualTo("RULE-4: MUST protect reservation-changing transactions (Reason: lost updates)");
	}

	@Test
	void theTitleIsTheRequirementToItsFirstClauseBreak() {
		assertThat(new Rfc2119Constraint("RULE-1", "MUST", "keep it short; then say more", "r").title())
			.isEqualTo("keep it short");
		assertThat(new Rfc2119Constraint("RULE-2", "MUST", "keep it short. Then say more", "r").title())
			.isEqualTo("keep it short");
	}

	@Test
	void aLongTitleIsCutOnAWordAndMarkedAsCut() {
		String requirement = "persist appointments and reservations and operations and locks and deadlines "
			+ "as instant values throughout the whole application";

		String title = new Rfc2119Constraint("RULE-2", "MUST", requirement, "r").title();

		assertThat(title).endsWith("…").doesNotContain(" …");
		assertThat(title.length()).isLessThanOrEqualTo(59);
		assertThat(requirement).startsWith(title.substring(0, title.length() - 1));
	}

	@Test
	void aShortRequirementIsItsOwnTitle() {
		assertThat(new Rfc2119Constraint("RULE-3", "MUST", "use constructor injection", "r").title())
			.isEqualTo("use constructor injection");
	}

	@Test
	void anUnreadableDocumentSaysWhichOne() {
		Path missing = directory.resolve("no-rules-here.md");

		assertThatThrownBy(() -> Rfc2119Constraint.from(missing))
			.isInstanceOf(UncheckedIOException.class)
			.hasMessageContaining("no-rules-here.md");
	}

	private Path write(String content) {
		try {
			Path file = Files.createTempFile(directory, "rules", ".md");
			Files.writeString(file, content);
			return file;
		}
		catch (java.io.IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}
