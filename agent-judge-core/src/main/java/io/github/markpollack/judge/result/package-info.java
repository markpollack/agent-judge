/*
 * Copyright (c) 2026 Mark Pollack
 * See LICENSE.txt in the repository root for license terms.
 */

/**
 * Judgment result values: {@link io.github.markpollack.judge.result.Judgment},
 * {@link io.github.markpollack.judge.result.JudgmentStatus} and
 * {@link io.github.markpollack.judge.result.Check}.
 *
 * <p>This package is {@link org.jspecify.annotations.NullMarked}: every reference type in a
 * declaration here is non-null unless it is explicitly annotated
 * {@link org.jspecify.annotations.Nullable}. That makes the optionality of
 * {@code Judgment.score} and {@code Judgment.label} visible to readers, reflection, and
 * schema derivers rather than only to prose.
 *
 * <p>JSpecify supplies vocabulary, not enforcement. Main sources in this package are checked
 * by NullAway at {@code ERROR} during {@code default-compile}, in JSpecify mode with
 * {@code OnlyNullMarked=true}; see {@code agent-judge-core/pom.xml}. Adoption is deliberately
 * bounded to this package — extending {@code @NullMarked} to another package is separate work
 * with its own diagnostic triage.
 */
@NullMarked
package io.github.markpollack.judge.result;

import org.jspecify.annotations.NullMarked;
