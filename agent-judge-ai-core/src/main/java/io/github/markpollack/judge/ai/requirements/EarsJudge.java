package io.github.markpollack.judge.ai.requirements;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.joining;

import io.github.markpollack.judge.ai.JudgmentClassifier;
import io.github.markpollack.judge.ai.ModelBackedJudge;
import io.github.markpollack.judge.ai.model.JudgeModel;
import io.github.markpollack.judge.ai.model.JudgeModelResponse;
import io.github.markpollack.judge.ai.prompt.JudgePromptTemplate;
import io.github.markpollack.judge.result.Check;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentReasonCode;
import io.github.markpollack.judge.result.JudgmentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Answers every acceptance criterion in a specification against an implementation.
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
 * <p>An abstention is not dropped. A written acceptance criterion is required by construction —
 * the specification says it applies — so "could not determine" is not "does not apply". Absorbing
 * it into a passing population would report that the implementation satisfies the complete
 * specification when one requirement was never settled.
 *
 * <h2>The model assesses; Java decides</h2>
 *
 * <p>The model returns one status and one line of evidence per criterion. It does not choose the
 * criteria, count them, decide how many there are, or compute the verdict. Those are this class's
 * job, in Java, where they are inspectable and cannot drift.
 *
 * <p>There is no score. Every criterion stays individually visible, and a failure names the
 * criterion that bound the verdict and where.
 *
 * @author Mark Pollack
 * @since 0.16.0
 * @see EarsCriterion
 * @see Observation
 */
public final class EarsJudge {

	/** Flow logging at INFO: what was answered, and what bound the verdict. */
	private static final Logger logger = LoggerFactory.getLogger(EarsJudge.class);

	/** {@code AppointmentServiceTests.java:191} — the only part of a message we treat as structured. */
	private static final Pattern LOCATION = Pattern.compile("[A-Za-z0-9_/.]*[A-Za-z0-9_]+\\.(?:java|xml|sql|html|yml|properties):\\d+");

	private EarsJudge() {
	}

	/**
	 * Build the judge over a requirement set and a backend.
	 *
	 * <p>The model is supplied rather than constructed here. Where its answers come from — a live
	 * agent, a recorded one, a stub in a test — is the caller's decision and not this judge's
	 * business, and keeping it that way is what lets the same judge run on a build server and on a
	 * laptop with no credentials.
	 * @param name the judge's name, also used to name its prompt template
	 * @param criteria the roster of acceptance criteria, every one of which must be answered; must
	 * be non-empty
	 * @param model the backend that answers them
	 * @return a judge over that roster
	 * @throws IllegalArgumentException if the roster is empty, since a conjunctive rollup over an
	 * empty denominator is vacuously satisfied
	 */
	public static ModelBackedJudge create(String name, List<EarsCriterion> criteria, JudgeModel model) {
		// A jury assembled around an empty roster is a configuration mistake, and it should never
		// reach a model: the run would spend an agent to produce a verdict computed over nothing.
		// The rollup refuses an empty roster too, and that second guard is the one that holds if
		// this one is ever bypassed.
		if (criteria == null || criteria.isEmpty()) {
			throw new IllegalArgumentException("a requirements judge needs at least one acceptance criterion; "
					+ "an empty roster has no denominator, and a conjunctive rollup over one is vacuously satisfied");
		}
		ModelBackedJudge.Builder builder = ModelBackedJudge.builder()
			.name(name)
			.description("Did the implementation satisfy the acceptance criteria it was built from?")
			.promptTemplate(templateFor(name, criteria))
			.model(model)
			.judgmentClassifier(classifier(criteria));
		// Declared only when the document itself authorized an exclusion, and naming the criteria
		// it authorized: a jury seating this judge can then see exactly how much of the roster is
		// allowed to leave the denominator.
		String conditional = conditionalIds(criteria);
		if (!conditional.isEmpty()) {
			builder.notApplicableWhen("criteria " + conditional + " are conditional");
		}
		return builder.build();
	}

	private static String conditionalIds(List<EarsCriterion> criteria) {
		return criteria.stream().filter(EarsCriterion::conditional).map(EarsCriterion::id).collect(joining(", "));
	}

	static JudgePromptTemplate templateFor(String name, List<EarsCriterion> criteria) {
		StringBuilder list = new StringBuilder();
		criteria.forEach(c -> list.append("  ").append(c.asPrompt()).append('\n'));
		int n = criteria.size();
		String conditional = conditionalIds(criteria);
		// The exclusion is offered only where the document authorized one, and the authorized ids
		// are named. Offering it everywhere would invite the audit to decide for itself which
		// criteria it has to answer.
		String exclusion = conditional.isEmpty() ? "" : """

            NOT_APPLICABLE is available for these criteria only: %s. They carry an "Applies
            when" clause, and you may answer NOT_APPLICABLE only when that clause does not hold
            for this subject. You must give the reason after the dash. Do not use it for any
            other criterion, and do not use it because a criterion is hard to establish; that is
            CANNOT_DETERMINE.
            """.formatted(conditional);
		return JudgePromptTemplate.fromString(name, """
            You are auditing a Java implementation against the acceptance criteria it was
            built to satisfy. You are in the implementation's root. Read files, grep, and
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
            """.formatted(n, n, n, exclusion, list.toString()));
	}

	/**
	 * Classify one answer against a roster, bypassing construction.
	 *
	 * <p>The construction guard refuses an empty roster, so this is how the rollup's own guard is
	 * exercised: it is the guard that holds if the first is ever bypassed.
	 *
	 * @param criteria the roster
	 * @param response the audit to classify
	 * @return the judgment the rollup produces
	 */
	static Judgment rollupFor(List<EarsCriterion> criteria, JudgeModelResponse response) {
		return classifier(criteria).classify(response);
	}

	private static JudgmentClassifier classifier(List<EarsCriterion> criteria) {
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

			if (criteria.isEmpty()) {
				// An empty roster is a denominator of zero, and a conjunctive rollup over one is
				// vacuously satisfied: nothing failed, nothing was unestablished, so the subject
				// "meets the specification". That green judgment is indistinguishable, in every
				// stored field, from one computed over a specification that was genuinely met.
				return rollup(JudgmentStatus.ERROR,
						"The criteria roster is empty, so there was nothing to establish", List.of(), 0, "",
						List.of(), 0, List.of());
			}

			Map<String, EarsCriterion> roster = new LinkedHashMap<>();
			criteria.forEach(c -> roster.put(c.id(), c));

			Map<String, JudgmentStatus> outcome = new LinkedHashMap<>();
			Map<String, String> evidence = new LinkedHashMap<>();
			parse(text, roster.keySet(), outcome, evidence);

			// A criterion the audit skipped is not a criterion that passed. It is an instrument
			// failure like any protocol breach, so it is collected here and reported through the
			// same evidence-preserving rollup rather than returned on the spot: the criteria the
			// audit did establish were still established, and discarding them would make the
			// instrument's mistake cost more than it should.
			List<String> unanswered = roster.keySet().stream().filter(id -> !outcome.containsKey(id)).toList();

			// Logged either way, so the passing case is as legible as the failing one:
			// "N of N answered" is the evidence that the roster was checked, not assumed.
			logger.info("{} of {} criteria answered", outcome.size(), roster.size());

			List<Check> checks = new ArrayList<>();
			List<String> abstained = new ArrayList<>();
			List<Map<String, Object>> excluded = new ArrayList<>();
			List<String> protocolErrors = new ArrayList<>();
			long passed = 0;
			long failed = 0;
			for (EarsCriterion criterion : roster.values()) {
				String id = criterion.id();
				JudgmentStatus status = outcome.get(id);
				String why = evidence.get(id);
				if (status == null) {
					// Unanswered. No Check, because nothing about this criterion was assessed and
					// a manufactured one would read as a finding about the subject; and no
					// exclusion, because nobody excluded it. The gap is named in the reasoning.
					continue;
				}
				switch (status) {
					case PASS -> {
						passed++;
						checks.add(Check.pass(id, why));
					}
					case FAIL -> {
						failed++;
						checks.add(Check.fail(id, why));
					}
					case NOT_APPLICABLE -> {
						// An excluded criterion is not a Check: nothing about it was assessed.
						// An illegal exclusion is a protocol error and is never counted as an
						// authorized one, or the count would launder the thing it records.
						String reason = why == null ? "" : why;
						if (!criterion.conditional()) {
							protocolErrors.add(id + " is unconditional, so NOT_APPLICABLE is not an available answer");
						}
						else if (reason.isBlank()) {
							protocolErrors.add(id + " was excluded with no reason, and an unexplained exclusion "
								+ "cannot be audited");
						}
						else {
							Map<String, Object> entry = new LinkedHashMap<>();
							entry.put("id", id);
							entry.put("reason", reason);
							excluded.add(entry);
						}
					}
					default -> {
						abstained.add(id);
						checks.add(Check.fail(id, "could not be established: " + why));
					}
				}
			}

			// PASS means every criterion that applied was affirmatively established. An
			// incomplete roster outranks every finding: a conjunction over part of a
			// specification says nothing about the whole of it.
			String rosterError = unanswered.isEmpty() ? null
				: "The audit did not answer " + unanswered.size() + " of " + roster.size()
					+ " criteria, beginning with " + unanswered.get(0);

			JudgmentStatus verdict = rosterError != null || !protocolErrors.isEmpty() ? JudgmentStatus.ERROR
				: failed > 0 ? JudgmentStatus.FAIL
				: !abstained.isEmpty() ? JudgmentStatus.ABSTAIN
				: excluded.size() == roster.size() ? JudgmentStatus.NOT_APPLICABLE
				: JudgmentStatus.PASS;

			String reasoning = rosterError != null
				? rosterError + (protocolErrors.isEmpty() ? ""
					: "; the audit also broke protocol: " + String.join("; ", protocolErrors))
				: !protocolErrors.isEmpty() ? "The audit broke protocol: " + String.join("; ", protocolErrors)
				: summarize(passed, failed, abstained, excluded.size(), roster.size());

			// The rollup happens in Java, not in the model, and this line says so: the verdict
			// and the requirement that bound it. On the ABSTAIN path that identifier is the
			// fact a jury would otherwise absorb -- see FixedRosterAggregationTests.
			logger.info("verdict {} - {}", verdict, verdict == JudgmentStatus.PASS ? reasoning
				: verdict == JudgmentStatus.ERROR
					? (rosterError != null ? unanswered.get(0) + " was not answered" : protocolErrors.get(0))
				: !abstained.isEmpty() && failed == 0 ? abstained.get(0) + " could not be established"
				: failed > 0 ? failed + " of " + roster.size() + " violated" : reasoning);

			// Non-binding: metadata takes no part in the rollup above.
			List<Map<String, Object>> observations = observations(text, roster.keySet()).stream()
				.map(Observation::toMetadata).toList();
			return rollup(verdict, reasoning, checks, passed, String.join(",", abstained), excluded, roster.size(),
				observations);
		};
	}

	/**
	 * Build the judgment, with the same evidence on every path.
	 *
	 * <p>Sibling checks and the totals survive a protocol error deliberately. A run that broke
	 * protocol on one criterion still established the others, and throwing that away would make
	 * the instrument's mistake cost more than it should.
	 */
	private static Judgment rollup(JudgmentStatus verdict, String reasoning, List<Check> checks, long established,
			String unestablished, List<Map<String, Object>> excluded, int total,
			List<Map<String, Object>> observations) {
		Judgment.EnrichmentBuilder builder = switch (verdict) {
			case PASS -> Judgment.builder().pass().reasoning(reasoning);
			case FAIL -> Judgment.builder().fail().reasoning(reasoning);
			case ABSTAIN -> Judgment.builder().abstain().reasoning(reasoning);
			case NOT_APPLICABLE -> Judgment.builder().notApplicable().reasoning(reasoning);
			case ERROR -> Judgment.builder().error(JudgmentReasonCode.JUDGE_REPORTED).reasoning(reasoning);
		};
		return builder.checks(checks)
			.metadata("criteriaTotal", total)
			.metadata("established", established)
			.metadata("unestablished", unestablished)
			.metadata("notApplicableCount", excluded.size())
			.metadata("notApplicable", excluded)
			.metadata(Observation.METADATA_KEY, observations)
			.build();
	}

	/**
	 * Optional, non-binding, and deliberately forgiving. An absent, malformed or unknown-id
	 * observation yields nothing at all — it must never turn a valid judgment into a failure,
	 * because a cosmetic change in non-binding model prose would then break a valid run.
	 */
	private static List<Observation> observations(String text, Set<String> roster) {
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
			if (!roster.contains(id) || message.isEmpty()) {
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
				: upper.startsWith("NOT_APPLICABLE") ? JudgmentStatus.NOT_APPLICABLE
				: upper.startsWith("CANNOT") ? JudgmentStatus.ABSTAIN : null;
			if (status == null) {
				continue;
			}
			int dash = rest.indexOf('-');
			outcome.put(id, status);
			// An exclusion's evidence is its reason, and the reason is what follows the dash. No
			// dash means no reason was given, which is a protocol error rather than a reason
			// that happens to read "NOT_APPLICABLE".
			evidence.put(id, dash < 0 ? (status == JudgmentStatus.NOT_APPLICABLE ? "" : rest)
				: rest.substring(dash + 1).strip());
		}
	}

	private static String summarize(long passed, long failed, List<String> abstained, int excluded, int total) {
		if (excluded == total) {
			return "none of the %d requirements applied to this subject".formatted(total);
		}
		if (failed == 0 && abstained.isEmpty() && excluded == 0) {
			return "all %d requirements established".formatted(total);
		}
		StringBuilder text = new StringBuilder("%d of %d established".formatted(passed, total));
		if (failed > 0) {
			text.append(", %d not satisfied".formatted(failed));
		}
		if (!abstained.isEmpty()) {
			text.append(", %d could not be established: %s".formatted(abstained.size(), String.join(", ", abstained)));
		}
		if (excluded > 0) {
			text.append(", %d did not apply".formatted(excluded));
		}
		return text.toString();
	}
}
