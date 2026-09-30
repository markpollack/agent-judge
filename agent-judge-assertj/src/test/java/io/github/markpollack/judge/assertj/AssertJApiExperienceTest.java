/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertj;

import java.util.Map;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.acceptance.AcceptanceAction;
import io.github.markpollack.judge.acceptance.AcceptanceDecision;
import io.github.markpollack.judge.acceptance.AcceptancePolicy;
import io.github.markpollack.judge.ai.JudgmentClassifiers;
import io.github.markpollack.judge.ai.ModelBackedJudge;
import io.github.markpollack.judge.ai.model.JudgeModelResponse;
import io.github.markpollack.judge.ai.prompt.JudgePromptTemplate;
import io.github.markpollack.judge.assertions.RequirementAssertions;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.jury.AllMustPassStrategy;
import io.github.markpollack.judge.jury.Jury;
import io.github.markpollack.judge.jury.SimpleJury;
import io.github.markpollack.judge.jury.interpretation.RequirementOutcome;
import io.github.markpollack.judge.requirement.Requirement;
import org.junit.jupiter.api.Test;

import static io.github.markpollack.judge.assertj.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/** Five progressive examples for reading the public API. No network or credentials. */
class AssertJApiExperienceTest {

	private static final Requirement<String> TWO_PLUS_TWO_REQUIREMENT = Requirement.text("two-plus-two", "1",
			"The response correctly communicates that 2 + 2 equals 4.");

	private static final String RESPONSE = "Two pairs make a group of four.";

	// The judging backend is stubbed for this API test. This exercises prompt rendering
	// and label classification; it does not claim to measure a model's accuracy.
	private static final Judge<String> TWO_PLUS_TWO_RESPONSE_JUDGE = ModelBackedJudge.<String>builder()
		.name("two-plus-two-response")
		.promptTemplate(JudgePromptTemplate.fromString("two-plus-two", """
				Requirement: {{requirement}}
				Response: {{response}}
				Does the response satisfy the requirement? Reply satisfied, violated, or unknown.
				"""))
		.variables(response -> Map.of("requirement", TWO_PLUS_TWO_REQUIREMENT.text(), "response", response))
		.model(request -> {
			assertThat(request.messages().getFirst().content()).contains(TWO_PLUS_TWO_REQUIREMENT.text(), RESPONSE);
			return new JudgeModelResponse("satisfied", "local-api-fixture", null, null);
		})
		.judgmentClassifier(JudgmentClassifiers.passFail("satisfied", "violated"))
		.build();

	@Test
	void deterministicArithmetic() {
		var calculator = new Calculator();
		assertThat(calculator.add(2, 2)).isEqualTo(4);
	}

	@Test
	void textualArithmetic() {
		assertThat(TWO_PLUS_TWO_REQUIREMENT).judgedBy(TWO_PLUS_TWO_RESPONSE_JUDGE).withEvidence(RESPONSE).isSatisfied();
	}

	@Test
	void explicitAcceptancePolicy() {
		AcceptancePolicy explainedJudgment = judgment -> judgment.reasoning().isBlank()
				? new AcceptanceDecision(AcceptanceAction.ABSTAIN, "An explanation is required")
				: new AcceptanceDecision(AcceptanceAction.RELY, "The judgment includes an explanation");
		assertThat(TWO_PLUS_TWO_REQUIREMENT).judgedBy(TWO_PLUS_TWO_RESPONSE_JUDGE)
			.withEvidence(RESPONSE)
			.withAcceptancePolicy(explainedJudgment)
			.isSatisfied();
	}

	@Test
	void jury() {
		Judge<String> nonemptyResponseJudge = response -> response.isBlank() ? Judgment.fail("The response is empty")
				: Judgment.pass("The response has text");
		Jury<String> jury = SimpleJury.<String>builder()
			.judge(nonemptyResponseJudge)
			.judge(TWO_PLUS_TWO_RESPONSE_JUDGE)
			.votingStrategy(new AllMustPassStrategy())
			.build();
		assertThat(TWO_PLUS_TWO_REQUIREMENT).judgedBy(jury).withEvidence(RESPONSE).isSatisfied();
	}

	@Test
	void retainedResultInspection() {
		var result = RequirementAssertions.relyingOnJudgment()
			.evaluate(TWO_PLUS_TWO_REQUIREMENT, TWO_PLUS_TWO_RESPONSE_JUDGE, RESPONSE, null);
		assertThat(result.interpretation().outcome()).isEqualTo(RequirementOutcome.SATISFIED);
		assertThat(result.verdict().individual()).hasSize(1);
		RequirementAssertions.requireSatisfied(result);
		RequirementAssertions.requireSatisfied(result);
	}

	private record Calculator() {
		int add(int left, int right) {
			return left + right;
		}
	}

}
