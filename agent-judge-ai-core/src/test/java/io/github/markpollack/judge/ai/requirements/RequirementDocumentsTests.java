/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.ai.requirements;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The specification linter applies the loaders' strict rules to a whole tree, so authors
 * see every problem while writing rather than when a judge runs.
 */
class RequirementDocumentsTests {

	@TempDir
	Path spec;

	private void write(String relative, String content) throws IOException {
		Path file = spec.resolve(relative);
		Files.createDirectories(file.getParent());
		Files.writeString(file, content);
	}

	@Test
	void aValidTreeReportsWhatItLoadedAndPasses() throws IOException {
		write("booking/criteria.md", """
				### UC1-AC1: Record a booking

				When a booking is saved, the system shall record it.
				""");
		write("booking/rules.md", """
				### RULE-1

				**MUST** record every booking.

				**Reason:** audit
				""");
		write("README.md", "# Notes\n\nNot a requirements document.\n");

		RequirementDocuments.Report report = RequirementDocuments.lint(spec);

		assertThat(report.valid()).isTrue();
		assertThat(report.documents()).containsExactly(Path.of("README.md"), Path.of("booking/criteria.md"),
				Path.of("booking/rules.md"));
		assertThat(report.criteria()).isEqualTo(1);
		assertThat(report.constraints()).isEqualTo(1);
		assertThatCode(() -> RequirementDocuments.assertValid(spec)).doesNotThrowAnyException();
	}

	@Test
	void everyProblemInTheTreeIsReportedWithItsFileIdentifierAndLine() throws IOException {
		write("booking/criteria.md", """
				### UC1-AC1: Record a booking

				**Covers:** UC1-B1

				### UC1-AC1: Duplicate
				When a booking is saved, the system shall record it.
				""");
		write("access/rules.md", """
				### RULE-1

				**MUST**
				""");

		RequirementDocuments.Report report = RequirementDocuments.lint(spec);

		assertThat(report.valid()).isFalse();
		assertThat(report.problems()).extracting(RequirementDocumentProblem::source)
			.contains("booking/criteria.md", "access/rules.md");
		assertThat(report.problems()).extracting(RequirementDocumentProblem::describe)
			.anySatisfy(text -> assertThat(text).startsWith("booking/criteria.md:1 UC1-AC1:"))
			.anySatisfy(text -> assertThat(text).startsWith("booking/criteria.md:5 UC1-AC1:"))
			.anySatisfy(text -> assertThat(text).startsWith("access/rules.md:3 RULE-1:"));
		assertThatThrownBy(() -> RequirementDocuments.assertValid(spec)).isInstanceOf(AssertionError.class)
			.hasMessageContaining(report.problems().size() + " problem(s)")
			.hasMessageContaining("booking/criteria.md:1 UC1-AC1:")
			.hasMessageContaining("access/rules.md:3 RULE-1:");
	}

	@Test
	void theCommandLineReportsAValidTreeWithoutExiting() throws IOException {
		write("criteria.md", """
				### UC1-AC1: Record a booking

				When a booking is saved, the system shall record it.
				""");
		assertThatCode(() -> RequirementDocuments.main(new String[] { spec.toString() })).doesNotThrowAnyException();
	}

	@Test
	void thePetClinicSpecificationPassesTheLinter() throws Exception {
		Path petclinic = Path.of(getClass().getResource("/configured-tutorial/criteria.md").toURI()).getParent();

		RequirementDocuments.Report report = RequirementDocuments.lint(petclinic);

		assertThat(report.problems()).isEmpty();
		assertThat(report.criteria()).isEqualTo(52);
		assertThat(report.constraints()).isPositive();
	}

}
