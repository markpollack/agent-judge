/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.ai.requirements;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * What the RFC2119 and EARS readers share: fenced-code skipping, heading detection, the
 * {@code Applies when:} entry, and the two ways a parse can be handed back.
 */
final class MarkdownRequirementDocument {

	private MarkdownRequirementDocument() {
	}

	/** One stripped line outside fenced code, numbered from 1 in the original document. */
	record Line(int number, String text) {
	}

	/**
	 * Everything one reader found in one document: the requirements it could build and the
	 * problems it noticed on the way.
	 *
	 * @param <T> requirement type
	 */
	record Parsed<T>(String source, List<T> items, List<RequirementDocumentProblem> problems) {

		/** The permissive result: every problem is logged, nothing is refused. */
		List<T> permissive(Logger logger) {
			for (RequirementDocumentProblem problem : problems) {
				logger.warn("{}", problem.describe());
			}
			return items;
		}

		/** The strict result: any problem refuses the whole load. */
		List<T> strict() {
			if (!problems.isEmpty()) {
				throw new RequirementDocumentException(source, problems);
			}
			return items;
		}
	}

	private static final Pattern FENCE = Pattern.compile("^(```|~~~).*$");

	private static final Pattern ANY_HEADING = Pattern.compile("^#{1,6}\\s.*$");

	/** {@code **Applies when:** condition} or {@code Applies when: condition}. */
	private static final Pattern APPLIES_WHEN = Pattern.compile("^(?:\\*\\*Applies when:\\*\\*|Applies when:)\\s*(.*)$",
			Pattern.CASE_INSENSITIVE);

	/**
	 * The document's lines outside fenced code blocks, stripped and numbered.
	 * @param snapshot the document text
	 * @return the lines a reader may interpret
	 */
	static List<Line> lines(String snapshot) {
		List<Line> lines = new ArrayList<>();
		String fence = null;
		int number = 0;
		for (String raw : snapshot.lines().toList()) {
			number++;
			String text = raw.strip();
			if (fence == null && FENCE.matcher(text).matches()) {
				fence = text.substring(0, 3);
				continue;
			}
			if (fence != null) {
				if (text.startsWith(fence)) {
					fence = null;
				}
				continue;
			}
			lines.add(new Line(number, text));
		}
		return lines;
	}

	/**
	 * Whether the line is a Markdown heading of any level.
	 * @param text a stripped line
	 * @return true for {@code # …} through {@code ###### …}
	 */
	static boolean isHeading(String text) {
		return ANY_HEADING.matcher(text).matches();
	}

	/**
	 * The heading's first token, with a trailing colon removed, as the nearest thing to an
	 * identifier a malformed heading offers.
	 * @param text a stripped heading line
	 * @return the token, or null when the heading has none
	 */
	static @Nullable String headingToken(String text) {
		String rest = text.replaceFirst("^#{1,6}\\s+", "").strip();
		if (rest.isEmpty()) {
			return null;
		}
		int space = rest.indexOf(' ');
		String token = space < 0 ? rest : rest.substring(0, space);
		return token.endsWith(":") ? token.substring(0, token.length() - 1) : token;
	}

	/**
	 * The applicability condition declared on the line.
	 * @param text a stripped line
	 * @return the condition, the empty string when the entry is present but blank, or null
	 * when the line is not an {@code Applies when:} entry
	 */
	static @Nullable String appliesWhen(String text) {
		Matcher entry = APPLIES_WHEN.matcher(text);
		return entry.matches() ? entry.group(1).strip() : null;
	}
}
