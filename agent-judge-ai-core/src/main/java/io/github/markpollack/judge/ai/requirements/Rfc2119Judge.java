package io.github.markpollack.judge.ai.requirements;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.markpollack.judge.ai.JudgmentClassifier;
import io.github.markpollack.judge.ai.ModelBackedJudge;
import io.github.markpollack.judge.ai.model.JudgeModel;
import io.github.markpollack.judge.ai.prompt.JudgePromptTemplate;
import io.github.markpollack.judge.result.Check;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Answers every architectural constraint in a design against an implementation.
 *
 * <h2>PASS means every requirement was affirmatively established</h2>
 *
 * <p>The rollup is deliberately strict:
 *
 * <pre>
 *   any ERROR        -&gt; ERROR
 *   else any FAIL    -&gt; FAIL
 *   else any ABSTAIN -&gt; ABSTAIN
 *   else             -&gt; PASS
 * </pre>
 *
 * <p>An abstention is not dropped. A written MUST is required by construction — the design says it
 * applies — so "could not determine" is not "does not apply". Absorbing it into a passing
 * population would report that the implementation satisfies the complete design when one
 * requirement was never settled.
 *
 * <p>The rollup is the same one the acceptance criteria get, for the same reason. The RFC 2119
 * keyword is carried on each constraint so a future {@code SHOULD} can be aggregated differently;
 * all thirteen of these are {@code MUST}, so nothing here depends on it yet.
 *
 * <h2>The model assesses; Java decides</h2>
 *
 * <p>The model returns one status and one line of evidence per constraint. It does not choose the
 * constraints, count them, decide how many there are, or compute the verdict. Those are this class's
 * job, in Java, where they are inspectable and cannot drift.
 *
 * <p>There is no score. Every constraint stays individually visible, and a failure names the
 * constraint that bound the verdict and where.
 *
 * @author Mark Pollack
 * @since 0.16.0
 * @see Rfc2119Constraint
 * @see Observation
 */
public final class Rfc2119Judge {

	/** Flow logging at INFO: what was answered, and what bound the verdict. */
	private static final Logger logger = LoggerFactory.getLogger(Rfc2119Judge.class);

	/** {@code AppointmentServiceTests.java:191} — the only part of a message we treat as structured. */
	private static final Pattern LOCATION = Pattern.compile("[A-Za-z0-9_/.]*[A-Za-z0-9_]+\\.(?:java|xml|sql|html|yml|properties):\\d+");

	private Rfc2119Judge() {
	}

	/**
	 * Build the judge over a requirement set and a backend.
	 *
	 * <p>The model is supplied rather than constructed here. Where its answers come from — a live
	 * agent, a recorded one, a stub in a test — is the caller's decision and not this judge's
	 * business, and keeping it that way is what lets the same judge run on a build server and on a
	 * laptop with no credentials.
	 * @param name the judge's name, also used to name its prompt template
	 * @param constraints the roster of architectural constraints, every one of which must be answered
	 * @param model the backend that answers them
	 * @return a judge over that roster
	 */
	public static ModelBackedJudge create(String name, List<Rfc2119Constraint> constraints, JudgeModel model) {
		return ModelBackedJudge.builder()
			.name(name)
			.description("Was the implementation built the way the design said it must be?")
			.promptTemplate(templateFor(name, constraints))
			.model(model)
			.judgmentClassifier(classifier(constraints))
			.build();
	}

	static JudgePromptTemplate templateFor(String name, List<Rfc2119Constraint> constraints) {
		StringBuilder list = new StringBuilder();
		constraints.forEach(c -> list.append("  ").append(c.asPrompt()).append('\n'));
		int n = constraints.size();
		return JudgePromptTemplate.fromString(name, """
            You are auditing a Java implementation against the architectural constraints it
            was built to. You are in the implementation's root. Read files, grep, and inspect
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

            Do not state an overall verdict. You assess each constraint; deciding what the set
            of assessments means is not your job.

            OPTIONAL. If, while establishing a constraint, you notice something useful that the
            constraint does not itself require, you may add a line:

              OBSERVATION <constraint-id>: <one externally verifiable sentence, citing a file>

            This does not change any answer. It is not a new constraint and it is not a
            complaint. Omit it entirely if there is nothing worth saying.

            THE CONSTRAINTS

            %s
            """.formatted(n, n, n, list.toString()));
	}

	private static JudgmentClassifier classifier(List<Rfc2119Constraint> constraints) {
		return response -> {
			String text = response.text() == null ? "" : response.text().strip();

			// A backend that could not produce an answer says so in metadata, and whatever text
			// it carries is its explanation to the operator. Passing that through matters: the
			// difference between "the agent did not complete its run" and a backend that was
			// never given credentials is the difference between blaming the subject and naming
			// the real problem, and only the backend knows which it is.
			Object successful = response.metadata() == null ? null : response.metadata().get("successful");
			if (Boolean.FALSE.equals(successful)) {
				return Judgment.error(text.isEmpty() ? "The judging agent did not complete its run" : text);
			}
			if (text.isEmpty()) {
				return Judgment.error("No audit was produced for this implementation");
			}

			Map<String, String> roster = new LinkedHashMap<>();
			constraints.forEach(c -> roster.put(c.id(), c.title()));

			Map<String, JudgmentStatus> outcome = new LinkedHashMap<>();
			Map<String, String> evidence = new LinkedHashMap<>();
			parse(text, roster.keySet(), outcome, evidence);

			// A constraint the audit skipped is not a constraint that passed.
			List<String> unanswered = roster.keySet().stream().filter(id -> !outcome.containsKey(id)).toList();
			if (!unanswered.isEmpty()) {
				return Judgment.error("The audit did not answer " + unanswered.size() + " of "
					+ roster.size() + " constraints, beginning with " + unanswered.get(0));
			}

			// The guard held. Logged so the passing case is as legible as the failing one:
			// "N of N answered" is the evidence that the roster was checked, not assumed.
			logger.info("{} of {} constraints answered", outcome.size(), roster.size());

			List<Check> checks = new ArrayList<>();
			List<String> abstained = new ArrayList<>();
			long passed = 0;
			long failed = 0;
			for (String id : roster.keySet()) {
				JudgmentStatus status = outcome.get(id);
				String why = evidence.get(id);
				switch (status) {
					case PASS -> {
						passed++;
						checks.add(Check.pass(id, why));
					}
					case FAIL -> {
						failed++;
						checks.add(Check.fail(id, why));
					}
					default -> {
						abstained.add(id);
						checks.add(Check.fail(id, "could not be established: " + why));
					}
				}
			}

			// PASS means every required constraint was affirmatively established.
			JudgmentStatus verdict = failed > 0 ? JudgmentStatus.FAIL
				: !abstained.isEmpty() ? JudgmentStatus.ABSTAIN
				: JudgmentStatus.PASS;

			String reasoning = summarize(passed, failed, abstained, roster.size());

			// The rollup happens in Java, not in the model, and this line says so: the verdict
			// and the requirement that bound it. On the ABSTAIN path that identifier is the
			// fact a jury would otherwise absorb -- see FixedRosterAggregationTests.
			logger.info("verdict {} - {}", verdict, verdict == JudgmentStatus.PASS ? reasoning
				: !abstained.isEmpty() && failed == 0 ? abstained.get(0) + " could not be established"
				: failed + " of " + roster.size() + " violated");

			String unestablished = String.join(",", abstained);
			// Non-binding: metadata takes no part in the rollup above.
			List<Map<String, Object>> observations = observations(text, roster).stream()
				.map(Observation::toMetadata).toList();
			return switch (verdict) {
				case PASS -> Judgment.builder().pass().reasoning(reasoning).checks(checks)
					.metadata("constraintsTotal", roster.size()).metadata("established", passed)
					.metadata("unestablished", unestablished)
					.metadata(Observation.METADATA_KEY, observations).build();
				case FAIL -> Judgment.builder().fail().reasoning(reasoning).checks(checks)
					.metadata("constraintsTotal", roster.size()).metadata("established", passed)
					.metadata("unestablished", unestablished)
					.metadata(Observation.METADATA_KEY, observations).build();
				default -> Judgment.builder().abstain().reasoning(reasoning).checks(checks)
					.metadata("constraintsTotal", roster.size()).metadata("established", passed)
					.metadata("unestablished", unestablished)
					.metadata(Observation.METADATA_KEY, observations).build();
			};
		};
	}

	/**
	 * Optional, non-binding, and deliberately forgiving. An absent, malformed or unknown-id
	 * observation yields nothing at all — it must never turn a valid judgment into a failure,
	 * because a cosmetic change in non-binding model prose would then break a valid run.
	 */
	private static List<Observation> observations(String text, Map<String, String> roster) {
		List<Observation> found = new ArrayList<>();
		for (String line : text.lines().map(String::strip).toList()) {
			if (!line.toUpperCase().startsWith("OBSERVATION")) {
				continue;
			}
			int colon = line.indexOf(':');
			if (colon < 0) {
				continue;
			}
			String id = line.substring("OBSERVATION".length(), colon).strip().replaceAll("[^A-Za-z0-9-]", "");
			String message = line.substring(colon + 1).strip();
			if (!roster.containsKey(id) || message.isEmpty()) {
				continue;
			}
			found.add(new Observation(id, message, locationsIn(message)));
		}
		return List.copyOf(found);
	}

	private static List<String> locationsIn(String message) {
		List<String> locations = new ArrayList<>();
		Matcher matcher = LOCATION.matcher(message);
		while (matcher.find()) {
			String location = matcher.group();
			if (!locations.contains(location)) {
				locations.add(location);
			}
		}
		return locations;
	}

	private static void parse(String text, Iterable<String> ids,
			Map<String, JudgmentStatus> outcome, Map<String, String> evidence) {
		for (String line : text.lines().map(String::strip).toList()) {
			int colon = line.indexOf(':');
			if (colon < 0) {
				continue;
			}
			String id = line.substring(0, colon).strip().replaceAll("[^A-Za-z0-9-]", "");
			boolean known = false;
			for (String candidate : ids) {
				if (candidate.equals(id)) {
					known = true;
					break;
				}
			}
			if (!known || outcome.containsKey(id)) {
				continue;
			}
			String rest = line.substring(colon + 1).strip();
			String upper = rest.toUpperCase();
			JudgmentStatus status = upper.startsWith("PASS") ? JudgmentStatus.PASS
				: upper.startsWith("FAIL") ? JudgmentStatus.FAIL
				: upper.startsWith("CANNOT") ? JudgmentStatus.ABSTAIN : null;
			if (status == null) {
				continue;
			}
			int dash = rest.indexOf('-');
			outcome.put(id, status);
			evidence.put(id, dash < 0 ? rest : rest.substring(dash + 1).strip());
		}
	}

	private static String summarize(long passed, long failed, List<String> abstained, int total) {
		if (failed == 0 && abstained.isEmpty()) {
			return "all %d constraints hold".formatted(total);
		}
		StringBuilder text = new StringBuilder("%d of %d hold".formatted(passed, total));
		if (failed > 0) {
			text.append(", %d violated".formatted(failed));
		}
		if (!abstained.isEmpty()) {
			text.append(", %d could not be established: %s".formatted(abstained.size(), String.join(", ", abstained)));
		}
		return text.toString();
	}
}
