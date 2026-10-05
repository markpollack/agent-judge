/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertj;
import io.github.markpollack.judge.verdict.Verdict;
import io.github.markpollack.judge.voting.MajorityVotingStrategy;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.*;
import io.github.markpollack.judge.*;
import io.github.markpollack.judge.judgment.*;
import io.github.markpollack.judge.jury.*;
import io.github.markpollack.judge.verdict.*;
import io.github.markpollack.judge.voting.*;
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
		Judge positive = () -> 42 > 0 ? Judgment.pass("positive") : Judgment.fail("not positive");
		assertThat(positive).isPassed();
	}

	@Test
	void nativeRequirement() {
		var api = new io.github.markpollack.judge.requirement.GeneralRequirement<ApiLimit>("api", "1",
				"No breaking changes", new ApiLimit(0),
				Requirement.text("source", "1", "Maximum breaking changes: 0").source());
		io.github.markpollack.judge.construction.JudgeRecipe<ApiLimit, Integer> compatible = io.github.markpollack.judge.assertj.TestRecipes
			.judge((r, count) -> count <= r.specification().maximumBreakingChanges() ? Judgment.pass("compatible")
					: Judgment.fail("breaking change"));
		assertThat(api).judgedBy(compatible).withEvidence(0).isSatisfied();
	}

	@Test
	void completeMixedAssignmentsAndExplicitSelectors() {
		var security = Requirement.text("security", "1", "No critical vulnerabilities");
		var compatibility = new io.github.markpollack.judge.requirement.GeneralRequirement<ApiLimit>("compatibility",
				"1", "No breaking changes", new ApiLimit(0),
				Requirement.text("source", "1", "Maximum breaking changes: 0").source());
		var observability = Requirement.text("observability", "1", "Required metrics exist");
		var readiness = new io.github.markpollack.judge.requirement.GeneralRequirement<AllOf>("readiness", "1",
				"Ready to deploy", new AllOf(List.of(security, compatibility, observability)),
				Requirement.text("source", "1", "security AND compatibility AND observability").source());
		io.github.markpollack.judge.jury.JuryRecipe<String, String> opinions = TestRecipes.voting(
				new MajorityVotingStrategy(),
				List.of(TestRecipes.<String, String>judge((r, e) -> Judgment.pass("scanner A")),
						TestRecipes.<String, String>judge((r, e) -> Judgment.pass("scanner B")),
						TestRecipes.<String, String>judge((r, e) -> Judgment.fail("dissent"))));
		io.github.markpollack.judge.construction.JudgeRecipe<ApiLimit, Integer> compatible = io.github.markpollack.judge.assertj.TestRecipes
			.judge((r, count) -> count <= r.specification().maximumBreakingChanges() ? Judgment.pass("compatible")
					: Judgment.fail("breaking"));
		io.github.markpollack.judge.construction.JudgeRecipe<String, Boolean> observable = io.github.markpollack.judge.assertj.TestRecipes
			.judge((r, present) -> present ? Judgment.pass("metrics exist") : Judgment.fail("metrics missing"));
		var prepared = Assignments.<ReleaseEvidence>forRequirement(readiness)
			.jury(security, ReleaseEvidence::securityReport, opinions)
			.judge(compatibility, ReleaseEvidence::breakingChanges, compatible)
			.judge(observability, ReleaseEvidence::observable, observable)
			.validate();
		var verdict = prepared.evidence(new ReleaseEvidence(0, false, "scan")).build().vote();
		var result = Evaluations.apply(verdict, v -> new PolicyDecision(PolicyAction.RELY, "Trust this rejection"));
		assertThat(result).hasConclusion(Verdict.Conclusion.FAIL);
		assertThat(result.verdict().compositeAttempts().getFirst().verdict().individual()).hasSize(3);
		assertThat(result.verdict().compositeAttempts()).extracting(a -> a.verdict().conclusion())
			.containsExactly(Verdict.Conclusion.PASS, Verdict.Conclusion.PASS, Verdict.Conclusion.FAIL);
		assertThatThrownBy(() -> assertThat(result).isPassed()).isInstanceOf(RequirementAssertionError.class);
		var codec = new VerdictCodec(Map.of("apiLimit", ApiLimit.class));
		var reopened = codec.readEvaluation(codec.write(result));
		assertThat(reopened).isEqualTo(result);
		assertThat(VerdictReport.of(reopened.verdict()).attempts()).hasSize(3);
	}

	@Test
	void nestedAllOfRetainsParentAndChildAssociations() {
		var a = Requirement.text("a", "1", "A");
		var b = Requirement.text("b", "1", "B");
		var inner = new io.github.markpollack.judge.requirement.GeneralRequirement<AllOf>("inner", "1", "Both",
				new AllOf(List.of(a, b)), Requirement.text("source", "1", "A AND B").source());
		var outer = new io.github.markpollack.judge.requirement.GeneralRequirement<AllOf>("outer", "1", "Inner",
				new AllOf(List.of(inner)), Requirement.text("source", "1", "Inner required").source());
		io.github.markpollack.judge.construction.JudgeRecipe<String, String> check = io.github.markpollack.judge.assertj.TestRecipes
			.judge((r, e) -> Judgment.pass(r.specification()));
		// The nested evaluator prepares against the actual child supplied by the outer
		// parent.
		io.github.markpollack.judge.jury.JuryRecipe<AllOf, String> child = actual -> Assignments
			.<String>forRequirement(actual)
			.judge(a, check)
			.judge(b, check)
			.validate();
		var verdict = Assignments.<String>forRequirement(outer)
			.jury(inner, child)
			.validate()
			.evidence("evidence")
			.build()
			.vote();
		assertThat(verdict).hasConclusion(Verdict.Conclusion.PASS);
		org.assertj.core.api.Assertions.assertThat(verdict.requirement()).isSameAs(outer);
		org.assertj.core.api.Assertions.assertThat(verdict.compositeAttempts().getFirst().verdict().requirement())
			.isSameAs(inner);
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
		var meaning = io.github.markpollack.judge.ai.ModelBackedJudge.<String>builder()
			.name("meaning")
			.promptTemplate(io.github.markpollack.judge.ai.prompt.JudgePromptTemplate.fromString("meaning", """
					Requirement: {{requirement}}
					Response: {{response}}
					Reply satisfied, violated, or unknown.
					"""))
			.variables(text -> Map.of("response", text))
			.model(judgeModel)
			.judgmentClassifier(io.github.markpollack.judge.ai.JudgmentClassifiers.passFail("satisfied", "violated"));
		var arithmetic = Requirement.text("arithmetic", "1", "The response communicates that 2 + 2 equals 4.");
		assertThat(arithmetic).judgedBy(meaning).withEvidence("Two pairs make a group of four.").isSatisfied();
		assertThat(calls).hasValue(1);
	}

}
