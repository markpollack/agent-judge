package io.github.markpollack.judge.ai.requirements;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.markpollack.judge.ai.ModelBackedJudge;
import io.github.markpollack.judge.ai.model.JudgeModel;
import io.github.markpollack.judge.ai.model.JudgeModelResponse;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.Check;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModernRequirementRosterTests {

	enum Kind {

		RFC, EARS

	}

	private static final JudgmentContext CONTEXT = JudgmentContext.builder().goal("audit").build();

	private static final String COMPLETE = "R-1: PASS - verified\nR-2: PASS - verified\nR-3: PASS - verified";

	private static ModelBackedJudge configured(Kind kind, List<String> ids, JudgeModel model) {
		return kind == Kind.RFC
				? Rfc2119Judge.create("requirements",
						ids.stream()
							.map(id -> new Rfc2119Constraint(id, "MUST", "requirement", "rationale",
									id.equals("R-3") ? "has storage" : null))
							.toList(),
						model)
				: EarsJudge.create("requirements", ids.stream()
					.map(id -> new EarsCriterion(id, "title", "requirement", id.equals("R-3") ? "has storage" : null))
					.toList(), model);
	}

	private static Judgment audit(Kind kind, String text) {
		return configured(kind, List.of("R-1", "R-2", "R-3"),
				request -> new JudgeModelResponse(text, "stub", null, Map.of()))
			.judge(CONTEXT);
	}

	private static void roster(Judgment result, JudgmentStatus... statuses) {
		assertThat(result.checks()).extracting(Check::id).containsExactly("R-1", "R-2", "R-3");
		assertThat(result.checks()).extracting(c -> c.judgment().status()).containsExactly(statuses);
		assertThat(result.checks()).allSatisfy(c -> assertThat(c.judgment().checks()).isEmpty());
		assertThat(result.metadata()
			.get(result.metadata().containsKey("constraintsTotal") ? "constraintsTotal" : "criteriaTotal"))
			.isEqualTo(3);
	}

	@ParameterizedTest
	@EnumSource(Kind.class)
	void uncertainAndExcludedRequirementsRetainTheirOwnOutcomes(Kind kind) {
		Judgment result = audit(kind,
				"R-1: FAIL - violated\nR-2: CANNOT_DETERMINE - insufficient evidence\nR-3: NOT_APPLICABLE - no storage");
		assertThat(result.status()).isEqualTo(JudgmentStatus.FAIL);
		roster(result, JudgmentStatus.FAIL, JudgmentStatus.ABSTAIN, JudgmentStatus.NOT_APPLICABLE);
	}

	@ParameterizedTest
	@EnumSource(Kind.class)
	void missingResponseRemainsAnErrorCheckAlongsideEstablishedFacts(Kind kind) {
		Judgment result = audit(kind, "R-1: FAIL - violated\nR-2: CANNOT_DETERMINE - insufficient evidence");
		assertThat(result.status()).isEqualTo(JudgmentStatus.ERROR);
		roster(result, JudgmentStatus.FAIL, JudgmentStatus.ABSTAIN, JudgmentStatus.ERROR);
	}

	@ParameterizedTest
	@EnumSource(Kind.class)
	void emptyAndFailedBackendResponsesKeepTheWholeRoster(Kind kind) {
		for (JudgeModelResponse response : List.of(new JudgeModelResponse("", null, null, Map.of()),
				new JudgeModelResponse("backend unavailable", null, null, Map.of("successful", false)))) {
			Judgment result = configured(kind, List.of("R-1", "R-2", "R-3"), request -> response).judge(CONTEXT);
			assertThat(result.status()).isEqualTo(JudgmentStatus.ERROR);
			roster(result, JudgmentStatus.ERROR, JudgmentStatus.ERROR, JudgmentStatus.ERROR);
		}
	}

	@ParameterizedTest
	@EnumSource(Kind.class)
	void ambiguousAndMalformedDeclaredRepliesAreProtocolErrors(Kind kind) {
		for (String text : List.of(COMPLETE + "\nR-1: FAIL - contradictory",
				COMPLETE.replace("R-1: PASS", "R-1: PASSENGER"),
				COMPLETE.replace("R-1: PASS - verified", "R-1: CANNOT_GUESS - unsupported token"),
				COMPLETE.replace("R-1: PASS - verified", "R-1: PASS"),
				COMPLETE.replace("R-1: PASS - verified", "R-1: PASS - "),
				COMPLETE + "\nR-1 PASS - malformed duplicate")) {
			Judgment result = audit(kind, text);
			assertThat(result.status()).as(text).isEqualTo(JudgmentStatus.ERROR);
			roster(result, JudgmentStatus.ERROR, JudgmentStatus.PASS, JudgmentStatus.PASS);
		}
	}

	@ParameterizedTest
	@EnumSource(Kind.class)
	void unknownAnswerIdsAreErrorsButObservationsStayNonbinding(Kind kind) {
		Judgment extra = audit(kind, COMPLETE + "\nR-4: PASS - invented requirement");
		assertThat(extra.status()).isEqualTo(JudgmentStatus.ERROR);
		roster(extra, JudgmentStatus.PASS, JudgmentStatus.PASS, JudgmentStatus.PASS);
		assertThat(audit(kind, COMPLETE + "\nOBSERVATION R-4: PASS - irrelevant\nOBSERVATION R-1: PASSENGER: prose")
			.status()).isEqualTo(JudgmentStatus.PASS);
	}

	@ParameterizedTest
	@EnumSource(Kind.class)
	void duplicateConfigurationIsRejectedBeforeCallingTheBackend(Kind kind) {
		AtomicInteger calls = new AtomicInteger();
		assertThatThrownBy(() -> configured(kind, List.of("R-1", "R-1"), request -> {
			calls.incrementAndGet();
			return new JudgeModelResponse(COMPLETE, null, null, Map.of());
		})).isInstanceOf(IllegalArgumentException.class);
		assertThat(calls).hasValue(0);
		assertThatThrownBy(() -> configured(kind, List.of(" "), request -> null))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest
	@EnumSource(Kind.class)
	void configurationRosterIsSnapshottedBeforeInference(Kind kind) {
		ModelBackedJudge judge;
		if (kind == Kind.RFC) {
			var source = new ArrayList<>(List.of(new Rfc2119Constraint("R-1", "MUST", "one", "why")));
			judge = Rfc2119Judge.create("rules", source,
					request -> new JudgeModelResponse("R-1: PASS - verified", null, null, Map.of()));
			source.add(new Rfc2119Constraint("R-2", "MUST", "two", "why"));
		}
		else {
			var source = new ArrayList<>(List.of(new EarsCriterion("R-1", "one", "one")));
			judge = EarsJudge.create("criteria", source,
					request -> new JudgeModelResponse("R-1: PASS - verified", null, null, Map.of()));
			source.add(new EarsCriterion("R-2", "two", "two"));
		}
		Judgment result = judge.judge(CONTEXT);
		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(result.checks()).extracting(Check::id).containsExactly("R-1");
	}

	@ParameterizedTest
	@EnumSource(Kind.class)
	void thrownOrNullBackendResultsAreContainedWithErrorChecks(Kind kind) {
		for (JudgeModel model : List.<JudgeModel>of(request -> null, request -> {
			throw new IllegalStateException("transport unavailable");
		})) {
			Judgment result = configured(kind, List.of("R-1", "R-2", "R-3"), model).judge(CONTEXT);
			assertThat(result.status()).isEqualTo(JudgmentStatus.ERROR);
			roster(result, JudgmentStatus.ERROR, JudgmentStatus.ERROR, JudgmentStatus.ERROR);
		}
	}

	@ParameterizedTest
	@EnumSource(Kind.class)
	void illegalAndUnexplainedExclusionsAreErrorChecks(Kind kind) {
		Judgment illegal = audit(kind, COMPLETE.replace("R-1: PASS - verified", "R-1: NOT_APPLICABLE - inconvenient"));
		assertThat(illegal.status()).isEqualTo(JudgmentStatus.ERROR);
		roster(illegal, JudgmentStatus.ERROR, JudgmentStatus.PASS, JudgmentStatus.PASS);
		Judgment unexplained = audit(kind, COMPLETE.replace("R-3: PASS - verified", "R-3: NOT_APPLICABLE"));
		assertThat(unexplained.status()).isEqualTo(JudgmentStatus.ERROR);
		roster(unexplained, JudgmentStatus.PASS, JudgmentStatus.PASS, JudgmentStatus.ERROR);
	}

	@ParameterizedTest
	@EnumSource(Kind.class)
	void exactConfiguredIdentifiersTakePrecedenceOverReplyDecoration(Kind kind) {
		List<JudgmentStatus> outcomes = List.of("R`1", "**R1**", "OBSERVATION-R1")
			.stream()
			.map(id -> configured(kind, List.of(id),
					request -> new JudgeModelResponse(id + ": PASS - verified", null, null, Map.of()))
				.judge(CONTEXT)
				.status())
			.toList();
		assertThat(outcomes).containsExactly(JudgmentStatus.PASS, JudgmentStatus.PASS, JudgmentStatus.PASS);
	}

}
