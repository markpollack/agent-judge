/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.description;

import java.util.List;
import java.util.Map;
import io.github.markpollack.judge.requirement.Requirement;

/**
 * Declared requirement coverage, with no common-subject opinion seats.
 *
 * @param format domain audit format
 * @param requirements actual declared requirements
 * @param aggregateMayBeNotApplicable declared exclusion bound
 */
public record AuditJuryDescription(String format, List<Requirement<?>> requirements,
		boolean aggregateMayBeNotApplicable) implements JuryDescription {

	/** Maximum independent requirement population. */
	public static final int MAX_REQUIREMENTS = 256;

	/** Validates and snapshots the roster. */
	public AuditJuryDescription {
		Requirement.requireText(format);
		requirements = List.copyOf(requirements);
		if (requirements.size() > MAX_REQUIREMENTS)
			throw new IllegalArgumentException("Roster population limit of " + MAX_REQUIREMENTS + " exceeded");
		if (requirements.isEmpty())
			throw new IllegalArgumentException("Empty audit roster");
		requirements.forEach(Requirement::validate);
		if (requirements.stream().map(Requirement::id).distinct().count() != requirements.size())
			throw new IllegalArgumentException("Duplicate audit identity");
	}

	@Override
	public OpinionBound routingOpinionBound() {
		return OpinionBound.KNOWN_NONE;
	}

	@Override
	public Map<String, Object> toPortable() {
		return Map.of("descriptionVersion", DESCRIPTION_VERSION, "kind", "AUDIT", "format", format, "requirements",
				requirements.stream().map(Requirement::id).toList(), "aggregateMayBeNotApplicable",
				aggregateMayBeNotApplicable, "routingOpinionBound", routingOpinionBound().name());
	}
}
