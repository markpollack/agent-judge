/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

/**
 * Judgment result values: {@link io.github.markpollack.judge.result.Judgment},
 * {@link io.github.markpollack.judge.result.JudgmentStatus},
 * {@link io.github.markpollack.judge.result.JudgmentReasonCode} and
 * {@link io.github.markpollack.judge.result.Check}.
 *
 * <h2>Three ways of not passing, told apart</h2>
 *
 * <p>A result that cannot distinguish them cannot produce an honest rate, because each belongs in
 * a different place in the arithmetic:
 *
 * <ul>
 *   <li>{@code FAIL} — the question was asked and answered no. Counted, against the subject.</li>
 *   <li>{@code ABSTAIN} — the question applied and has no answer yet. Counted; no vote cast.</li>
 *   <li>{@code NOT_APPLICABLE} — the question should not have been asked here. <em>Excluded</em>
 *       from the denominator, and counted separately so a reader can say how much of a rubric
 *       applied.</li>
 *   <li>{@code ERROR} — the instrument never reached a finding. Excluded from the subject
 *       denominator and counted as an instrument failure.</li>
 * </ul>
 *
 * <p>Exclusion is the consequential one, because a criterion that leaves the denominator cannot
 * fail. A judge may only return it where it declared in advance that it can; an undeclared
 * exclusion is contained as an error rather than honoured.
 *
 * <h2>One countable code plus mandatory free text</h2>
 *
 * <p>Every {@code ERROR} carries a {@code JudgmentReasonCode} from the instrument family, because
 * an instrument failure nobody can count is a failure nobody fixes. A {@code FAIL} may carry one
 * from the subject family, and usually does not: most rejections are explained in prose, and
 * forcing a category would manufacture a taxonomy nobody asked for. The code is what a reader
 * counts; the reasoning is what a human reads; neither substitutes for the other, and a code is
 * never present without reasoning.
 *
 * <h2>Counts are stored; rates are derived</h2>
 *
 * <p>A result records what happened, never a rate over it. A stored rate cannot be recomputed when
 * the definition of its denominator changes, and this release changes exactly that definition.
 *
 * <p>This package is {@link org.jspecify.annotations.NullMarked}: every reference type in a
 * declaration here is non-null unless it is explicitly annotated
 * {@link org.jspecify.annotations.Nullable}. That makes the optionality of
 * {@code Judgment.score} and {@code Judgment.label} visible to readers, reflection, and
 * schema derivers rather than only to prose.
 *
 * <p>Values here are results, so they are portable: {@code Judgment} metadata accepts only
 * strings, booleans, interoperable integers, finite numbers, arrays, and string-keyed
 * objects, recursively, and construction refuses anything else. That is a property of the
 * constructed value rather than of caller restraint. Evaluation <em>inputs</em> carry no
 * such guarantee — {@code JudgmentContext} is in-process and may hold framework-native
 * objects a judge needs to inspect.
 *
 * <p>JSpecify supplies vocabulary, not enforcement. Main sources in this package are checked
 * by NullAway at {@code ERROR} during {@code default-compile}, in JSpecify mode with
 * {@code OnlyNullMarked=true}; see {@code agent-judge-core/pom.xml}. Adoption is per package:
 * this package and {@code io.github.markpollack.judge.description} have opted in, and
 * extending {@code @NullMarked} to another package is separate work with its own diagnostic
 * triage.
 */
@NullMarked
package io.github.markpollack.judge.result;

import org.jspecify.annotations.NullMarked;
