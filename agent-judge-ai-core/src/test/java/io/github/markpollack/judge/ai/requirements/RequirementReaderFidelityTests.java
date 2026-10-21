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
 * What a loaded native requirement must carry out of the document it came from.
 *
 * <p>
 * The native specification is what a runtime renders for the judge. A display label is
 * allowed to be short; the obligation is not. And a condition under which a requirement
 * applies is only real when the document declares it, so the readers must load the
 * declaration rather than leave every requirement unconditional.
 */
class RequirementReaderFidelityTests {

	@TempDir
	Path directory;

	private static final String LONG_RULE = "protect reservation-changing transactions with pessimistic write locks; "
			+ "acquire locks in a global order. Identifiers ascend within a type, and external solver work "
			+ "MUST NOT run while a lock is open, with no exception for retries.";

	@Test
	void aLoadedRfcRequirementKeepsItsCompleteObligationInTheNativeSpecification() {
		Path rules = write("""
				### RULE-4
				**MUST** %s
				**Reason:** Stable row locking serializes races.
				""".formatted(LONG_RULE));

		Rfc2119Constraint constraint = Rfc2119Constraint.from(rules).get(0);
		Rfc2119Requirement loaded = Rfc2119Requirement.from(rules, "design:1").get(0);

		// The label is cut at the first clause break; the obligation must not be.
		assertThat(constraint.title()).isNotEqualTo(constraint.requirement());
		assertThat(loaded.specification().requirement()).isEqualTo(LONG_RULE);
		assertThat(loaded.specification().asPrompt()).contains("with no exception for retries.");
		assertThat(loaded.text()).isEqualTo(constraint.asPrompt());
		assertThat(loaded.specification().keyword()).isEqualTo("MUST");
		assertThat(loaded.specification().reason()).isEqualTo("Stable row locking serializes races.");
		assertThat(loaded.source().nativeId()).isEqualTo("RULE-4");
		assertThat(loaded.revision()).isEqualTo("design:1");
	}

	@Test
	void aConsequentialClauseAfterEveryShorteningBoundarySurvives() {
		for (String mark : List.of("; ", ". ", ", and ", ", with ")) {
			String statement = "keep the first clause" + mark + "the consequential clause MUST survive";
			Path rules = write("""
					### RULE-1
					**MUST** %s
					**Reason:** r
					""".formatted(statement));

			Rfc2119Requirement loaded = Rfc2119Requirement.from(rules, "v").get(0);

			assertThat(loaded.specification().requirement()).as("boundary %s", mark).isEqualTo(statement);
		}
	}

	@Test
	void craftedAndLoadedRfcRequirementsAgreeOnTheStatement() {
		Path rules = write("""
				### RULE-2
				**SHOULD** persist instants; derive local time from an injected Clock.
				**Reason:** One time model.
				""");

		Rfc2119Requirement loaded = Rfc2119Requirement.from(rules, "v").get(0);
		Rfc2119Requirement crafted = Rfc2119Requirement.of("RULE-2", "v", "SHOULD",
				"persist instants; derive local time from an injected Clock.", "One time model.", null);

		assertThat(loaded.specification()).isEqualTo(crafted.specification());
		assertThat(loaded.text()).isEqualTo(crafted.text());
	}

	@Test
	void aLoadedEarsRequirementKeepsItsCompleteSentence() {
		Path criteria = write("""
				### UC1-AC4: Report a stream gap instead of enriching from the archive

				While a stream is the selected input, if a fact is absent from the stream, then the capture shall record that limitation and shall not read the native session file to supply it.
				Rule: RULE-5. Tests: none yet.
				""");

		EarsRequirement loaded = EarsRequirement.from(criteria, "v").get(0);

		assertThat(loaded.specification().requirement()).startsWith("While a stream")
			.endsWith("to supply it.");
		assertThat(loaded.specification().title()).isEqualTo("Report a stream gap instead of enriching from the archive");
		assertThat(loaded.specification().applicability()).isNull();
	}

	@Test
	void anExplicitRfcApplicabilityEntryIsLoaded() {
		Path rules = write("""
				### RULE-7
				**MUST** write audit metadata in the same transaction as the mutation.
				**Reason:** Local effects are clearest when the committing service controls them.
				**Applies when:** The implementation has a relational persistence layer.

				### RULE-8
				**MUST** enforce authorization at the route boundary.
				**Reason:** Defence in depth.
				""");

		List<Rfc2119Constraint> constraints = Rfc2119Constraint.from(rules);
		List<Rfc2119Requirement> requirements = Rfc2119Requirement.from(rules, "v");

		assertThat(constraints).hasSize(2);
		assertThat(constraints.get(0).applicability())
			.isEqualTo("The implementation has a relational persistence layer.");
		assertThat(constraints.get(0).conditional()).isTrue();
		assertThat(constraints.get(1).conditional()).as("metadata does not attach to the next rule").isFalse();
		assertThat(requirements.get(0).specification().applicability())
			.isEqualTo("The implementation has a relational persistence layer.");
		assertThat(requirements.get(0).specification().asPrompt())
			.endsWith("(Applies when: The implementation has a relational persistence layer.)");
		assertThat(requirements.get(0).text()).isEqualTo(constraints.get(0).asPrompt());
		assertThat(requirements.get(1).specification().applicability()).isNull();
	}

	@Test
	void anExplicitEarsApplicabilityEntryIsLoaded() {
		Path criteria = write("""
				### UC1-AC2: Import a native session file

				When capture starts, Journal shall import the native session file.

				Applies when: The selected CLI offers no streaming output.

				### UC1-AC3: Archive the native session file unchanged

				When a streaming CLI writes a native session file, the capture shall archive it byte-identical.
				""");

		List<EarsCriterion> parsed = EarsCriterion.from(criteria);
		List<EarsRequirement> requirements = EarsRequirement.from(criteria, "v");

		assertThat(parsed).hasSize(2);
		assertThat(parsed.get(0).requirement())
			.isEqualTo("When capture starts, Journal shall import the native session file.");
		assertThat(parsed.get(0).applicability()).isEqualTo("The selected CLI offers no streaming output.");
		assertThat(parsed.get(1).conditional()).as("metadata does not attach to the next criterion").isFalse();
		assertThat(requirements.get(0).specification().applicability())
			.isEqualTo("The selected CLI offers no streaming output.");
		assertThat(requirements.get(0).specification().asPrompt())
			.endsWith("(Applies when: The selected CLI offers no streaming output.)");
		assertThat(requirements.get(1).specification().applicability()).isNull();
	}

	@Test
	void applicabilityAtTheEndOfTheDocumentIsLoaded() {
		Path rules = write("""
				### RULE-1
				**MAY** cache the effective calendar per request.
				**Reason:** Cost.
				**Applies when:** Requests are served by more than one node.""");

		assertThat(Rfc2119Constraint.from(rules)).singleElement()
			.extracting(Rfc2119Constraint::applicability)
			.isEqualTo("Requests are served by more than one node.");
	}

	@Test
	void anEarsMetadataEntryIsNeverMistakenForTheStatement() {
		// Preferred layout puts the entry after the sentence; written first, it is still
		// metadata rather than the requirement.
		Path criteria = write("""
				### UC2-AC1: Record the version

				Applies when: The CLI reports a version.

				When a run starts, the capture shall record the CLI version.
				""");

		EarsCriterion criterion = EarsCriterion.from(criteria).get(0);

		assertThat(criterion.requirement()).isEqualTo("When a run starts, the capture shall record the CLI version.");
		assertThat(criterion.applicability()).isEqualTo("The CLI reports a version.");
	}

	@Test
	void earsTriggerWordsDoNotPopulateApplicability() {
		Path criteria = write("""
				### UC1-AC5: Treat an unsupported stream as a gap

				If no stream adapter supports a CLI, then the capture shall report an unsupported-stream gap.

				### UC1-AC7: Hold while recording

				While a run is recording, when a child starts, the capture shall attach it.
				""");

		assertThat(EarsCriterion.from(criteria)).hasSize(2).allSatisfy(c -> assertThat(c.conditional()).isFalse());
	}

	@Test
	void aDocumentWithoutApplicabilityIsUnchanged() {
		Path rules = write("""
				### RULE-1
				**Covers:** UC3-AC47
				**MUST** implement mutations as explicit application-service operations.
				**Reason:** Inspectable transitions.

				### RULE-2
				**MUST NOT** perform workflow transitions in controllers.
				**Reason:** Presentation code hides behaviour.
				""");

		List<Rfc2119Constraint> constraints = Rfc2119Constraint.from(rules);

		assertThat(constraints).extracting(Rfc2119Constraint::id).containsExactly("RULE-1", "RULE-2");
		assertThat(constraints).allSatisfy(c -> assertThat(c.conditional()).isFalse());
		assertThat(constraints.get(0).asPrompt()).doesNotContain("Applies when");
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
