/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.JudgeMetadata;
import io.github.markpollack.judge.JudgeWithMetadata;
import io.github.markpollack.judge.Judges;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.description.JuryDescription;
import io.github.markpollack.judge.description.KeySource;
import io.github.markpollack.judge.description.SeatDescription;
import io.github.markpollack.judge.description.SimpleJuryDescription;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentReasonCode;
import io.github.markpollack.judge.result.JudgmentStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * The same holds for a {@link JudgeWithMetadata} whose {@code metadata()} returns
 * {@code null} or throws. The jury cannot tell what the judge is called, so it does not run
 * it: the seat's judgment is an {@code ERROR} naming its position and the metadata failure,
 * stored under the positional key {@code "Judge#" + (position + 1)}.
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

	/** Positions whose verdict key {@link Juries#fromJudges} manufactured to break a name collision. */
	private final Set<Integer> deduplicatedPositions;

	/**
	 * Each seat's declared exclusion capability, read once at construction.
	 * <p>
	 * Read here, not at vote time, for two reasons. The composition check below needs it before
	 * anything is spent, and the guard must honour what was validated: a judge whose metadata
	 * changed between construction and the vote does not get to acquire a capability the jury
	 * was never built with.
	 * </p>
	 */
	private final List<String> declaredCapabilities;

	private SimpleJury(List<Judge> judges, VotingStrategy votingStrategy, Map<String, Double> weights, boolean parallel,
			Executor executor, Set<Integer> deduplicatedPositions, boolean requireDeclaredNames) {
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
		this.deduplicatedPositions = Set.copyOf(deduplicatedPositions);
		this.declaredCapabilities = readCapabilities(this.judges);
		requireCoherentExclusionPolicy(this.judges, this.declaredCapabilities, votingStrategy);
		if (requireDeclaredNames) {
			requireDeclaredNames(this.judges, this.deduplicatedPositions);
		}
	}

	/**
	 * Read every seat's declared exclusion capability.
	 * <p>
	 * A judge whose metadata cannot be read has declared nothing, and is recorded as declaring
	 * nothing rather than failing construction. It never gets to exercise the absent capability
	 * either: the jury cannot tell what it is called, so it does not run it, and the seat is an
	 * {@code ERROR judge_metadata_unreadable}. Describing such a jury still fails loudly, which
	 * is where an unreadable judge is actually reported.
	 * </p>
	 * @param judges the configured judges
	 * @return the declaration per position, with null for a seat that declares none
	 */
	private static List<String> readCapabilities(List<Judge> judges) {
		List<String> capabilities = new ArrayList<>(judges.size());
		for (Judge judge : judges) {
			String declared;
			try {
				declared = Judges.notApplicableCapability(judge).orElse(null);
			}
			catch (IllegalArgumentException ex) {
				declared = null;
			}
			capabilities.add(declared);
		}
		return Collections.unmodifiableList(capabilities);
	}

	/**
	 * Refuse a jury whose strategy would not honour an exclusion one of its seats declares.
	 * <p>
	 * Cheap configuration is validated at build time rather than contained at vote time: the
	 * contradiction is visible in the jury as assembled, and every run of it would waste a
	 * judge's work to reach the same error.
	 * </p>
	 * @param judges the configured judges
	 * @param capabilities each seat's declaration
	 * @param strategy the configured strategy
	 */
	private static void requireCoherentExclusionPolicy(List<Judge> judges, List<String> capabilities,
			VotingStrategy strategy) {
		if (strategy.notApplicablePolicy() != NotApplicablePolicy.REFUSE) {
			return;
		}
		for (int position = 0; position < capabilities.size(); position++) {
			String declared = capabilities.get(position);
			if (declared != null) {
				throw new IllegalArgumentException("seats[" + position + "] declares that it may return NOT_APPLICABLE ("
						+ declared + "), but strategy '" + strategy.getName()
						+ "' refuses exclusions; configure NotApplicablePolicy.EXCLUDE or TREAT_AS_FAIL, "
						+ "or seat a judge that does not exclude");
			}
		}
	}

	/**
	 * Refuse a jury whose seats are not all identified by a name their judges declared.
	 * @param judges the configured judges
	 * @param deduplicated positions whose key was manufactured to break a collision
	 */
	private static void requireDeclaredNames(List<Judge> judges, Set<Integer> deduplicated) {
		Map<String, Integer> byKey = new LinkedHashMap<>();
		for (int position = 0; position < judges.size(); position++) {
			SeatKey key = SeatKey.of(judges.get(position), position);
			if (!key.declared() && !deduplicated.contains(position)) {
				throw new IllegalArgumentException("seats[" + position + "] has no declared name, so its verdict key '"
						+ key.verdictKey() + "' identifies a position rather than a judge; "
						+ "name it, or drop requireDeclaredNames()");
			}
			Integer earlier = byKey.putIfAbsent(key.verdictKey(), position);
			if (earlier != null) {
				throw new IllegalArgumentException("seats[" + position + "] and seats[" + earlier
						+ "] share the verdict key '" + key.verdictKey()
						+ "', so one judgment would overwrite the other in individualByName");
			}
		}
	}

	/**
	 * A capable seat exists and the strategy is configured to honour an exclusion.
	 * <p>
	 * Both halves are needed, and the conjunction is what makes this bound conservative: a
	 * capable seat under a refusing strategy produces an error, never an excluded aggregate.
	 * </p>
	 * @return true when this jury's aggregate may be NOT_APPLICABLE
	 * @since 0.17.0
	 */
	@Override
	public boolean aggregateMayBeNotApplicable() {
		return votingStrategy.notApplicablePolicy() == NotApplicablePolicy.EXCLUDE
				&& declaredCapabilities.stream().anyMatch(declared -> declared != null);
	}

	@Override
	public List<Judge> getJudges() {
		return judges;
	}

	@Override
	public VotingStrategy getVotingStrategy() {
		return votingStrategy;
	}

	/**
	 * Describe this jury's strategy and seats, before any vote.
	 * <p>
	 * Each seat pairs a zero-based position, which is the index of
	 * {@link Verdict#individual()} and the key of {@link Verdict#weights()}, with the verdict
	 * key its judgment is stored under in {@link Verdict#individualByName()} and the weight it
	 * votes with. The key is {@link KeySource#DECLARED} when the judge declares a name,
	 * {@link KeySource#DEDUPLICATED} when {@link Juries#fromJudges} suffixed a colliding name,
	 * and {@link KeySource#POSITIONAL} when the judge declares no name and the key is
	 * {@code "Judge#" + (position + 1)}.
	 * </p>
	 * @return a simple jury description
	 * @throws IllegalArgumentException if a seat cannot be described, for example because its
	 * judge declares a non-portable configuration or its metadata cannot be read; the message
	 * names the seat
	 * @since 0.17.0
	 */
	@Override
	public JuryDescription describe() {
		List<SeatDescription> seats = new ArrayList<>(judges.size());
		for (int position = 0; position < judges.size(); position++) {
			Judge judge = judges.get(position);
			SeatKey key = SeatKey.of(judge, position);
			KeySource keySource;
			if (deduplicatedPositions.contains(position)) {
				keySource = KeySource.DEDUPLICATED;
			}
			else if (key.declared()) {
				keySource = KeySource.DECLARED;
			}
			else {
				keySource = KeySource.POSITIONAL;
			}
			double weight = weights.getOrDefault(String.valueOf(position), 1.0);
			try {
				// Judges.describe refuses metadata it cannot read, rather than describing the
				// judge as undeclared.
				SeatDescription seat = new SeatDescription(position, key.verdictKey(), keySource, weight,
						Judges.describe(judge));
				requireStableCapability(position, seat.judge().notApplicableWhen());
				seats.add(seat);
			}
			catch (IllegalArgumentException ex) {
				throw new IllegalArgumentException(
						"seats[" + position + "] ('" + key.verdictKey() + "'): " + ex.getMessage(), ex);
			}
		}
		return new SimpleJuryDescription(votingStrategy.describe(), seats);
	}

	/**
	 * Refuse to describe a seat whose declaration has changed since the jury was built.
	 * <p>
	 * The description and the guard must agree, or the description is a claim about a jury that
	 * does not exist. A judge whose {@code metadata()} answers differently on each call would
	 * otherwise be described as capable while being guarded as incapable, or the reverse.
	 * </p>
	 * @param position the seat's position
	 * @param described what the judge declares now
	 */
	private void requireStableCapability(int position, String described) {
		String built = declaredCapabilities.get(position);
		if (!java.util.Objects.equals(built, described)) {
			throw new IllegalArgumentException("its exclusion capability changed after the jury was built: "
					+ "it declared " + describeCapability(built) + " at construction and "
					+ describeCapability(described) + " now");
		}
	}

	private static String describeCapability(String declared) {
		return declared == null ? "none" : "'" + declared + "'";
	}

	@Override
	public Verdict vote(JudgmentContext context) {
		// Read every seat's key once, on the caller's thread and before any judge runs, so a
		// judge whose metadata cannot be read becomes an ERROR seat instead of an exception.
		List<SeatKey> keys = IntStream.range(0, judges.size())
			.mapToObj(index -> SeatKey.of(judges.get(index), index))
			.toList();

		List<Judgment> individualJudgments;

		if (parallel) {
			// Parallel execution using CompletableFuture
			List<CompletableFuture<Judgment>> futures = IntStream.range(0, judges.size())
				.mapToObj(index -> CompletableFuture.supplyAsync(() -> invokeJudge(index, keys.get(index), context),
						executor))
				.toList();

			// Wait for all to complete
			CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

			// Collect results
			individualJudgments = allOf.thenApply(v -> futures.stream().map(CompletableFuture::join).toList()).join();
		}
		else {
			// Sequential execution
			individualJudgments = IntStream.range(0, judges.size())
				.mapToObj(index -> invokeJudge(index, keys.get(index), context))
				.toList();
		}

		// Build identity map (preserves order via LinkedHashMap) and the seats that join it to
		// the ordered list.
		Map<String, Judgment> judgmentByName = new LinkedHashMap<>();
		List<Seat> seats = new ArrayList<>(judges.size());
		for (int i = 0; i < judges.size(); i++) {
			judgmentByName.put(keys.get(i).verdictKey(), individualJudgments.get(i));
			seats.add(new Seat(i, keys.get(i).verdictKey(), keySourceAt(i, keys.get(i))));
		}

		Judgment aggregated = aggregateWithinBoundary(individualJudgments);

		return Verdict.builder()
			.aggregated(aggregated)
			.individual(individualJudgments)
			.individualByName(judgmentByName)
			.weights(weights)
			.seats(seats)
			.decision(AggregationBoundary.decisionFor(aggregated))
			.compositeAttempts(List.of())
			.build();
	}

	private KeySource keySourceAt(int position, SeatKey key) {
		if (deduplicatedPositions.contains(position)) {
			return KeySource.DEDUPLICATED;
		}
		return key.declared() ? KeySource.DECLARED : KeySource.POSITIONAL;
	}

	/**
	 * Call the strategy inside the shared boundary, so a broken reduction becomes a contained,
	 * countable error instead of an exception that discards every judge that succeeded.
	 * @param individualJudgments the judgments to reduce
	 * @return the strategy's aggregate, or the contained error that replaces it
	 */
	private Judgment aggregateWithinBoundary(List<Judgment> individualJudgments) {
		return AggregationBoundary.aggregate(votingStrategy, individualJudgments, weights,
				aggregateMayBeNotApplicable(), logger);
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
	 * @param key the seat's key, read before any judge ran
	 * @param context the judgment context
	 * @return the judge's judgment, or an ERROR judgment naming the judge and the cause
	 */
	private Judgment invokeJudge(int index, SeatKey key, JudgmentContext context) {
		if (key.metadataFailure() != null) {
			String reasoning = key.unreadableMetadata();
			logger.warn("{}; recording an ERROR for the error policy to resolve", reasoning, key.cause());
			return Judgment.error(JudgmentReasonCode.JUDGE_METADATA_UNREADABLE, reasoning);
		}
		Judge judge = judges.get(index);
		String name = key.verdictKey();
		try {
			Judgment judgment = guardExclusion(index, name, judge.judge(context));
			if (judgment == null) {
				logger.warn("Judge '{}' returned no judgment; recording an ERROR for the error policy to resolve",
						name);
				return Judgment.error(JudgmentReasonCode.JUDGE_FAILED, "Judge '" + name + "' returned no judgment");
			}
			return judgment;
		}
		catch (Exception ex) {
			logger.warn("Judge '{}' threw {}; recording an ERROR for the error policy to resolve", name,
					ex.getClass().getName(), ex);
			return Judgment.error(JudgmentReasonCode.JUDGE_FAILED,
					"Judge '" + name + "' threw " + ex.getClass().getName() + describeCause(ex));
		}
	}

	/**
	 * Convert an exclusion from a seat that never declared one into an error.
	 * <p>
	 * Exclusion is the only outcome that removes a judge from its own denominator, so it is the
	 * one a judge could use to dodge a criterion it does not like the look of. A seat that
	 * declared the capability in advance is honoured; a seat that did not gets an
	 * {@code ERROR undeclared_not_applicable}, which is a judge-origin error and therefore the
	 * configured {@link ErrorPolicy}'s to resolve, exactly like any other judge failure.
	 * </p>
	 * @param index the seat's position
	 * @param name the seat's verdict key
	 * @param judgment the judge's result, possibly null
	 * @return the judgment, or the error that replaces an undeclared exclusion
	 */
	private Judgment guardExclusion(int index, String name, Judgment judgment) {
		if (judgment == null || judgment.status() != JudgmentStatus.NOT_APPLICABLE
				|| declaredCapabilities.get(index) != null) {
			return judgment;
		}
		String reasoning = "Judge '" + name + "' returned NOT_APPLICABLE without declaring that it may exclude a "
				+ "subject, so the exclusion is not honoured: " + judgment.reasoning();
		logger.warn("{}; recording an ERROR for the error policy to resolve", reasoning);
		return Judgment.error(JudgmentReasonCode.UNDECLARED_NOT_APPLICABLE, reasoning);
	}

	private static String describeCause(Exception ex) {
		String message = ex.getMessage();
		return (message == null || message.isBlank()) ? "" : ": " + message;
	}

	/**
	 * The key a seat's judgment is stored under in {@link Verdict#individualByName()}, read
	 * from its judge's metadata without letting a failure to read it escape.
	 *
	 * @param position the seat's zero-based position
	 * @param verdictKey the declared name, or {@code "Judge#" + (position + 1)} when the judge
	 * declares none or its metadata cannot be read
	 * @param declared whether the judge declared the name
	 * @param metadataFailure why the metadata could not be read, or {@code null} when it could
	 * @param cause the exception {@code metadata()} threw, or {@code null}
	 */
	record SeatKey(int position, String verdictKey, boolean declared, String metadataFailure, Exception cause) {

		static SeatKey of(Judge judge, int position) {
			String positional = "Judge#" + (position + 1);
			if (!(judge instanceof JudgeWithMetadata withMetadata)) {
				return new SeatKey(position, positional, false, null, null);
			}
			JudgeMetadata metadata;
			try {
				metadata = withMetadata.metadata();
			}
			catch (Exception ex) {
				return new SeatKey(position, positional, false,
						"metadata() threw " + ex.getClass().getName() + describeCause(ex), ex);
			}
			if (metadata == null) {
				return new SeatKey(position, positional, false, "metadata() returned null", null);
			}
			if (metadata.name() == null) {
				return new SeatKey(position, positional, false, null, null);
			}
			return new SeatKey(position, metadata.name(), true, null, null);
		}

		/**
		 * State the metadata failure, naming the seat.
		 * @return a sentence naming the position, the positional key and the failure
		 */
		String unreadableMetadata() {
			return "Judge at position " + position + " ('" + verdictKey + "') has unreadable metadata: "
					+ metadataFailure;
		}

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

		private final Set<Integer> deduplicated = new HashSet<>();

		private boolean requireDeclaredNames;

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
		 * @param weight the weight for this judge; finite and not negative
		 * @return this builder
		 * @throws IllegalArgumentException if the judge is null, or the weight is not finite or
		 * is negative
		 */
		public Builder judge(Judge judge, double weight) {
			if (judge == null) {
				throw new IllegalArgumentException("Judge cannot be null");
			}
			// Checked before the sign: NaN < 0 is false, so a sign check alone accepts NaN.
			if (!Double.isFinite(weight)) {
				throw new IllegalArgumentException("Weight must be finite, but was " + weight);
			}
			if (weight < 0) {
				throw new IllegalArgumentException("Weight must be non-negative");
			}
			judges.add(judge);
			weights.put(String.valueOf(judges.size() - 1), weight);
			return this;
		}

		/**
		 * Add a judge, with equal weight, whose name was suffixed to break a collision, so its
		 * seat is described as {@link KeySource#DEDUPLICATED}.
		 * @param judge the renamed judge
		 * @return this builder
		 */
		Builder deduplicatedJudge(Judge judge) {
			judge(judge);
			deduplicated.add(judges.size() - 1);
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
		 * Require every seat to be identified by a name its judge declared, and require those
		 * names to be unique.
		 * <p>
		 * Opt-in, because it is a real constraint and existing juries seat unnamed lambdas
		 * freely. Turn it on where the verdict is going to be <em>stored</em> and read later:
		 * without it a seat can be keyed {@code "Judge#2"}, which identifies a position rather
		 * than a judge and silently means something else the moment a judge is inserted above
		 * it — and a judge that declares the name {@code "Judge#2"} collides with exactly that
		 * key, so one judgment overwrites the other in {@code individualByName}.
		 * </p>
		 * <p>
		 * {@link #build()} then rejects a positional seat, a duplicate declared name, and a
		 * declared name that collides with a positional key.
		 * </p>
		 * @return this builder
		 * @since 0.17.0
		 */
		public Builder requireDeclaredNames() {
			this.requireDeclaredNames = true;
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
			return new SimpleJury(judges, votingStrategy, weights, parallel, executor, deduplicated,
					requireDeclaredNames);
		}

	}

}
