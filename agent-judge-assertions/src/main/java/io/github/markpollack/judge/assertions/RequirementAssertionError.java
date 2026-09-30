/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.assertions;

import java.util.Objects;
import org.opentest4j.AssertionFailedError;
import io.github.markpollack.judge.jury.interpretation.Interpretation;

/**
 * An unsuccessful requirement assertion with a concise explanation and the complete
 * unchanged result. The message connects producer finding, metric-specific support,
 * application policy and the authoritative Interpretation. Long text and identifiers are
 * abbreviated; use {@link #result()} for full finding, provenance and evidence
 * references.
 *
 * <p>
 * The subclasses describe why the assertion did not pass. An inconclusive result or
 * instrument error is an assertion failure, not a rewritten subject violation or a test
 * skip. Message wording is for people; use {@link #category()} and the structured result
 * for programmatic decisions.
 */
public abstract class RequirementAssertionError extends AssertionFailedError {

	/** Complete evaluation carried for diagnostics. */
	private final AssertionResult result;

	/** Unsuccessful assertion category. */
	private final FailureKind category;

	/** Diagnostic cause, never a rewritten subject outcome. */
	public enum FailureKind {

		/** Supported negative finding. */
		REJECTED,
		/** No usable conclusion, including a request for escalation. */
		INCONCLUSIVE,
		/** Judge, policy or invocation failure. */
		INSTRUMENT_FAILURE,
		/** Assertion requirement did not apply. */
		NOT_APPLICABLE,
		/** Reading facts were absent or contradicted. */
		UNSUPPORTED_READING

	}

	private RequirementAssertionError(AssertionResult result, FailureKind category) {
		super(AssertionDiagnostics.message(result, category));
		this.result = Objects.requireNonNull(result);
		this.category = category;
	}

	/**
	 * Return the complete evaluation.
	 * @return full retained evaluation
	 */
	public final AssertionResult result() {
		return result;
	}

	/**
	 * Return the retained authoritative reading.
	 * @return authoritative interpretation carried by this error
	 */
	public final Interpretation interpretation() {
		return result.interpretation();
	}

	/**
	 * Return the diagnostic cause.
	 * @return diagnostic cause category
	 */
	public final FailureKind category() {
		return category;
	}

	/** A supported violation. */
	public static final class Rejected extends RequirementAssertionError {

		Rejected(AssertionResult result) {
			super(result, FailureKind.REJECTED);
		}

	}

	/**
	 * An uncertain result or escalation request; this failure does not execute
	 * escalation.
	 */
	public static final class Inconclusive extends RequirementAssertionError {

		Inconclusive(AssertionResult result) {
			super(result, FailureKind.INCONCLUSIVE);
		}

	}

	/** An instrument failure, not a subject violation. */
	public static final class InstrumentFailure extends RequirementAssertionError {

		InstrumentFailure(AssertionResult result) {
			super(result, FailureKind.INSTRUMENT_FAILURE);
		}

	}

	/** Unexpected non-applicability at an assertion boundary. */
	public static final class NotApplicable extends RequirementAssertionError {

		NotApplicable(AssertionResult result) {
			super(result, FailureKind.NOT_APPLICABLE);
		}

	}

	/** An unsupported or contradicted authoritative reading. */
	public static final class UnsupportedReading extends RequirementAssertionError {

		UnsupportedReading(AssertionResult result) {
			super(result, FailureKind.UNSUPPORTED_READING);
		}

	}

}
