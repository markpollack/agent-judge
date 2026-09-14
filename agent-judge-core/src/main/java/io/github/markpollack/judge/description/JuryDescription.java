/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.description;

import java.util.Map;

import io.github.markpollack.judge.jury.Jury;

/**
 * A jury as configured, available before it votes.
 *
 * <p>
 * Obtained from {@link Jury#describe()}. The four variants mirror the library's juries:
 * </p>
 * <ul>
 * <li>{@link SimpleJuryDescription} — a strategy and its seats, each with position, verdict
 * key, weight and judge;</li>
 * <li>{@link CascadedJuryDescription} — named tiers in order, each with its policy and
 * jury;</li>
 * <li>{@link MetaJuryDescription} — a strategy over named member juries;</li>
 * <li>{@link OpaqueJuryDescription} — a jury that does not describe its own structure.</li>
 * </ul>
 *
 * <p>
 * The portable form returned by {@link #toPortable()} starts with
 * {@code "descriptionVersion"}, then {@code "kind"}: {@code SIMPLE}, {@code CASCADED},
 * {@code META} or {@code OPAQUE}. The remaining keys are fixed for each kind. A jury nested in
 * a tier or member carries {@code "kind"} but not the version, which belongs to the root.
 * </p>
 *
 * @author Mark Pollack
 * @since 0.17.0
 */
public sealed interface JuryDescription
		permits SimpleJuryDescription, CascadedJuryDescription, MetaJuryDescription, OpaqueJuryDescription {

	/**
	 * The format version, carried as the root {@code "descriptionVersion"} key of every
	 * description's portable form.
	 * <p>
	 * A consumer that hashes a portable description hashes this key with it, so a new hash can
	 * be told apart as a format change rather than a change of jury. The version is an integer
	 * that starts at 1. It is incremented whenever the same configured jury could produce a
	 * different {@code toPortable()} map because the library changed the format:
	 * </p>
	 * <ul>
	 * <li>a key is added, removed or renamed, at any level;</li>
	 * <li>a value vocabulary is added to or changed, such as a new
	 * {@link ImplementationIdentity.Form}, {@link KeySource} or declaration flag value;</li>
	 * <li>the way an existing value is derived changes, such as the implementation identity
	 * rules or the key-source rules.</li>
	 * </ul>
	 * <p>
	 * It is not incremented by a release that leaves every map unchanged.
	 * </p>
	 * <p>
	 * It does not cover what a judge declares. A judge, including a library judge in a later
	 * release, that starts declaring configuration or changes its declared values produces a
	 * different map under the same version. That is a change in the description of the
	 * instrument, not a change of format.
	 * </p>
	 * <p>
	 * <b>Version 2</b> adds what a reader needs in order to tell an exclusion from an abstention
	 * <em>before</em> the jury runs: a strategy's {@code notApplicablePolicy} parameter, a
	 * judge's {@code notApplicableWhen} capability node, and each jury's
	 * {@code aggregateMayBeNotApplicable}. The same configured jury produces a different map
	 * than it did at version 1, which is what the version is for.
	 * </p>
	 */
	int DESCRIPTION_VERSION = 2;

	/**
	 * The portable form: an ordered map of JSON-compatible values, validated by the same
	 * portable-value algebra as judgment metadata, with stable keys and
	 * {@code "descriptionVersion"} first.
	 * @return an ordered, validated, immutable map
	 * @throws IllegalArgumentException if a value anywhere in the description is not
	 * portable; the message names its path from {@code jury}
	 */
	Map<String, Object> toPortable();

	/**
	 * A conservative bound on whether this jury's aggregate may be
	 * {@link io.github.markpollack.judge.result.JudgmentStatus#NOT_APPLICABLE}.
	 * <p>
	 * True means the jury is <em>permitted</em> to exclude the subject, not that it will. False
	 * is the load-bearing direction: a built-in parent that receives a
	 * {@code NOT_APPLICABLE} aggregate from a child whose bound is false treats it as a stage
	 * failure rather than honouring it, so a jury cannot acquire the right to shrink a
	 * denominator by being nested inside something.
	 * </p>
	 * <p>
	 * Every variant derives this from what it already carries — a strategy's not-applicable
	 * policy and its seats' declared capabilities, its members', or its tiers' — so a reader
	 * holding only the portable form reaches the same answer the jury does, without running it.
	 * </p>
	 * @return true when the aggregate may be not applicable
	 * @since 0.17.0
	 */
	boolean aggregateMayBeNotApplicable();

	/**
	 * Describe a jury from its public view only: its implementation, its strategy and its
	 * flattened judges.
	 * <p>
	 * This is the default of {@link Jury#describe()}. It is truthful but not structural: it
	 * does not know how the jury seats, weights, keys or orders those judges.
	 * </p>
	 * @param jury the jury
	 * @return an opaque description
	 */
	static JuryDescription opaque(Jury jury) {
		return OpaqueJuryDescription.of(jury);
	}

}
