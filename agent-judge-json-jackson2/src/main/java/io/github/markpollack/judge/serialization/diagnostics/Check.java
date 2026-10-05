/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.serialization.diagnostics;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import com.fasterxml.jackson.annotation.JsonIgnore;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * One check a judge recorded, as recorded.
 *
 * <p>
 * The same fact as {@link io.github.markpollack.judge.judgment.Check}, carried on the
 * {@link JudgeSeat} so a reader never opens the stored verdict to learn what a judge
 * checked. {@code detail} is the check's recorded message.
 *
 * @param name the check's name
 * @param legacyPassed historical boolean, absent on modern checks
 * @param judgment full modern child, absent on historical checks
 * @param detail the recorded message, or an empty string when none was recorded
 * @author Mark Pollack
 * @since 0.17.0
 */
@JsonPropertyOrder({ "name", "legacyPassed", "detail", "judgment" })
public record Check(String name, @Nullable Boolean legacyPassed, String detail, @Nullable JudgmentView judgment) {

	/**
	 * Construct an explicitly historical boolean check.
	 * @param name check identity
	 * @param passed historical boolean, not a modern FAIL/PASS determination
	 * @param detail historical explanation
	 */
	public Check(String name, boolean passed, String detail) {
		this(name, passed, detail, null);
	}

	/**
	 * Historical compatibility accessor; modern checks have no boolean projection.
	 * @return recorded historical boolean
	 * @throws IllegalStateException for a modern check
	 */
	@JsonIgnore
	public boolean passed() {
		if (legacyPassed == null)
			throw new IllegalStateException("Modern checks retain five outcomes");
		return legacyPassed;
	}

	/** Validate that the name and detail are present. */
	public Check {
		if ((legacyPassed == null) == (judgment == null))
			throw new IllegalArgumentException("Exactly one of legacyPassed or judgment is required");
		Objects.requireNonNull(name, "name must not be null");
		Objects.requireNonNull(detail, "detail must not be null");
	}

}
