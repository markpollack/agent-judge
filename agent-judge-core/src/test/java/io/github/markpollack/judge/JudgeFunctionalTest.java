/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge;

import org.junit.jupiter.api.Test;
import io.github.markpollack.judge.completion.CompletionEvidence;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests demonstrating Judge as a functional interface.
 *
 * @author Mark Pollack
 */
class JudgeFunctionalTest {

	private static CompletionEvidence context() {
		return CompletionEvidence.builder().request("test").build();
	}

	@Test
	void lambdaJudgeWorks() {
		// Lambda judge - very simple
		Judge<CompletionEvidence> simplePass = ctx -> Judgment.pass("All good");

		CompletionEvidence context = CompletionEvidence.builder().request("test").build();

		Judgment judgment = simplePass.judge(context);

		assertThat(judgment.pass()).isTrue();
		assertThat(judgment.reasoning()).isEqualTo("All good");
	}

	@Test
	void namedJudgeHasMetadata() {
		// Lambda with metadata via Judges.named()
		Judge<CompletionEvidence> simple = ctx -> Judgment.pass("Success");

		NamedJudge<CompletionEvidence> named = Judges.named(simple, "MyJudge", "A test judge");

		assertThat(named.metadata().name()).isEqualTo("MyJudge");
		assertThat(named.metadata().description()).isEqualTo("A test judge");
		assertThat(named.metadata().type()).isEqualTo(JudgeType.DETERMINISTIC);
	}

	@Test
	void alwaysPassJudge() {
		Judge<CompletionEvidence> pass = Judges.alwaysPass("Default success");

		CompletionEvidence context = CompletionEvidence.builder().request("test").build();

		Judgment judgment = pass.judge(context);

		assertThat(judgment.pass()).isTrue();
		assertThat(judgment.reasoning()).isEqualTo("Default success");
	}

	@Test
	void alwaysFailJudge() {
		Judge<CompletionEvidence> fail = Judges.alwaysFail("Not implemented yet");

		CompletionEvidence context = CompletionEvidence.builder().request("test").build();

		Judgment judgment = fail.judge(context);

		assertThat(judgment.pass()).isFalse();
		assertThat(judgment.reasoning()).isEqualTo("Not implemented yet");
	}

	@Test
	void methodReferenceWorks() {
		// Method reference judge
		Judge<CompletionEvidence> methodRef = this::validateOutput;

		CompletionEvidence context = CompletionEvidence.builder().request("test").response("valid output").build();

		Judgment judgment = methodRef.judge(context);

		assertThat(judgment.pass()).isTrue();
	}

	// Method to use as reference
	private Judgment validateOutput(CompletionEvidence ctx) {
		boolean valid = java.util.Optional.ofNullable(ctx.response()).isPresent()
				&& java.util.Optional.ofNullable(ctx.response()).get().contains("valid");
		return valid ? Judgment.pass("Output valid") : Judgment.fail("Output invalid");
	}

	@Test
	void andComposition_bothPass() {
		Judge<CompletionEvidence> first = ctx -> Judgment.pass("First passed");
		Judge<CompletionEvidence> second = ctx -> Judgment.pass("Second passed");
		Judge<CompletionEvidence> composed = Judges.and(first, second);

		CompletionEvidence context = CompletionEvidence.builder().request("test").build();

		Judgment judgment = composed.judge(context);

		assertThat(judgment.pass()).isTrue();
		assertThat(judgment.reasoning()).isEqualTo("Second passed");
	}

	@Test
	void andComposition_firstFails_shortCircuit() {
		Judge<CompletionEvidence> first = ctx -> Judgment.fail("First failed");
		Judge<CompletionEvidence> second = ctx -> Judgment.pass("Second passed");
		Judge<CompletionEvidence> composed = Judges.and(first, second);

		CompletionEvidence context = CompletionEvidence.builder().request("test").build();

		Judgment judgment = composed.judge(context);

		assertThat(judgment.pass()).isFalse();
		assertThat(judgment.reasoning()).isEqualTo("First failed");
	}

	@Test
	void andComposition_secondFails() {
		Judge<CompletionEvidence> first = ctx -> Judgment.pass("First passed");
		Judge<CompletionEvidence> second = ctx -> Judgment.fail("Second failed");
		Judge<CompletionEvidence> composed = Judges.and(first, second);

		CompletionEvidence context = CompletionEvidence.builder().request("test").build();

		Judgment judgment = composed.judge(context);

		assertThat(judgment.pass()).isFalse();
		assertThat(judgment.reasoning()).isEqualTo("Second failed");
	}

	@Test
	void orComposition_firstPasses_shortCircuit() {
		Judge<CompletionEvidence> first = ctx -> Judgment.pass("First passed");
		Judge<CompletionEvidence> second = ctx -> Judgment.fail("Second failed");
		Judge<CompletionEvidence> composed = Judges.or(first, second);

		CompletionEvidence context = CompletionEvidence.builder().request("test").build();

		Judgment judgment = composed.judge(context);

		assertThat(judgment.pass()).isTrue();
		assertThat(judgment.reasoning()).isEqualTo("First passed");
	}

	@Test
	void orComposition_firstFails_secondPasses() {
		Judge<CompletionEvidence> first = ctx -> Judgment.fail("First failed");
		Judge<CompletionEvidence> second = ctx -> Judgment.pass("Second passed");
		Judge<CompletionEvidence> composed = Judges.or(first, second);

		CompletionEvidence context = CompletionEvidence.builder().request("test").build();

		Judgment judgment = composed.judge(context);

		assertThat(judgment.pass()).isTrue();
		assertThat(judgment.reasoning()).isEqualTo("Second passed");
	}

	@Test
	void orComposition_bothFail() {
		Judge<CompletionEvidence> first = ctx -> Judgment.fail("First failed");
		Judge<CompletionEvidence> second = ctx -> Judgment.fail("Second failed");
		Judge<CompletionEvidence> composed = Judges.or(first, second);

		CompletionEvidence context = CompletionEvidence.builder().request("test").build();

		Judgment judgment = composed.judge(context);

		assertThat(judgment.pass()).isFalse();
		assertThat(judgment.reasoning()).isEqualTo("Second failed");
	}

	@Test
	void allOfComposition_allPass() {
		Judge<CompletionEvidence> first = ctx -> Judgment.pass("First passed");
		Judge<CompletionEvidence> second = ctx -> Judgment.pass("Second passed");
		Judge<CompletionEvidence> third = ctx -> Judgment.pass("Third passed");
		Judge<CompletionEvidence> composed = Judges.allOf(first, second, third);

		CompletionEvidence context = CompletionEvidence.builder().request("test").build();

		Judgment judgment = composed.judge(context);

		assertThat(judgment.pass()).isTrue();
		assertThat(judgment.reasoning()).isEqualTo("All checks passed");
	}

	@Test
	void allOfComposition_middleFails_shortCircuit() {
		Judge<CompletionEvidence> first = ctx -> Judgment.pass("First passed");
		Judge<CompletionEvidence> second = ctx -> Judgment.fail("Second failed");
		Judge<CompletionEvidence> third = ctx -> Judgment.pass("Third passed");
		Judge<CompletionEvidence> composed = Judges.allOf(first, second, third);

		CompletionEvidence context = CompletionEvidence.builder().request("test").build();

		Judgment judgment = composed.judge(context);

		assertThat(judgment.pass()).isFalse();
		assertThat(judgment.reasoning()).isEqualTo("Second failed");
	}

	@Test
	void anyOfComposition_firstPasses_shortCircuit() {
		Judge<CompletionEvidence> first = ctx -> Judgment.pass("First passed");
		Judge<CompletionEvidence> second = ctx -> Judgment.fail("Second failed");
		Judge<CompletionEvidence> third = ctx -> Judgment.fail("Third failed");
		Judge<CompletionEvidence> composed = Judges.anyOf(first, second, third);

		CompletionEvidence context = CompletionEvidence.builder().request("test").build();

		Judgment judgment = composed.judge(context);

		assertThat(judgment.pass()).isTrue();
		assertThat(judgment.reasoning()).isEqualTo("First passed");
	}

	@Test
	void anyOfComposition_middlePasses() {
		Judge<CompletionEvidence> first = ctx -> Judgment.fail("First failed");
		Judge<CompletionEvidence> second = ctx -> Judgment.pass("Second passed");
		Judge<CompletionEvidence> third = ctx -> Judgment.fail("Third failed");
		Judge<CompletionEvidence> composed = Judges.anyOf(first, second, third);

		CompletionEvidence context = CompletionEvidence.builder().request("test").build();

		Judgment judgment = composed.judge(context);

		assertThat(judgment.pass()).isTrue();
		assertThat(judgment.reasoning()).isEqualTo("Second passed");
	}

	@Test
	void anyOfComposition_allFail() {
		Judge<CompletionEvidence> first = ctx -> Judgment.fail("First failed");
		Judge<CompletionEvidence> second = ctx -> Judgment.fail("Second failed");
		Judge<CompletionEvidence> third = ctx -> Judgment.fail("Third failed");
		Judge<CompletionEvidence> composed = Judges.anyOf(first, second, third);

		CompletionEvidence context = CompletionEvidence.builder().request("test").build();

		Judgment judgment = composed.judge(context);

		assertThat(judgment.pass()).isFalse();
		assertThat(judgment.reasoning()).isEqualTo("All checks failed");
	}

	@Test
	void compositionWithMetadata() {
		Judge<CompletionEvidence> first = ctx -> Judgment.pass("First passed");
		Judge<CompletionEvidence> second = ctx -> Judgment.pass("Second passed");
		Judge<CompletionEvidence> composed = Judges.and(first, second);

		// Wrap composition with metadata
		Judge<CompletionEvidence> namedComposed = Judges.named(composed, "BuildAndTest",
				"Both build and tests must succeed", JudgeType.DETERMINISTIC);

		assertThat(namedComposed).isInstanceOf(JudgeWithMetadata.class);

		JudgeWithMetadata<CompletionEvidence> withMeta = (JudgeWithMetadata<CompletionEvidence>) namedComposed;
		assertThat(withMeta.metadata().name()).isEqualTo("BuildAndTest");
		assertThat(withMeta.metadata().description()).isEqualTo("Both build and tests must succeed");

		// Composition still works
		CompletionEvidence context = CompletionEvidence.builder().request("test").build();
		Judgment judgment = namedComposed.judge(context);
		assertThat(judgment.pass()).isTrue();
	}

	@Test
	void nestedComposition() {
		// Complex composition: (A AND B) OR (C AND D)
		Judge<CompletionEvidence> a = ctx -> Judgment.fail("A failed");
		Judge<CompletionEvidence> b = ctx -> Judgment.pass("B passed");
		Judge<CompletionEvidence> c = ctx -> Judgment.pass("C passed");
		Judge<CompletionEvidence> d = ctx -> Judgment.pass("D passed");

		Judge<CompletionEvidence> ab = Judges.and(a, b);
		Judge<CompletionEvidence> cd = Judges.and(c, d);
		Judge<CompletionEvidence> composed = Judges.or(ab, cd);

		CompletionEvidence context = CompletionEvidence.builder().request("test").build();

		Judgment judgment = composed.judge(context);

		// A fails, so AB fails, but CD passes, so overall passes
		assertThat(judgment.pass()).isTrue();
		assertThat(judgment.reasoning()).isEqualTo("D passed");
	}

	// ---------------------------------------------------------------------------------
	// Boundary of the Boolean combinators, PINNED rather than changed.
	//
	// These four branch on Judgment.pass(), so ABSTAIN and ERROR are "not passed". That
	// is
	// the documented contract of a Boolean combinator, and widening it would alter every
	// existing composition. The jury API covers abstaining judges properly; these tests
	// exist so the boundary is visible and cannot drift unnoticed.
	// ---------------------------------------------------------------------------------

	@Test
	void allOfShortCircuitsOnAbstainAndReturnsIt() {
		Judge<CompletionEvidence> first = ctx -> Judgment.pass("first passed");
		Judge<CompletionEvidence> abstaining = ctx -> Judgment.abstain("not applicable to this subject");
		Judge<CompletionEvidence> never = ctx -> {
			throw new AssertionError("must not run: allOf short-circuits on a non-PASS");
		};

		Judgment result = Judges.allOf(first, abstaining, never).judge(context());

		assertThat(result.status()).isEqualTo(JudgmentStatus.ABSTAIN);
	}

	@Test
	void allOfShortCircuitsOnErrorAndReturnsIt() {
		Judge<CompletionEvidence> erroring = ctx -> Judgment.error("judge could not complete");
		Judge<CompletionEvidence> never = ctx -> {
			throw new AssertionError("must not run: allOf short-circuits on a non-PASS");
		};

		Judgment result = Judges.allOf(erroring, never).judge(context());

		assertThat(result.status()).isEqualTo(JudgmentStatus.ERROR);
	}

	@Test
	void anyOfReportsFailWhenEveryJudgeAbstained() {
		// Documented consequence of a Boolean contract: "nothing passed" is reported as
		// FAIL
		// even though no judge made a negative finding. Use a jury when judges can
		// abstain.
		Judge<CompletionEvidence> a = ctx -> Judgment.abstain("not applicable");
		Judge<CompletionEvidence> b = ctx -> Judgment.abstain("not applicable");

		Judgment result = Judges.anyOf(a, b).judge(context());

		assertThat(result.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(result.reasoning()).isEqualTo("All checks failed");
	}

	@Test
	void aJuryIsTheSupportedRouteForAbstainingJudges() {
		// The same two abstaining judges through the jury API: ABSTAIN, not FAIL, with
		// the
		// population published. This is why the combinators are left alone.
		Judgment aggregate = new io.github.markpollack.judge.jury.AllMustPassStrategy().aggregate(
				java.util.List.of(Judgment.abstain("not applicable"), Judgment.abstain("not applicable")),
				java.util.Map.of());

		assertThat(aggregate.status()).isEqualTo(JudgmentStatus.ABSTAIN);
	}

}
