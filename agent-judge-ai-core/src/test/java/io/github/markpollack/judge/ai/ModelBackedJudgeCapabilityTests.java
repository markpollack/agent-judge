/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.ai;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.Judges;
import io.github.markpollack.judge.ai.model.JudgeModel;
import io.github.markpollack.judge.ai.model.JudgeModelRequest;
import io.github.markpollack.judge.ai.model.JudgeModelResponse;
import io.github.markpollack.judge.ai.prompt.JudgePromptTemplate;
import io.github.markpollack.judge.completion.CompletionEvidence;
import io.github.markpollack.judge.description.SeatDescription;
import io.github.markpollack.judge.description.SimpleJuryDescription;
import io.github.markpollack.judge.jury.ConsensusStrategy;
import io.github.markpollack.judge.jury.ErrorHandling;
import io.github.markpollack.judge.jury.Juries;
import io.github.markpollack.judge.jury.Jury;
import io.github.markpollack.judge.jury.ExclusionHandling;
import io.github.markpollack.judge.jury.SimpleJury;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.tuple;

/**
 * A model-backed judge declares its exclusion capability, or it does not have one.
 *
 * <p>
 * The three witnesses the design pins: an ordinary model judge composes under the default
 * refusing policy because it declares nothing; a judge that declares a condition carries
 * it through {@code Judges.notApplicableCapability}; and the rename
 * {@code Juries.fromJudges} applies to break a name collision does not strip the
 * declaration off the judge underneath, which is the one place a capability could
 * silently disappear.
 * </p>
 */
@DisplayName("ModelBackedJudge exclusion capability")
class ModelBackedJudgeCapabilityTests {

	private static final String CONDITION = "criteria UC3-AC7 and UC3-AC9 are conditional";

	private static JudgeModel model(String text) {
		return new JudgeModel() {
			@Override
			public JudgeModelResponse generate(JudgeModelRequest request) {
				return new JudgeModelResponse(text, "test-model", null, Map.of());
			}
		};
	}

	private static ModelBackedJudge judge(String name, String notApplicableWhen, String answer) {
		ModelBackedJudge.Builder<CompletionEvidence> builder = ModelBackedJudge.<io.github.markpollack.judge.completion.CompletionEvidence>builder()
			.variables(io.github.markpollack.judge.ai.prompt.CompletionVariables::from)
			.name(name)
			.description("a model-backed judge")
			.promptTemplate(JudgePromptTemplate.fromString(name, "assess {goal}"))
			.model(model(answer))
			.judgmentClassifier(response -> "excluded".equals(response.text()) ? Judgment.notApplicable(CONDITION)
					: Judgment.pass(response.text()));
		if (notApplicableWhen != null) {
			builder.notApplicableWhen(notApplicableWhen);
		}
		return builder
			.evidence(io.github.markpollack.judge.completion.CompletionEvidence.builder()
				.request("capability fixture")
				.build())
			.build();
	}

	@Test
	@DisplayName("an unconditional model judge declares nothing and builds under the default refusing policy")
	void unconditionalJudgeBuildsUnderRefuse() {
		ModelBackedJudge unconditional = judge("correctness", null, "ok");

		assertThat(unconditional.metadata().notApplicableWhen()).isNull();
		assertThat(Judges.notApplicableCapability(unconditional)).isEmpty();
		assertThatCode(() -> SimpleJury.builder().judge(unconditional).votingStrategy(new ConsensusStrategy()).build())
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("a declared condition reaches the lookup, the description and the jury's bound")
	void aDeclaredConditionIsVisibleEverywhere() {
		ModelBackedJudge conditional = judge("rubric", CONDITION, "excluded");

		assertThat(Judges.notApplicableCapability(conditional)).contains(CONDITION);
		assertThat(Judges.describe(conditional).notApplicableWhen()).isEqualTo(CONDITION);

		Jury jury = SimpleJury.builder()
			.seat(io.github.markpollack.judge.jury.JudgeSeat.named("rubric", conditional).notApplicableWhen(CONDITION))
			.votingStrategy(new ConsensusStrategy(ErrorHandling.PROPAGATE, ExclusionHandling.EXCLUDE))
			.build();

		assertThat(jury.aggregateMayBeNotApplicable()).isTrue();
		Verdict verdict = jury.vote();
		assertThat(verdict.individualByName().get("rubric").status()).isEqualTo(JudgmentStatus.NOT_APPLICABLE);
		assertThat(verdict.judgment().status()).as("every criterion was excluded, so the aggregate is too")
			.isEqualTo(JudgmentStatus.NOT_APPLICABLE);
	}

	@Test
	@DisplayName("the deduplicating rename keeps the declaration on the judge it wraps")
	void deduplicationPreservesTheDeclaration() {
		Judge first = judge("rubric", CONDITION, "excluded");
		Judge second = judge("rubric", CONDITION, "excluded");

		Jury jury = SimpleJury.builder()
			.seat(io.github.markpollack.judge.jury.JudgeSeat.named("rubric", first).notApplicableWhen(CONDITION))
			.seat(io.github.markpollack.judge.jury.JudgeSeat.named("rubric-2", second).notApplicableWhen(CONDITION))
			.votingStrategy(new ConsensusStrategy(ErrorHandling.PROPAGATE, ExclusionHandling.EXCLUDE))
			.build();

		SimpleJuryDescription description = (SimpleJuryDescription) jury.describe();
		assertThat(description.seats())
			.extracting(SeatDescription::verdictKey, seat -> seat.judge().notApplicableWhen())
			.containsExactly(tuple("rubric", CONDITION), tuple("rubric-2", CONDITION));
		assertThat(jury.aggregateMayBeNotApplicable()).isTrue();
	}

	@Test
	@DisplayName("the portable description says whether this seat may leave the denominator")
	void thePortableDescriptionCarriesIt() {
		assertThat(Judges.describe(judge("rubric", CONDITION, "excluded")).toPortable())
			.containsEntry("notApplicableWhen", Map.of("declared", true, "value", CONDITION));
		assertThat(Judges.describe(judge("correctness", null, "ok")).toPortable()).containsEntry("notApplicableWhen",
				Map.of("declared", false));
	}

}
