/**
 * Spring AI ChatResponse evaluation bridge for Agent Judge.
 * <p>
 * This module converts Spring AI {@code ChatResponse} output into
 * {@link io.github.markpollack.judge.context.JudgmentContext} for evaluation
 * by ordinary judges and juries.
 * <p>
 * <strong>This is the evaluated-side bridge, NOT the judging-side LLM dependency.</strong>
 * {@code agent-judge-llm} uses {@code spring-ai-client-chat} to call LLMs for
 * LLM-as-judge evaluation. This module evaluates Spring AI agent output — the
 * opposite side of the evaluation boundary.
 * <p>
 * <strong>Boundary</strong>: Extracts output text, finish reason, token usage, model
 * name, response ID, and tool-call indicators into metadata. Tool call details are
 * best-effort — detailed tool execution traces and conversation history belong to
 * agent-journal / advisor instrumentation, not this bridge.
 *
 * @see io.github.markpollack.judge.springai.SpringAiEvaluator
 * @see io.github.markpollack.judge.springai.SpringAiJudgmentContextBuilder
 * @see io.github.markpollack.judge.springai.SpringAiMetadataKeys
 */
package io.github.markpollack.judge.springai;
