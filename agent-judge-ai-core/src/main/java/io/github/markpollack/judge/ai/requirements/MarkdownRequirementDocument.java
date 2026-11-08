/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.ai.requirements;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.commonmark.node.Block;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.Heading;
import org.commonmark.node.ListBlock;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SourceSpan;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * What the RFC2119 and EARS readers share: the Markdown structure, the
 * {@code Applies when:} entry, and the two ways a parse can be handed back.
 *
 * <p>
 * CommonMark decides what is a heading, a paragraph, a fenced or indented code block, an HTML
 * block or a list; the readers decide what is a requirement. Text is never taken from a
 * rendered tree. Each line the readers see is the document's own line, located by the
 * parser's source spans and stripped, so the normative text, its line number and the
 * document digest are the author's.
 */
final class MarkdownRequirementDocument {

	private MarkdownRequirementDocument() {
	}

	/**
	 * One stripped source line the readers may interpret, numbered from 1 in the original
	 * document.
	 *
	 * @param number 1-based line number
	 * @param text the document's own line, stripped
	 * @param heading whether CommonMark read the line as a heading of any level
	 * @param continuation whether the line continues the paragraph of the line before it
	 * @param content the paragraph's own text on this line, stripped: the line without any
	 * list or block-quote markers
	 */
	record Line(int number, String text, boolean heading, boolean continuation, String content) {
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

	private static final Parser PARSER = Parser.builder().includeSourceSpans(IncludeSourceSpans.BLOCKS).build();

	/** {@code **Applies when:** condition} or {@code Applies when: condition}. */
	private static final Pattern APPLIES_WHEN = Pattern.compile("^(?:\\*\\*Applies when:\\*\\*|Applies when:)\\s*(.*)$",
			Pattern.CASE_INSENSITIVE);

	/**
	 * The lines of headings and paragraphs, in document order. Fenced and indented code, HTML
	 * blocks, thematic breaks and link definitions are not read; paragraphs inside lists and
	 * block quotes are.
	 * @param snapshot the document text
	 * @return the lines a reader may interpret
	 */
	static List<Line> lines(String snapshot) {
		List<String> source = snapshot.lines().toList();
		List<Line> lines = new ArrayList<>();
		collect(PARSER.parse(snapshot), source, lines);
		return lines;
	}

	private static void collect(Node parent, List<String> source, List<Line> lines) {
		for (Node node = parent.getFirstChild(); node != null; node = node.getNext()) {
			if (node instanceof Heading) {
				List<SourceSpan> spans = node.getSourceSpans();
				if (!spans.isEmpty()) {
					lines.add(line(spans.get(0), source, true, false));
				}
			}
			else if (node instanceof Paragraph) {
				boolean continuation = false;
				for (SourceSpan span : node.getSourceSpans()) {
					lines.add(line(span, source, false, continuation));
					continuation = true;
				}
			}
			else if (node instanceof BlockQuote || node instanceof ListBlock || node instanceof ListItem) {
				collect(node, source, lines);
			}
			else if (!(node instanceof Block)) {
				collect(node, source, lines);
			}
		}
	}

	private static Line line(SourceSpan span, List<String> source, boolean heading, boolean continuation) {
		int index = span.getLineIndex();
		String whole = source.get(index);
		int start = Math.min(span.getColumnIndex(), whole.length());
		int end = Math.min(start + span.getLength(), whole.length());
		return new Line(index + 1, whole.strip(), heading, continuation, whole.substring(start, end).strip());
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
	 * Whether a requirement sentence visibly ends: its last character, after any closing
	 * brackets, quotes or inline-markup characters, is {@code .}, {@code !} or
	 * {@code ?}. A sentence that stops mid-clause, such as one truncated at a line break,
	 * does not.
	 * @param sentence a requirement sentence
	 * @return true when the sentence ends in terminal punctuation
	 */
	static boolean endsSentence(String sentence) {
		String text = sentence.strip();
		int end = text.length();
		while (end > 0 && ")]}\"'`*_".indexOf(text.charAt(end - 1)) >= 0)
			end--;
		return end > 0 && ".!?".indexOf(text.charAt(end - 1)) >= 0;
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
