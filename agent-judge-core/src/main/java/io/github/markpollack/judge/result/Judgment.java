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
 * <tr><th>Status</th><th>score</th><th>label</th><th>reasoning</th><th>reasonCode</th></tr>
 * <tr><td>PASS</td><td>allowed</td><td>allowed</td><td>may be blank</td><td>forbidden</td></tr>
 * <tr><td>FAIL</td><td>allowed</td><td>allowed</td><td>may be blank; required when coded</td>
 * <td>optional, subject family</td></tr>
 * <tr><td>ABSTAIN</td><td>forbidden</td><td>allowed</td><td>required</td><td>forbidden</td></tr>
 * <tr><td>NOT_APPLICABLE</td><td>forbidden</td><td>allowed</td><td>required</td><td>forbidden</td></tr>
 * <tr><td>ERROR</td><td>forbidden</td><td>forbidden</td><td>required</td>
 * <td>required, instrument family</td></tr>
 * </table>
 * <p>
 * An abstaining or excluded classifier can complete successfully and produce a meaningful
 * category — which of several reasons it could not decide, or which characteristic of the
 * subject put the criterion out of scope — while still casting no vote, so a label is
 * legitimate there. {@code ERROR} means the judge never completed, so a label would either
 * impersonate a completed classification or become an undeclared error-code channel; the
 * machine-readable classification of an error is {@link #reasonCode()}, which is a closed
 * vocabulary rather than free text.
 * </p>
 *
 * <h2>Errors</h2>
 * <p>
 * A {@code Judgment} is a result value, not an exception transport. It carries no
 * {@link Throwable}: machines classify on {@link #reasonCode()}, humans read
 * {@code reasoning}, and the original exception is logged where it was caught. Because
 * {@code reasoning} is the only carrier of what did not happen, it must be non-blank for
 * {@code ERROR}, {@code ABSTAIN} and {@code NOT_APPLICABLE}, and wherever a
 * {@code reasonCode} is present.
 * </p>
 * <p>
 * One countable code plus mandatory free text: the code is what a reader counts, the
 * reasoning is what a human reads, and neither substitutes for the other. An instrument code
 * is required on every {@code ERROR}, because an instrument failure nobody can count is a
 * failure nobody fixes; a subject code is optional on a {@code FAIL}, because most rejections
 * are explained in prose and inventing a category for them would manufacture a taxonomy
 * nobody asked for.
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
 * declared {@link Nullable}. {@code score}, {@code label} and {@code reasonCode} are the only
 * optional members, and they are optional in the Java declaration exactly as they are on the
 * wire: a consumer reading the record by eye, by reflection, or through a schema deriver sees
 * the same contract that {@link JsonInclude} produces. The build enforces these declarations
 * with NullAway rather than leaving them as prose.
 * </p>
 *
 * @param status the judgment status (PASS, FAIL, ABSTAIN, NOT_APPLICABLE, ERROR); never null
 * @param score normalized assessment in [0.0, 1.0], or null when no measurement was made
 * @param label the classification assigned, or null when nothing was classified
 * @param reasonCode the countable cause: required and instrument-family on ERROR, optional and
 * subject-family on FAIL, forbidden elsewhere; null when a FAIL is an uncoded rejection
 * @param reasoning human-readable explanation of the judgment; never null
 * @param checks individual check results
 * @param metadata additional judgment information (extensibility, timing, evidence);
 * recursively portable and recursively immutable, in encounter order
 * @author Mark Pollack
 * @since 0.1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "status", "score", "label", "reasonCode", "reasoning", "checks", "metadata" })
public record Judgment(JudgmentStatus status, @Nullable Double score, @Nullable String label,
		@Nullable JudgmentReasonCode reasonCode, String reasoning, List<Check> checks, Map<String, Object> metadata) {

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

	/**
	 * Aggregation-evidence key carrying the terminal reason codes an
	 * {@link JudgmentReasonCode#ERRORS_PROPAGATED} aggregate propagated, as a non-empty map of
	 * wire name to positive count.
	 * <p>
	 * The key is declared here because the invariant is enforced here: a judgment whose
	 * {@code reasonCode} is {@code errors_propagated} must carry a structurally valid block
	 * under it, inside the reserved {@value #AGGREGATION_KEY} block. Strategies write it through
	 * the jury package's evidence contract, which re-exports this same constant.
	 * </p>
	 *
	 * @since 0.17.0
	 */
	public static final String ERROR_CODE_COUNTS_KEY = "errorCodeCounts";

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
			if (status == JudgmentStatus.ABSTAIN || status == JudgmentStatus.NOT_APPLICABLE
					|| status == JudgmentStatus.ERROR) {
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

		if (reasoning.isBlank() && requiresReasoning(status)) {
			throw new IllegalArgumentException(status + " requires non-blank reasoning explaining "
					+ (status == JudgmentStatus.NOT_APPLICABLE ? "what the subject lacks that the criterion is about"
							: "why the judge could not evaluate"));
		}

		requireReasonCodeFamily(status, reasonCode);
		if (reasonCode != null && reasoning.isBlank()) {
			throw new IllegalArgumentException(
					"a reasonCode is one countable cause plus free text, so " + reasonCode.wireName()
							+ " requires non-blank reasoning");
		}
		if (reasonCode == JudgmentReasonCode.ERRORS_PROPAGATED) {
			requireOrigin(metadata);
		}
	}

	/**
	 * Whether a status is meaningless without an explanation.
	 * <p>
	 * Each of these three records that something did <em>not</em> happen, and the only carrier
	 * of what that was is the prose.
	 * </p>
	 * @param status the judgment status
	 * @return true when reasoning must be non-blank
	 */
	private static boolean requiresReasoning(JudgmentStatus status) {
		return status == JudgmentStatus.ABSTAIN || status == JudgmentStatus.NOT_APPLICABLE
				|| status == JudgmentStatus.ERROR;
	}

	/**
	 * Enforce the one status each code family may appear on.
	 * @param status the judgment status
	 * @param reasonCode the declared code, or null
	 */
	private static void requireReasonCodeFamily(JudgmentStatus status, @Nullable JudgmentReasonCode reasonCode) {
		if (status == JudgmentStatus.ERROR) {
			if (reasonCode == null) {
				throw new IllegalArgumentException(
						"ERROR requires a reasonCode: an instrument failure nobody can count is a failure nobody fixes");
			}
			if (reasonCode.family() != JudgmentReasonCode.Family.INSTRUMENT) {
				throw new IllegalArgumentException("ERROR requires an instrument reasonCode, but " + reasonCode.wireName()
						+ " describes the subject");
			}
			return;
		}
		if (reasonCode == null) {
			return;
		}
		if (status == JudgmentStatus.FAIL) {
			if (reasonCode.family() != JudgmentReasonCode.Family.SUBJECT) {
				throw new IllegalArgumentException("FAIL may only carry a subject reasonCode, but " + reasonCode.wireName()
						+ " says the instrument failed; report that as an ERROR instead");
			}
			return;
		}
		throw new IllegalArgumentException(
				status + " is not a rejection and not an instrument failure, so it must not carry a reasonCode");
	}

	/**
	 * Enforce the origin a propagating aggregate owes.
	 * <p>
	 * Validation reads the already-frozen metadata, so what is checked is exactly what the
	 * judgment will hold. It validates structure, not authenticity: it proves the aggregate
	 * names terminal causes with positive counts, not that those causes really occurred.
	 * </p>
	 * @param metadata the frozen metadata
	 */
	private static void requireOrigin(Map<String, Object> metadata) {
		String required = "errors_propagated requires a non-empty '" + ERROR_CODE_COUNTS_KEY + "' in its '"
				+ AGGREGATION_KEY + "' evidence, naming the terminal causes it propagated";
		Object aggregation = metadata.get(AGGREGATION_KEY);
		if (!(aggregation instanceof Map<?, ?> evidence)) {
			throw new IllegalArgumentException(required);
		}
		Object counts = evidence.get(ERROR_CODE_COUNTS_KEY);
		if (!(counts instanceof Map<?, ?> origin) || origin.isEmpty()) {
			throw new IllegalArgumentException(required);
		}
		for (Map.Entry<?, ?> entry : origin.entrySet()) {
			JudgmentReasonCode code = JudgmentReasonCode.fromWire(String.valueOf(entry.getKey()));
			if (code.family() != JudgmentReasonCode.Family.INSTRUMENT || !code.terminal()) {
				throw new IllegalArgumentException("'" + ERROR_CODE_COUNTS_KEY
						+ "' holds terminal instrument causes only, but was given " + code.wireName());
			}
			Object count = entry.getValue();
			boolean positiveInteger = (count instanceof Integer || count instanceof Long)
					&& ((Number) count).longValue() > 0L;
			if (!positiveInteger) {
				throw new IllegalArgumentException("'" + ERROR_CODE_COUNTS_KEY + "' counts must be positive integers, but "
						+ code.wireName() + " was given " + count);
			}
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
	 * Check whether the criterion did not apply to this subject.
	 * <p>
	 * Distinct from {@link #pass()} being false and from {@link #hasError()}: the instrument
	 * worked and the question was simply the wrong one to ask here. A reader excludes this
	 * judgment from its denominator and counts it separately, rather than scoring it.
	 * </p>
	 * @return true if status is NOT_APPLICABLE
	 * @since 0.17.0
	 */
	public boolean notApplicable() {
		return status == JudgmentStatus.NOT_APPLICABLE;
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
			case ABSTAIN, NOT_APPLICABLE, ERROR -> OptionalDouble.empty();
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
	 * Used when the criterion applies and this is the right instrument, but the judge could
	 * not decide. It carries no score: abstention means no assessment was made, which is not
	 * the same fact as an assessment of zero.
	 * </p>
	 * <p>
	 * A criterion that does not apply to this subject at all is
	 * {@link #notApplicable(String)}, which is excluded from a denominator rather than left
	 * undecided within it.
	 * </p>
	 * @param reasoning why the judge could not decide; must be non-blank
	 * @return abstaining judgment
	 */
	public static Judgment abstain(String reasoning) {
		return builder().abstain().reasoning(reasoning).build();
	}

	/**
	 * Create a judgment that the criterion does not apply to this subject.
	 * <p>
	 * Only where the subject by definition lacks what the criterion is about. Evidence that is
	 * merely missing is {@link #abstain(String)}. The reasoning is required and is what makes
	 * the exclusion auditable: it must say what the subject lacks.
	 * </p>
	 * <p>
	 * A jury honours this only from a seat that declared in advance that it can exclude; an
	 * undeclared exclusion is contained as an error, so a judge cannot dodge a criterion after
	 * seeing it.
	 * </p>
	 * @param reasoning what the subject lacks that the criterion is about; must be non-blank
	 * @return a not-applicable judgment
	 * @since 0.17.0
	 */
	public static Judgment notApplicable(String reasoning) {
		return builder().notApplicable().reasoning(reasoning).build();
	}

	/**
	 * Create an error judgment reported by the judge itself.
	 * <p>
	 * Coded {@link JudgmentReasonCode#JUDGE_REPORTED}: the judge is the source, and the
	 * reasoning says what happened. Infrastructure that knows a more specific shape uses
	 * {@link #error(JudgmentReasonCode, String)} instead.
	 * </p>
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
	 * Create an error judgment with an explicit countable cause.
	 * @param reasonCode the instrument code naming the cause; not
	 * {@link JudgmentReasonCode#ERRORS_PROPAGATED}, which requires an origin and is built by
	 * {@link #propagatedError}
	 * @param reasoning why no finding was reached; must be non-blank
	 * @return error judgment
	 * @since 0.17.0
	 */
	public static Judgment error(JudgmentReasonCode reasonCode, String reasoning) {
		return builder().error(reasonCode).reasoning(reasoning).build();
	}

	/**
	 * Create the {@link JudgmentReasonCode#ERRORS_PROPAGATED} aggregate for a set of errored
	 * inputs, atomically with the origin that makes it countable.
	 * <p>
	 * A propagating aggregate is not itself a cause; it stands for the causes it propagated.
	 * Those live in its aggregation evidence under {@value #ERROR_CODE_COUNTS_KEY}, and the
	 * constructor refuses the code without them, so there is no moment at which a propagated
	 * error exists with nothing to attribute it to.
	 * </p>
	 * <p>
	 * The map is flattened: a propagated input contributes the origins <em>it</em> propagated,
	 * never {@code errors_propagated} itself. Counts may therefore exceed the number of errored
	 * inputs.
	 * </p>
	 * @param origin terminal codes to positive counts; must be non-empty and must not contain
	 * {@link JudgmentReasonCode#ERRORS_PROPAGATED}
	 * @param reasoning why the aggregate is an error; must be non-blank
	 * @return the propagating error judgment, carrying its origin
	 * @throws IllegalArgumentException if the origin is empty, holds a non-terminal key, or
	 * holds a count that is not positive
	 * @since 0.17.0
	 */
	public static Judgment propagatedError(Map<JudgmentReasonCode, Integer> origin, String reasoning) {
		Objects.requireNonNull(origin, "origin must not be null");
		Map<String, Object> counts = new LinkedHashMap<>();
		origin.forEach((code, count) -> counts.put(
				Objects.requireNonNull(code, "origin must not contain a null code").wireName(),
				Objects.requireNonNull(count, "origin must not contain a null count")));
		Map<String, Object> evidence = new LinkedHashMap<>();
		evidence.put(ERROR_CODE_COUNTS_KEY, counts);
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put(AGGREGATION_KEY, evidence);
		return new Judgment(JudgmentStatus.ERROR, null, null, JudgmentReasonCode.ERRORS_PROPAGATED, reasoning, List.of(),
				metadata);
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
		builder.reasonCode = this.reasonCode;
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
		 * Select a not-applicable outcome.
		 * @return a stage requiring NOT_APPLICABLE reasoning
		 * @since 0.17.0
		 */
		RequiredNotApplicableReason notApplicable();

		/**
		 * Select an error outcome, coded {@link JudgmentReasonCode#JUDGE_REPORTED}.
		 * <p>
		 * The default is the judge's own report of its failure. Infrastructure that knows a more
		 * specific shape states it through {@link #error(JudgmentReasonCode)}.
		 * </p>
		 * @return a stage requiring ERROR reasoning
		 */
		RequiredErrorReason error();

		/**
		 * Select an error outcome with an explicit countable cause.
		 * @param reasonCode the instrument code naming the cause
		 * @return a stage requiring ERROR reasoning
		 * @since 0.17.0
		 */
		RequiredErrorReason error(JudgmentReasonCode reasonCode);

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

		/**
		 * Record the countable subject cause of a rejection.
		 * <p>
		 * Legal on {@code FAIL} only, and optional there: a {@code FAIL} with no code is an
		 * uncoded rejection, explained by its reasoning alone.
		 * </p>
		 * @param reasonCode a {@link JudgmentReasonCode.Family#SUBJECT} code
		 * @return this builder
		 * @since 0.17.0
		 */
		FindingBuilder reasonCode(JudgmentReasonCode reasonCode);

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

	/**
	 * Required reasoning step for a NOT_APPLICABLE outcome.
	 *
	 * @since 0.17.0
	 */
	public interface RequiredNotApplicableReason {

		/**
		 * Set the required exclusion explanation.
		 * @param reasoning what the subject lacks that the criterion is about
		 * @return a not-applicable builder
		 */
		NotApplicableBuilder reasoning(String reasoning);

	}

	/**
	 * Builder for a reasoned NOT_APPLICABLE outcome.
	 * <p>
	 * A label is legitimate here: the judge completed and may have classified <em>why</em> the
	 * criterion does not apply. A score is not: an excluded criterion was never assessed, and
	 * zero is a real assessment rather than the absence of one.
	 * </p>
	 *
	 * @since 0.17.0
	 */
	public interface NotApplicableBuilder extends EnrichmentBuilder {

		/**
		 * Replace the exclusion explanation.
		 * @param reasoning explanation
		 * @return this builder
		 */
		NotApplicableBuilder reasoning(String reasoning);

		/**
		 * Set a completed classification of why the criterion does not apply.
		 * @param label completed classification
		 * @return this builder
		 */
		NotApplicableBuilder label(String label);

		/** {@inheritDoc} */
		@Override
		NotApplicableBuilder check(Check check);

		/** {@inheritDoc} */
		@Override
		NotApplicableBuilder checks(Collection<Check> checks);

		/** {@inheritDoc} */
		@Override
		NotApplicableBuilder metadata(String key, Object value);

		/** {@inheritDoc} */
		@Override
		NotApplicableBuilder metadata(Map<String, Object> metadata);

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

		/**
		 * Replace the countable instrument cause.
		 * @param reasonCode an {@link JudgmentReasonCode.Family#INSTRUMENT} code
		 * @return this builder
		 * @since 0.17.0
		 */
		ErrorBuilder reasonCode(JudgmentReasonCode reasonCode);

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
			RequiredNotApplicableReason, NotApplicableBuilder, RequiredErrorReason, ErrorBuilder {

		/** Null until an outcome stage is selected; {@link #build()} requires it. */
		private @Nullable JudgmentStatus status;

		private @Nullable Double score;

		private @Nullable String label;

		private @Nullable JudgmentReasonCode reasonCode;

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
		public Builder notApplicable() {
			this.status = JudgmentStatus.NOT_APPLICABLE;
			return this;
		}

		@Override
		public Builder error() {
			return error(JudgmentReasonCode.JUDGE_REPORTED);
		}

		@Override
		public Builder error(JudgmentReasonCode reasonCode) {
			Objects.requireNonNull(reasonCode, "reasonCode must not be null");
			this.status = JudgmentStatus.ERROR;
			this.reasonCode = reasonCode;
			return this;
		}

		/**
		 * Record the countable cause of this judgment.
		 * @param reasonCode the code; its family must match the selected outcome
		 * @return this builder
		 */
		public Builder reasonCode(JudgmentReasonCode reasonCode) {
			this.reasonCode = Objects.requireNonNull(reasonCode, "reasonCode must not be null");
			return this;
		}

		/**
		 * Explain the judgment.
		 * @param reasoning human-readable explanation
		 * @return this builder
		 */
		public Builder reasoning(String reasoning) {
			Objects.requireNonNull(reasoning, "reasoning must not be null");
			if (status != null && requiresReasoning(status) && reasoning.isBlank()) {
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
			return new Judgment(Objects.requireNonNull(status, "status must not be null"), score, label, reasonCode,
					reasoning, checks, metadata);
		}

	}

}
