/**
 * Framework-neutral AI-backed judge infrastructure.
 *
 * <p>This module defines how an AI-backed judge is built: load prompt text, render it
 * with context, invoke an AI/model/agent backend, classify the output, and return a
 * normalized {@link io.github.markpollack.judge.result.Judgment}.
 *
 * <p>Key types:
 * <ul>
 *   <li>{@link io.github.markpollack.judge.ai.ModelBackedJudge} — composed judge (builder, no subclassing)</li>
 *   <li>{@link io.github.markpollack.judge.ai.model.JudgeModel} — framework-agnostic model invocation</li>
 *   <li>{@link io.github.markpollack.judge.ai.prompt.JudgePromptTemplate} — externalized prompt templates</li>
 *   <li>{@link io.github.markpollack.judge.ai.JudgmentClassifier} — maps model response to Judgment</li>
 * </ul>
 *
 * <p>Framework adapter modules bridge specific AI runtimes into
 * {@link io.github.markpollack.judge.ai.model.JudgeModel}.
 */
package io.github.markpollack.judge.ai;
