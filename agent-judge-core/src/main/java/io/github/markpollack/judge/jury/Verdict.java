/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.github.markpollack.judge.result.Judgment;

/**
 * Immutable result from a jury of judges.
 *
 * <p>The first four components describe the root result. {@code compositeAttempts}
 * contains the complete ordered evidence for each direct stage entered by a composite
 * jury. A leaf verdict has an empty attempt list.</p>
 *
 * @param aggregated the final aggregated judgment
 * @param individual the ordered judgments aggregated at this root
 * @param individualByName those judgments keyed by configured identity in insertion order
 * @param weights the configured weights in insertion order
 * @param compositeAttempts complete ordered direct composite attempts
 * @author Mark Pollack
 * @since 0.1.0
 */
@JsonPropertyOrder({ "aggregated", "individual", "individualByName", "weights", "compositeAttempts" })
public record Verdict(Judgment aggregated, List<Judgment> individual, Map<String, Judgment> individualByName,
		Map<String, Double> weights, List<CompositeAttempt> compositeAttempts) {

	/** Validate, bound, and defensively copy all verdict components. */
	public Verdict {
		Objects.requireNonNull(aggregated, "aggregated judgment must not be null");
		individual = individual != null ? List.copyOf(individual) : List.of();
		individualByName = immutableLinkedMap(individualByName);
		weights = immutableLinkedMap(weights);
		Objects.requireNonNull(compositeAttempts, "compositeAttempts must not be null");
		compositeAttempts = List.copyOf(compositeAttempts);
		CompositeExecutionScope.validateTree(compositeAttempts);
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
			.build();
	}

	/** Builder for {@link Verdict}. */
	public static class Builder {

		private Judgment aggregated;

		private List<Judgment> individual = new ArrayList<>();

		private Map<String, Judgment> individualByName = new LinkedHashMap<>();

		private Map<String, Double> weights = new LinkedHashMap<>();

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
		 * @param weights weights by judge identity
		 * @return this builder
		 */
		public Builder weights(Map<String, Double> weights) {
			this.weights = new LinkedHashMap<>(weights);
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
		 * @return immutable verdict
		 */
		public Verdict build() {
			return new Verdict(aggregated, individual, individualByName, weights, compositeAttempts);
		}

	}

}
