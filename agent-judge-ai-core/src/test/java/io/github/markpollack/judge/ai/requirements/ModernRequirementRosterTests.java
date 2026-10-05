/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.ai.requirements;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.markpollack.judge.ai.ModelBackedJudge;
import io.github.markpollack.judge.ai.model.JudgeModel;
import io.github.markpollack.judge.ai.model.JudgeModelResponse;
import java.nio.file.Path;
import io.github.markpollack.judge.judgment.Check;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModernRequirementRosterTests {

	enum Kind {

		RFC, EARS

	}

	private static final Path CONTEXT = Path.of("/tmp/implementation");

	private static final String COMPLETE = "R-1: PASS - verified\nR-2: PASS - verified\nR-3: PASS - verified";

	private static io.github.markpollack.judge.jury.Jury configured(Kind kind, List<String> ids, JudgeModel model) {
		return kind == Kind.RFC
				? Rfc2119Jury.builder()
					.runtime(model)
					.requirements(ids.stream()
						.map(id -> Rfc2119Requirement.of(id, "test", "MUST", "requirement", "rationale",
								id.equals("R-3") ? "has storage" : null))
						.toList())
					.build()
				: EarsJury.builder()
					.runtime(model)
					.requirements(ids.stream()
						.map(id -> EarsRequirement.of(id, "test", "title", "requirement",
								id.equals("R-3") ? "has storage" : null))
						.toList())
					.build();
	}

	private static io.github.markpollack.judge.verdict.Verdict audit(Kind kind, String text) {
		return configured(kind, List.of("R-1", "R-2", "R-3"),
				request -> new JudgeModelResponse(text, "stub", null, Map.of()))
			.vote();
	}

	private static void roster(io.github.markpollack.judge.verdict.Verdict result, JudgmentStatus... statuses) {
		assertThat(result.roster()).extracting(io.github.markpollack.judge.requirement.Requirement::id)
			.containsExactly("R-1", "R-2", "R-3");
		assertThat(result.compositeAttempts()).extracting(a -> a.verdict().individualByName().get(a.name()).status())
			.containsExactly(statuses);
		assertThat(result.invocations()).hasSize(1);
		assertThat(result.individual()).isEmpty();
	}

	@ParameterizedTest
	@EnumSource(Kind.class)
	void uncertainAndExcludedRequirementsRetainTheirOwnOutcomes(Kind kind) {
		io.github.markpollack.judge.verdict.Verdict result = audit(kind,
				"R-1: FAIL - violated\nR-2: CANNOT_DETERMINE - insufficient evidence\nR-3: NOT_APPLICABLE - no storage");
		assertThat(result.judgment().status()).isEqualTo(JudgmentStatus.FAIL);
		roster(result, JudgmentStatus.FAIL, JudgmentStatus.ABSTAIN, JudgmentStatus.NOT_APPLICABLE);
	}

	@ParameterizedTest
	@EnumSource(Kind.class)
	void missingResponseRemainsAnErrorCheckAlongsideEstablishedFacts(Kind kind) {
		io.github.markpollack.judge.verdict.Verdict result = audit(kind,
				"R-1: FAIL - violated\nR-2: CANNOT_DETERMINE - insufficient evidence");
		assertThat(result.judgment().status()).isEqualTo(JudgmentStatus.FAIL);
		roster(result, JudgmentStatus.FAIL, JudgmentStatus.ABSTAIN, JudgmentStatus.ERROR);
	}

	@ParameterizedTest
	@EnumSource(Kind.class)
	void emptyAndFailedBackendResponsesKeepTheWholeRoster(Kind kind) {
		for (JudgeModelResponse response : List.of(new JudgeModelResponse("", null, null, Map.of()),
				new JudgeModelResponse("backend unavailable", null, null, Map.of(), false))) {
			io.github.markpollack.judge.verdict.Verdict result = configured(kind, List.of("R-1", "R-2", "R-3"),
					request -> response)
				.vote();
			assertThat(result.judgment().status()).isEqualTo(JudgmentStatus.ABSTAIN);
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
			io.github.markpollack.judge.verdict.Verdict result = audit(kind, text);
			assertThat(result.judgment().status()).as(text).isEqualTo(JudgmentStatus.ABSTAIN);
			roster(result, JudgmentStatus.ERROR, JudgmentStatus.PASS, JudgmentStatus.PASS);
		}
	}

	@ParameterizedTest
	@EnumSource(Kind.class)
	void unknownAnswerIdsAreErrorsButObservationsStayNonbinding(Kind kind) {
		io.github.markpollack.judge.verdict.Verdict extra = audit(kind, COMPLETE + "\nR-4: PASS - invented requirement");
		assertThat(extra.judgment().status()).isEqualTo(JudgmentStatus.ABSTAIN);
		roster(extra, JudgmentStatus.PASS, JudgmentStatus.PASS, JudgmentStatus.PASS);
		assertThat(audit(kind, COMPLETE + "\nOBSERVATION R-4: PASS - irrelevant\nOBSERVATION R-1: PASSENGER: prose")
			.judgment()
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
		io.github.markpollack.judge.jury.Jury judge;
		if (kind == Kind.RFC) {
			var source = new ArrayList<>(List.of(Rfc2119Requirement.of("R-1", "test", "MUST", "one", "why", null)));
			judge = Rfc2119Jury.builder()
				.runtime((JudgeModel) request -> new JudgeModelResponse("R-1: PASS - verified", null, null, Map.of()))
				.requirements(source)
				.build();
			source.add(Rfc2119Requirement.of("R-2", "test", "MUST", "two", "why", null));
		}
		else {
			var source = new ArrayList<>(List.of(EarsRequirement.of("R-1", "test", "one", "one", null)));
			judge = EarsJury.builder()
				.runtime((JudgeModel) request -> new JudgeModelResponse("R-1: PASS - verified", null, null, Map.of()))
				.requirements(source)
				.build();
			source.add(EarsRequirement.of("R-2", "test", "two", "two", null));
		}
		var result = judge.vote();
		assertThat(result.conclusion()).isEqualTo(io.github.markpollack.judge.verdict.Verdict.Conclusion.PASS);
		assertThat(result.roster()).extracting(io.github.markpollack.judge.requirement.Requirement::id)
			.containsExactly("R-1");

	}

	@ParameterizedTest
	@EnumSource(Kind.class)
	void thrownOrNullBackendResultsAreContainedWithErrorChecks(Kind kind) {
		for (JudgeModel model : List.<JudgeModel>of(request -> null, request -> {
			throw new IllegalStateException("transport unavailable");
		})) {
			io.github.markpollack.judge.verdict.Verdict result = configured(kind, List.of("R-1", "R-2", "R-3"), model)
				.vote();
			assertThat(result.judgment().status()).isEqualTo(JudgmentStatus.ABSTAIN);
			roster(result, JudgmentStatus.ERROR, JudgmentStatus.ERROR, JudgmentStatus.ERROR);
		}
	}

	@ParameterizedTest
	@EnumSource(Kind.class)
	void illegalAndUnexplainedExclusionsAreErrorChecks(Kind kind) {
		io.github.markpollack.judge.verdict.Verdict illegal = audit(kind,
				COMPLETE.replace("R-1: PASS - verified", "R-1: NOT_APPLICABLE - inconvenient"));
		assertThat(illegal.judgment().status()).isEqualTo(JudgmentStatus.ABSTAIN);
		roster(illegal, JudgmentStatus.NOT_APPLICABLE, JudgmentStatus.PASS, JudgmentStatus.PASS);
		io.github.markpollack.judge.verdict.Verdict unexplained = audit(kind,
				COMPLETE.replace("R-3: PASS - verified", "R-3: NOT_APPLICABLE"));
		assertThat(unexplained.judgment().status()).isEqualTo(JudgmentStatus.ABSTAIN);
		roster(unexplained, JudgmentStatus.PASS, JudgmentStatus.PASS, JudgmentStatus.ERROR);
	}

	@ParameterizedTest
	@EnumSource(Kind.class)
	void exactConfiguredIdentifiersTakePrecedenceOverReplyDecoration(Kind kind) {
		List<JudgmentStatus> outcomes = List.of("R`1", "**R1**", "OBSERVATION-R1")
			.stream()
			.map(id -> configured(kind, List.of(id),
					request -> new JudgeModelResponse(id + ": PASS - verified", null, null, Map.of()))
				.vote()
				.judgment()
				.status())
			.toList();
		assertThat(outcomes).containsExactly(JudgmentStatus.PASS, JudgmentStatus.PASS, JudgmentStatus.PASS);
	}

}
