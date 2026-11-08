/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.ai.requirements;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import io.github.markpollack.judge.ai.requirements.MarkdownRequirementDocument.Line;
import io.github.markpollack.judge.ai.requirements.MarkdownRequirementDocument.Parsed;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One architectural constraint from the design the code was built to.
 *
 * <p>
 * These are the other half of the specification. An acceptance criterion says what the
 * system must <em>do</em>; a constraint says how it must be <em>built</em>. Same author,
 * same moment, different document — and, as it turns out, a different answer.
 *
 * <p>
 * The keyword is RFC 2119's, and it is captured rather than discarded because the
 * document is telling us how strictly to read the requirement. A document whose rules are
 * all {@code MUST} does not depend on it; it is recorded so a later {@code SHOULD} does
 * not have to be guessed at.
 *
 * <h2>Conditional constraints</h2>
 *
 * <p>
 * Some constraints only apply to some implementations — a rule about a persistence layer
 * against a service that has none. {@code applicability} states that condition, and
 * stating it is what makes {@code NOT_APPLICABLE} an available answer. A constraint with
 * no applicability clause is unconditional: an audit that tries to exclude it is making a
 * protocol error rather than a finding, because excluding a constraint removes it from
 * the denominator and the condition for that belongs in the design rather than in an
 * answer written after the code was read.
 *
 * <h2>Document layout</h2>
 *
 * <p>
 * The document is read as CommonMark. A rule is a {@code ### RULE-n} (or {@code ### UCn-RULEm})
 * heading with nothing else on its line, followed within the same entry by one bold keyword
 * statement, {@code **MUST** …}, and one {@code **Reason:** …} line, each on its own line of
 * a paragraph. The entry ends at the next heading of any level or at the end of the
 * document. Two further lines are recognised inside an entry and everything else is prose:
 * {@code **Covers:** …} is traceability and {@code **Applies when:** condition} (also
 * accepted without the bold) declares the optional applicability. Fenced and indented code
 * blocks, HTML blocks and thematic breaks are never read, so an example rule inside a fence
 * of any length or character is documentation; paragraphs inside lists and block quotes are
 * read like any other. The text of a rule is always the document's own line, never a
 * rendering of it.
 *
 * <p>
 * {@link #from(Path)} is permissive: an entry missing its keyword or reason is left out, and
 * every such problem is logged at WARN. {@link #fromStrict(Path)} refuses the document
 * instead, naming each problem by identifier and line, and {@link #problems(Path)} returns
 * the same list without loading. An apparently complete strict load therefore has every
 * recognisable rule in it.
 *
 * @param id the document's own identifier, such as {@code RULE-4}
 * @param keyword MUST, MUST NOT, SHOULD, SHOULD NOT or MAY
 * @param requirement the constraint text
 * @param reason why the design chose it, which travels with the rule into the prompt
 * @param applicability the condition under which this constraint applies, or null when it
 * always does; must be non-blank when present
 * @author Mark Pollack
 * @since 0.16.0
 */
public record Rfc2119Constraint(String id, String keyword, String requirement, String reason,
		@Nullable String applicability) {

	/**
	 * Validate the applicability clause.
	 * @throws IllegalArgumentException if {@code applicability} is present and blank
	 */
	public Rfc2119Constraint {
		if (applicability != null && applicability.isBlank()) {
			throw new IllegalArgumentException(
					"applicability must be non-blank when present; use null for an unconditional constraint");
		}
	}

	/**
	 * An unconditional constraint, which applies to every implementation.
	 * @param id the document's own identifier
	 * @param keyword the RFC 2119 keyword
	 * @param requirement the constraint text
	 * @param reason why the design chose it
	 */
	public Rfc2119Constraint(String id, String keyword, String requirement, String reason) {
		this(id, keyword, requirement, reason, null);
	}

	/**
	 * Whether this constraint may be answered {@code NOT_APPLICABLE}.
	 * @return true when the design states a condition under which it applies
	 */
	public boolean conditional() {
		return this.applicability != null;
	}

	/**
	 * Flow logging at INFO: what was parsed, what was answered, what bound the verdict.
	 */
	private static final Logger logger = LoggerFactory.getLogger(Rfc2119Constraint.class);

	static Logger logger() {
		return logger;
	}

	/** {@code ### RULE-4} or {@code ### UC6-RULE1} */
	private static final Pattern HEADING = Pattern.compile("^### ((?:UC\\d+-)?RULE-?\\d+)$");

	/** {@code **MUST** protect reservation-changing transactions ...} */
	private static final Pattern KEYWORD = Pattern
		.compile("^\\*\\*(MUST NOT|MUST|SHOULD NOT|SHOULD|MAY)\\*\\*\\s*(.*)$");

	/** A heading that looks like a rule but is not written exactly as {@link #HEADING}. */
	private static final Pattern RULE_LIKE = Pattern.compile("^#{1,6}\\s+(?:UC\\d+-)?RULE[-_ ]?\\d+\\b.*$",
			Pattern.CASE_INSENSITIVE);

	/**
	 * Read every constraint out of a design's {@code rules.md}.
	 * @param rulesFile the design document to read
	 * @return every constraint that states both a keyword and a reason, in the document's
	 * order
	 * @throws java.io.UncheckedIOException if the document cannot be read
	 */
	public static List<Rfc2119Constraint> from(Path rulesFile) {
		return parseSnapshot(read(rulesFile), rulesFile.getFileName().toString());
	}

	/**
	 * Read every constraint out of a design's {@code rules.md}, refusing the document if any
	 * recognisable rule is malformed.
	 * @param rulesFile the design document to read
	 * @return every constraint, in the document's order
	 * @throws RequirementDocumentException naming each malformed heading, missing statement
	 * or reason, duplicate identifier and invalid applicability entry, with its line
	 * @throws java.io.UncheckedIOException if the document cannot be read
	 * @since 0.19.0
	 */
	public static List<Rfc2119Constraint> fromStrict(Path rulesFile) {
		return parse(read(rulesFile), rulesFile.getFileName().toString()).strict();
	}

	/**
	 * Every problem a strict read of the document would report, without loading it.
	 * @param rulesFile the design document to check
	 * @return the problems in document order; empty when {@link #fromStrict(Path)} would
	 * succeed
	 * @throws java.io.UncheckedIOException if the document cannot be read
	 * @since 0.19.0
	 */
	public static List<RequirementDocumentProblem> problems(Path rulesFile) {
		return parse(read(rulesFile), rulesFile.getFileName().toString()).problems();
	}

	static List<Rfc2119Constraint> parseSnapshot(String snapshot, String sourceName) {
		return parse(snapshot, sourceName).permissive(logger);
	}

	static Parsed<Rfc2119Constraint> parse(String snapshot, String sourceName) {
		List<Rfc2119Constraint> constraints = new ArrayList<>();
		List<RequirementDocumentProblem> problems = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		Entry entry = null;

		for (Line line : MarkdownRequirementDocument.lines(snapshot)) {
			String text = line.text();
			if (line.heading()) {
				finish(entry, constraints, problems, seen, sourceName);
				entry = null;
				Matcher heading = HEADING.matcher(text);
				if (heading.matches()) {
					entry = new Entry(heading.group(1), line.number());
				}
				else if (RULE_LIKE.matcher(text).matches()) {
					problems.add(new RequirementDocumentProblem(sourceName,
							MarkdownRequirementDocument.headingToken(text), line.number(),
							"heading resembles a rule but is not exactly `### RULE-n` or `### UCn-RULEm` on its own line"));
				}
				continue;
			}
			if (entry == null) {
				continue;
			}
			Matcher must = KEYWORD.matcher(text);
			if (must.matches()) {
				String statement = must.group(2).strip();
				if (statement.isEmpty()) {
					entry.problems.add(new RequirementDocumentProblem(sourceName, entry.id, line.number(),
							"keyword `" + must.group(1) + "` without a statement after it"));
				}
				else if (entry.keyword != null) {
					entry.problems.add(new RequirementDocumentProblem(sourceName, entry.id, line.number(),
							"a second keyword statement; one obligation per heading"));
					if (entry.reason == null) {
						entry.keyword = must.group(1);
						entry.requirement = statement;
						entry.requirementLine = line.number();
					}
				}
				else {
					entry.keyword = must.group(1);
					entry.requirement = statement;
					entry.requirementLine = line.number();
				}
				continue;
			}
			if (text.startsWith("**Reason:**")) {
				String reason = text.substring("**Reason:**".length()).strip();
				if (reason.isEmpty()) {
					entry.problems.add(new RequirementDocumentProblem(sourceName, entry.id, line.number(),
							"`**Reason:**` without a reason after it"));
				}
				else if (entry.reason != null) {
					entry.problems.add(new RequirementDocumentProblem(sourceName, entry.id, line.number(),
							"a second `**Reason:**` line; one reason per rule"));
				}
				else {
					entry.reason = reason;
				}
				continue;
			}
			String applies = MarkdownRequirementDocument.appliesWhen(text);
			if (applies != null) {
				if (applies.isEmpty()) {
					entry.problems.add(new RequirementDocumentProblem(sourceName, entry.id, line.number(),
							"blank `Applies when:` entry; state the condition or omit the entry"));
				}
				else if (entry.applicability != null) {
					entry.problems.add(new RequirementDocumentProblem(sourceName, entry.id, line.number(),
							"a second `Applies when:` entry; one condition per rule"));
				}
				else {
					entry.applicability = applies;
				}
			}
		}
		finish(entry, constraints, problems, seen, sourceName);
		// The denominator comes from the parser, not from the model. Logged before any
		// answer exists, because that is what makes the roster guard meaningful.
		logger.info("{} constraints parsed from {}", constraints.size(), sourceName);

		return new Parsed<>(sourceName, List.copyOf(constraints), List.copyOf(problems));
	}

	/**
	 * Completeness: a keyword statement that stops mid-clause is most likely truncated.
	 */
	private static java.util.Optional<RequirementDocumentProblem> incomplete(Entry entry, String sourceName) {
		String requirement = entry.requirement;
		if (requirement == null || MarkdownRequirementDocument.endsSentence(requirement))
			return java.util.Optional.empty();
		return java.util.Optional.of(new RequirementDocumentProblem(sourceName, entry.id, entry.requirementLine,
				"keyword statement does not end in terminal punctuation (. ! ?); it may be truncated"));
	}

	private static void finish(@Nullable Entry entry, List<Rfc2119Constraint> constraints,
			List<RequirementDocumentProblem> problems, Set<String> seen, String sourceName) {
		if (entry == null) {
			return;
		}
		if (!seen.add(entry.id)) {
			problems.add(new RequirementDocumentProblem(sourceName, entry.id, entry.line,
					"duplicate identifier; an earlier heading already declared it"));
		}
		String keyword = entry.keyword;
		String requirement = entry.requirement;
		String reason = entry.reason;
		if (keyword == null || requirement == null) {
			problems.add(new RequirementDocumentProblem(sourceName, entry.id, entry.line,
					"no bold RFC2119 keyword statement (`**MUST** …`) before the next heading or the end of the document"));
		}
		if (reason == null) {
			problems.add(new RequirementDocumentProblem(sourceName, entry.id, entry.line,
					"no `**Reason:**` line before the next heading or the end of the document"));
		}
		incomplete(entry, sourceName).ifPresent(problems::add);
		problems.addAll(entry.problems);
		if (keyword != null && requirement != null && reason != null) {
			constraints.add(new Rfc2119Constraint(entry.id, keyword, requirement, reason, entry.applicability));
		}
	}

	/** One rule entry while its lines are being read. */
	private static final class Entry {

		private final String id;

		private final int line;

		private @Nullable String keyword;

		private @Nullable String requirement;

		private @Nullable String reason;

		private @Nullable String applicability;

		/** The line of the keyword statement in use. */
		private int requirementLine;

		private final List<RequirementDocumentProblem> problems = new ArrayList<>();

		private Entry(String id, int line) {
			this.id = id;
			this.line = line;
		}
	}

	/**
	 * A short label for terminal output: the requirement to its first clause break, on a
	 * word.
	 * @return the shortened label, ending in an ellipsis when it was cut
	 */
	public String title() {
		int cut = requirement.length();
		for (String mark : List.of("; ", ". ", ", and ", ", with ")) {
			int at = requirement.indexOf(mark);
			if (at > 0) {
				cut = Math.min(cut, at);
			}
		}
		String clause = requirement.substring(0, cut).strip();
		if (clause.length() <= 58) {
			return clause;
		}
		int space = clause.lastIndexOf(' ', 58);
		return clause.substring(0, space < 20 ? 58 : space).strip() + "\u2026";
	}

	/**
	 * The constraint as the judge is asked to assess it, with the design's own reason
	 * attached.
	 * @return the identifier, keyword, requirement and reason as one line
	 */
	public String asPrompt() {
		String base = id + ": " + keyword.toUpperCase(Locale.ROOT) + " " + requirement + " (Reason: " + reason + ")";
		return applicability == null ? base : base + " (Applies when: " + applicability + ")";
	}

	private static String read(Path path) {
		try {
			return Files.readString(path);
		}
		catch (IOException e) {
			throw new UncheckedIOException("Could not read " + path, e);
		}
	}
}
