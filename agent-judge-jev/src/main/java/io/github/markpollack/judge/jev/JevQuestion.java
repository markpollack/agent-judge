/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jev;

import io.github.markpollack.judge.judgment.QualityDirection;
import io.github.markpollack.judge.provenance.ArtifactRef;
import java.util.*;
import org.jspecify.annotations.Nullable;

/**
 * One explicit native question and its versioned requirement projection. The instructions
 * must evaluate the requirement supplied as state.requirement against state.evidence.
 */
public sealed interface JevQuestion permits JevQuestion.Noul, JevQuestion.Choice, JevQuestion.Score {

	/**
	 * Return the explicit provider instructions.
	 * @return explicit instructions
	 */
	String instructions();

	/**
	 * Return the declared projection identity.
	 * @return versioned projection identity
	 */
	String projectionId();

	/**
	 * Complete-evidence binary question: false means supported violation.
	 *
	 * @param instructions explicit instructions
	 * @param projectionId versioned mapping identity
	 * @param completeEvidenceBinary declares the binary question's completeness
	 * precondition
	 */
	record Noul(String instructions, String projectionId, boolean completeEvidenceBinary) implements JevQuestion {
		/** Validate text. */
		public Noul {
			Checks.text(instructions);
			Checks.versioned(projectionId);
		}
	}

	/** Label meaning, independent of predictive probability. */
	enum Meaning {

		/** Requirement satisfied. */
		SATISFIED,
		/** Supported violation. */
		VIOLATED,
		/** Evidence does not support a determination. */
		INSUFFICIENT

	}

	/**
	 * Choice with complete declared descriptions and meanings.
	 *
	 * @param instructions explicit instructions
	 * @param projectionId versioned mapping identity
	 * @param criteria ordered label-to-description map
	 * @param meanings complete label projection
	 */
	record Choice(String instructions, String projectionId, Map<String, Object> criteria,
			Map<String, Meaning> meanings) implements JevQuestion {
		/** Freeze the portable criteria and projection. */
		public Choice {
			Checks.text(instructions);
			Checks.versioned(projectionId);
			criteria = Checks.portable(criteria);
			meanings = Map.copyOf(meanings);
			if (criteria.isEmpty() || criteria.size() > 255 || !criteria.keySet().equals(meanings.keySet()))
				throw new IllegalArgumentException("Choice requires 1..255 fully mapped options");
			criteria.forEach((k, v) -> {
				Checks.text(k);
				Checks.criterion(v);
			});
		}
	}

	/**
	 * Independent semantic review assertion, not automatic proof of prose ordering.
	 *
	 * @param configurationDigest exact candidate digest produced by
	 * Score.configurationDigest()
	 * @param author candidate author identity
	 * @param reviewer independent reviewer identity
	 * @param approved false for missing or unresolved approval
	 * @param evidence retained review record
	 */
	record Review(String configurationDigest, String author, String reviewer, boolean approved, ArtifactRef evidence) {
		/** Validate review identity. */
		public Review {
			Checks.text(configurationDigest);
			Checks.text(author);
			Checks.text(reviewer);
			Objects.requireNonNull(evidence);
		}
	}

	/**
	 * Ordinal expectation on a single reviewed dimension. Projection uses quality in
	 * [0,1]: values at/below violatedAtOrBelow are FAIL, at/above satisfiedAtOrAbove
	 * PASS, between them ABSTAIN. Missing answers are protocol ERROR, never an inferred
	 * negative.
	 *
	 * @param instructions explicit instructions
	 * @param projectionId versioned projection
	 * @param rubricId versioned rubric
	 * @param dimension single named dimension
	 * @param criteria ordered structured level meanings
	 * @param ranks semantic quality ranks for corresponding levels
	 * @param direction direction of improving quality by index
	 * @param violatedAtOrBelow inclusive negative quality boundary
	 * @param satisfiedAtOrAbove inclusive positive quality boundary
	 * @param review independent review bound to this exact configuration, or null if
	 * ineligible
	 */
	record Score(String instructions, String projectionId, String rubricId, String dimension, List<Object> criteria,
			List<Integer> ranks, QualityDirection direction, double violatedAtOrBelow, double satisfiedAtOrAbove,
			@Nullable Review review) implements JevQuestion {
		/** Freeze configuration; semantic eligibility is checked before every call. */
		public Score {
			Checks.text(instructions);
			Checks.versioned(projectionId);
			Checks.versioned(rubricId);
			Checks.text(dimension);
			criteria = Checks.portableList(criteria);
			ranks = List.copyOf(ranks);
			Objects.requireNonNull(direction);
			if (criteria.size() < 2 || criteria.size() > 10 || criteria.size() != ranks.size())
				throw new IllegalArgumentException("Score requires 2..10 ranked levels");
			criteria.forEach(Checks::criterion);
			if (!Double.isFinite(violatedAtOrBelow) || !Double.isFinite(satisfiedAtOrAbove) || violatedAtOrBelow < 0
					|| satisfiedAtOrAbove > 1 || violatedAtOrBelow >= satisfiedAtOrAbove)
				throw new IllegalArgumentException("Invalid projection boundaries");
		}

		/**
		 * Hash exact candidate configuration bytes for an independent review binding.
		 * @return digest binding exact serialized criterion bytes, ranks, direction and
		 * projection
		 */
		public String configurationDigest() {
			return ArtifactRef.ofBytes("configuration", Checks.json(configuration()), null).sha256();
		}

		Map<String, Object> configuration() {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("instructions", instructions);
			m.put("projectionId", projectionId);
			m.put("rubricId", rubricId);
			m.put("dimension", dimension);
			m.put("criteria", criteria);
			m.put("ranks", ranks);
			m.put("direction", direction.name());
			m.put("violatedAtOrBelow", violatedAtOrBelow);
			m.put("satisfiedAtOrAbove", satisfiedAtOrAbove);
			m.put("betweenBoundaries", "ABSTAIN");
			m.put("missingAnswer", "ERROR");
			return m;
		}

		void preflight() {
			for (int i = 1; i < ranks.size(); i++) {
				int cmp = Integer.compare(ranks.get(i), ranks.get(i - 1));
				if (cmp != (direction == QualityDirection.INCREASING ? 1 : -1))
					throw new IllegalArgumentException("Score ranks must be strictly monotonic");
			}
			Review r = review;
			if (r == null || !r.approved() || r.author().equals(r.reviewer())
					|| !r.configurationDigest().equals(configurationDigest()))
				throw new IllegalArgumentException("Score requires current independent semantic review");
		}
	}

}
