/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.result;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jspecify.annotations.Nullable;

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
 * construction path including the canonical constructor. The outcome-specific builder stages
 * make incomplete or contradictory construction unavailable in ordinary autocomplete; the
 * constructor independently enforces every cross-field combination.
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
 * <h2>Serialization and portability</h2>
 * <p>
 * Every {@code Judgment} is portable. Absent optionals are omitted rather than emitted as
 * {@code null}, and {@code metadata} accepts only strings, booleans, interoperable
 * integers, finite numbers, arrays, and string-keyed objects, recursively. Anything else —
 * a live exception, a {@link Duration}, an SDK response, an enum constant, an
 * arbitrary-precision number — is refused by the constructor, naming the exact path of the
 * offending value. Accepted containers are copied and recursively frozen, so a caller that
 * keeps mutating its own map, list, or array cannot alter a judgment that was already
 * built.
 * </p>
 * <p>
 * This is a property of the value, not of caller restraint: a consumer never discovers at
 * serialization time that an in-process judgment held a Java object. Diagnostics that
 * genuinely need live objects belong in {@code JudgmentContext} or another non-result
 * surface. Result timing is the worked example — {@value #ELAPSED_MILLIS_KEY} carries an
 * integer and {@link #elapsed()} derives the {@code Duration} view.
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
 * <h2>Declared optionality</h2>
 * <p>
 * This package is {@code @NullMarked}, so every component below is non-null unless it is
 * declared {@link Nullable}. {@code score} and {@code label} are the only two optional
 * members, and they are optional in the Java declaration exactly as they are on the wire: a
 * consumer reading the record by eye, by reflection, or through a schema deriver sees the
 * same contract that {@link JsonInclude} produces. The build enforces these declarations with
 * NullAway rather than leaving them as prose.
 * </p>
 *
 * @param status the judgment status (PASS, FAIL, ABSTAIN, ERROR); never null
 * @param score normalized assessment in [0.0, 1.0], or null when no measurement was made
 * @param label the classification assigned, or null when nothing was classified
 * @param reasoning human-readable explanation of the judgment; never null
 * @param checks individual check results
 * @param metadata additional judgment information (extensibility, timing, evidence);
 * recursively portable and recursively immutable, in encounter order
 * @author Mark Pollack
 * @since 0.1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "status", "score", "label", "reasoning", "checks", "metadata" })
public record Judgment(JudgmentStatus status, @Nullable Double score, @Nullable String label, String reasoning,
		List<Check> checks, Map<String, Object> metadata) {

	/**
	 * Metadata key reserved for aggregation evidence written by voting strategies.
	 * <p>
	 * Callers cannot write this key through the ordinary metadata builder methods; voting
	 * strategies write it through their package-local aggregation helper. This prevents
	 * accidental collision on the ordinary builder path; it is not a provenance or
	 * authenticity guarantee for values received through the public constructor or JSON.
	 * </p>
	 */
	public static final String AGGREGATION_KEY = "aggregation";

	/**
	 * Metadata key carrying elapsed result timing, as a non-negative interoperable integer
	 * count of milliseconds.
	 * <p>
	 * The unit is part of the key so the number is never ambiguous, and absence is the
	 * omission of the key rather than a sentinel. {@link #elapsed()} derives a Java
	 * {@link Duration} from it. A live {@code Duration} belongs in
	 * {@code JudgmentContext} or another non-result surface; it is not a portable result
	 * value and is refused here.
	 * </p>
	 */
	public static final String ELAPSED_MILLIS_KEY = "elapsedMillis";

	/** Validate, copy, and recursively freeze every judgment component. */
	public Judgment {
		Objects.requireNonNull(status, "status must not be null");
		Objects.requireNonNull(reasoning, "reasoning must not be null");
		Objects.requireNonNull(checks, "checks must not be null");
		Objects.requireNonNull(metadata, "metadata must not be null");

		checks = List.copyOf(checks);
		metadata = PortableValues.normalizeMetadata(metadata);

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
	 * The elapsed evaluation time recorded on this judgment, as a Java {@link Duration}.
	 * <p>
	 * This is a derived view over the portable {@value #ELAPSED_MILLIS_KEY} metadata key,
	 * never stored state. The stored value is an integer count of milliseconds so the
	 * judgment stays portable; this accessor is the Java convenience over it.
	 * </p>
	 * @return the elapsed duration, or null when the judgment records no timing
	 */
	public @Nullable Duration elapsed() {
		Object millis = metadata.get(ELAPSED_MILLIS_KEY);
		return millis == null ? null : Duration.ofMillis(((Number) millis).longValue());
	}

	// ==================== Direct conveniences ====================

	/**
	 * Create a passing judgment with reasoning.
	 * @param reasoning the reasoning for passing
	 * @return passing judgment
	 */
	public static Judgment pass(String reasoning) {
		return builder().pass().reasoning(reasoning).build();
	}

	/**
	 * Create a failing judgment with reasoning.
	 * @param reasoning the reasoning for failing
	 * @return failing judgment
	 */
	public static Judgment fail(String reasoning) {
		return builder().fail().reasoning(reasoning).build();
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
		return builder().abstain().reasoning(reasoning).build();
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
		return builder().error().reasoning(reasoning).build();
	}

	/**
	 * Begin a Boolean judgment whose outcome is already represented as a value.
	 * <p>
	 * This is the concise counterpart to selecting {@link OutcomeStage#pass()} or
	 * {@link OutcomeStage#fail()} explicitly. It records only the outcome; no duplicate
	 * {@code 1.0}/{@code 0.0} score is stored.
	 * </p>
	 * @param passed whether the subject satisfied the judge
	 * @return a finding builder with PASS or FAIL selected
	 */
	public static FindingBuilder verdict(boolean passed) {
		return passed ? builder().pass() : builder().fail();
	}

	/**
	 * Begin a quantitative judgment from an already-normalized score.
	 * <p>
	 * The returned stage deliberately has no {@code build()} method: a score does not
	 * determine an outcome until the caller supplies the acceptance threshold through
	 * {@link ScoredJudgment#passingAt(double)}.
	 * </p>
	 * @param normalizedScore score in [0.0, 1.0]
	 * @return a stage requiring an acceptance threshold
	 */
	public static ScoredJudgment scored(double normalizedScore) {
		requireNormalized("score", normalizedScore);
		return new ScoredStage(normalizedScore);
	}

	/**
	 * Begin a quantitative judgment from a raw value on a declared finite scale.
	 * <p>
	 * The raw value is normalized once here and is not retained as a second competing score.
	 * The caller must still state the normalized acceptance threshold through
	 * {@link ScoredJudgment#passingAt(double)}.
	 * </p>
	 * @param value the raw value, within [minimum, maximum]
	 * @param minimum the inclusive minimum of the source scale
	 * @param maximum the inclusive maximum of the source scale; greater than minimum
	 * @return a stage requiring an acceptance threshold
	 */
	public static ScoredJudgment scored(double value, double minimum, double maximum) {
		if (!Double.isFinite(value) || !Double.isFinite(minimum) || !Double.isFinite(maximum)) {
			throw new IllegalArgumentException("value, minimum and maximum must all be finite");
		}
		if (maximum <= minimum) {
			throw new IllegalArgumentException("maximum must be greater than minimum");
		}
		if (value < minimum || value > maximum) {
			throw new IllegalArgumentException("value must be between minimum and maximum, but was " + value);
		}
		double range = maximum - minimum;
		if (!Double.isFinite(range)) {
			throw new IllegalArgumentException("maximum - minimum must be finite");
		}
		double normalizedScore = (value - minimum) / range;
		requireNormalized("normalized score", normalizedScore);
		return new ScoredStage(normalizedScore);
	}

	private static void requireNormalized(String name, double value) {
		if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
			throw new IllegalArgumentException(name + " must be finite and between 0.0 and 1.0, but was " + value);
		}
	}

	// ==================== General construction ====================

	/**
	 * Begin a judgment by selecting its required outcome.
	 * <p>
	 * The selected outcome narrows the methods offered by the next stage. In particular,
	 * ABSTAIN cannot carry a score, ERROR can carry neither a score nor a label, and both
	 * require reasoning before {@code build()} is available.
	 * </p>
	 * @return the outcome-selection stage
	 */
	public static OutcomeStage builder() {
		return new Builder();
	}

	/**
	 * Copy this judgment into a builder for immutable enrichment.
	 * <p>
	 * All six value components are preserved. Subsequent metadata calls accumulate entries
	 * and replace only matching keys.
	 * </p>
	 * @return a builder initialized from this judgment
	 */
	public EnrichmentBuilder toBuilder() {
		Builder builder = new Builder();
		builder.status = this.status;
		builder.score = this.score;
		builder.label = this.label;
		builder.reasoning = this.reasoning;
		builder.checks = new ArrayList<>(this.checks);
		builder.metadata = new LinkedHashMap<>(this.metadata);
		return builder;
	}

	/**
	 * Select the judgment outcome. This stage deliberately has no {@code build()} method.
	 */
	public interface OutcomeStage {

		/**
		 * Select a passing outcome.
		 * @return a builder with PASS selected
		 */
		FindingBuilder pass();

		/**
		 * Select a failing outcome.
		 * @return a builder with FAIL selected
		 */
		FindingBuilder fail();

		/**
		 * Select an abstaining outcome.
		 * @return a stage requiring ABSTAIN reasoning
		 */
		RequiredAbstainReason abstain();

		/**
		 * Select an error outcome.
		 * @return a stage requiring ERROR reasoning
		 */
		RequiredErrorReason error();

	}

	/** A normalized score awaiting the threshold that determines its outcome. */
	public interface ScoredJudgment {

		/**
		 * Derive PASS when the score is greater than or equal to the supplied threshold,
		 * otherwise derive FAIL.
		 * @param normalizedThreshold threshold in [0.0, 1.0]
		 * @return a finding builder carrying both the score and derived outcome
		 */
		FindingBuilder passingAt(double normalizedThreshold);

	}

	private record ScoredStage(double normalizedScore) implements ScoredJudgment {

		@Override
		public FindingBuilder passingAt(double normalizedThreshold) {
			requireNormalized("threshold", normalizedThreshold);
			return verdict(this.normalizedScore >= normalizedThreshold).score(this.normalizedScore);
		}

	}

	/** Common enrichment operations that are legal for every outcome. */
	public interface EnrichmentBuilder {

		/**
		 * Add a check.
		 * @param check check to add
		 * @return this builder
		 */
		EnrichmentBuilder check(Check check);

		/**
		 * Add checks.
		 * @param checks checks to add
		 * @return this builder
		 */
		EnrichmentBuilder checks(Collection<Check> checks);

		/**
		 * Add a portable metadata entry.
		 * @param key metadata key
		 * @param value portable value
		 * @return this builder
		 */
		EnrichmentBuilder metadata(String key, Object value);

		/**
		 * Add metadata entries.
		 * @param metadata metadata entries
		 * @return this builder
		 */
		EnrichmentBuilder metadata(Map<String, Object> metadata);

		/**
		 * Build the immutable judgment.
		 * @return the immutable judgment
		 */
		Judgment build();

	}

	/** Builder for completed PASS and FAIL findings. */
	public interface FindingBuilder extends EnrichmentBuilder {

		/**
		 * Set the explanation.
		 * @param reasoning explanation
		 * @return this builder
		 */
		FindingBuilder reasoning(String reasoning);

		/**
		 * Set the normalized score.
		 * @param score normalized score
		 * @return this builder
		 */
		FindingBuilder score(double score);

		/**
		 * Set the classification label.
		 * @param label classification label
		 * @return this builder
		 */
		FindingBuilder label(String label);

		/** {@inheritDoc} */
		@Override
		FindingBuilder check(Check check);

		/** {@inheritDoc} */
		@Override
		FindingBuilder checks(Collection<Check> checks);

		/** {@inheritDoc} */
		@Override
		FindingBuilder metadata(String key, Object value);

		/** {@inheritDoc} */
		@Override
		FindingBuilder metadata(Map<String, Object> metadata);

	}

	/** Required reasoning step for an ABSTAIN outcome. */
	public interface RequiredAbstainReason {

		/**
		 * Set the required abstention explanation.
		 * @param reasoning explanation
		 * @return an abstention builder
		 */
		AbstainBuilder reasoning(String reasoning);

	}

	/** Builder for a reasoned ABSTAIN outcome. */
	public interface AbstainBuilder extends EnrichmentBuilder {

		/**
		 * Replace the abstention explanation.
		 * @param reasoning explanation
		 * @return this builder
		 */
		AbstainBuilder reasoning(String reasoning);

		/**
		 * Set a completed classification.
		 * @param label completed classification
		 * @return this builder
		 */
		AbstainBuilder label(String label);

		/** {@inheritDoc} */
		@Override
		AbstainBuilder check(Check check);

		/** {@inheritDoc} */
		@Override
		AbstainBuilder checks(Collection<Check> checks);

		/** {@inheritDoc} */
		@Override
		AbstainBuilder metadata(String key, Object value);

		/** {@inheritDoc} */
		@Override
		AbstainBuilder metadata(Map<String, Object> metadata);

	}

	/** Required reasoning step for an ERROR outcome. */
	public interface RequiredErrorReason {

		/**
		 * Set the required error explanation.
		 * @param reasoning explanation
		 * @return an error builder
		 */
		ErrorBuilder reasoning(String reasoning);

	}

	/** Builder for a reasoned ERROR outcome. */
	public interface ErrorBuilder extends EnrichmentBuilder {

		/**
		 * Replace the error explanation.
		 * @param reasoning explanation
		 * @return this builder
		 */
		ErrorBuilder reasoning(String reasoning);

		/** {@inheritDoc} */
		@Override
		ErrorBuilder check(Check check);

		/** {@inheritDoc} */
		@Override
		ErrorBuilder checks(Collection<Check> checks);

		/** {@inheritDoc} */
		@Override
		ErrorBuilder metadata(String key, Object value);

		/** {@inheritDoc} */
		@Override
		ErrorBuilder metadata(Map<String, Object> metadata);

	}

	private static final class Builder implements OutcomeStage, FindingBuilder, RequiredAbstainReason, AbstainBuilder,
			RequiredErrorReason, ErrorBuilder {

		/** Null until an outcome stage is selected; {@link #build()} requires it. */
		private @Nullable JudgmentStatus status;

		private @Nullable Double score;

		private @Nullable String label;

		private String reasoning = "";

		private List<Check> checks = new ArrayList<>();

		private Map<String, Object> metadata = new LinkedHashMap<>();

		@Override
		public Builder pass() {
			this.status = JudgmentStatus.PASS;
			return this;
		}

		@Override
		public Builder fail() {
			this.status = JudgmentStatus.FAIL;
			return this;
		}

		@Override
		public Builder abstain() {
			this.status = JudgmentStatus.ABSTAIN;
			return this;
		}

		@Override
		public Builder error() {
			this.status = JudgmentStatus.ERROR;
			return this;
		}

		/**
		 * Explain the judgment.
		 * @param reasoning human-readable explanation
		 * @return this builder
		 */
		public Builder reasoning(String reasoning) {
			Objects.requireNonNull(reasoning, "reasoning must not be null");
			if ((status == JudgmentStatus.ABSTAIN || status == JudgmentStatus.ERROR) && reasoning.isBlank()) {
				throw new IllegalArgumentException(status + " requires non-blank reasoning");
			}
			this.reasoning = reasoning;
			return this;
		}

		/**
		 * Refine an already-decided outcome with a quantitative assessment.
		 * @param score normalized score in [0.0, 1.0]
		 * @return this builder
		 */
		public Builder score(double score) {
			if (!Double.isFinite(score)) {
				throw new IllegalArgumentException("score must be finite, but was " + score);
			}
			if (score < 0.0 || score > 1.0) {
				throw new IllegalArgumentException("score must be between 0.0 and 1.0, but was " + score);
			}
			this.score = score;
			return this;
		}

		/**
		 * Attach a classification label.
		 * @param label the non-blank classification
		 * @return this builder
		 */
		public Builder label(String label) {
			Objects.requireNonNull(label, "label must not be null");
			if (label.isBlank()) {
				throw new IllegalArgumentException("label must be non-blank when present");
			}
			this.label = label;
			return this;
		}

		/**
		 * Add a single check.
		 * @param check the check
		 * @return this builder
		 */
		public Builder check(Check check) {
			this.checks.add(Objects.requireNonNull(check, "check must not be null"));
			return this;
		}

		/**
		 * Add several checks.
		 * @param checks the checks
		 * @return this builder
		 */
		public Builder checks(Collection<Check> checks) {
			this.checks.addAll(List.copyOf(Objects.requireNonNull(checks, "checks must not be null")));
			return this;
		}

		/**
		 * Add a metadata entry.
		 * @param key the key; must not be the reserved {@value Judgment#AGGREGATION_KEY}
		 * key
		 * @param value the value; must belong to the portable value algebra, which the
		 * constructor enforces
		 * @return this builder
		 */
		public Builder metadata(String key, Object value) {
			Objects.requireNonNull(key, "metadata key must not be null");
			Objects.requireNonNull(value, "metadata value must not be null");
			requireNotReserved(key);
			this.metadata.put(key, value);
			return this;
		}

		/**
		 * Add several metadata entries.
		 * Existing entries are retained; values in this map replace entries with the same
		 * key.
		 * @param metadata the entries; must not contain the reserved
		 * {@value Judgment#AGGREGATION_KEY} key
		 * @return this builder
		 */
		public Builder metadata(Map<String, Object> metadata) {
			Objects.requireNonNull(metadata, "metadata must not be null");
			metadata.keySet().forEach(Builder::requireNotReserved);
			// Copied in encounter order rather than through Map.copyOf, which rehashes.
			// Portability of the values themselves is settled once, at construction.
			this.metadata.putAll(metadata);
			return this;
		}

		private static void requireNotReserved(String key) {
			if (AGGREGATION_KEY.equals(key)) {
				throw new IllegalArgumentException("'" + AGGREGATION_KEY + "' is reserved for aggregation evidence");
			}
		}

		public Judgment build() {
			// The staged interfaces make build() unreachable before an outcome is selected,
			// but that invariant is invisible to a nullness checker reading this class. State
			// it where the value is used rather than trusting the stage types; the message
			// matches the one the compact constructor would otherwise raise.
			return new Judgment(Objects.requireNonNull(status, "status must not be null"), score, label, reasoning,
					checks, metadata);
		}

	}

}
