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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import io.github.markpollack.judge.ai.requirements.MarkdownRequirementDocument.Line;
import io.github.markpollack.judge.ai.requirements.MarkdownRequirementDocument.Parsed;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One acceptance criterion, read from the specification the code was built from.
 *
 * <p>
 * The judge does not invent these and does not get to choose them. Somebody wrote them
 * down, numbered, before any code existed, and this reads them verbatim.
 *
 * <p>
 * The identifier is what makes an answer traceable back to the thing that was asked, and
 * what lets the roster notice a missing answer. A specification with 52 criteria has 52;
 * a judge that answers five of them has sampled it, not evaluated it.
 *
 * <h2>Conditional criteria</h2>
 *
 * <p>
 * Some criteria only apply to some subjects — a rule about Java sources against a change
 * set that contains none. {@code applicability} states that condition, and stating it is
 * what makes {@code NOT_APPLICABLE} an available answer for this criterion. A criterion
 * with no applicability clause is unconditional: it applies to every subject, and an
 * audit that tries to exclude it is making a protocol error rather than a finding.
 *
 * <p>
 * The asymmetry is deliberate. Excluding a criterion removes it from the denominator, so
 * the condition under which that is legitimate belongs in the document, written before
 * the subject was seen, rather than in an answer written after.
 *
 * <h2>Document layout</h2>
 *
 * <p>
 * The document is read as CommonMark. A criterion is a {@code ### UCn-ACm: Title} heading,
 * followed within the same entry by its requirement sentence: the first paragraph line that
 * is not a metadata line. Metadata lines begin with one of the labels {@code Covers:},
 * {@code Rule:}, {@code Rules:}, {@code Tests:} or {@code Status:} (bold or plain) or are an
 * {@code Applies when:} entry; they can neither stand in for the sentence nor hide its
 * absence. The entry ends at the next heading of any level or at the end of the document;
 * later lines in the entry are prose and are not read. {@code Applies when: condition} (also
 * accepted as {@code **Applies when:** condition}) declares the optional applicability
 * anywhere inside the entry, preferably after the sentence. An EARS trigger such as
 * {@code When}, {@code While} or {@code If} inside the sentence never declares
 * applicability. Fenced and indented code blocks, HTML blocks and thematic breaks are never
 * read, so an example criterion inside a fence of any length or character is documentation;
 * paragraphs inside lists and block quotes are read like any other. The sentence is always
 * the document's own line, never a rendering of it.
 *
 * <p>
 * {@link #from(Path)} is permissive: a heading without a sentence is left out, and every
 * such problem is logged at WARN. {@link #fromStrict(Path)} refuses the document instead,
 * naming each problem by identifier and line, and {@link #problems(Path)} returns the same
 * list without loading.
 *
 * @param id the specification's own identifier, such as {@code UC6-AC41}
 * @param title the criterion's heading text
 * @param requirement the criterion sentence, verbatim from the specification
 * @param applicability the condition under which this criterion applies, or null when it
 * always does; must be non-blank when present
 * @author Mark Pollack
 * @since 0.16.0
 */
public record EarsCriterion(String id, String title, String requirement, @Nullable String applicability) {

	/**
	 * Validate the applicability clause.
	 * @throws IllegalArgumentException if {@code applicability} is present and blank
	 */
	public EarsCriterion {
		if (applicability != null && applicability.isBlank()) {
			throw new IllegalArgumentException(
					"applicability must be non-blank when present; use null for an unconditional criterion");
		}
	}

	/**
	 * An unconditional criterion, which applies to every subject.
	 * @param id the specification's own identifier
	 * @param title the criterion's heading text
	 * @param requirement the criterion sentence, verbatim
	 */
	public EarsCriterion(String id, String title, String requirement) {
		this(id, title, requirement, null);
	}

	/**
	 * Whether this criterion may be answered {@code NOT_APPLICABLE}.
	 * @return true when the document states a condition under which it applies
	 */
	public boolean conditional() {
		return this.applicability != null;
	}

	/**
	 * Flow logging at INFO: what was parsed, what was answered, what bound the verdict.
	 */
	private static final Logger logger = LoggerFactory.getLogger(EarsCriterion.class);

	static Logger logger() {
		return logger;
	}

	/** {@code ### UC6-AC5: Permit adjacent future appointments} */
	private static final Pattern HEADING = Pattern.compile("^### (UC\\d+-AC\\d+): (.+)$");

	/**
	 * The labelled lines a criteria document carries beside its sentence: traceability and
	 * status, never the requirement. {@code Applies when:} is recognised separately.
	 */
	private static final Pattern METADATA = Pattern.compile(
			"^(?:\\*\\*)?(?:Covers|Rules?|Tests|Status):(?:\\*\\*)?(?:\\s|$)", Pattern.CASE_INSENSITIVE);

	/** A heading that looks like a criterion but is not written exactly as {@link #HEADING}. */
	private static final Pattern CRITERION_LIKE = Pattern.compile("^#{1,6}\\s+UC\\d+-AC\\d+\\b.*$",
			Pattern.CASE_INSENSITIVE);

	/**
	 * Read every criterion out of a use case's {@code criteria.md}.
	 * @param criteriaFile the acceptance-criteria document to read
	 * @return every criterion in the document, in the document's order
	 * @throws java.io.UncheckedIOException if the document cannot be read
	 */
	public static List<EarsCriterion> from(Path criteriaFile) {
		return parseSnapshot(read(criteriaFile), criteriaFile.getFileName().toString());
	}

	/**
	 * Read every criterion out of a use case's {@code criteria.md}, refusing the document if
	 * any recognisable criterion is malformed.
	 * @param criteriaFile the acceptance-criteria document to read
	 * @return every criterion, in the document's order
	 * @throws RequirementDocumentException naming each malformed heading, missing sentence,
	 * duplicate identifier and invalid applicability entry, with its line
	 * @throws java.io.UncheckedIOException if the document cannot be read
	 * @since 0.19.0
	 */
	public static List<EarsCriterion> fromStrict(Path criteriaFile) {
		return parse(read(criteriaFile), criteriaFile.getFileName().toString()).strict();
	}

	/**
	 * Every problem a strict read of the document would report, without loading it.
	 * @param criteriaFile the acceptance-criteria document to check
	 * @return the problems in document order; empty when {@link #fromStrict(Path)} would
	 * succeed
	 * @throws java.io.UncheckedIOException if the document cannot be read
	 * @since 0.19.0
	 */
	public static List<RequirementDocumentProblem> problems(Path criteriaFile) {
		return parse(read(criteriaFile), criteriaFile.getFileName().toString()).problems();
	}

	static List<EarsCriterion> parseSnapshot(String snapshot, String sourceName) {
		return parse(snapshot, sourceName).permissive(logger);
	}

	static Parsed<EarsCriterion> parse(String snapshot, String sourceName) {
		List<EarsCriterion> criteria = new ArrayList<>();
		List<RequirementDocumentProblem> problems = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		Entry entry = null;

		for (Line line : MarkdownRequirementDocument.lines(snapshot)) {
			String text = line.text();
			if (line.heading()) {
				finish(entry, criteria, problems, seen, sourceName);
				entry = null;
				Matcher heading = HEADING.matcher(text);
				if (heading.matches()) {
					entry = new Entry(heading.group(1), heading.group(2).strip(), line.number());
				}
				else if (CRITERION_LIKE.matcher(text).matches()) {
					problems.add(new RequirementDocumentProblem(sourceName,
							MarkdownRequirementDocument.headingToken(text), line.number(),
							"heading resembles a criterion but is not exactly `### UCn-ACm: Title`"));
				}
				continue;
			}
			if (entry == null || text.isEmpty() || METADATA.matcher(text).find()) {
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
							"a second `Applies when:` entry; one condition per criterion"));
				}
				else {
					entry.applicability = applies;
				}
				continue;
			}
			if (entry.requirement == null) {
				entry.requirement = text;
			}
		}
		finish(entry, criteria, problems, seen, sourceName);
		// The denominator comes from the parser, not from the model. Logged before any
		// answer exists, because that is what makes the roster guard meaningful.
		logger.info("{} criteria parsed from {}", criteria.size(), sourceName);

		return new Parsed<>(sourceName, List.copyOf(criteria), List.copyOf(problems));
	}

	private static void finish(@Nullable Entry entry, List<EarsCriterion> criteria,
			List<RequirementDocumentProblem> problems, Set<String> seen, String sourceName) {
		if (entry == null) {
			return;
		}
		if (!seen.add(entry.id)) {
			problems.add(new RequirementDocumentProblem(sourceName, entry.id, entry.line,
					"duplicate identifier; an earlier heading already declared it"));
		}
		String requirement = entry.requirement;
		if (requirement == null) {
			problems.add(new RequirementDocumentProblem(sourceName, entry.id, entry.line,
					"no requirement sentence before the next heading or the end of the document"));
		}
		problems.addAll(entry.problems);
		if (requirement != null) {
			criteria.add(new EarsCriterion(entry.id, entry.title, requirement, entry.applicability));
		}
	}

	/** One criterion entry while its lines are being read. */
	private static final class Entry {

		private final String id;

		private final String title;

		private final int line;

		private @Nullable String requirement;

		private @Nullable String applicability;

		private final List<RequirementDocumentProblem> problems = new ArrayList<>();

		private Entry(String id, String title, int line) {
			this.id = id;
			this.title = title;
			this.line = line;
		}
	}

	/**
	 * Only the criteria whose identifiers were named, in the document's order.
	 * @param all the criteria read from the document
	 * @param ids the identifiers to select, such as {@code UC6-AC41}
	 * @return the named criteria, in the document's order rather than the caller's
	 * @throws IllegalArgumentException if any named identifier is not in the document,
	 * because silently returning fewer would evaluate a roster nobody wrote
	 */
	public static List<EarsCriterion> select(List<EarsCriterion> all, String... ids) {
		List<String> wanted = List.of(ids);
		List<EarsCriterion> selected = all.stream().filter(c -> wanted.contains(c.id())).toList();
		if (selected.size() != wanted.size()) {
			throw new IllegalArgumentException(
					"asked for " + wanted.size() + " criteria and found " + selected.size() + " in the document");
		}
		return selected;
	}

	/**
	 * The criterion as the judge is asked to assess it.
	 * @return the identifier and the requirement sentence
	 */
	public String asPrompt() {
		return applicability == null ? id + ": " + requirement
				: id + ": " + requirement + " (Applies when: " + applicability + ")";
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
