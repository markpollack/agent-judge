/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

/**
 * Jury orchestration, verdicts, aggregation strategies, and cascade policies.
 *
 * <p>A jury turns several judgments into one, and records enough about how it did so that a
 * reader can check the answer instead of trusting it. Three ideas carry most of that weight.
 *
 * <h2>The population is resolved once, and published</h2>
 *
 * <p>Every strategy resolves what it reduces over through the same scan, and writes the result
 * into its {@link io.github.markpollack.judge.jury.AggregationEvidence} block: how many
 * judgments arrived, how many were abstentions, how many were exclusions, how many errored and
 * with what causes, and what each policy actually did with them. Counts are stored and rates
 * are derived, because a stored rate cannot be recomputed when the definition of its
 * denominator changes.
 *
 * <p>Two policies decide what leaves that population.
 * {@link io.github.markpollack.judge.jury.ErrorPolicy} governs a judge that could not finish;
 * {@link io.github.markpollack.judge.jury.NotApplicablePolicy} governs a criterion that did not
 * apply. Both default to refusing to guess — {@code PROPAGATE} and {@code REFUSE} — because a
 * jury assembled without deciding these questions has not decided them.
 *
 * <h2>Machinery failure never supplies rejection evidence</h2>
 *
 * <p>When the library's own composition or reduction fails, the subject is not charged for it.
 * A contained failure is an {@code ERROR} with a machinery reason code, excluded from the
 * subject's denominator and counted as an instrument failure. It is never converted into a
 * FAIL, under any error policy, because a rejection nobody can tell apart from a real one is
 * worse than no rejection at all.
 *
 * <h2>Failures are contained, and loud</h2>
 *
 * <p>A judge that throws becomes an ERROR seat rather than discarding its jury. A strategy that
 * throws, returns null, or returns an aggregate it was not entitled to produce becomes an
 * {@code ERROR aggregation_failed} verdict rather than an escaping exception that would
 * collapse an enclosing cascade tier. A composite stage that did not produce a usable
 * determination is recorded as a stage failure on its attempt, with the child's actual verdict
 * kept. Nothing is swallowed: every containment writes a code a reader can count.
 *
 * @see io.github.markpollack.judge.jury.Verdict
 * @see io.github.markpollack.judge.jury.CompositeAttempt
 */
package io.github.markpollack.judge.jury;
