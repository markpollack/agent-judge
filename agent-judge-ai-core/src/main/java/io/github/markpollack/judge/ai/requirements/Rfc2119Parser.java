/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.ai.requirements;

import java.util.List;
import static java.util.stream.Collectors.joining;
import io.github.markpollack.judge.ai.JudgmentClassifier;
import io.github.markpollack.judge.ai.model.EvalModelResponse;
import io.github.markpollack.judge.ai.prompt.JudgePromptTemplate;
import io.github.markpollack.judge.judgment.Judgment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Answers every architectural constraint in a design against an implementation.
 *
 * <h2>PASS means every requirement was affirmatively established</h2>
 *
 * <p>
 * The rollup is deliberately strict:
 *
 * <pre>
 *   any ERROR        -&gt; ERROR
 *   else any FAIL    -&gt; FAIL
 *   else any ABSTAIN -&gt; ABSTAIN
 *   else             -&gt; PASS
 * </pre>
 *
 * <p>
 * An abstention is not dropped. A written MUST is required by construction — the design
 * says it applies — so "could not determine" is not "does not apply". Absorbing it into a
 * passing population would report that the implementation satisfies the complete design
 * when one requirement was never settled.
 *
 * <p>
 * The rollup is the same one the acceptance criteria get, for the same reason. The RFC
 * 2119 keyword is carried on each constraint so a future {@code SHOULD} can be judgment
 * differently; all thirteen of these are {@code MUST}, so nothing here depends on it yet.
 *
 * <h2>The model assesses; Java decides</h2>
 *
 * <p>
 * The model returns one status and one line of evidence per constraint. It does not
 * choose the constraints, count them, decide how many there are, or compute the verdict.
 * Those are this class's job, in Java, where they are inspectable and cannot drift.
 *
 * <p>
 * There is no score. Every constraint stays individually visible, and a failure names the
 * constraint that bound the verdict and where.
 *
 * @author Mark Pollack
 * @since 0.16.0
 * @see Rfc2119Constraint
 * @see Observation
 */
final class Rfc2119Parser {

	/** Flow logging at INFO: what was answered, and what bound the verdict. */
	private static final Logger logger = LoggerFactory.getLogger(Rfc2119Parser.class);

	private static final RosterRollup.Vocabulary WORDS = new RosterRollup.Vocabulary("constraints",
			"constraintsTotal", "none of the %d constraints applied to this implementation", "all %d constraints hold",
			"%d of %d hold", ", %d violated");

	private Rfc2119Parser() {
	}

	private static String conditionalIds(List<Rfc2119Constraint> constraints) {
		return constraints.stream()
			.filter(Rfc2119Constraint::conditional)
			.map(Rfc2119Constraint::id)
			.collect(joining(", "));
	}

	static JudgePromptTemplate templateFor(String name, List<Rfc2119Constraint> constraints) {
		StringBuilder list = new StringBuilder();
		constraints.forEach(c -> list.append("  ").append(c.asPrompt()).append('\n'));
		int n = constraints.size();
		String conditional = conditionalIds(constraints);
		// The exclusion is offered only where the design authorized one, and the
		// authorized ids
		// are named. Offering it everywhere would invite the audit to decide for itself
		// which
		// constraints it has to answer.
		String exclusion = conditional.isEmpty() ? "" : """

				NOT_APPLICABLE is available for these constraints only: %s. They carry an "Applies
				when" clause, and you may answer NOT_APPLICABLE only when that clause does not hold
				for this implementation. You must give the reason after the dash. Do not use it for
				any other constraint, and do not use it because a constraint is hard to establish;
				that is CANNOT_DETERMINE.
				""".formatted(conditional);
		return JudgePromptTemplate.fromString(name,
				"""
						You are auditing a Java implementation against the architectural constraints it
						was built to. The implementation workspace is {{workspace}}. The judging backend must be configured to access it. Read files, grep, and inspect
						configuration and tests.

						Answer every one of the %d constraints below. Do not add constraints, do not merge
						two into one, and do not skip one because it looks obvious or looks hard. The
						design asked %d questions and owes %d answers.

						For each, reply with exactly one line:

						  <constraint-id>: PASS|FAIL|CANNOT_DETERMINE - <one sentence, citing a file>

						PASS              the code demonstrably holds to this, and you can point at where
						FAIL              the code demonstrably does not
						CANNOT_DETERMINE  this cannot be settled from the code and tests available

						Cite a file and line for every claim. If you state a count, obtain it with a
						command rather than by reading and estimating.

						CANNOT_DETERMINE is a real answer. Use it rather than guessing.
						%s

						Do not state an overall verdict. You assess each constraint; deciding what the set
						of assessments means is not your job.

						OPTIONAL. If, while establishing a constraint, you notice something useful that the
						constraint does not itself require, you may add a line:

						  OBSERVATION <constraint-id>: <one externally verifiable sentence, citing a file>

						This does not change any answer. It is not a new constraint and it is not a
						complaint. Omit it entirely if there is nothing worth saying.

						THE CONSTRAINTS

						%s
						"""
					.formatted(n, n, n, exclusion, list.toString()));
	}

	/**
	 * Classify one answer against a roster, bypassing construction.
	 *
	 * <p>
	 * The construction guard refuses an empty roster, so this is how the rollup's own
	 * guard is exercised: it is the guard that holds if the first is ever bypassed.
	 * @param constraints the roster
	 * @param response the audit to classify
	 * @return the judgment the rollup produces
	 */
	static Judgment rollupFor(List<Rfc2119Constraint> constraints, EvalModelResponse response) {
		return classifier(constraints).classify(response);
	}

	private static JudgmentClassifier classifier(List<Rfc2119Constraint> supplied) {
		return RosterRollup.of(
				RosterRollup.items(supplied, Rfc2119Constraint::id, Rfc2119Constraint::conditional), WORDS, logger);
	}

}
