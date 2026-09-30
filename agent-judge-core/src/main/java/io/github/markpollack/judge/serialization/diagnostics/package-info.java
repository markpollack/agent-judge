/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

/**
 * The interpretation of a verdict: what it says about the subject, whether the recorded
 * facts support that, which stage decided, every stage and judge with its recorded
 * reasons and evidence, what the record is missing, and a deterministic summary that
 * agrees with all of it.
 *
 * <p>
 * One shape, two entry points.
 * {@link io.github.markpollack.judge.serialization.diagnostics.StoredVerdicts#interpret(io.github.markpollack.judge.jury.Verdict)}
 * reads a live verdict;
 * {@link io.github.markpollack.judge.serialization.diagnostics.StoredVerdicts#interpret(java.util.Map)}
 * reads a stored one of any age — the 0.13 {@code subVerdicts} form, the 0.14–0.16
 * {@code compositeAttempts} form without decisions, the unversioned 0.17 form, and
 * current version-4 domain results. Historical absence remains distinct from malformed
 * modern data; modern unknown/mixed versions cannot supply a usable subject
 * determination. Complete modern views retain raw facts separately from derived policy
 * outcomes.
 *
 * <h2>Nothing is inferred</h2>
 *
 * <p>
 * Which stage decided is read from the recorded provenance and never guessed from
 * equality, ordering or reasoning text. A record without a provenance reports
 * {@code decidedBy = null} and says so in its defects. An unknown wire token is carried
 * as the string it was, with an {@code UNKNOWN_VOCABULARY} defect, rather than refused.
 * An absent stage status is never read as a failure.
 *
 * <h2>Counting is not here</h2>
 *
 * <p>
 * Nothing in this package says whether a reading counts against the subject, enters a
 * denominator, or affects a rate. That policy belongs to the consumer that owns the
 * denominator; this package reports what the verdict says and how far the record can
 * support it.
 *
 * <p>
 * This package is {@link org.jspecify.annotations.NullMarked} and its main sources are
 * checked by NullAway at {@code ERROR} during {@code default-compile}; see
 * {@code agent-judge-core/pom.xml}.
 *
 * @see io.github.markpollack.judge.serialization.diagnostics.StoredReading
 * @see io.github.markpollack.judge.serialization.diagnostics.StoredVerdicts
 * @see io.github.markpollack.judge.serialization.diagnostics.Summaries
 */
@NullMarked
package io.github.markpollack.judge.serialization.diagnostics;

import org.jspecify.annotations.NullMarked;
