/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertj;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.*;
import io.github.markpollack.judge.*;
import io.github.markpollack.judge.judgment.*;
import io.github.markpollack.judge.jury.*;
import io.github.markpollack.judge.requirement.*;
import io.github.markpollack.judge.policy.*;
import io.github.markpollack.judge.evaluation.*;
import io.github.markpollack.judge.reporting.VerdictReport;
import io.github.markpollack.judge.serialization.VerdictCodec;
import io.github.markpollack.judge.assertions.RequirementAssertionError;
import static io.github.markpollack.judge.assertj.Assertions.*;
import static org.assertj.core.api.Assertions.*;

/** Progressive examples used by the README. All providers here are deterministic. */
class AssertJApiExperienceTest {

	record ApiLimit(int maximumBreakingChanges) {
	}

	record ReleaseEvidence(int breakingChanges, boolean observable, String securityReport) {
	}

	@Test
	void ordinaryCheck() {
		Judge<Integer> positive = n -> n > 0 ? Judgment.pass("positive") : Judgment.fail("not positive");
		assertThatEvidence(42).judgedBy(positive).isPassed();
	}

	@Test
	void nativeRequirement() {
		var api = new Requirement<ApiLimit>("api", "1", "No breaking changes", new ApiLimit(0),
				Requirement.text("source", "1", "Maximum breaking changes: 0").source());
		RequirementJudge<ApiLimit, Integer> compatible = (r,
				count) -> count <= r.specification().maximumBreakingChanges() ? Judgment.pass("compatible")
						: Judgment.fail("breaking change");
		assertThat(api).judgedBy(compatible).withEvidence(0).isSatisfied();
	}

	@Test
	void completeMixedAssignmentsAndExplicitSelectors() {
		var security = Requirement.text("security", "1", "No critical vulnerabilities");
		var compatibility = new Requirement<ApiLimit>("compatibility", "1", "No breaking changes", new ApiLimit(0),
				Requirement.text("source", "1", "Maximum breaking changes: 0").source());
		var observability = Requirement.text("observability", "1", "Required metrics exist");
		var readiness = new Requirement<AllOf>("readiness", "1", "Ready to deploy",
				new AllOf(List.of(security, compatibility, observability)),
				Requirement.text("source", "1", "security AND compatibility AND observability").source());
		RequirementJury<String, String> opinions = RequirementJuries.voting(new MajorityVotingStrategy(),
				List.of((r, e) -> Judgment.pass("scanner A"), (r, e) -> Judgment.pass("scanner B"),
						(r, e) -> Judgment.fail("dissent")));
		RequirementJudge<ApiLimit, Integer> compatible = (r,
				count) -> count <= r.specification().maximumBreakingChanges() ? Judgment.pass("compatible")
						: Judgment.fail("breaking");
		RequirementJudge<String, Boolean> observable = (r, present) -> present ? Judgment.pass("metrics exist")
				: Judgment.fail("metrics missing");
		var prepared = Assignments.<ReleaseEvidence>forRequirement(readiness)
			.jury(security, ReleaseEvidence::securityReport, opinions)
			.judge(compatibility, ReleaseEvidence::breakingChanges, compatible)
			.judge(observability, ReleaseEvidence::observable, observable)
			.validate();
		var verdict = prepared.vote(new ReleaseEvidence(0, false, "scan"));
		var result = Evaluations.apply(verdict, v -> new PolicyDecision(PolicyAction.RELY, "Trust this rejection"));
		assertThat(result).hasConclusion(Verdict.Conclusion.FAIL);
		assertThat(result.verdict().compositeAttempts().getFirst().verdict().individual()).hasSize(3);
		assertThat(result.verdict().compositeAttempts()).extracting(a -> a.verdict().conclusion())
			.containsExactly(Verdict.Conclusion.PASS, Verdict.Conclusion.PASS, Verdict.Conclusion.FAIL);
		assertThatThrownBy(() -> assertThat(result).isSatisfied()).isInstanceOf(RequirementAssertionError.class);
		var codec = new VerdictCodec(Map.of("apiLimit", ApiLimit.class));
		var reopened = codec.readEvaluation(codec.write(result));
		assertThat(reopened).isEqualTo(result);
		assertThat(VerdictReport.of(reopened.verdict()).attempts()).hasSize(3);
	}

	@Test
	void nestedAllOfRetainsParentAndChildAssociations() {
		var a = Requirement.text("a", "1", "A");
		var b = Requirement.text("b", "1", "B");
		var inner = new Requirement<AllOf>("inner", "1", "Both", new AllOf(List.of(a, b)),
				Requirement.text("source", "1", "A AND B").source());
		var outer = new Requirement<AllOf>("outer", "1", "Inner", new AllOf(List.of(inner)),
				Requirement.text("source", "1", "Inner required").source());
		RequirementJudge<String, String> check = (r, e) -> Judgment.pass(r.specification());
		// The nested evaluator prepares against the actual child supplied by the outer
		// parent.
		RequirementJury<AllOf, String> child = (actual, evidence) -> Assignments.<String>forRequirement(actual)
			.judge(a, check)
			.judge(b, check)
			.validate()
			.vote(evidence);
		var verdict = Assignments.<String>forRequirement(outer).jury(inner, child).validate().vote("evidence");
		assertThat(verdict).hasConclusion(Verdict.Conclusion.PASS);
		assertThat(verdict.requirement()).isSameAs(outer);
		assertThat(verdict.compositeAttempts().getFirst().verdict().requirement()).isSameAs(inner);
		assertThat(VerdictReport.of(verdict).attempts()).hasSize(3);
		assertThat(new VerdictCodec().read(new VerdictCodec().write(verdict))).isEqualTo(verdict);
	}

	@Test
	void modelBackedTextUsesActualRequirementAndLocalProtocolStub() {
		var calls = new AtomicInteger();
		io.github.markpollack.judge.ai.model.JudgeModel judgeModel = request -> {
			calls.incrementAndGet();
			String rendered = request.messages()
				.stream()
				.map(io.github.markpollack.judge.ai.model.JudgeMessage::content)
				.reduce("", String::concat);
			assertThat(rendered).contains("The response communicates that 2 + 2 equals 4.",
					"Two pairs make a group of four.");
			return new io.github.markpollack.judge.ai.model.JudgeModelResponse("satisfied", "local-protocol-stub", null,
					Map.of());
		};
		RequirementJudge<String, String> meaning = (actual, response) -> io.github.markpollack.judge.ai.ModelBackedJudge
			.<String>builder()
			.name("meaning")
			.promptTemplate(io.github.markpollack.judge.ai.prompt.JudgePromptTemplate.fromString("meaning", """
					Requirement: {{requirement}}
					Response: {{response}}
					Reply satisfied, violated, or unknown.
					"""))
			.variables(text -> Map.of("requirement", actual.specification(), "response", text))
			.model(judgeModel)
			.judgmentClassifier(io.github.markpollack.judge.ai.JudgmentClassifiers.passFail("satisfied", "violated"))
			.build()
			.judge(response);
		var arithmetic = Requirement.text("arithmetic", "1", "The response communicates that 2 + 2 equals 4.");
		assertThat(arithmetic).judgedBy(meaning).withEvidence("Two pairs make a group of four.").isSatisfied();
		assertThat(calls).hasValue(1);
	}

}
