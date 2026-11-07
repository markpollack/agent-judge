/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.ai.requirements;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.markpollack.judge.ai.JudgmentClassifier;
import io.github.markpollack.judge.ai.model.EvalModelResponse;
import io.github.markpollack.judge.judgment.Check;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentReasonCode;
import io.github.markpollack.judge.judgment.JudgmentStatus;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The conjunctive rollup of a generated roster audit, shared by the EARS and RFC 2119
 * parsers. The model answers one line per roster item; this class parses those lines,
 * keeps every item's own disposition as a check, and computes the verdict in Java.
 *
 * <p>
 * PASS means every item that applied was affirmatively established. An incomplete roster
 * or a protocol breach is an ERROR that still carries every check the audit did
 * establish. Observations are non-binding metadata and never change the verdict.
 * </p>
 */
final class RosterRollup implements JudgmentClassifier {

	/**
	 * {@code AppointmentServiceTests.java:191} — the only part of a message we treat as
	 * structured.
	 */
	private static final Pattern LOCATION = Pattern
		.compile("[A-Za-z0-9_/.]*[A-Za-z0-9_]+\\.(?:java|xml|sql|html|yml|properties):\\d+");

	/**
	 * One roster entry, as the rollup sees it.
	 *
	 * @param id protocol identifier the model answers under
	 * @param conditional whether NOT_APPLICABLE is an authorized answer
	 */
	record Item(String id, boolean conditional) {
	}

	/**
	 * The words that differ between the specification kinds.
	 *
	 * @param plural roster noun in the plural, such as "criteria"
	 * @param totalKey metadata key for the roster size
	 * @param noneApplied summary when every item was excluded, with one {@code %d}
	 * @param allPassed summary when every item passed, with one {@code %d}
	 * @param passedOf summary prefix, with {@code %d} passed and {@code %d} total
	 * @param failedSuffix summary clause for failures, with one {@code %d}
	 */
	record Vocabulary(String plural, String totalKey, String noneApplied, String allPassed, String passedOf,
			String failedSuffix) {
	}

	private final List<Item> items;

	private final Vocabulary words;

	private final Logger logger;

	private RosterRollup(List<Item> items, Vocabulary words, Logger logger) {
		this.items = items;
		this.words = words;
		this.logger = logger;
	}

	/**
	 * Validate the roster identities and build its classifier.
	 * @param items roster entries, in order
	 * @param words the specification kind's vocabulary
	 * @param logger the calling parser's logger
	 * @return classifier for one roster
	 */
	static RosterRollup of(List<Item> items, Vocabulary words, Logger logger) {
		Set<String> ids = new HashSet<>();
		for (Item item : items) {
			String id = item.id();
			if (id == null || id.isBlank() || !id.equals(id.strip()) || id.contains(":") || id.contains("\n")
					|| id.contains("\r") || !ids.add(id)) {
				throw new IllegalArgumentException(
						"requirement IDs must be nonblank, unique single-line protocol identifiers");
			}
		}
		return new RosterRollup(List.copyOf(items), words, logger);
	}

	/**
	 * Project a typed roster onto rollup items, refusing a missing entry with the same
	 * message as an invalid identity.
	 * @param <T> roster entry type
	 * @param source roster, in order
	 * @param id entry identity
	 * @param conditional whether the entry authorizes NOT_APPLICABLE
	 * @return items, in order
	 */
	static <T> List<Item> items(List<T> source, java.util.function.Function<T, String> id,
			java.util.function.Predicate<T> conditional) {
		List<Item> items = new ArrayList<>(source.size());
		for (T entry : source) {
			if (entry == null)
				throw new IllegalArgumentException(
						"requirement IDs must be nonblank, unique single-line protocol identifiers");
			items.add(new Item(id.apply(entry), conditional.test(entry)));
		}
		return items;
	}

	@Override
	public Judgment classify(@Nullable EvalModelResponse response) {
		String text = response == null || response.text() == null ? "" : response.text().strip();
		// A backend that could not produce an answer reports that explicitly, and whatever
		// text it carries is its explanation to the operator. Passing that through matters:
		// the difference between "the agent did not complete its run" and a backend that was
		// never given credentials is the difference between blaming the subject and naming
		// the real problem, and only the backend knows which it is.
		if (response == null || !response.completed()) {
			return auditError(text.isEmpty() ? "The judging agent did not complete its run" : text);
		}
		if (text.isEmpty()) {
			return auditError("No audit was produced for this implementation");
		}
		if (items.isEmpty()) {
			// An empty roster is a denominator of zero, and a conjunctive rollup over one is
			// vacuously satisfied: nothing failed, nothing was unestablished, so the subject
			// "meets the specification". That green judgment is indistinguishable, in every
			// stored field, from one computed over a specification that was genuinely met.
			return rollup(JudgmentStatus.ERROR,
					"The " + words.plural() + " roster is empty, so there was nothing to establish", List.of(), 0, "",
					List.of(), 0, List.of());
		}
		Map<String, Item> roster = new LinkedHashMap<>();
		items.forEach(item -> roster.put(item.id(), item));
		Answers answers = Answers.parse(text, roster.keySet());

		// An item the audit skipped is not an item that passed. It is an instrument failure
		// like any protocol breach, so it is collected here and reported through the same
		// evidence-preserving rollup rather than returned on the spot: the items the audit
		// did establish were still established, and discarding them would make the
		// instrument's mistake cost more than it should.
		List<String> unanswered = roster.keySet().stream().filter(id -> !answers.outcome.containsKey(id)).toList();
		// Logged either way, so the passing case is as legible as the failing one: "N of N
		// answered" is the evidence that the roster was checked, not assumed.
		logger.info("{} of {} {} answered", answers.outcome.size(), roster.size(), words.plural());

		Tally tally = new Tally(answers.protocolErrors);
		roster.values().forEach(item -> tally.record(item, answers));

		// An incomplete roster outranks every finding: a conjunction over part of a
		// specification says nothing about the whole of it.
		String rosterError = unanswered.isEmpty() ? null : "The audit did not answer " + unanswered.size() + " of "
				+ roster.size() + " " + words.plural() + ", beginning with " + unanswered.get(0);
		JudgmentStatus verdict = verdict(rosterError, tally, roster.size());
		String reasoning = reasoning(rosterError, tally, roster.size());
		logVerdict(verdict, reasoning, rosterError, unanswered, tally, roster.size());

		// Non-binding: metadata takes no part in the rollup above.
		List<Map<String, Object>> observations = observations(text, roster.keySet()).stream()
			.map(Observation::toMetadata)
			.toList();
		return rollup(verdict, reasoning, tally.checks, tally.passed, String.join(",", tally.abstained),
				tally.excluded, roster.size(), observations)
			.toBuilder()
			.metadata("protocolIdentityBound",
					tally.protocolErrors.stream()
						.noneMatch(error -> error.contains("undeclared requirement")
								|| error.contains("more than once")))
			.build();
	}

	private static JudgmentStatus verdict(@Nullable String rosterError, Tally tally, int total) {
		if (rosterError != null || !tally.protocolErrors.isEmpty())
			return JudgmentStatus.ERROR;
		if (tally.failed > 0)
			return JudgmentStatus.FAIL;
		if (!tally.abstained.isEmpty())
			return JudgmentStatus.ABSTAIN;
		return tally.excluded.size() == total ? JudgmentStatus.NOT_APPLICABLE : JudgmentStatus.PASS;
	}

	private String reasoning(@Nullable String rosterError, Tally tally, int total) {
		if (rosterError != null)
			return rosterError + (tally.protocolErrors.isEmpty() ? ""
					: "; the audit also broke protocol: " + String.join("; ", tally.protocolErrors));
		if (!tally.protocolErrors.isEmpty())
			return "The audit broke protocol: " + String.join("; ", tally.protocolErrors);
		return summarize(tally.passed, tally.failed, tally.abstained, tally.excluded.size(), total);
	}

	/**
	 * The rollup happens in Java, not in the model, and this line says so: the verdict
	 * and the requirement that bound it. On the ABSTAIN path that identifier is the fact
	 * a jury would otherwise absorb -- see FixedRosterAggregationTests.
	 */
	private void logVerdict(JudgmentStatus verdict, String reasoning, @Nullable String rosterError,
			List<String> unanswered, Tally tally, int total) {
		String bound;
		if (verdict == JudgmentStatus.PASS)
			bound = reasoning;
		else if (verdict == JudgmentStatus.ERROR)
			bound = rosterError != null ? unanswered.get(0) + " was not answered" : tally.protocolErrors.get(0);
		else if (!tally.abstained.isEmpty() && tally.failed == 0)
			bound = tally.abstained.get(0) + " could not be established";
		else if (tally.failed > 0)
			bound = tally.failed + " of " + total + " violated";
		else
			bound = reasoning;
		logger.info("verdict {} - {}", verdict, bound);
	}

	private Judgment auditError(String reason) {
		List<Check> checks = items.stream().map(item -> new Check(item.id(), Judgment.error(reason))).toList();
		return rollup(JudgmentStatus.ERROR, reason, checks, 0, "", List.of(), items.size(), List.of());
	}

	/**
	 * Build the judgment, with the same evidence on every path.
	 *
	 * <p>
	 * Sibling checks and the totals survive a protocol error deliberately. A run that
	 * broke protocol on one item still established the others, and throwing that away
	 * would make the instrument's mistake cost more than it should.
	 */
	private Judgment rollup(JudgmentStatus verdict, String reasoning, List<Check> checks, long established,
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
			.metadata(words.totalKey(), total)
			.metadata("established", established)
			.metadata("unestablished", unestablished)
			.metadata("notApplicableCount", excluded.size())
			.metadata("notApplicable", excluded)
			.metadata(Observation.METADATA_KEY, observations)
			.build();
	}

	private String summarize(long passed, long failed, List<String> abstained, int excluded, int total) {
		if (excluded == total) {
			return words.noneApplied().formatted(total);
		}
		if (failed == 0 && abstained.isEmpty() && excluded == 0) {
			return words.allPassed().formatted(total);
		}
		StringBuilder text = new StringBuilder(words.passedOf().formatted(passed, total));
		if (failed > 0) {
			text.append(words.failedSuffix().formatted(failed));
		}
		if (!abstained.isEmpty()) {
			text.append(", %d could not be established: %s".formatted(abstained.size(), String.join(", ", abstained)));
		}
		if (excluded > 0) {
			text.append(", %d did not apply".formatted(excluded));
		}
		return text.toString();
	}

	/**
	 * Optional, non-binding, and deliberately forgiving. An absent, malformed or
	 * unknown-id observation yields nothing at all — it must never turn a valid judgment
	 * into a failure, because a cosmetic change in non-binding model prose would then
	 * break a valid run.
	 */
	private static List<Observation> observations(String text, Set<String> roster) {
		List<Observation> found = new ArrayList<>();
		for (String line : text.lines().map(String::strip).toList()) {
			if (!line.toUpperCase(Locale.ROOT).startsWith("OBSERVATION")) {
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

	/** Each item's checked disposition and the counts the verdict is computed from. */
	private static final class Tally {

		final List<Check> checks = new ArrayList<>();

		final List<String> abstained = new ArrayList<>();

		final List<Map<String, Object>> excluded = new ArrayList<>();

		final List<String> protocolErrors;

		long passed;

		long failed;

		Tally(List<String> protocolErrors) {
			this.protocolErrors = protocolErrors;
		}

		void record(Item item, Answers answers) {
			String id = item.id();
			JudgmentStatus status = answers.outcome.get(id);
			String why = answers.evidence.get(id);
			if (status == null) {
				checks.add(new Check(id, Judgment.error("No answer was recorded for " + id)));
				return;
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
				case NOT_APPLICABLE -> exclusion(item, why == null ? "" : why);
				case ABSTAIN -> {
					abstained.add(id);
					checks.add(new Check(id, Judgment.abstain("could not be established: " + why)));
				}
				case ERROR -> {
					protocolErrors.add(id + ": " + why);
					checks.add(new Check(id, Judgment.error(why)));
				}
			}
		}

		/**
		 * Every declared requirement retains its actual disposition. An illegal exclusion
		 * is a protocol error and is never counted as an authorized one, or the count
		 * would launder the thing it records.
		 */
		private void exclusion(Item item, String reason) {
			String id = item.id();
			if (!item.conditional()) {
				String error = id + " is unconditional, so NOT_APPLICABLE is not an available answer";
				protocolErrors.add(error);
				checks.add(new Check(id, reason.isBlank() ? Judgment.error(error) : Judgment.notApplicable(reason)));
			}
			else if (reason.isBlank()) {
				String error = id + " was excluded with no reason, and an unexplained exclusion cannot be audited";
				protocolErrors.add(error);
				checks.add(new Check(id, Judgment.error(error)));
			}
			else {
				Map<String, Object> entry = new LinkedHashMap<>();
				entry.put("id", id);
				entry.put("reason", reason);
				excluded.add(entry);
				checks.add(new Check(id, Judgment.notApplicable(reason)));
			}
		}

	}

	/** The audit's answers, one per declared item, and its protocol errors. */
	private static final class Answers {

		final Map<String, JudgmentStatus> outcome = new LinkedHashMap<>();

		final Map<String, String> evidence = new LinkedHashMap<>();

		final List<String> protocolErrors = new ArrayList<>();

		private final Set<String> ids;

		private final Set<String> seen = new HashSet<>();

		private Answers(Set<String> ids) {
			this.ids = ids;
		}

		static Answers parse(String text, Set<String> ids) {
			Answers answers = new Answers(ids);
			for (String line : text.lines().map(String::strip).toList()) {
				answers.line(line);
			}
			return answers;
		}

		private void line(String line) {
			int colon = line.indexOf(':');
			if (colon < 0) {
				undecorated(line);
				return;
			}
			String id = declared(line.substring(0, colon).strip());
			if (!ids.contains(id) && line.toUpperCase(Locale.ROOT).startsWith("OBSERVATION")) {
				return; // Optional observation prose is nonbinding.
			}
			String rest = line.substring(colon + 1).strip();
			int dash = rest.indexOf('-');
			String token = (dash < 0 ? rest : rest.substring(0, dash)).strip().toUpperCase(Locale.ROOT);
			JudgmentStatus status = switch (token) {
				case "PASS" -> JudgmentStatus.PASS;
				case "FAIL" -> JudgmentStatus.FAIL;
				case "NOT_APPLICABLE" -> JudgmentStatus.NOT_APPLICABLE;
				case "CANNOT_DETERMINE" -> JudgmentStatus.ABSTAIN;
				default -> null;
			};
			if (!ids.contains(id)) {
				if (status != null || token.startsWith("PASS") || token.startsWith("FAIL") || token.startsWith("CANNOT")
						|| token.startsWith("NOT_APPLICABLE")) {
					protocolErrors.add("The audit answered undeclared requirement " + id);
				}
				return;
			}
			answer(id, status, token, dash < 0 ? "" : rest.substring(dash + 1).strip());
		}

		/** A line naming a declared item without a colon is a malformed answer for it. */
		private void undecorated(String line) {
			for (String id : ids) {
				if (line.equals(id) || line.startsWith(id + " ") || line.startsWith("**" + id + "** ")
						|| line.startsWith("`" + id + "` ")) {
					seen.add(id);
					outcome.put(id, JudgmentStatus.ERROR);
					evidence.put(id, "Malformed answer for " + id + ": missing colon");
				}
			}
		}

		/** Exact declared identity wins before optional outer Markdown decoration. */
		private String declared(String id) {
			if (ids.contains(id))
				return id;
			if (id.startsWith("**") && id.endsWith("**") && id.length() > 4)
				return id.substring(2, id.length() - 2);
			if (id.startsWith("`") && id.endsWith("`") && id.length() > 2)
				return id.substring(1, id.length() - 1);
			return id;
		}

		private void answer(String id, @Nullable JudgmentStatus parsed, String token, String evidenceText) {
			JudgmentStatus status = parsed;
			String why = evidenceText;
			if (!seen.add(id)) {
				status = JudgmentStatus.ERROR;
				why = "The audit answered " + id + " more than once";
			}
			else if (status == null) {
				status = JudgmentStatus.ERROR;
				why = "Invalid answer token for " + id + ": " + token;
			}
			else if (why.isBlank() && status != JudgmentStatus.NOT_APPLICABLE) {
				status = JudgmentStatus.ERROR;
				why = "The answer for " + id + " lacks the required evidence after a dash";
			}
			outcome.put(id, status);
			evidence.put(id, why);
		}

	}

}
