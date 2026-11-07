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
 * Answers every acceptance criterion in a specification against an implementation.
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
 * An abstention is not dropped. A written acceptance criterion is required by
 * construction — the specification says it applies — so "could not determine" is not
 * "does not apply". Absorbing it into a passing population would report that the
 * implementation satisfies the complete specification when one requirement was never
 * settled.
 *
 * <h2>The model assesses; Java decides</h2>
 *
 * <p>
 * The model returns one status and one line of evidence per criterion. It does not choose
 * the criteria, count them, decide how many there are, or compute the verdict. Those are
 * this class's job, in Java, where they are inspectable and cannot drift.
 *
 * <p>
 * There is no score. Every criterion stays individually visible, and a failure names the
 * criterion that bound the verdict and where.
 *
 * @author Mark Pollack
 * @since 0.16.0
 * @see EarsCriterion
 * @see Observation
 */
final class EarsParser {

	/** Flow logging at INFO: what was answered, and what bound the verdict. */
	private static final Logger logger = LoggerFactory.getLogger(EarsParser.class);

	private static final RosterRollup.Vocabulary WORDS = new RosterRollup.Vocabulary("criteria", "criteriaTotal",
			"none of the %d requirements applied to this subject", "all %d requirements established",
			"%d of %d established", ", %d not satisfied");

	private EarsParser() {
	}

	private static String conditionalIds(List<EarsCriterion> criteria) {
		return criteria.stream().filter(EarsCriterion::conditional).map(EarsCriterion::id).collect(joining(", "));
	}

	static JudgePromptTemplate templateFor(String name, List<EarsCriterion> criteria) {
		StringBuilder list = new StringBuilder();
		criteria.forEach(c -> list.append("  ").append(c.asPrompt()).append('\n'));
		int n = criteria.size();
		String conditional = conditionalIds(criteria);
		// The exclusion is offered only where the document authorized one, and the
		// authorized ids
		// are named. Offering it everywhere would invite the audit to decide for itself
		// which
		// criteria it has to answer.
		String exclusion = conditional.isEmpty() ? "" : """

				NOT_APPLICABLE is available for these criteria only: %s. They carry an "Applies
				when" clause, and you may answer NOT_APPLICABLE only when that clause does not hold
				for this subject. You must give the reason after the dash. Do not use it for any
				other criterion, and do not use it because a criterion is hard to establish; that is
				CANNOT_DETERMINE.
				""".formatted(conditional);
		return JudgePromptTemplate.fromString(name,
				"""
						You are auditing a Java implementation against the acceptance criteria it was
						built to satisfy. The implementation workspace is {{workspace}}. The judging backend must be configured to access it. Read files, grep, and
						inspect tests.

						Answer every one of the %d criteria below. Do not add criteria, do not merge two
						into one, and do not skip one because it looks obvious or looks hard. The
						specification asked %d questions and owes %d answers.

						For each, reply with exactly one line:

						  <criterion-id>: PASS|FAIL|CANNOT_DETERMINE - <one sentence, citing a file>

						PASS              the code demonstrably does this, and you can point at where
						FAIL              the code demonstrably does not
						CANNOT_DETERMINE  this cannot be settled from the code and tests available

						Cite a file and line for every claim. If you state a count, obtain it with a
						command rather than by reading and estimating.

						CANNOT_DETERMINE is a real answer. Use it rather than guessing.
						%s

						Do not state an overall verdict. You assess each criterion; deciding what the set
						of assessments means is not your job.

						OPTIONAL. If, while establishing a criterion, you notice something useful that the
						criterion does not itself require, you may add a line:

						  OBSERVATION <criterion-id>: <one externally verifiable sentence, citing a file>

						This does not change any answer. It is not a new criterion and it is not a
						complaint. Omit it entirely if there is nothing worth saying.

						THE CRITERIA

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
	 * @param criteria the roster
	 * @param response the audit to classify
	 * @return the judgment the rollup produces
	 */
	static Judgment rollupFor(List<EarsCriterion> criteria, EvalModelResponse response) {
		return classifier(criteria).classify(response);
	}

	private static JudgmentClassifier classifier(List<EarsCriterion> supplied) {
		return RosterRollup.of(RosterRollup.items(supplied, EarsCriterion::id, EarsCriterion::conditional), WORDS,
				logger);
	}

}
