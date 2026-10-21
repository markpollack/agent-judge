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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * A reader that silently shrinks the roster has sampled the specification. Strict loading
 * refuses the document and names what it could not read; the permissive reader keeps its
 * behaviour and says the same thing at WARN.
 */
class StrictRequirementLoadingTests {

	@TempDir
	Path directory;

	/** The shape of a consumer's negative control: three headings, one well-formed rule. */
	private static final String SHRINKING_RULES = """
			# Negative control

			### RULE-1
			**MUST** keep this rule well-formed.
			**Reason:** It is the control that parses.

			### RULE-2
			**MUST** be skipped because this rule has no reason line.

			### RULE-3
			MUST be skipped because the keyword is not bold, so the requirement line never matches.
			**Reason:** A reason without a matched requirement is ignored.
			""";

	private static final String SHRINKING_CRITERIA = """
			# Negative control

			### UC1-AC1: The control that parses

			When the document is read, the reader shall return this criterion.

			### UC1-AC2 Missing the colon after the identifier

			When the heading lacks the colon, the reader shall never see this sentence.

			### UC1-AC3: A heading with no requirement sentence
			**Covers:** nothing

			## The sentence below belongs to no criterion because a new section started first

			When a section heading intervenes, the reader shall not attach this sentence to UC1-AC3.
			""";

	@Test
	void thePermissiveRfcReaderStillShrinksAndNowSaysSo() {
		Path rules = write("rules.md", SHRINKING_RULES);

		assertThat(Rfc2119Constraint.from(rules)).extracting(Rfc2119Constraint::id).containsExactly("RULE-1");
		assertThat(Rfc2119Constraint.problems(rules)).extracting(RequirementDocumentProblem::id)
			.containsExactly("RULE-2", "RULE-3");
	}

	@Test
	void theStrictRfcReaderRefusesTheShrinkingDocumentAndNamesEachSkippedRule() {
		Path rules = write("rules.md", SHRINKING_RULES);

		RequirementDocumentException refusal = catchThrowableOfType(RequirementDocumentException.class,
				() -> Rfc2119Constraint.fromStrict(rules));

		assertThat(refusal.source()).isEqualTo("rules.md");
		assertThat(refusal.problems()).hasSize(2);
		assertThat(refusal.problems().get(0).id()).isEqualTo("RULE-2");
		assertThat(refusal.problems().get(0).line()).isEqualTo(7);
		assertThat(refusal.problems().get(0).reason()).contains("**Reason:**");
		assertThat(refusal.problems().get(1).id()).isEqualTo("RULE-3");
		assertThat(refusal.problems().get(1).line()).isEqualTo(10);
		assertThat(refusal.problems().get(1).reason()).contains("keyword statement");
		assertThat(refusal.getMessage()).startsWith("2 problems in rules.md:")
			.contains("rules.md:7 RULE-2:")
			.contains("rules.md:10 RULE-3:");
		assertThat(refusal.problems()).isEqualTo(Rfc2119Constraint.problems(rules));
	}

	@Test
	void aMalformedRuleFollowedByAValidOneCannotDisappearUnnoticed() {
		Path rules = write("rules.md", """
				### RULE-1
				**MUST** have a reason, but does not.

				### RULE-2
				**MUST NOT** be lost because its neighbour is broken.
				**Reason:** Order and identity survive.
				""");

		assertThat(Rfc2119Constraint.from(rules)).extracting(Rfc2119Constraint::id).containsExactly("RULE-2");
		assertThatThrownBy(() -> Rfc2119Constraint.fromStrict(rules)).isInstanceOf(RequirementDocumentException.class)
			.hasMessageContaining("RULE-1");
	}

	@Test
	void duplicateRuleIdentifiersAreReportedAtTheSecondHeading() {
		Path rules = write("rules.md", """
				### RULE-1
				**MUST** be first.
				**Reason:** one.

				### RULE-1
				**MUST** be second under the same name.
				**Reason:** two.
				""");

		assertThat(Rfc2119Constraint.from(rules)).hasSize(2);
		assertThat(Rfc2119Constraint.problems(rules)).singleElement().satisfies(problem -> {
			assertThat(problem.id()).isEqualTo("RULE-1");
			assertThat(problem.line()).isEqualTo(5);
			assertThat(problem.reason()).contains("duplicate identifier");
		});
	}

	@Test
	void headingsThatResembleRulesAreReportedAndOrdinaryHeadingsAreNot() {
		Path rules = write("rules.md", """
				# Technical Design

				## Overview

				### Components

				Prose about components, with a MUST in it that is not a rule.

				## Rules

				#### RULE-1
				**MUST** sit at the wrong heading level.
				**Reason:** r

				### Rule-2
				**MUST** be upper case.
				**Reason:** r

				### RULE 3
				**MUST** use a hyphen.
				**Reason:** r

				### RULE-4: A title does not belong here
				**MUST** have nothing after the identifier.
				**Reason:** r

				### RULE-5
				**MUST** be the one well-formed rule.
				**Reason:** r

				```markdown
				### RULE-6
				**MUST** never be read, because it is an example inside a fence.
				**Reason:** r
				```
				""");

		assertThat(Rfc2119Constraint.from(rules)).extracting(Rfc2119Constraint::id).containsExactly("RULE-5");
		List<RequirementDocumentProblem> problems = Rfc2119Constraint.problems(rules);
		assertThat(problems).extracting(RequirementDocumentProblem::id).containsExactly("RULE-1", "Rule-2", "RULE", "RULE-4");
		assertThat(problems).extracting(RequirementDocumentProblem::line).containsExactly(11, 15, 19, 23);
		assertThat(problems).allSatisfy(problem -> assertThat(problem.reason()).contains("resembles a rule"));
	}

	@Test
	void statementAndReasonDefectsInsideAnEntryAreReported() {
		Path rules = write("rules.md", """
				### RULE-1
				**MUST**
				**Reason:** A keyword with nothing after it is not a statement.

				### RULE-2
				**MUST** state one thing.
				**SHOULD** not state a second thing under the same heading.
				**Reason:** r

				### RULE-3
				**MUST** have one reason.
				**Reason:**
				**Reason:** r
				""");

		List<RequirementDocumentProblem> problems = Rfc2119Constraint.problems(rules);

		assertThat(problems).extracting(RequirementDocumentProblem::id).containsExactly("RULE-1", "RULE-1", "RULE-2", "RULE-3");
		assertThat(problems.get(0).reason()).contains("keyword statement");
		assertThat(problems.get(1).reason()).contains("without a statement");
		assertThat(problems.get(2).reason()).contains("second keyword statement");
		assertThat(problems.get(3).reason()).contains("without a reason");
		assertThat(Rfc2119Constraint.from(rules)).extracting(Rfc2119Constraint::id).containsExactly("RULE-2", "RULE-3");
	}

	@Test
	void blankAndRepeatedRfcApplicabilityAreReported() {
		Path rules = write("rules.md", """
				### RULE-1
				**MUST** do a thing.
				**Reason:** r
				**Applies when:**

				### RULE-2
				**MUST** do another thing.
				**Reason:** r
				**Applies when:** first condition
				Applies when: second condition
				""");

		List<Rfc2119Constraint> permissive = Rfc2119Constraint.from(rules);
		assertThat(permissive.get(0).conditional()).isFalse();
		assertThat(permissive.get(1).applicability()).isEqualTo("first condition");

		List<RequirementDocumentProblem> problems = Rfc2119Constraint.problems(rules);
		assertThat(problems).hasSize(2);
		assertThat(problems.get(0).id()).isEqualTo("RULE-1");
		assertThat(problems.get(0).line()).isEqualTo(4);
		assertThat(problems.get(0).reason()).contains("blank `Applies when:`");
		assertThat(problems.get(1).id()).isEqualTo("RULE-2");
		assertThat(problems.get(1).line()).isEqualTo(10);
		assertThat(problems.get(1).reason()).contains("second `Applies when:`");
	}

	@Test
	void aWellFormedRfcDocumentLoadsStrictlyAsItLoadsPermissively() throws Exception {
		Path base = Path.of(getClass().getResource("/configured-tutorial/rules.md").toURI()).getParent();

		List<Rfc2119Constraint> strict = Rfc2119Constraint.fromStrict(base.resolve("rules.md"));

		assertThat(strict).hasSize(13).isEqualTo(Rfc2119Constraint.from(base.resolve("rules.md")));
		assertThat(Rfc2119Constraint.problems(base.resolve("rules.md"))).isEmpty();
		assertThat(Rfc2119Requirement.fromStrict(base.resolve("rules.md"), "tutorial:27756e9"))
			.isEqualTo(Rfc2119Requirement.from(base.resolve("rules.md"), "tutorial:27756e9"));
	}

	@Test
	void thePermissiveEarsReaderStillShrinksAndNowSaysSo() {
		Path criteria = write("criteria.md", SHRINKING_CRITERIA);

		assertThat(EarsCriterion.from(criteria)).extracting(EarsCriterion::id).containsExactly("UC1-AC1");
		assertThat(EarsCriterion.problems(criteria)).extracting(RequirementDocumentProblem::id)
			.containsExactly("UC1-AC2", "UC1-AC3");
	}

	@Test
	void theStrictEarsReaderRefusesTheShrinkingDocumentAndNamesEachSkippedCriterion() {
		Path criteria = write("criteria.md", SHRINKING_CRITERIA);

		RequirementDocumentException refusal = catchThrowableOfType(RequirementDocumentException.class,
				() -> EarsCriterion.fromStrict(criteria));

		assertThat(refusal.problems()).hasSize(2);
		assertThat(refusal.problems().get(0).id()).isEqualTo("UC1-AC2");
		assertThat(refusal.problems().get(0).line()).isEqualTo(7);
		assertThat(refusal.problems().get(0).reason()).contains("resembles a criterion");
		assertThat(refusal.problems().get(1).id()).isEqualTo("UC1-AC3");
		assertThat(refusal.problems().get(1).line()).isEqualTo(11);
		assertThat(refusal.problems().get(1).reason()).contains("no requirement sentence");
		assertThatThrownBy(() -> EarsRequirement.fromStrict(criteria, "v"))
			.isInstanceOf(RequirementDocumentException.class)
			.hasMessageContaining("criteria.md:7 UC1-AC2:");
	}

	@Test
	void aMetadataOnlyEarsEntryHasNoStatementAndIsReportedRatherThanLoaded() {
		// The labelled lines a criteria document carries beside its sentence are metadata;
		// they can neither stand in for the sentence nor vanish without a word.
		Path criteria = write("criteria.md", """
				### UC1-AC1: Preserve timestamps
				Rule: RULE-1. Tests: none yet. Status: not implemented.

				### UC1-AC2: Record the version

				When a run starts, the capture shall record the CLI version.
				Rule: RULE-2. Tests: `VersionTest`. Status: demonstrated.
				""");

		assertThat(EarsCriterion.from(criteria)).singleElement().satisfies(criterion -> {
			assertThat(criterion.id()).isEqualTo("UC1-AC2");
			assertThat(criterion.requirement()).isEqualTo("When a run starts, the capture shall record the CLI version.");
		});
		assertThat(EarsCriterion.problems(criteria)).singleElement().satisfies(problem -> {
			assertThat(problem.id()).isEqualTo("UC1-AC1");
			assertThat(problem.line()).isEqualTo(1);
			assertThat(problem.reason()).contains("no requirement sentence");
		});
		assertThatThrownBy(() -> EarsCriterion.fromStrict(criteria)).isInstanceOf(RequirementDocumentException.class)
			.hasMessageContaining("criteria.md:1 UC1-AC1:");
		assertThatThrownBy(() -> EarsRequirement.fromStrict(criteria, "v"))
			.isInstanceOf(RequirementDocumentException.class);
	}

	@Test
	void eachRecognisedEarsMetadataLabelIsNeverTheStatement() {
		for (String label : List.of("Rule:", "Rules:", "Tests:", "Status:", "Covers:", "**Covers:**", "**Rule:**",
				"Applies when:")) {
			Path criteria = write("criteria.md", """
					### UC1-AC1: Labelled first
					%s something
					When read, the system shall return the sentence, not the label.
					""".formatted(label));

			List<EarsCriterion> parsed = EarsCriterion.from(criteria);

			assertThat(parsed).as(label).singleElement().extracting(EarsCriterion::requirement)
				.isEqualTo("When read, the system shall return the sentence, not the label.");
		}
	}

	@Test
	void headingsThatResembleCriteriaAreReportedAndOrdinaryHeadingsAreNot() {
		Path criteria = write("criteria.md", """
				# Acceptance Criteria: UC1

				## Functional

				#### UC1-AC1: Wrong level

				When read, the system shall not see this.

				### uc1-ac2: Lower case

				When read, the system shall not see this either.

				### UC1-AC3:

				When the title is missing, the system shall not see this.

				### UC1-AC4: The well-formed one

				When read, the system shall return this criterion.

				### UC1-AC4: The duplicate

				When read, the system shall report the duplicate.

				```text
				### UC1-AC5: Inside a fence
				When fenced, the system shall never be read.
				```
				""");

		assertThat(EarsCriterion.from(criteria)).extracting(EarsCriterion::id).containsExactly("UC1-AC4", "UC1-AC4");
		List<RequirementDocumentProblem> problems = EarsCriterion.problems(criteria);
		assertThat(problems).extracting(RequirementDocumentProblem::id).containsExactly("UC1-AC1", "uc1-ac2", "UC1-AC3", "UC1-AC4");
		assertThat(problems).extracting(RequirementDocumentProblem::line).containsExactly(5, 9, 13, 21);
		assertThat(problems.get(3).reason()).contains("duplicate identifier");
	}

	@Test
	void blankAndRepeatedEarsApplicabilityAreReported() {
		Path criteria = write("criteria.md", """
				### UC1-AC1: Blank

				When read, the system shall return this.
				Applies when:

				### UC1-AC2: Repeated

				When read, the system shall return this too.
				Applies when: first
				**Applies when:** second
				""");

		List<EarsCriterion> permissive = EarsCriterion.from(criteria);
		assertThat(permissive).hasSize(2);
		assertThat(permissive.get(0).conditional()).isFalse();
		assertThat(permissive.get(1).applicability()).isEqualTo("first");

		List<RequirementDocumentProblem> problems = EarsCriterion.problems(criteria);
		assertThat(problems).extracting(RequirementDocumentProblem::id).containsExactly("UC1-AC1", "UC1-AC2");
		assertThat(problems).extracting(RequirementDocumentProblem::line).containsExactly(4, 10);
	}

	@Test
	void aWellFormedEarsDocumentLoadsStrictlyAsItLoadsPermissively() throws Exception {
		Path base = Path.of(getClass().getResource("/configured-tutorial/criteria.md").toURI()).getParent();

		List<EarsCriterion> strict = EarsCriterion.fromStrict(base.resolve("criteria.md"));

		assertThat(strict).hasSize(52).isEqualTo(EarsCriterion.from(base.resolve("criteria.md")));
		assertThat(EarsCriterion.problems(base.resolve("criteria.md"))).isEmpty();
		assertThat(EarsRequirement.fromStrict(base.resolve("criteria.md"), "tutorial:27756e9"))
			.isEqualTo(EarsRequirement.from(base.resolve("criteria.md"), "tutorial:27756e9"));
	}

	@Test
	void aStrictLoadWithApplicabilityKeepsOrderIdentityStatementAndSource() {
		Path rules = write("rules.md", """
				### RULE-1
				**MUST** do the first thing; and then the second.
				**Reason:** one
				**Applies when:** the first condition holds

				### RULE-2
				**MAY** do the third thing.
				**Reason:** two
				""");

		List<Rfc2119Requirement> loaded = Rfc2119Requirement.fromStrict(rules, "v1");

		assertThat(loaded).extracting(Rfc2119Requirement::id).containsExactly("RULE-1", "RULE-2");
		assertThat(loaded.get(0).specification())
			.isEqualTo(new Rfc2119Specification("MUST", "do the first thing; and then the second.", "one",
					"the first condition holds"));
		assertThat(loaded.get(1).specification().applicability()).isNull();
		assertThat(loaded).allSatisfy(requirement -> {
			assertThat(requirement.revision()).isEqualTo("v1");
			assertThat(requirement.source().artifact().sha256()).isNotBlank();
			assertThat(requirement.source().nativeId()).isEqualTo(requirement.id());
		});
	}

	@Test
	void aProblemDescribesItselfWithSourceLineAndIdentifier() {
		RequirementDocumentProblem named = new RequirementDocumentProblem("rules.md", "RULE-2", 7, "no reason");
		RequirementDocumentProblem anonymous = new RequirementDocumentProblem("rules.md", null, 3, "odd heading");

		assertThat(named.describe()).isEqualTo("rules.md:7 RULE-2: no reason");
		assertThat(anonymous.describe()).isEqualTo("rules.md:3: odd heading");
		assertThatThrownBy(() -> new RequirementDocumentProblem("rules.md", null, 0, "x"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new RequirementDocumentException("rules.md", List.of()))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private Path write(String name, String content) {
		try {
			Path file = directory.resolve(name);
			Files.writeString(file, content);
			return file;
		}
		catch (java.io.IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}
