package io.github.markpollack.judge.rag;

import io.github.markpollack.judge.context.JudgmentContext;

/**
 * Metadata key conventions for RAG evaluation triples.
 * <p>
 * RAG judges expect the JudgmentContext to contain these metadata entries:
 * <ul>
 *   <li>{@code rag.question} — the user's question</li>
 *   <li>{@code rag.context} — the retrieved context (string or list of strings)</li>
 *   <li>{@code rag.answer} — the generated answer</li>
 * </ul>
 * <p>
 * The answer can also be provided via {@link JudgmentContext#agentOutput()}.
 *
 * @author Mark Pollack
 * @since 0.10.0
 */
public final class RagContext {

	public static final String QUESTION_KEY = "rag.question";

	public static final String CONTEXT_KEY = "rag.context";

	public static final String ANSWER_KEY = "rag.answer";

	private RagContext() {
	}

	/**
	 * Extract the question from context metadata.
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
	 */
	public static String context(JudgmentContext context) {
		Object c = context.metadata().get(CONTEXT_KEY);
		return c != null ? c.toString() : "";
	}

	/**
	 * Extract the answer from metadata or agentOutput.
	 */
	public static String answer(JudgmentContext context) {
		Object a = context.metadata().get(ANSWER_KEY);
		if (a != null) {
			return a.toString();
		}
		return context.agentOutput().orElse("");
	}

}
