/*
 * Copyright 2024 Spring AI Community
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.markpollack.judge.context;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Rich context containing all information needed for judgment.
 *
 * <p>
 * This record encapsulates the agent's goal, workspace, execution result, timing
 * information, and any additional context needed for evaluation. Judges extract the
 * information they need from this context.
 * </p>
 *
 * <p>
 * <strong>Design Inspiration:</strong> Follows the pattern from deepeval's LLMTestCase
 * and ragas's metric input columns - a single rich context object with all evaluation
 * inputs. The "context is king" principle from our research: all inputs in one place,
 * optional fields for flexibility, extensible via metadata map. Workspace-centric design
 * is unique to our agent-focused implementation.
 * </p>
 *
 * @param goal the agent's goal or task description
 * @param workspace the workspace directory where the agent executed
 * @param executionTime how long the agent took to execute
 * @param startedAt when the agent started executing
 * @param agentOutput the agent's output (if successful)
 * @param status the agent's execution status
 * @param error any exception that occurred during execution
 * @param metadata additional context information (extensibility); an in-process evaluation
 * input, so unlike {@link io.github.markpollack.judge.result.Judgment} metadata it may carry
 * framework-native objects a judge needs to inspect
 * @author Mark Pollack
 * @since 0.1.0
 */
public record JudgmentContext(String goal, Path workspace, Duration executionTime, Instant startedAt,
		Optional<String> agentOutput, ExecutionStatus status, Optional<Throwable> error, Map<String, Object> metadata) {

	/** Copy context metadata into an immutable map. */
	public JudgmentContext {
		metadata = Map.copyOf(metadata);
	}

	/**
	 * Start building a context.
	 * @return a new builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/** Builder for an immutable judgment context. */
	public static class Builder {

		/** Create an empty context builder. */
		public Builder() {
		}

		private String goal;

		private Path workspace;

		private Duration executionTime;

		private Instant startedAt;

		private Optional<String> agentOutput = Optional.empty();

		private ExecutionStatus status = ExecutionStatus.UNKNOWN;

		private Optional<Throwable> error = Optional.empty();

		private Map<String, Object> metadata = new HashMap<>();

		/**
		 * Set the task being evaluated.
		 * @param goal task being evaluated
		 * @return this builder
		 */
		public Builder goal(String goal) {
			this.goal = goal;
			return this;
		}

		/**
		 * Set the execution workspace.
		 * @param workspace execution workspace
		 * @return this builder
		 */
		public Builder workspace(Path workspace) {
			this.workspace = workspace;
			return this;
		}

		/**
		 * Set the elapsed execution time.
		 * @param executionTime elapsed execution time
		 * @return this builder
		 */
		public Builder executionTime(Duration executionTime) {
			this.executionTime = executionTime;
			return this;
		}

		/**
		 * Set the execution start time.
		 * @param startedAt execution start time
		 * @return this builder
		 */
		public Builder startedAt(Instant startedAt) {
			this.startedAt = startedAt;
			return this;
		}

		/**
		 * Set the agent output.
		 * @param agentOutput agent output, or null when absent
		 * @return this builder
		 */
		public Builder agentOutput(String agentOutput) {
			this.agentOutput = Optional.ofNullable(agentOutput);
			return this;
		}

		/**
		 * Set the execution status.
		 * @param status execution status
		 * @return this builder
		 */
		public Builder status(ExecutionStatus status) {
			this.status = status;
			return this;
		}

		/**
		 * Set the execution failure.
		 * @param error execution failure, or null when absent
		 * @return this builder
		 */
		public Builder error(Throwable error) {
			this.error = Optional.ofNullable(error);
			return this;
		}

		/**
		 * Replace the context metadata.
		 * @param metadata context metadata
		 * @return this builder
		 */
		public Builder metadata(Map<String, Object> metadata) {
			this.metadata = new HashMap<>(metadata);
			return this;
		}

		/**
		 * Add a context metadata entry.
		 * @param key metadata key
		 * @param value metadata value
		 * @return this builder
		 */
		public Builder metadata(String key, Object value) {
			this.metadata.put(key, value);
			return this;
		}

		/**
		 * Build the immutable judgment context.
		 * @return the immutable judgment context
		 */
		public JudgmentContext build() {
			return new JudgmentContext(goal, workspace, executionTime, startedAt, agentOutput, status, error, metadata);
		}

	}

}
