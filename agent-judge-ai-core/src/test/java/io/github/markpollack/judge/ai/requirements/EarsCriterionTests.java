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
 * Reading the roster out of the document somebody wrote.
 *
 * <p>The judge does not get to choose its criteria, so this is where the roster is fixed. A
 * document with 52 criteria must yield 52; anything less has silently sampled the specification.
 */
class EarsCriterionTests {

	@TempDir
	Path directory;

	@Test
	void everyCriterionInTheDocumentIsRead() {
		Path file = write("""
			    # Acceptance Criteria: UC5 — Resolve a Request Through Staff

			    ## Functional

			    ### UC5-AC1: Persist every defined fallback

			    **Covers:** UC5-B1

			    When automation reaches `UNSUPPORTED_LANGUAGE`, the system shall persist the request.

			    ### UC5-AC2: Represent ordinary exhaustion as fallback

			    **Covers:** UC5-B2

			    When automated matching has no feasible candidate, the system shall represent the outcome.
			    """);

		List<EarsCriterion> criteria = EarsCriterion.from(file);

		assertThat(criteria).hasSize(2);
		assertThat(criteria.get(0).id()).isEqualTo("UC5-AC1");
		assertThat(criteria.get(0).title()).isEqualTo("Persist every defined fallback");
		assertThat(criteria.get(0).requirement()).startsWith("When automation reaches");
		assertThat(criteria.get(1).id()).isEqualTo("UC5-AC2");
	}

	@Test
	void theCoversLineAndSectionHeadingsAreNotRequirements() {
		// The requirement is the sentence, not the traceability annotation above it.
		Path file = write("""
			    ### UC5-AC1: Persist every defined fallback

			    **Covers:** UC5-B1

			    When automation reaches a fallback, the system shall persist the request.
			    """);

		assertThat(EarsCriterion.from(file)).singleElement()
			.extracting(EarsCriterion::requirement)
			.isEqualTo("When automation reaches a fallback, the system shall persist the request.");
	}

	@Test
	void onlyTheFirstSentenceUnderAHeadingIsTheRequirement() {
		// Prose that follows belongs to the document, not to the roster.
		Path file = write("""
			    ### UC5-AC1: Persist every defined fallback

			    When automation reaches a fallback, the system shall persist the request.

			    This paragraph explains the reasoning and is not a second criterion.
			    """);

		assertThat(EarsCriterion.from(file)).hasSize(1);
	}

	@Test
	void aHeadingWithNothingUnderItYieldsNoCriterion() {
		Path file = write("""
			    ### UC5-AC1: A criterion nobody finished writing

			    ### UC5-AC2: Represent ordinary exhaustion as fallback

			    When matching has no feasible candidate, the system shall represent the outcome.
			    """);

		assertThat(EarsCriterion.from(file)).singleElement()
			.extracting(EarsCriterion::id).isEqualTo("UC5-AC2");
	}

	@Test
	void anEmptyDocumentYieldsNoCriteriaRatherThanFailing() {
		assertThat(EarsCriterion.from(write("# Acceptance Criteria\n\nNothing written yet.\n"))).isEmpty();
	}

	@Test
	void namedCriteriaComeBackInTheDocumentsOrder() {
		List<EarsCriterion> all = EarsCriterion.from(threeCriteria());

		assertThat(EarsCriterion.select(all, "UC5-AC3", "UC5-AC1").stream().map(EarsCriterion::id))
			.as("the document's order, not the caller's")
			.containsExactly("UC5-AC1", "UC5-AC3");
	}

	@Test
	void askingForACriterionTheDocumentDoesNotHaveIsAnError() {
		// Silently returning two of three would evaluate a roster nobody wrote.
		List<EarsCriterion> all = EarsCriterion.from(threeCriteria());

		assertThatThrownBy(() -> EarsCriterion.select(all, "UC5-AC1", "UC5-AC9"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("asked for 2")
			.hasMessageContaining("found 1");
	}

	@Test
	void theCriterionIsAskedByIdentifierAndSentence() {
		EarsCriterion criterion = EarsCriterion.from(threeCriteria()).get(0);

		assertThat(criterion.asPrompt()).isEqualTo("UC5-AC1: " + criterion.requirement());
	}

	@Test
	void anUnreadableDocumentSaysWhichOne() {
		Path missing = directory.resolve("nothing-here.md");

		assertThatThrownBy(() -> EarsCriterion.from(missing))
			.isInstanceOf(UncheckedIOException.class)
			.hasMessageContaining("nothing-here.md");
	}

	private Path threeCriteria() {
		return write("""
			    ### UC5-AC1: First

			    When a thing happens, the system shall do the first thing.

			    ### UC5-AC2: Second

			    If a thing happens, then the system shall do the second thing.

			    ### UC5-AC3: Third

			    While a state holds, the system shall do the third thing.
			    """);
	}

	private Path write(String content) {
		try {
			Path file = Files.createTempFile(directory, "criteria", ".md");
			Files.writeString(file, content);
			return file;
		}
		catch (java.io.IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}
