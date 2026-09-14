/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jspecify.annotations.Nullable;

/**
 * What produced a verdict's aggregate, and — when a cascade adopted it — from which tier and on
 * what basis.
 *
 * <p>
 * A cascade's verdict copies its aggregate from the tier that stopped it, and a stored result
 * used to give no way to tell a copied outcome from a computed one. That is not a cosmetic gap:
 * counting a cascade by its root and by its tiers counts the stopping tier twice, and an
 * aggregate that is an {@code ERROR} means something entirely different depending on whether
 * the cascade reached no conclusion or reached a rejection through a stage that failed. A
 * decision makes the difference explicit, so a result can be read correctly on its own rather
 * than by knowing how the jury was built.
 * </p>
 *
 * <table border="1">
 * <caption>The three kinds</caption>
 * <tr><th>Kind</th><th>tier</th><th>basis</th><th>What it means</th></tr>
 * <tr><td>{@link DecisionKind#OWN}</td><td>absent</td><td>absent</td>
 * <td>this jury reduced, or a policy decided</td></tr>
 * <tr><td>{@link DecisionKind#TIER}</td><td>required</td><td>required</td>
 * <td>the named direct tier determined it</td></tr>
 * <tr><td>{@link DecisionKind#UNDECIDED}</td><td>absent</td><td>absent</td>
 * <td>nothing determined an outcome; the aggregate is a machinery error</td></tr>
 * </table>
 *
 * <p>
 * <b>Names are local.</b> {@code tier} names a direct tier of <em>this</em> verdict, never one
 * further down. A reader following a chain of cascades moves one level at a time, and a name
 * means nothing outside the verdict that carries it.
 * </p>
 *
 * @param kind what produced the aggregate
 * @param tier the direct tier that determined the outcome, or null
 * @param basis how that tier determined it, or null
 * @author Mark Pollack
 * @since 0.17.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "kind", "tier", "basis" })
public record Decision(DecisionKind kind, @Nullable String tier, @Nullable DecisionBasis basis) {

	/**
	 * Validate the combination.
	 * @throws IllegalArgumentException if a tier or basis is present on a kind that forbids it,
	 * or absent from {@link DecisionKind#TIER}
	 */
	public Decision {
		Objects.requireNonNull(kind, "kind must not be null");
		if (kind == DecisionKind.TIER) {
			if (tier == null || basis == null) {
				throw new IllegalArgumentException("TIER requires both the tier name and the basis; "
						+ "an adopted outcome nobody can attribute is not attributable");
			}
			tier = NamedJury.requireValidName(tier);
		}
		else if (tier != null || basis != null) {
			throw new IllegalArgumentException(kind + " is this jury's own decision, so it names no tier and no basis");
		}
	}

	/** The jury reduced, or one of its policies decided. */
	private static final Decision OWN = new Decision(DecisionKind.OWN, null, null);

	/** Nothing determined an outcome. */
	private static final Decision UNDECIDED = new Decision(DecisionKind.UNDECIDED, null, null);

	/**
	 * This jury's own reduction or policy outcome.
	 * @return the OWN decision
	 */
	public static Decision own() {
		return OWN;
	}

	/**
	 * Nothing determined an outcome.
	 * @return the UNDECIDED decision
	 */
	public static Decision undecided() {
		return UNDECIDED;
	}

	/**
	 * The named direct tier determined the outcome.
	 * @param tier the tier's configured name
	 * @param basis how the tier determined it
	 * @return the TIER decision
	 */
	public static Decision tier(String tier, DecisionBasis basis) {
		return new Decision(DecisionKind.TIER, tier, basis);
	}

}
