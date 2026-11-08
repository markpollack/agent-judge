/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.ai.requirements;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A requirement sentence is its whole first paragraph. Authors wrap long EARS sentences
 * across lines; Markdown reads a soft wrap as a space, and so must the reader, or the
 * judge is shown a truncated requirement that omits its final clause.
 */
class WrappedRequirementParagraphTests {

	/** The shape of the booking criterion UC1-AC32: the binding clause comes last. */
	private static final String SENTENCE = "When a confirmed booking is saved, the system shall record the booking "
			+ "and send exactly five confirmations.";

	private static final String FINAL_CLAUSE = "send exactly five confirmations.";

	private static EarsCriterion only(String document) {
		List<EarsCriterion> parsed = EarsCriterion.parseSnapshot(document, "criteria.md");
		assertThat(parsed).hasSize(1);
		return parsed.getFirst();
	}

	private static List<RequirementDocumentProblem> problems(String document) {
		return EarsCriterion.parse(document, "criteria.md").problems();
	}

	@Test
	void wrappedRequirementRendersExactlyAsItsSingleLineForm() {
		EarsCriterion single = only("""
				### UC1-AC32: Confirm a saved booking

				**Covers:** UC1-B32

				%s
				""".formatted(SENTENCE));
		EarsCriterion wrapped = only("""
				### UC1-AC32: Confirm a saved booking

				**Covers:** UC1-B32

				When a confirmed booking is saved,
				the system shall record the booking
				and send exactly five confirmations.
				""");

		assertThat(single.requirement()).isEqualTo(SENTENCE).endsWith(FINAL_CLAUSE);
		assertThat(wrapped.requirement()).isEqualTo(single.requirement());
		assertThat(wrapped.asPrompt()).isEqualTo(single.asPrompt()).endsWith(FINAL_CLAUSE);
		assertThat(wrapped.id()).isEqualTo("UC1-AC32");
		assertThat(wrapped.title()).isEqualTo("Confirm a saved booking");
	}

	@Test
	void paragraphEndsAtAMetadataLineAndApplicabilityStaysSeparate() {
		EarsCriterion criterion = only("""
				### UC1-AC32: Confirm a saved booking
				When a confirmed booking is saved,
				the system shall record the booking
				and send exactly five confirmations.
				**Covers:** UC1-B32
				Applies when: the clinic sends confirmations
				""");

		assertThat(criterion.requirement()).isEqualTo(SENTENCE);
		assertThat(criterion.applicability()).isEqualTo("the clinic sends confirmations");
	}

	@Test
	void applicabilityLineInsideTheParagraphEndsTheSentence() {
		EarsCriterion criterion = only("""
				### UC1-AC32: Confirm a saved booking

				When a confirmed booking is saved,
				the system shall record the booking
				and send exactly five confirmations.
				Applies when: the clinic sends confirmations
				Explanatory prose that is not part of the sentence.
				""");

		assertThat(criterion.requirement()).isEqualTo(SENTENCE);
		assertThat(criterion.applicability()).isEqualTo("the clinic sends confirmations");
	}

	@Test
	void laterExplanatoryParagraphsAreNotRead() {
		EarsCriterion criterion = only("""
				### UC1-AC32: Confirm a saved booking

				When a confirmed booking is saved,
				the system shall record the booking
				and send exactly five confirmations.

				The five confirmations go to the owner, the vet, the front desk,
				the billing system and the audit log.
				""");

		assertThat(criterion.requirement()).isEqualTo(SENTENCE);
	}

	@Test
	void hardBreaksJoinLikeSoftWrapsWithoutTheirMarkup() {
		EarsCriterion spaces = only("### UC1-AC32: Confirm a saved booking\n\n"
				+ "When a confirmed booking is saved,  \nthe system shall record the booking  \n"
				+ "and send exactly five confirmations.\n");
		EarsCriterion backslash = only("### UC1-AC32: Confirm a saved booking\n\n"
				+ "When a confirmed booking is saved,\\\nthe system shall record the booking\\\n"
				+ "and send exactly five confirmations.\n");

		assertThat(spaces.requirement()).isEqualTo(SENTENCE);
		assertThat(backslash.requirement()).isEqualTo(SENTENCE);
	}

	@Test
	void listAndQuoteMarkersAreStrippedFromContinuationLinesOnly() {
		List<EarsCriterion> parsed = EarsCriterion.parseSnapshot("""
				### UC1-AC1: Listed

				- When listed, the system shall wrap
				  the sentence onto a second line.

				### UC1-AC2: Quoted

				> When quoted, the system shall wrap
				> the sentence onto a second line.

				### UC1-AC3: Lazy quote

				> When quoted lazily, the system shall wrap
				the sentence onto a second line.
				""", "criteria.md");

		// The first line stays the document's own line, marker included, as before.
		assertThat(parsed).extracting(EarsCriterion::requirement)
			.containsExactly("- When listed, the system shall wrap the sentence onto a second line.",
					"> When quoted, the system shall wrap the sentence onto a second line.",
					"> When quoted lazily, the system shall wrap the sentence onto a second line.");
	}

	@Test
	void problemLineNumbersStayOnTheLinesThatCarryThem() {
		String document = """
				### UC1-AC32: Confirm a saved booking

				When a confirmed booking is saved,
				the system shall record the booking
				and send exactly five confirmations.
				Applies when:

				### UC1-AC33: No sentence

				**Covers:** UC1-B33
				""";

		assertThat(problems(document)).extracting(RequirementDocumentProblem::line).containsExactly(6, 8);
		assertThat(only(document.substring(0, document.indexOf("### UC1-AC33"))).requirement()).isEqualTo(SENTENCE);
	}

}
