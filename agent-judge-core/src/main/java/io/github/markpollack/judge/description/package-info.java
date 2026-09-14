/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

/**
 * The configured structure of judges, juries and voting strategies, available before any
 * vote.
 *
 * <p>
 * A {@link io.github.markpollack.judge.jury.Verdict} records what a jury did. This package
 * records what a jury was configured to do, so the two can be compared: the seats a
 * {@link io.github.markpollack.judge.jury.SimpleJury} lists against the
 * {@code inputCount} its aggregation evidence reports, or the tiers a
 * {@link io.github.markpollack.judge.jury.CascadedJury} lists against the attempts it made.
 * Call {@link io.github.markpollack.judge.jury.Jury#describe()}; a single judge is described
 * by {@link io.github.markpollack.judge.Judges#describe(io.github.markpollack.judge.Judge)}.
 * </p>
 *
 * <h2>Each type describes itself</h2>
 * <p>
 * Nothing here uses reflection, and an architecture test holds that. A built-in jury or
 * strategy states its own configuration, so renaming a private field cannot change a
 * description. A consumer's {@code Jury} that does not override {@code describe()} is
 * described truthfully as {@link io.github.markpollack.judge.description.OpaqueJuryDescription
 * opaque} rather than as empty, and a consumer's judge declares configuration only by
 * implementing {@link io.github.markpollack.judge.description.ConfiguredJudge}.
 * </p>
 *
 * <h2>The portable form</h2>
 * <p>
 * Every description has a {@code toPortable()} form: an ordered map of JSON-compatible values,
 * validated by the same portable-value algebra that {@link io.github.markpollack.judge.result.Judgment}
 * metadata uses. Its keys are a published contract, and it is built to be stable across JVM
 * runs so a consumer can hash it:
 * </p>
 * <ul>
 * <li>A jury, judge or strategy description returned as a root starts with
 * {@code "descriptionVersion"}, currently
 * {@value io.github.markpollack.judge.description.JuryDescription#DESCRIPTION_VERSION}, so a
 * change of format is distinguishable from a change of configuration.</li>
 * <li>A declaration that may be absent is carried as {@code {"declared": false}} or
 * {@code {"declared": true, "values": {...}}}. Absence is never the omission of a key, so
 * "declared nothing" and "declared an empty configuration" have different bytes.</li>
 * <li>Keys of a declared values map are in ascending order, so a configuration supplied as a
 * {@code HashMap} or {@code Map.of} describes identically in every JVM.</li>
 * <li>A hidden class, such as a lambda, is described as {@code HIDDEN} with no class name.
 * An anonymous or local class is described by its form and its enclosing top-level class,
 * never by a binary name whose {@code $1} ordinal moves when an unrelated class is
 * added.</li>
 * </ul>
 *
 * <p>
 * This package is {@link org.jspecify.annotations.NullMarked} and checked by NullAway during
 * {@code default-compile}; see {@code agent-judge-core/pom.xml}.
 * </p>
 */
@NullMarked
package io.github.markpollack.judge.description;

import org.jspecify.annotations.NullMarked;
