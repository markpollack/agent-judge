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

package io.github.markpollack.judge.result;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Result of a judgment: an outcome, optionally a normalized quantitative assessment, and
 * optionally a classification label.
 *
 * <p>
 * These are three independent facts, not one polymorphic value:
 * </p>
 * <ul>
 * <li>{@code status} — the outcome. Always present.</li>
 * <li>{@code score} — a quantitative assessment already normalized to {@code [0.0, 1.0]}.
 * Absent when the judge made no measurement.</li>
 * <li>{@code label} — the exact classification the judge assigned. Absent when the judge
 * classified nothing.</li>
 * </ul>
 *
 * <p>
 * A Boolean judgment records only its {@code status}: {@link JudgmentStatus#PASS} already
 * carries that fact, and storing {@code 1.0} alongside it would duplicate one fact in two
 * places that could then disagree. Use {@link #effectiveScore()} where a numeric view of a
 * Boolean outcome is wanted.
 * </p>
 *
 * <h2>Invariants</h2>
 * <p>
 * Every rule below is enforced in the compact constructor, so it holds for <em>any</em>
 * construction path including the canonical constructor. The staged builders make invalid
 * combinations hard to express; the constructor is what makes them impossible.
 * </p>
 * <table border="1">
 * <caption>Which facts each status may carry</caption>
 * <tr><th>Status</th><th>score</th><th>label</th></tr>
 * <tr><td>PASS</td><td>allowed</td><td>allowed</td></tr>
 * <tr><td>FAIL</td><td>allowed</td><td>allowed</td></tr>
 * <tr><td>ABSTAIN</td><td>forbidden</td><td>allowed</td></tr>
 * <tr><td>ERROR</td><td>forbidden</td><td>forbidden</td></tr>
 * </table>
 * <p>
 * An abstaining classifier can complete successfully and produce a meaningful category
 * such as {@code not_applicable} while still casting no vote, so a label is legitimate
 * there. {@code ERROR} means the judge never completed, so a label would either impersonate
 * a completed classification or become an undeclared error-code channel. If machine-readable
 * error classification is needed, add an explicit error code rather than overloading
 * {@code label}.
 * </p>
 *
 * <h2>Errors</h2>
 * <p>
 * A {@code Judgment} is a result value, not an exception transport. It carries no
 * {@link Throwable}: machines classify on {@code status == ERROR}, humans read
 * {@code reasoning}, and the original exception is logged where it was caught. Because
 * {@code reasoning} is the only carrier of why a judge could not evaluate, it must be
 * non-blank for {@code ERROR} and {@code ABSTAIN}.
 * </p>
 *
 * <h2>Serialization</h2>
 * <p>
 * {@code status}, {@code score}, {@code label}, {@code reasoning}, and {@code checks} are
 * unconditionally serializable; absent optionals are omitted rather than emitted as
 * {@code null}. A <em>serializable</em> {@code Judgment} additionally requires every
 * {@code metadata} value to be a string, a finite number, a boolean, a list, or a
 * string-keyed map, recursively. Judges that place richer objects in {@code metadata}
 * produce judgments that are valid in-process but not portable.
 * </p>
 *
 * <p>
 * <strong>Design Inspiration:</strong> Combines patterns from multiple frameworks:
 * deepeval's rich metadata (score_breakdown, reason, success), ragas's explainability
 * emphasis, and the "judges" framework's Judgment structure. The checks list allows
 * judges to report multiple sub-assertions (inspired by AssertJ's SoftAssertions
 * pattern), providing transparency into evaluation logic.
 * </p>
 *
 * @param status the judgment status (PASS, FAIL, ABSTAIN, ERROR); never null
 * @param score normalized assessment in [0.0, 1.0], or null when no measurement was made
 * @param label the classification assigned, or null when nothing was classified
 * @param reasoning human-readable explanation of the judgment; never null
 * @param checks individual check results
 * @param metadata additional judgment information (extensibility, timing, evidence)
 * @author Mark Pollack
 * @since 0.1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "status", "score", "label", "reasoning", "checks", "metadata" })
public record Judgment(JudgmentStatus status, Double score, String label, String reasoning, List<Check> checks,
		Map<String, Object> metadata) {

	/**
	 * Metadata key reserved for aggregation evidence written by voting strategies.
	 * <p>
	 * Callers cannot write this key through {@link Builder#metadata(String, Object)};
	 * strategies write it through {@link Builder#aggregationEvidence(Map)}. Reserving it
	 * means a consumer reading {@code metadata.aggregation} can trust what it finds.
	 * </p>
	 */
	public static final String AGGREGATION_KEY = "aggregation";

	public Judgment {
		Objects.requireNonNull(status, "status must not be null");
		Objects.requireNonNull(reasoning, "reasoning must not be null");
		Objects.requireNonNull(checks, "checks must not be null");
		Objects.requireNonNull(metadata, "metadata must not be null");

		checks = List.copyOf(checks);
		metadata = Map.copyOf(metadata);

		if (score != null) {
			if (!Double.isFinite(score)) {
				throw new IllegalArgumentException("score must be finite, but was " + score);
			}
			if (score < 0.0 || score > 1.0) {
				throw new IllegalArgumentException("score must be between 0.0 and 1.0, but was " + score);
			}
			if (status == JudgmentStatus.ABSTAIN || status == JudgmentStatus.ERROR) {
				throw new IllegalArgumentException(
						status + " represents no completed measurement, so it must not carry a score");
			}
		}

		if (label != null) {
			if (label.isBlank()) {
				throw new IllegalArgumentException("label must be non-blank when present");
			}
			if (status == JudgmentStatus.ERROR) {
				throw new IllegalArgumentException("ERROR means the judge did not complete, so it must not carry a label");
			}
		}

		if ((status == JudgmentStatus.ABSTAIN || status == JudgmentStatus.ERROR) && reasoning.isBlank()) {
			throw new IllegalArgumentException(
					status + " requires non-blank reasoning explaining why the judge could not evaluate");
		}
	}

	/**
	 * Check if judgment passed.
	 * @return true if status is PASS
	 */
	public boolean pass() {
		return status == JudgmentStatus.PASS;
	}

	/**
	 * Check whether the judge failed to complete its evaluation.
	 * <p>
	 * Distinct from {@link #pass()} being false: a {@code FAIL} means the judge completed
	 * and rejected the subject, whereas an {@code ERROR} means it never reached a finding.
	 * The explanation is in {@link #reasoning()}; the original exception, if any, was
	 * logged where it was caught.
	 * </p>
	 * @return true if status is ERROR
	 */
	public boolean hasError() {
		return status == JudgmentStatus.ERROR;
	}

	/**
	 * The numeric view of this judgment, for strategies that deliberately treat a Boolean
	 * outcome as a number.
	 * <p>
	 * This is a derived view, never stored state:
	 * </p>
	 * <ul>
	 * <li>an explicit {@code score} if the judge measured one;</li>
	 * <li>otherwise {@code 1.0} for {@code PASS} and {@code 0.0} for {@code FAIL};</li>
	 * <li>otherwise empty — {@code ABSTAIN} and {@code ERROR} made no assessment, and zero
	 * is a real assessment rather than the absence of one.</li>
	 * </ul>
	 * @return the normalized numeric contribution, or empty when there is none
	 */
	public OptionalDouble effectiveScore() {
		if (score != null) {
			return OptionalDouble.of(score);
		}
		return switch (status) {
			case PASS -> OptionalDouble.of(1.0);
			case FAIL -> OptionalDouble.of(0.0);
			case ABSTAIN, ERROR -> OptionalDouble.empty();
		};
	}

	/**
	 * Get elapsed time from metadata.
	 * @return elapsed duration, or null if not present
	 */
	public Duration elapsed() {
		return (Duration) metadata.get("elapsed");
	}

	// ==================== Direct conveniences ====================

	/**
	 * Create a passing judgment with reasoning.
	 * @param reasoning the reasoning for passing
	 * @return passing judgment
	 */
	public static Judgment pass(String reasoning) {
		return passing().because(reasoning).build();
	}

	/**
	 * Create a failing judgment with reasoning.
	 * @param reasoning the reasoning for failing
	 * @return failing judgment
	 */
	public static Judgment fail(String reasoning) {
		return failing().because(reasoning).build();
	}

	/**
	 * Create an abstaining judgment with reasoning.
	 * <p>
	 * Used when a judge does not apply to this subject. It carries no score: abstention
	 * means no assessment was made, which is not the same fact as an assessment of zero.
	 * </p>
	 * @param reasoning why the judge is not applicable; must be non-blank
	 * @return abstaining judgment
	 */
	public static Judgment abstain(String reasoning) {
		return abstaining().because(reasoning).build();
	}

	/**
	 * Create an error judgment with reasoning.
	 * <p>
	 * Log the originating exception at the point it is caught; it is deliberately not
	 * carried here.
	 * </p>
	 * @param reasoning why the judge could not complete; must be non-blank
	 * @return error judgment
	 */
	public static Judgment error(String reasoning) {
		return erroring().because(reasoning).build();
	}

	// ==================== Intent-specific entry points ====================

	/**
	 * Begin a Boolean judgment whose outcome is already decided.
	 * @param passed whether the subject satisfied the judge
	 * @return a builder with the corresponding status set
	 */
	public static Builder verdict(boolean passed) {
		return new Builder(passed ? JudgmentStatus.PASS : JudgmentStatus.FAIL);
	}

	/**
	 * Begin a passing judgment.
	 * <p>
	 * Named {@code passing()} rather than {@code pass()} because the no-argument
	 * {@code pass()} signature is occupied by the instance predicate {@link #pass()}.
	 * </p>
	 * @return a builder with PASS set
	 */
	public static Builder passing() {
		return new Builder(JudgmentStatus.PASS);
	}

	/**
	 * Begin a failing judgment.
	 * @return a builder with FAIL set
	 */
	public static Builder failing() {
		return new Builder(JudgmentStatus.FAIL);
	}

	/**
	 * Begin an abstaining judgment.
	 * @return a builder with ABSTAIN set
	 */
	public static Builder abstaining() {
		return new Builder(JudgmentStatus.ABSTAIN);
	}

	/**
	 * Begin an error judgment.
	 * @return a builder with ERROR set
	 */
	public static Builder erroring() {
		return new Builder(JudgmentStatus.ERROR);
	}

	/**
	 * Begin a judgment whose status is chosen dynamically.
	 * @param status the status; must not be null
	 * @return a builder with that status set
	 */
	public static Builder withStatus(JudgmentStatus status) {
		return new Builder(Objects.requireNonNull(status, "status must not be null"));
	}

	/**
	 * Begin a quantitative judgment from an already-normalized value.
	 * <p>
	 * The returned stage has no {@code build()}: the caller must state whether the outcome
	 * is threshold-derived via {@link ScoredJudgment#passingAt(double)} or independently
	 * decided via {@link ScoredJudgment#withStatus(JudgmentStatus)}. A score without a
	 * stated outcome policy is not representable.
	 * </p>
	 * @param normalized the score, in [0.0, 1.0]
	 * @return a staged builder
	 */
	public static ScoredJudgment scored(double normalized) {
		if (!Double.isFinite(normalized)) {
			throw new IllegalArgumentException("score must be finite, but was " + normalized);
		}
		if (normalized < 0.0 || normalized > 1.0) {
			throw new IllegalArgumentException("score must be between 0.0 and 1.0, but was " + normalized);
		}
		return new ScoredStage(normalized);
	}

	/**
	 * Begin a quantitative judgment from a raw value on a declared scale, normalizing at
	 * construction.
	 * <p>
	 * The raw value and its range are not retained: a judge that needs them for review
	 * should record them in typed output or metadata. Publishing both a raw and a
	 * normalized number on the same object makes the wrong one easy to read by accident.
	 * </p>
	 * @param value the raw value, within [min, max]
	 * @param min the minimum of the scale
	 * @param max the maximum of the scale; must be strictly greater than min
	 * @return a staged builder
	 */
	public static ScoredJudgment scored(double value, double min, double max) {
		if (!Double.isFinite(value) || !Double.isFinite(min) || !Double.isFinite(max)) {
			throw new IllegalArgumentException(
					String.format("value, min and max must all be finite, but were %s, %s and %s", value, min, max));
		}
		if (max <= min) {
			throw new IllegalArgumentException(String.format("max must be greater than min, but were %s and %s", max, min));
		}
		if (value < min || value > max) {
			throw new IllegalArgumentException(
					String.format("value %s must be between %s and %s", value, min, max));
		}
		return new ScoredStage((value - min) / (max - min));
	}

	/**
	 * Begin a categorical judgment.
	 * <p>
	 * The returned stage has no {@code build()}: a label is not implicitly an outcome, so
	 * the caller must state one via {@link ClassifiedJudgment#as(JudgmentStatus)}. Nor is
	 * a label implicitly a number — attach one only under a declared policy, via
	 * {@link Builder#withNormalizedScore(double)}.
	 * </p>
	 * @param label the classification; must be non-blank
	 * @return a staged builder
	 */
	public static ClassifiedJudgment classified(String label) {
		Objects.requireNonNull(label, "label must not be null");
		if (label.isBlank()) {
			throw new IllegalArgumentException("label must be non-blank when present");
		}
		return status -> new Builder(status).label(label);
	}

	/**
	 * Staged builder for a quantitative judgment, requiring the outcome to be stated.
	 */
	public interface ScoredJudgment {

		/**
		 * Derive the outcome by comparing the score against a threshold.
		 * @param normalizedThreshold the acceptance threshold; a score greater than or
		 * equal to it passes
		 * @return a builder with score and derived status set
		 */
		Builder passingAt(double normalizedThreshold);

		/**
		 * State the outcome independently of the score.
		 * @param status must be PASS or FAIL; ABSTAIN and ERROR carry no measurement
		 * @return a builder with score and status set
		 */
		Builder withStatus(JudgmentStatus status);

	}

	/**
	 * Staged builder for a categorical judgment, requiring the outcome to be stated.
	 */
	@FunctionalInterface
	public interface ClassifiedJudgment {

		/**
		 * State the outcome this classification represents.
		 * @param status the outcome
		 * @return a builder with label and status set
		 */
		Builder as(JudgmentStatus status);

	}

	private record ScoredStage(double normalized) implements ScoredJudgment {

		@Override
		public Builder passingAt(double normalizedThreshold) {
			if (!Double.isFinite(normalizedThreshold) || normalizedThreshold < 0.0 || normalizedThreshold > 1.0) {
				throw new IllegalArgumentException(
						"threshold must be finite and between 0.0 and 1.0, but was " + normalizedThreshold);
			}
			return withStatus(normalized >= normalizedThreshold ? JudgmentStatus.PASS : JudgmentStatus.FAIL);
		}

		@Override
		public Builder withStatus(JudgmentStatus status) {
			Objects.requireNonNull(status, "status must not be null");
			if (status != JudgmentStatus.PASS && status != JudgmentStatus.FAIL) {
				throw new IllegalArgumentException(
						status + " represents no completed measurement, so it cannot carry a score");
			}
			return new Builder(status).withNormalizedScore(normalized);
		}

	}

	public static Builder builder() {
		return new Builder(null);
	}

	/**
	 * Builder for {@link Judgment}.
	 * <p>
	 * Obtained from one of the intent-specific entry points, which set the status once,
	 * atomically, at the point where intent is known. There is deliberately no public
	 * {@code status(...)} setter: keeping coupled facts consistent is the value's job, not
	 * every caller's.
	 * </p>
	 */
	public static class Builder {

		private JudgmentStatus status;

		private Double score;

		private String label;

		private String reasoning = "";

		private List<Check> checks = new ArrayList<>();

		private Map<String, Object> metadata = new HashMap<>();

		private Builder(JudgmentStatus status) {
			this.status = status;
		}

		private Builder label(String label) {
			this.label = label;
			return this;
		}

		/**
		 * Explain the judgment.
		 * @param reasoning human-readable explanation
		 * @return this builder
		 */
		public Builder because(String reasoning) {
			this.reasoning = reasoning;
			return this;
		}

		/**
		 * Refine an already-decided outcome with a quantitative assessment.
		 * @param score normalized score in [0.0, 1.0]
		 * @return this builder
		 */
		public Builder withNormalizedScore(double score) {
			this.score = score;
			return this;
		}

		/**
		 * Add a single check.
		 * @param check the check
		 * @return this builder
		 */
		public Builder withCheck(Check check) {
			this.checks.add(check);
			return this;
		}

		/**
		 * Add several checks.
		 * @param checks the checks
		 * @return this builder
		 */
		public Builder withChecks(Collection<Check> checks) {
			this.checks.addAll(checks);
			return this;
		}

		/**
		 * Add a metadata entry.
		 * @param key the key; must not be the reserved {@value Judgment#AGGREGATION_KEY}
		 * key
		 * @param value the value
		 * @return this builder
		 */
		public Builder metadata(String key, Object value) {
			requireNotReserved(key);
			this.metadata.put(key, value);
			return this;
		}

		/**
		 * Add several metadata entries.
		 * @param metadata the entries; must not contain the reserved
		 * {@value Judgment#AGGREGATION_KEY} key
		 * @return this builder
		 */
		public Builder metadata(Map<String, Object> metadata) {
			metadata.keySet().forEach(Builder::requireNotReserved);
			this.metadata.putAll(metadata);
			return this;
		}

		/**
		 * Record aggregation evidence under the reserved
		 * {@value Judgment#AGGREGATION_KEY} key.
		 * <p>
		 * Intended for {@code VotingStrategy} implementations. The map is copied
		 * immutably, since the enclosing {@code Map.copyOf} is only a shallow copy.
		 * </p>
		 * @param evidence the evidence block
		 * @return this builder
		 */
		public Builder aggregationEvidence(Map<String, Object> evidence) {
			this.metadata.put(AGGREGATION_KEY, Map.copyOf(evidence));
			return this;
		}

		private static void requireNotReserved(String key) {
			if (AGGREGATION_KEY.equals(key)) {
				throw new IllegalArgumentException("'" + AGGREGATION_KEY
						+ "' is reserved for aggregation evidence; use aggregationEvidence(Map) instead");
			}
		}

		public Judgment build() {
			return new Judgment(status, score, label, reasoning, checks, metadata);
		}

	}

}
