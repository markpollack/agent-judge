/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.github.markpollack.judge.description.KeySource;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentReasonCode;
import io.github.markpollack.judge.result.JudgmentStatus;

/**
 * Immutable result from a jury of judges.
 *
 * <p>The first four components describe the root result. {@code seats} records where each
 * judgment sat and under what key, so the ordered list and the keyed map can be joined without
 * guessing. {@code decision} says what produced the aggregate. {@code compositeAttempts}
 * contains the complete ordered evidence for each direct stage entered by a composite jury; a
 * leaf verdict has an empty attempt list.</p>
 *
 * <h2>Reading a composite verdict</h2>
 * <p>A cascade copies its aggregate from the tier that stopped it, so counting the root
 * <em>and</em> the tiers counts that tier twice. {@link #decision()} is what makes the copy
 * visible: follow a {@link DecisionKind#TIER} decision into the attempt it names rather than
 * counting the root as a reduction of its own.</p>
 *
 * @param aggregated the final aggregated judgment
 * @param individual the ordered judgments aggregated at this root
 * @param individualByName those judgments keyed by configured identity in insertion order
 * @param weights the configured weights in insertion order, keyed by configured position
 * @param seats one seat per entry of {@code individual}, joining position to verdict key
 * @param decision what produced {@code aggregated}
 * @param compositeAttempts complete ordered direct composite attempts
 * @author Mark Pollack
 * @since 0.1.0
 */
@JsonPropertyOrder({ "aggregated", "individual", "individualByName", "weights", "seats", "decision",
		"compositeAttempts" })
public record Verdict(Judgment aggregated, List<Judgment> individual, Map<String, Judgment> individualByName,
		Map<String, Double> weights, List<Seat> seats, Decision decision, List<CompositeAttempt> compositeAttempts) {

	/** Validate, bound, and defensively copy all verdict components. */
	public Verdict {
		Objects.requireNonNull(aggregated, "aggregated judgment must not be null");
		individual = individual != null ? List.copyOf(individual) : List.of();
		individualByName = immutableLinkedMap(individualByName);
		weights = immutableLinkedMap(weights);
		Objects.requireNonNull(seats, "seats must not be null");
		seats = List.copyOf(seats);
		Objects.requireNonNull(decision, "decision must not be null");
		Objects.requireNonNull(compositeAttempts, "compositeAttempts must not be null");
		compositeAttempts = List.copyOf(compositeAttempts);
		CompositeExecutionScope.validateTree(compositeAttempts);
		requireCoherentSeats(individual, individualByName, seats);
		requireCoherentDecision(aggregated, individual, individualByName, weights, seats, decision, compositeAttempts);
	}

	/**
	 * Enforce that the seats really do join the ordered list to the keyed map.
	 * <p>
	 * A seat list that does not line up is worse than none: a reader would attribute a judgment
	 * to the wrong judge and have no way to notice.
	 * </p>
	 * @param individual the ordered judgments
	 * @param individualByName the keyed judgments
	 * @param seats the seats
	 */
	private static void requireCoherentSeats(List<Judgment> individual, Map<String, Judgment> individualByName,
			List<Seat> seats) {
		if (seats.size() != individual.size()) {
			throw new IllegalArgumentException("seats must have one entry per individual judgment, but had "
					+ seats.size() + " for " + individual.size());
		}
		int previous = -1;
		Set<String> keys = new LinkedHashSet<>();
		for (Seat seat : seats) {
			if (seat.position() <= previous) {
				throw new IllegalArgumentException("seat positions must be unique and strictly increasing, but "
						+ seat.position() + " followed " + previous);
			}
			previous = seat.position();
			if (!individualByName.containsKey(seat.verdictKey())) {
				throw new IllegalArgumentException("seat " + seat.position() + " is keyed '" + seat.verdictKey()
						+ "', which is not a key of individualByName");
			}
			keys.add(seat.verdictKey());
		}
		// Duplicate keys are legal — two seats can share one map entry when their judges declare
		// the same name — so the map holds the distinct keys, in first-occurrence order.
		if (!keys.equals(individualByName.keySet())) {
			throw new IllegalArgumentException("individualByName holds " + individualByName.keySet()
					+ ", but the seats are keyed " + keys);
		}
	}

	/**
	 * Enforce what each decision kind claims.
	 * <p>
	 * A decision is a claim about where the aggregate came from, and a reader counts on it
	 * without being able to check it. So each claim is checked here, once, against the evidence
	 * the verdict itself carries.
	 * </p>
	 * @param aggregated the aggregate
	 * @param individual the ordered judgments
	 * @param individualByName the keyed judgments
	 * @param weights the configured weights
	 * @param seats the seats
	 * @param decision the decision
	 * @param attempts the direct attempts
	 */
	private static void requireCoherentDecision(Judgment aggregated, List<Judgment> individual,
			Map<String, Judgment> individualByName, Map<String, Double> weights, List<Seat> seats, Decision decision,
			List<CompositeAttempt> attempts) {
		if (decision.kind() == DecisionKind.UNDECIDED) {
			JudgmentReasonCode code = aggregated.reasonCode();
			if (aggregated.status() != JudgmentStatus.ERROR
					|| code == null || code.originFamily() != JudgmentReasonCode.OriginFamily.MACHINERY) {
				throw new IllegalArgumentException("an UNDECIDED verdict reports that the instrument reached no "
						+ "outcome, so its aggregate must be an ERROR with a machinery reason code, but was "
						+ aggregated.status() + " / " + code);
			}
			return;
		}
		if (decision.kind() != DecisionKind.TIER) {
			return;
		}

		String name = Objects.requireNonNull(decision.tier());
		CompositeAttempt attempt = attempts.stream()
			.filter(candidate -> candidate.relation() == CompositeRelation.CASCADE_TIER
					&& candidate.name().equals(name))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("decision names tier '" + name
					+ "', which is not a direct cascade tier of this verdict; tier names are local"));
		Verdict tierVerdict = attempt.verdict();
		if (tierVerdict == null) {
			throw new IllegalArgumentException(
					"decision names tier '" + name + "', which returned no verdict to determine an outcome from");
		}

		if (decision.basis() == DecisionBasis.TIER_OUTCOME) {
			if (attempt.disposition() != AttemptDisposition.USED) {
				throw new IllegalArgumentException("TIER_OUTCOME adopts a tier's own determination, so tier '" + name
						+ "' must be USED, but was " + attempt.disposition());
			}
			if (!aggregated.equals(tierVerdict.aggregated())) {
				throw new IllegalArgumentException(
						"TIER_OUTCOME copies tier '" + name + "' exactly, but the aggregate differs");
			}
		}
		else {
			requireIndividualRejection(aggregated, name, attempt, tierVerdict);
		}
		requireCopiedFrom(name, individual, individualByName, weights, seats, tierVerdict);
	}

	/**
	 * Enforce the preconditions of a stop on an individual rejection.
	 * @param aggregated the root aggregate
	 * @param name the tier's name
	 * @param attempt the tier's attempt
	 * @param tierVerdict the tier's verdict
	 */
	private static void requireIndividualRejection(Judgment aggregated, String name, CompositeAttempt attempt,
			Verdict tierVerdict) {
		DispositionReason reason = attempt.dispositionReason();
		if (attempt.disposition() != AttemptDisposition.STAGE_FAILED || reason == DispositionReason.EXECUTION_FAILED) {
			throw new IllegalArgumentException("INDIVIDUAL_REJECTION means tier '" + name
					+ "' returned a verdict the cascade could not use, so the attempt must be STAGE_FAILED with "
					+ "CHILD_UNDECIDED or UNDECLARED_NOT_APPLICABLE, but was " + attempt.disposition() + " / " + reason);
		}
		if (attempt.policy() != TierPolicy.REJECT_ON_ANY_FAIL) {
			throw new IllegalArgumentException("only REJECT_ON_ANY_FAIL stops on an individual rejection, but tier '"
					+ name + "' uses " + attempt.policy());
		}
		if (tierVerdict.individual().stream().noneMatch(judgment -> judgment.status() == JudgmentStatus.FAIL)) {
			throw new IllegalArgumentException("INDIVIDUAL_REJECTION requires a genuine FAIL among tier '" + name
					+ "' individuals; a broken stage on its own justifies nothing");
		}
		if (reason == DispositionReason.CHILD_UNDECIDED) {
			if (!aggregated.equals(tierVerdict.aggregated())) {
				throw new IllegalArgumentException("a CHILD_UNDECIDED rejection keeps the child's own machinery error "
						+ "as the root aggregate, but tier '" + name + "' differs");
			}
			return;
		}
		// UNDECLARED_NOT_APPLICABLE: the child's aggregate is an exclusion the cascade refused,
		// so the root cannot be a copy of it. The parent authors a machinery error instead, and
		// the child's verdict stays unchanged on its attempt.
		if (aggregated.reasonCode() != JudgmentReasonCode.STAGE_FAILED) {
			throw new IllegalArgumentException("a rejection on a boundary-refused exclusion builds a parent-authored "
					+ "ERROR stage_failed root, but tier '" + name + "' produced " + aggregated.reasonCode());
		}
	}

	/**
	 * Enforce that everything a cascade copies really was copied.
	 * @param name the tier's name
	 * @param individual the root's ordered judgments
	 * @param individualByName the root's keyed judgments
	 * @param weights the root's weights
	 * @param seats the root's seats
	 * @param tierVerdict the tier's verdict
	 */
	private static void requireCopiedFrom(String name, List<Judgment> individual,
			Map<String, Judgment> individualByName, Map<String, Double> weights, List<Seat> seats,
			Verdict tierVerdict) {
		if (!individual.equals(tierVerdict.individual()) || !individualByName.equals(tierVerdict.individualByName())
				|| !weights.equals(tierVerdict.weights()) || !seats.equals(tierVerdict.seats())) {
			throw new IllegalArgumentException("a cascade that stops on tier '" + name
					+ "' copies its individuals, map, weights and seats; they differ here");
		}
	}

	private static <K, V> Map<K, V> immutableLinkedMap(Map<K, V> source) {
		if (source == null || source.isEmpty()) {
			return Map.of();
		}
		return Collections.unmodifiableMap(new LinkedHashMap<>(source));
	}

	/**
	 * Create a builder for Verdict.
	 * @return new builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Create the complete verdict of a one-member jury.
	 * @param name non-blank identity of the sole judge
	 * @param judgment the judge's result and therefore the jury's aggregate
	 * @return a complete one-member verdict
	 */
	public static Verdict single(String name, Judgment judgment) {
		Objects.requireNonNull(name, "name must not be null");
		Objects.requireNonNull(judgment, "judgment must not be null");
		if (name.isBlank()) {
			throw new IllegalArgumentException("name must be non-blank");
		}
		return builder()
			.aggregated(judgment)
			.individual(List.of(judgment))
			.individualByName(Map.of(name, judgment))
			.seats(List.of(new Seat(0, name, KeySource.DECLARED)))
			.decision(Decision.own())
			.build();
	}

	/**
	 * Create the complete verdict of a jury whose judges all declared a distinct name.
	 * <p>
	 * This is the ordinary hand-built case: one judgment per declared name, seated in the order
	 * the map hands them over, aggregated by this jury itself. The ordered
	 * {@link #individual()} list is the map's values in encounter order, seat <em>i</em> keys the
	 * <em>i</em>-th entry as {@link KeySource#DECLARED}, and the decision is
	 * {@link Decision#own()}. It produces exactly what the full builder call produces:
	 * </p>
	 * <pre>{@code
	 * Map<String, Judgment> byName = new LinkedHashMap<>();
	 * byName.put("style", styleResult);
	 * byName.put("coverage", coverageResult);
	 *
	 * Verdict.of(aggregate, byName);
	 *
	 * // the same verdict, written out
	 * Verdict.builder()
	 *     .aggregated(aggregate)
	 *     .individual(List.of(styleResult, coverageResult))
	 *     .individualByName(byName)
	 *     .seats(List.of(new Seat(0, "style", KeySource.DECLARED),
	 *                    new Seat(1, "coverage", KeySource.DECLARED)))
	 *     .decision(Decision.own())
	 *     .build();
	 * }</pre>
	 * <p>
	 * <b>Pass an ordered map.</b> Both the ordered list and the seats are taken from the same
	 * encounter order, so whatever order the map iterates in is the order the judgments are
	 * reported and attributed in. {@link java.util.LinkedHashMap} and
	 * {@link java.util.SequencedMap} keep the order they were populated in;
	 * {@link Map#of(Object, Object) Map.of} does not specify one, so use it only when the
	 * positions genuinely do not matter.
	 * </p>
	 * <p>
	 * <b>What this does not cover.</b> A map key is unique and a map value is one judgment, so
	 * this factory cannot express two seats sharing one key — which is how a duplicate declared
	 * name is recorded — and it claims {@code DECLARED} for every key, which a positional key
	 * such as {@code "Judge#2"} is not. It also seats the entries at 0..n-1 with no gaps, where a
	 * meta-jury that could not use a member leaves one. Those verdicts are built with
	 * {@link #builder()} and explicit seats. For a one-judge jury, {@link #single} says so more
	 * directly.
	 * </p>
	 * @param aggregated the jury's own aggregate of the given judgments
	 * @param individualByName one judgment per declared judge name, in seating order
	 * @return a complete multi-judgment verdict
	 * @throws NullPointerException if either argument, a key, or a judgment is null
	 * @throws IllegalArgumentException if the map is empty or any key is blank
	 * @since 0.17.0
	 */
	public static Verdict of(Judgment aggregated, Map<String, Judgment> individualByName) {
		Objects.requireNonNull(aggregated, "aggregated judgment must not be null");
		Objects.requireNonNull(individualByName, "individualByName must not be null");
		if (individualByName.isEmpty()) {
			throw new IllegalArgumentException("individualByName must hold at least one judgment; "
					+ "a verdict that reduced nothing is built with the builder and an UNDECIDED decision");
		}
		List<Judgment> individual = new ArrayList<>(individualByName.size());
		List<Seat> seats = new ArrayList<>(individualByName.size());
		for (Map.Entry<String, Judgment> entry : individualByName.entrySet()) {
			String name = Objects.requireNonNull(entry.getKey(), "a judge name must not be null");
			if (name.isBlank()) {
				throw new IllegalArgumentException("a judge name must be non-blank");
			}
			individual.add(Objects.requireNonNull(entry.getValue(), "the judgment for '" + name
					+ "' must not be null"));
			seats.add(new Seat(seats.size(), name, KeySource.DECLARED));
		}
		return builder()
			.aggregated(aggregated)
			.individual(individual)
			.individualByName(individualByName)
			.seats(seats)
			.decision(Decision.own())
			.build();
	}

	/** Builder for {@link Verdict}. */
	public static class Builder {

		private Judgment aggregated;

		private List<Judgment> individual = new ArrayList<>();

		private Map<String, Judgment> individualByName = new LinkedHashMap<>();

		private Map<String, Double> weights = new LinkedHashMap<>();

		private List<Seat> seats = new ArrayList<>();

		private Decision decision;

		private List<CompositeAttempt> compositeAttempts = new ArrayList<>();

		/** Create an empty verdict builder. */
		public Builder() {
		}

		/**
		 * Set the aggregated judgment.
		 * @param aggregated aggregated judgment
		 * @return this builder
		 */
		public Builder aggregated(Judgment aggregated) {
			this.aggregated = Objects.requireNonNull(aggregated, "aggregated judgment must not be null");
			return this;
		}

		/**
		 * Set ordered individual judgments.
		 * @param individual individual judgments
		 * @return this builder
		 */
		public Builder individual(List<Judgment> individual) {
			this.individual = new ArrayList<>(individual);
			return this;
		}

		/**
		 * Set named individual judgments.
		 * @param individualByName judgments by name
		 * @return this builder
		 */
		public Builder individualByName(Map<String, Judgment> individualByName) {
			this.individualByName = new LinkedHashMap<>(individualByName);
			return this;
		}

		/**
		 * Set judge weights.
		 * @param weights weights by configured position
		 * @return this builder
		 */
		public Builder weights(Map<String, Double> weights) {
			this.weights = new LinkedHashMap<>(weights);
			return this;
		}

		/**
		 * Set the seats, one per individual judgment.
		 * @param seats the seats, in position order
		 * @return this builder
		 * @since 0.17.0
		 */
		public Builder seats(List<Seat> seats) {
			this.seats = new ArrayList<>(seats);
			return this;
		}

		/**
		 * Set what produced the aggregate.
		 * @param decision the decision
		 * @return this builder
		 * @since 0.17.0
		 */
		public Builder decision(Decision decision) {
			this.decision = Objects.requireNonNull(decision, "decision must not be null");
			return this;
		}

		/**
		 * Set complete ordered direct composite attempts.
		 * @param compositeAttempts composite attempts
		 * @return this builder
		 */
		public Builder compositeAttempts(List<CompositeAttempt> compositeAttempts) {
			this.compositeAttempts = new ArrayList<>(compositeAttempts);
			return this;
		}

		/**
		 * Build the verdict.
		 * <p>
		 * A decision is required. There is no default, because every default would be a claim
		 * about where the aggregate came from that nobody made.
		 * </p>
		 * @return immutable verdict
		 */
		public Verdict build() {
			if (decision == null) {
				throw new IllegalStateException("a verdict must say what produced its aggregate; "
						+ "set a decision (Decision.own() for an ordinary reduction)");
			}
			return new Verdict(aggregated, individual, individualByName, weights, seats, decision, compositeAttempts);
		}

	}

}
