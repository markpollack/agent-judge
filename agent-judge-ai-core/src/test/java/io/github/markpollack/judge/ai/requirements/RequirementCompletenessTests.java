/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.ai.requirements;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A strict load refuses a requirement sentence that is visibly incomplete. A truncated
 * sentence such as "When a confirmed booking is saved," passed every earlier check, and a
 * judge was then asked to assess half a requirement.
 */
class RequirementCompletenessTests {

	private static List<RequirementDocumentProblem> ears(String sentence) {
		return EarsCriterion.parse("### UC1-AC32: Confirm a saved booking\n\n" + sentence + "\n", "criteria.md")
			.problems();
	}

	private static List<RequirementDocumentProblem> rfc(String statement) {
		return Rfc2119Constraint.parse("### RULE-1\n\n**MUST** " + statement + "\n\n**Reason:** audit\n", "rules.md")
			.problems();
	}

	@Test
	void aTruncatedEarsSentenceIsAStrictProblemOnItsLine() {
		// Truncated before its response clause, it has neither "shall" nor a sentence end.
		assertThat(ears("When a confirmed booking is saved,")).hasSize(2).allSatisfy(problem -> {
			assertThat(problem.id()).isEqualTo("UC1-AC32");
			assertThat(problem.line()).isEqualTo(3);
		}).extracting(RequirementDocumentProblem::reason).anySatisfy(reason -> assertThat(reason).contains("shall"))
			.anySatisfy(reason -> assertThat(reason).contains("terminal punctuation"));
		assertThat(ears("When a confirmed booking is saved, the system shall record the booking and"))
			.singleElement()
			.satisfies(problem -> assertThat(problem.reason()).contains("terminal punctuation"));
	}

	@Test
	void anEarsSentenceWithoutShallIsAStrictProblem() {
		assertThat(ears("When a confirmed booking is saved, the booking is recorded.")).singleElement()
			.satisfies(problem -> assertThat(problem.reason()).contains("shall"));
	}

	@Test
	void completeEarsSentencesStayValid() {
		assertThat(ears("When a confirmed booking is saved, the system shall send exactly five confirmations."))
			.isEmpty();
		assertThat(ears("While the clinic is closed, the system shall show only `BOOKED` appointments.")).isEmpty();
		assertThat(ears("The system shall answer within one second (measured at the server).")).isEmpty();
		assertThat(ears("If the slot is taken, then the system shall offer another slot!")).isEmpty();
	}

	@Test
	void anRfc2119StatementWithoutTerminalPunctuationIsAStrictProblem() {
		assertThat(rfc("record the booking")).singleElement().satisfies(problem -> {
			assertThat(problem.id()).isEqualTo("RULE-1");
			assertThat(problem.line()).isEqualTo(3);
			assertThat(problem.reason()).contains("terminal punctuation");
		});
		assertThat(rfc("record the booking.")).isEmpty();
	}

	@Test
	void permissiveLoadingKeepsTheCriterionAndLogsTheProblem() {
		var parsed = EarsCriterion.parseSnapshot("### UC1-AC32: Confirm a saved booking\n\nWhen a confirmed booking is saved,\n",
				"criteria.md");
		assertThat(parsed).extracting(EarsCriterion::requirement).containsExactly("When a confirmed booking is saved,");
	}

}
