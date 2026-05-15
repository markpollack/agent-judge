/**
 * AgentClient CLI-agent evaluation bridge for Agent Judge.
 * <p>
 * This module converts {@code AgentClientResponse} from CLI-delegated agent
 * executions (Claude Code, Codex, Gemini CLI, Amazon Q, etc.) into
 * {@link io.github.markpollack.judge.context.JudgmentContext} for evaluation
 * by ordinary judges and juries.
 * <p>
 * <strong>Boundary</strong>: This module adapts execution results only.
 * It does not own runtime semantics — provider configuration, session management,
 * approval modes, CLI process lifecycle, advisor context, journal traces, and
 * phase capture all remain in AgentClient. Detailed tool execution traces belong
 * to agent-journal / advisor instrumentation, not this bridge.
 * <p>
 * This is distinct from the agent-as-judge pattern ({@code AgentJudge}), which
 * uses a CLI agent to perform evaluation. That concern belongs with agent runtimes.
 *
 * @see io.github.markpollack.judge.agentclient.AgentClientEvaluator
 * @see io.github.markpollack.judge.agentclient.AgentClientJudgmentContextBuilder
 * @see io.github.markpollack.judge.agentclient.AgentClientMetadataKeys
 */
package io.github.markpollack.judge.agentclient;
