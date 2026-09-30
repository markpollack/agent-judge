/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.judgment;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import org.jspecify.annotations.Nullable;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A raw numeric finding. Quality normalization is a derived view, never confidence.
 *
 * @param value finite raw value within bounds
 * @param kind measurement or ordinal expectation
 * @param scaleId declared scale or rubric identity
 * @param lower inclusive finite lower bound
 * @param upper inclusive finite upper bound, greater than lower
 * @param levels ordered unique ordinal level IDs; empty for measurements
 * @param qualityDirection semantic quality direction, absent for uninterpreted
 * measurements
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NumericFinding(@JsonProperty(required = true) @JsonSetter(nulls = Nulls.FAIL) double value,
		NumericKind kind, String scaleId, @JsonProperty(required = true) @JsonSetter(nulls = Nulls.FAIL) double lower,
		@JsonProperty(required = true) @JsonSetter(nulls = Nulls.FAIL) double upper, List<String> levels,
		@Nullable QualityDirection qualityDirection) {
	/** Validate and freeze this value. */
	public NumericFinding {
		Objects.requireNonNull(kind, "kind");
		ValueRequirements.text(scaleId, "scaleId");
		levels = List.copyOf(levels);
		if (!Double.isFinite(value) || !Double.isFinite(lower) || !Double.isFinite(upper) || lower >= upper
				|| value < lower || value > upper) {
			throw new IllegalArgumentException("numeric value must lie within finite lower < upper bounds");
		}
		if (kind == NumericKind.MEASUREMENT && !levels.isEmpty()) {
			throw new IllegalArgumentException("measurement has no levels");
		}
		if (kind == NumericKind.ORDINAL_EXPECTATION) {
			levels = ValueRequirements.domain(levels, "levels");
			if (levels.size() < 2 || lower != 0 || upper != levels.size() - 1) {
				throw new IllegalArgumentException(
						"ordinal expectation requires at least two levels and bounds 0..k-1");
			}
		}
	}

	/**
	 * Returns normalized quality, or empty without a declared quality direction.
	 * @return normalized quality, or empty without a declared quality direction
	 */
	public OptionalDouble qualityScore() {
		if (qualityDirection == null) {
			return OptionalDouble.empty();
		}
		double ratio;
		if (value == lower) {
			ratio = 0;
		}
		else if (value == upper) {
			ratio = 1;
		}
		else {
			double range = upper - lower;
			// A finite range also guarantees finite nonnegative numerator. Halving only
			// when subtraction overflows preserves subnormal and adjacent finite ranges.
			ratio = Double.isFinite(range) ? (value - lower) / range
					: (value / 2 - lower / 2) / (upper / 2 - lower / 2);
		}
		return OptionalDouble.of(qualityDirection == QualityDirection.INCREASING ? ratio : 1 - ratio);
	}
}
