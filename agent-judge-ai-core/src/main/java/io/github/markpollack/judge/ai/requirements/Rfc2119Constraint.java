package io.github.markpollack.judge.ai.requirements;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One architectural constraint from the design the code was built to.
 *
 * <p>These are the other half of the specification. An acceptance criterion says what the system
 * must <em>do</em>; a constraint says how it must be <em>built</em>. Same author, same moment,
 * different document — and, as it turns out, a different answer.
 *
 * <p>The keyword is RFC 2119's, and it is captured rather than discarded because the document is
 * telling us how strictly to read the requirement. A document whose rules are all {@code MUST}
 * does not depend on it; it is recorded so a later {@code SHOULD} does not have to be guessed at.
 *
 * @param id the document's own identifier, such as {@code RULE-4}
 * @param keyword MUST, MUST NOT, SHOULD, SHOULD NOT or MAY
 * @param requirement the constraint text
 * @param reason why the design chose it, which travels with the rule into the prompt
 *
 * @author Mark Pollack
 * @since 0.16.0
 */
public record Rfc2119Constraint(String id, String keyword, String requirement, String reason) {

	/** Flow logging at INFO: what was parsed, what was answered, what bound the verdict. */
	private static final Logger logger = LoggerFactory.getLogger(Rfc2119Constraint.class);

	/** {@code ### RULE-4} or {@code ### UC6-RULE1} */
	private static final Pattern HEADING = Pattern.compile("^### ((?:UC\\d+-)?RULE-?\\d+)$");

	/** {@code **MUST** protect reservation-changing transactions ...} */
	private static final Pattern KEYWORD =
		Pattern.compile("^\\*\\*(MUST NOT|MUST|SHOULD NOT|SHOULD|MAY)\\*\\*\\s*(.*)$");

	/**
	 * Read every constraint out of a design's {@code rules.md}.
	 * @param rulesFile the design document to read
	 * @return every constraint that states both a keyword and a reason, in the document's order
	 * @throws java.io.UncheckedIOException if the document cannot be read
	 */
	public static List<Rfc2119Constraint> from(Path rulesFile) {
		List<Rfc2119Constraint> constraints = new ArrayList<>();
		String id = null;
		String keyword = null;
		String requirement = null;

		for (String line : read(rulesFile).lines().toList()) {
			String text = line.strip();
			Matcher heading = HEADING.matcher(text);
			if (heading.matches()) {
				id = heading.group(1);
				keyword = null;
				requirement = null;
				continue;
			}
			if (id == null) {
				continue;
			}
			Matcher must = KEYWORD.matcher(text);
			if (must.matches()) {
				keyword = must.group(1);
				requirement = must.group(2).strip();
			}
			else if (text.startsWith("**Reason:**") && requirement != null) {
				constraints.add(new Rfc2119Constraint(id, keyword, requirement,
					text.substring("**Reason:**".length()).strip()));
				id = null;
				keyword = null;
				requirement = null;
			}
		}
		// The denominator comes from the parser, not from the model. Logged before any
		// answer exists, because that is what makes the roster guard meaningful.
		logger.info("{} constraints parsed from {}", constraints.size(), rulesFile.getFileName());

		return List.copyOf(constraints);
	}

	/**
	 * A short label for terminal output: the requirement to its first clause break, on a word.
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
	 * The constraint as the judge is asked to assess it, with the design's own reason attached.
	 * @return the identifier, keyword, requirement and reason as one line
	 */
	public String asPrompt() {
		return id + ": " + keyword.toUpperCase(Locale.ROOT) + " " + requirement + " (Reason: " + reason + ")";
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
