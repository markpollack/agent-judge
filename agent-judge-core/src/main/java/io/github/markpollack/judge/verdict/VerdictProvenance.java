/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.verdict;
import io.github.markpollack.judge.judgment.*;
import io.github.markpollack.judge.jury.Jury;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jspecify.annotations.Nullable;

/**
 * Records how the Verdict's collective Judgment was produced. An ordinary Jury reduces
 * its individual Judgments; a one-seat Jury can retain its sole Judgment unchanged. Both
 * have {@link VerdictProvenanceKind#OWN} provenance. A composite Jury can instead adopt a
 * direct tier's conclusion, recording that tier and the basis for adoption. This
 * distinguishes a computed conclusion from an inherited one and prevents counting an
 * adopted Judgment twice when reading composition history.
 *
 * <table border="1">
 * <caption>The three kinds</caption>
 * <tr>
 * <th>Kind</th>
 * <th>tier</th>
 * <th>basis</th>
 * <th>What it means</th>
 * </tr>
 * <tr>
 * <td>{@link VerdictProvenanceKind#OWN}</td>
 * <td>absent</td>
 * <td>absent</td>
 * <td>this jury reduced, retained one valid result by identity, or a policy decided</td>
 * </tr>
 * <tr>
 * <td>{@link VerdictProvenanceKind#TIER}</td>
 * <td>required</td>
 * <td>required</td>
 * <td>the named direct tier determined it</td>
 * </tr>
 * <tr>
 * <td>{@link VerdictProvenanceKind#UNDECIDED}</td>
 * <td>absent</td>
 * <td>absent</td>
 * <td>nothing determined an outcome; the collective Judgment is a machinery error</td>
 * </tr>
 * </table>
 *
 * <p>
 * <b>Names are local.</b> {@code tier} names a direct tier of <em>this</em> verdict,
 * never one further down. A reader following a chain of cascades moves one level at a
 * time, and a name means nothing outside the verdict that carries it.
 * </p>
 *
 * @param kind what produced the collective Judgment
 * @param tier the direct tier that determined the outcome, or null
 * @param basis how that tier determined it, or null
 * @author Mark Pollack
 * @since 0.17.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "kind", "tier", "basis" })
public record VerdictProvenance(VerdictProvenanceKind kind, @Nullable String tier,
		@Nullable VerdictProvenanceBasis basis) {

	/**
	 * Validate the combination.
	 * @throws IllegalArgumentException if a tier or basis is present on a kind that
	 * forbids it, or absent from {@link VerdictProvenanceKind#TIER}
	 */
	public VerdictProvenance {
		Objects.requireNonNull(kind, "kind must not be null");
		if (kind == VerdictProvenanceKind.TIER) {
			if (tier == null || basis == null) {
				throw new IllegalArgumentException("TIER requires both the tier name and the basis; "
						+ "an adopted outcome nobody can attribute is not attributable");
			}
			tier = CompositeNames.requireValidName(tier);
		}
		else if (tier != null || basis != null) {
			throw new IllegalArgumentException(kind + " is this jury's own decision, so it names no tier and no basis");
		}
	}

	/**
	 * The jury reduced, retained a valid identity result, or one of its policies decided.
	 */
	private static final VerdictProvenance OWN = new VerdictProvenance(VerdictProvenanceKind.OWN, null, null);

	/** Nothing determined an outcome. */
	private static final VerdictProvenance UNDECIDED = new VerdictProvenance(VerdictProvenanceKind.UNDECIDED, null,
			null);

	/**
	 * This jury's own reduction or policy outcome.
	 * @return the OWN provenance
	 */
	public static VerdictProvenance own() {
		return OWN;
	}

	/**
	 * Nothing determined an outcome.
	 * @return the UNDECIDED provenance
	 */
	public static VerdictProvenance undecided() {
		return UNDECIDED;
	}

	/**
	 * The named direct tier determined the outcome.
	 * @param tier the tier's configured name
	 * @param basis how the tier determined it
	 * @return the TIER provenance
	 */
	public static VerdictProvenance tier(String tier, VerdictProvenanceBasis basis) {
		return new VerdictProvenance(VerdictProvenanceKind.TIER, tier, basis);
	}

 /** Derive aggregate origin from the retained producer status.
  * @param judgment reduced result
  * @return authoritative origin
  */
	public static VerdictProvenance decisionFor(Judgment judgment) {
		JudgmentReasonCode code = judgment.reasonCode();
		boolean undecided = judgment.status() == JudgmentStatus.ERROR && code != null
				&& code.originFamily() == JudgmentReasonCode.OriginFamily.MACHINERY;
		return undecided ? VerdictProvenance.undecided() : VerdictProvenance.own();
	}

}
