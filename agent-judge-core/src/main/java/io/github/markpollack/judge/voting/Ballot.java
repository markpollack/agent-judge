/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.voting;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import io.github.markpollack.judge.judgment.Judgment;

/**
 * One configured seat remains bound to its original and reduction treatment.
 *
 * @param position configured seat identity; labels need not be unique
 * @param label retained local label
 * @param original complete producer return
 * @param treatment actual reduction input, separate from the original
 * @param participation parent participation; NOT_RECORDED before reduction
 * @param declaredWeight optional positive finite declaration; absent means 1.0
 */
public record Ballot(int position, String label, Judgment original, Judgment treatment, Participation participation,
		@Nullable Double declaredWeight) {
	/** Validate one immutable ballot. */
	public Ballot {
		if (position < 0)
			throw new IllegalArgumentException("Negative seat position");
		io.github.markpollack.judge.portable.ValueRequirements.text(label, "ballot label");
		Objects.requireNonNull(original);
		Objects.requireNonNull(treatment);
		Objects.requireNonNull(participation);
		if (declaredWeight != null && !Double.isFinite(declaredWeight))
			throw new IllegalArgumentException("Declared weight must be finite");
		if (declaredWeight != null && declaredWeight <= 0)
			throw new IllegalArgumentException("Declared weight must be positive");
	}

	/**
	 * Effective voting weight, without inventing an explicit declaration.
	 * @return declared weight or default 1.0
	 */
	public double effectiveWeight() {
		return declaredWeight == null ? 1.0 : declaredWeight;
	}
}
