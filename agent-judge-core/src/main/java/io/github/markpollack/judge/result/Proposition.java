/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.result;

import org.jspecify.annotations.Nullable;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A proposition, with an absent selected truth value permitted.
 *
 * @param value selected truth value, or null when not selected
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Proposition(@Nullable Boolean value) {
}
