/**
 * Evaluated-side bridge from Koog agents to Agent Judge contexts.
 *
 * <p>The bridge captures the configured provider/model identity and the agent ID. Koog
 * token, finish, and tool-event evidence requires separately configured event-pipeline
 * instrumentation and is deliberately outside this output-oriented adapter.
 */
package io.github.markpollack.judge.koog;
