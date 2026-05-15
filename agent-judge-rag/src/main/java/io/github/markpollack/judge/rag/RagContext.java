package io.github.markpollack.judge.rag;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

import io.github.markpollack.judge.context.JudgmentContext;

/**
 * Metadata key conventions and extraction helpers for RAG evaluation triples.
 * <p>
 * RAG judges expect the JudgmentContext to contain these metadata entries:
 * <ul>
 *   <li>{@code rag.question} — the user's question</li>
 *   <li>{@code rag.context} — the retrieved context (String or List of objects)</li>
 *   <li>{@code rag.answer} — the generated answer</li>
 * </ul>
 * <p>
 * The question falls back to {@link JudgmentContext#goal()}, and the answer falls back
 * to {@link JudgmentContext#agentOutput()}. Context returns {@link Optional#empty()} when
 * no context is available — judges should ABSTAIN rather than evaluate without context.
 * <p>
 * When {@code rag.context} is absent, the extractor also checks
 * {@code langchain4j.sources} as a fallback, joining list elements with newlines.
 *
 * @author Mark Pollack
 * @since 0.10.0
 */
public final class RagContext {

	public static final String QUESTION_KEY = "rag.question";

	public static final String CONTEXT_KEY = "rag.context";

	public static final String ANSWER_KEY = "rag.answer";

	public static final String LANGCHAIN4J_SOURCES_KEY = "langchain4j.sources";

	private RagContext() {
	}

	/**
	 * Extract the question from context metadata, falling back to
	 * {@link JudgmentContext#goal()}.
	 */
	public static String question(JudgmentContext context) {
		Object q = context.metadata().get(QUESTION_KEY);
		if (q != null) {
			return q.toString();
		}
		return context.goal();
	}

	/**
	 * Extract the retrieved context from metadata.
	 * <p>
	 * Checks {@code rag.context} first, then {@code langchain4j.sources} as a fallback.
	 * Handles both String and List values (list elements are joined with newlines).
	 * Returns {@link Optional#empty()} when no context is available — judges should
	 * ABSTAIN rather than evaluate against empty context.
	 */
	public static Optional<String> context(JudgmentContext context) {
		// Try rag.context first
		Object c = context.metadata().get(CONTEXT_KEY);
		if (c == null) {
			// Fallback to langchain4j.sources
			c = context.metadata().get(LANGCHAIN4J_SOURCES_KEY);
		}
		if (c == null) {
			return Optional.empty();
		}
		return Optional.of(normalizeToString(c)).filter(s -> !s.isBlank());
	}

	/**
	 * Extract the answer from metadata or {@link JudgmentContext#agentOutput()}.
	 * Returns {@link Optional#empty()} when no answer is available.
	 */
	public static Optional<String> answer(JudgmentContext context) {
		Object a = context.metadata().get(ANSWER_KEY);
		if (a != null) {
			String s = a.toString();
			return s.isBlank() ? Optional.empty() : Optional.of(s);
		}
		return context.agentOutput().filter(s -> !s.isBlank());
	}

	/**
	 * Normalize a metadata value to a String. Lists and Collections are joined with
	 * newlines. Other types use toString().
	 */
	private static String normalizeToString(Object value) {
		if (value instanceof String s) {
			return s;
		}
		if (value instanceof Collection<?> coll) {
			return coll.stream().map(Object::toString).collect(Collectors.joining("\n"));
		}
		return value.toString();
	}

}
