/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.Judges;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.Judgment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple jury implementation with parallel judge execution.
 *
 * <p>
 * Executes all judges in parallel using CompletableFuture and aggregates their judgments
 * using the configured VotingStrategy. Parallel execution can be disabled for sequential
 * evaluation.
 * </p>
 *
 * <p>
 * <strong>A judge that fails still votes.</strong> If a judge throws, or returns no
 * judgment at all, the jury records an {@link io.github.markpollack.judge.result.JudgmentStatus#ERROR}
 * judgment naming the judge and the cause, and continues. Every configured judge is
 * therefore represented in the returned {@link Verdict}, and the strategy's
 * {@link ErrorPolicy} decides what an error means — which is the whole point of having
 * one. Letting the exception escape instead would discard every other judge's result in
 * the same jury and, inside a {@link CascadedJury}, collapse the entire tier: a jury would
 * silently score with fewer judges than it lists, or report nothing where most judges
 * succeeded. The count that actually voted is recoverable from the
 * {@link AggregationEvidence} block on the aggregate.
 * </p>
 *
 * <p>
 * Example usage with builder:
 * </p>
 * Executable examples are maintained in the Agent Judge Tutorial: https://github.com/markpollack/agent-judge-tutorial.
 *
 * @author Mark Pollack
 * @since 0.1.0
 */
public class SimpleJury implements Jury {

	private static final Logger logger = LoggerFactory.getLogger(SimpleJury.class);

	private final List<Judge> judges;

	private final VotingStrategy votingStrategy;

	private final Map<String, Double> weights;

	private final boolean parallel;

	private final Executor executor;

	private SimpleJury(List<Judge> judges, VotingStrategy votingStrategy, Map<String, Double> weights, boolean parallel,
			Executor executor) {
		if (judges == null || judges.isEmpty()) {
			throw new IllegalArgumentException("Jury must have at least one judge");
		}
		if (votingStrategy == null) {
			throw new IllegalArgumentException("Voting strategy is required");
		}
		this.judges = List.copyOf(judges);
		this.votingStrategy = votingStrategy;
		this.weights = Collections.unmodifiableMap(new LinkedHashMap<>(weights));
		this.parallel = parallel;
		this.executor = executor != null ? executor : ForkJoinPool.commonPool();
	}

	@Override
	public List<Judge> getJudges() {
		return judges;
	}

	@Override
	public VotingStrategy getVotingStrategy() {
		return votingStrategy;
	}

	@Override
	public Verdict vote(JudgmentContext context) {
		List<Judgment> individualJudgments;

		if (parallel) {
			// Parallel execution using CompletableFuture
			List<CompletableFuture<Judgment>> futures = IntStream.range(0, judges.size())
				.mapToObj(index -> CompletableFuture.supplyAsync(() -> invokeJudge(index, context), executor))
				.toList();

			// Wait for all to complete
			CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

			// Collect results
			individualJudgments = allOf.thenApply(v -> futures.stream().map(CompletableFuture::join).toList()).join();
		}
		else {
			// Sequential execution
			individualJudgments = IntStream.range(0, judges.size()).mapToObj(index -> invokeJudge(index, context))
				.toList();
		}

		// Build identity map (preserves order via LinkedHashMap)
		Map<String, Judgment> judgmentByName = new LinkedHashMap<>();
		for (int i = 0; i < judges.size(); i++) {
			String name = getJudgeName(judges.get(i), i);
			judgmentByName.put(name, individualJudgments.get(i));
		}

		// Aggregate using voting strategy
		Judgment aggregated = votingStrategy.aggregate(individualJudgments, weights);

		return Verdict.builder()
			.aggregated(aggregated)
			.individual(individualJudgments)
			.individualByName(judgmentByName)
			.weights(weights)
			.compositeAttempts(List.of())
			.build();
	}

	/**
	 * Invoke one judge, converting any failure into an ERROR judgment.
	 * <p>
	 * The conversion happens here, inside the task, so the parallel and sequential paths
	 * share one definition of failure and no {@code CompletionException} unwrapping is
	 * needed at the join. {@link Error} is deliberately not caught: a
	 * {@code StackOverflowError} or {@code OutOfMemoryError} is not a judgment this jury
	 * can report on.
	 * </p>
	 * @param index the judge's position in the configured list
	 * @param context the judgment context
	 * @return the judge's judgment, or an ERROR judgment naming the judge and the cause
	 */
	private Judgment invokeJudge(int index, JudgmentContext context) {
		Judge judge = judges.get(index);
		String name = getJudgeName(judge, index);
		try {
			Judgment judgment = judge.judge(context);
			if (judgment == null) {
				logger.warn("Judge '{}' returned no judgment; recording an ERROR for the error policy to resolve",
						name);
				return Judgment.error("Judge '" + name + "' returned no judgment");
			}
			return judgment;
		}
		catch (Exception ex) {
			logger.warn("Judge '{}' threw {}; recording an ERROR for the error policy to resolve", name,
					ex.getClass().getName(), ex);
			return Judgment.error("Judge '" + name + "' threw " + ex.getClass().getName() + describeCause(ex));
		}
	}

	private static String describeCause(Exception ex) {
		String message = ex.getMessage();
		return (message == null || message.isBlank()) ? "" : ": " + message;
	}

	/**
	 * Get judge name from metadata or generate default.
	 * @param judge the judge
	 * @param index the judge index
	 * @return judge name
	 */
	private String getJudgeName(Judge judge, int index) {
		return Judges.tryMetadata(judge).map(m -> m.name()).orElse("Judge#" + (index + 1));
	}

	/**
	 * Create a new builder for SimpleJury.
	 * @return builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for SimpleJury.
	 */
	public static class Builder {

		/** Create an empty jury builder. */
		public Builder() {
		}

		private final List<Judge> judges = new ArrayList<>();

		private final Map<String, Double> weights = new LinkedHashMap<>();

		private VotingStrategy votingStrategy;

		private boolean parallel = true;

		private Executor executor;

		/**
		 * Add a judge with equal weight (1.0).
		 * @param judge the judge to add
		 * @return this builder
		 */
		public Builder judge(Judge judge) {
			return judge(judge, 1.0);
		}

		/**
		 * Add a judge with a custom weight.
		 * @param judge the judge to add
		 * @param weight the weight for this judge
		 * @return this builder
		 */
		public Builder judge(Judge judge, double weight) {
			if (judge == null) {
				throw new IllegalArgumentException("Judge cannot be null");
			}
			if (weight < 0) {
				throw new IllegalArgumentException("Weight must be non-negative");
			}
			judges.add(judge);
			weights.put(String.valueOf(judges.size() - 1), weight);
			return this;
		}

		/**
		 * Set the voting strategy.
		 * @param votingStrategy the voting strategy
		 * @return this builder
		 */
		public Builder votingStrategy(VotingStrategy votingStrategy) {
			this.votingStrategy = votingStrategy;
			return this;
		}

		/**
		 * Enable or disable parallel execution.
		 * @param parallel true for parallel execution (default), false for sequential
		 * @return this builder
		 */
		public Builder parallel(boolean parallel) {
			this.parallel = parallel;
			return this;
		}

		/**
		 * Set custom executor for parallel execution.
		 * @param executor the executor to use
		 * @return this builder
		 */
		public Builder executor(Executor executor) {
			this.executor = executor;
			return this;
		}

		/**
		 * Build the SimpleJury instance.
		 * @return configured SimpleJury
		 */
		public SimpleJury build() {
			if (votingStrategy == null) {
				throw new IllegalStateException("Voting strategy is required");
			}
			return new SimpleJury(judges, votingStrategy, weights, parallel, executor);
		}

	}

}
