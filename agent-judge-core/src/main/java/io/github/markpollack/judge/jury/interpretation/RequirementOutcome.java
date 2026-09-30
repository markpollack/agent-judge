/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury.interpretation;

/**
 * What the evaluation establishes about the Requirement, before final application acceptance.
 * A supported SATISFIED outcome establishes the requirement; VIOLATED establishes its
 * violation. UNRESOLVED, NOT_APPLICABLE and NOT_ASSESSED keep uncertainty, exclusion and
 * instrument failure distinct. Consumers must also inspect the Interpretation's reading
 * support before relying on an outcome.
 *
 * @since 0.18.0
 */
public enum RequirementOutcome {

	/** The requirement was established: the collective Judgment is {@code pass}. */
	SATISFIED,

	/**
	 * The requirement was found violated: the collective Judgment is {@code fail}, or the recorded
	 * provenance stopped on an individual rejection a stage had established.
	 */
	VIOLATED,

	/**
	 * The subject was judged and the jury could not decide: the collective Judgment is
	 * {@code abstain}.
	 */
	UNRESOLVED,

	/**
	 * The criteria did not apply to the subject: the collective Judgment is {@code not_applicable}.
	 */
	NOT_APPLICABLE,

	/**
	 * The instrument failed before assessing the subject: the collective Judgment is {@code error}.
	 * Distinct from an item that carries no verdict at all, which is never interpreted.
	 */
	NOT_ASSESSED

}
