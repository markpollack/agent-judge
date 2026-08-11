/*
 * Copyright (c) 2026 Mark Pollack
 * See LICENSE.txt in the repository root for license terms.
 */

package io.github.markpollack.judge.jury;

import io.github.markpollack.judge.result.Judgment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Verdict from a jury of judges.
 *
 * <p>
 * Contains the aggregated judgment from voting strategy plus all individual judgments
 * with identity preservation and optional sub-verdicts for meta-jury composition.
 * </p>
 *
 * @param aggregated the final aggregated judgment from voting strategy
 * @param individual all individual judgments from each judge (ordered)
 * @param individualByName judgments indexed by judge name for identity preservation
 * @param weights the weights assigned to each judge (by judge name or index)
 * @param subVerdicts nested verdicts from sub-juries (for MetaJury composition)
 * @author Mark Pollack
 * @since 0.1.0
 */
public record Verdict(Judgment aggregated, List<Judgment> individual, Map<String, Judgment> individualByName,
		Map<String, Double> weights, List<Verdict> subVerdicts) {

	/** Validate and defensively copy all verdict components. */
	public Verdict {
		Objects.requireNonNull(aggregated, "aggregated judgment must not be null");
		// Defensive copy for immutability
		individual = individual != null ? List.copyOf(individual) : List.of();
		individualByName = individualByName != null ? Map.copyOf(individualByName) : Map.of();
		weights = weights != null ? Map.copyOf(weights) : Map.of();
		subVerdicts = subVerdicts != null ? List.copyOf(subVerdicts) : List.of();
	}

	/**
	 * Create a builder for Verdict.
	 * @return new builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Create the complete verdict of a one-member jury without making the caller repeat the
	 * same immutable judgment in its aggregate and evidence roles.
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
			.build();
	}

	/**
	 * Builder for Verdict.
	 */
	public static class Builder {

		/** Create an empty verdict builder. */
		public Builder() {
		}

		private Judgment aggregated;

		private List<Judgment> individual = new ArrayList<>();

		private Map<String, Judgment> individualByName = new HashMap<>();

		private Map<String, Double> weights = new HashMap<>();

		private List<Verdict> subVerdicts = new ArrayList<>();

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
			this.individualByName = new HashMap<>(individualByName);
			return this;
		}

		/**
		 * Set judge weights.
		 * @param weights weights by judge identity
		 * @return this builder
		 */
		public Builder weights(Map<String, Double> weights) {
			this.weights = new HashMap<>(weights);
			return this;
		}

		/**
		 * Set nested verdicts.
		 * @param subVerdicts nested verdicts
		 * @return this builder
		 */
		public Builder subVerdicts(List<Verdict> subVerdicts) {
			this.subVerdicts = new ArrayList<>(subVerdicts);
			return this;
		}

		/**
		 * Build the verdict.
		 * @return immutable verdict
		 */
		public Verdict build() {
			return new Verdict(aggregated, individual, individualByName, weights, subVerdicts);
		}

	}

}
