/**
 * Judges that run a written requirements document back against an implementation.
 *
 * <p>The input is a document somebody wrote before the code existed — acceptance criteria,
 * architectural constraints — and the question is whether the implementation satisfies it. The
 * document supplies the roster; the judge answers every entry on it.
 *
 * <p>Key types:
 * <ul>
 *   <li>{@link io.github.markpollack.judge.ai.requirements.EarsCriterion} — one acceptance
 *       criterion, parsed from the specification</li>
 *   <li>{@link io.github.markpollack.judge.ai.requirements.EarsJudge} — answers every acceptance
 *       criterion</li>
 *   <li>{@link io.github.markpollack.judge.ai.requirements.Rfc2119Constraint} — one architectural
 *       constraint, with its RFC 2119 keyword</li>
 *   <li>{@link io.github.markpollack.judge.ai.requirements.Rfc2119Judge} — answers every
 *       architectural constraint</li>
 *   <li>{@link io.github.markpollack.judge.ai.requirements.Observation} — non-binding evidence
 *       noticed while establishing a judgment</li>
 * </ul>
 *
 * <h2>A fixed roster, not a sampled population</h2>
 *
 * <p>These judges roll their per-requirement answers up strictly:
 *
 * <pre>
 *   any ERROR        -&gt; ERROR
 *   else any FAIL    -&gt; FAIL
 *   else any ABSTAIN -&gt; ABSTAIN
 *   else             -&gt; PASS
 * </pre>
 *
 * <p>An abstention is <em>not</em> dropped here, and that is the one thing to understand before
 * composing these judges with anything else. A written requirement is required by construction: the
 * document says it applies, so "could not be established" is not "does not apply". Fifty-one
 * criteria established and one unsettled is {@code ABSTAIN} — the specification has not been shown
 * to hold.
 *
 * <p>This differs deliberately from jury aggregation in
 * {@link io.github.markpollack.judge.jury}, where {@code ABSTAIN} means a judge does not apply and
 * so casts no vote, and is therefore removed from the eligible population. That reading is right
 * for a heterogeneous jury and wrong for a fixed roster of requirements. Routing these judgments
 * through a voting strategy that discards abstentions would report a specification as satisfied
 * when one of its requirements was never settled.
 *
 * <p>There is no score anywhere in the result. Every requirement stays individually visible as its
 * own {@link io.github.markpollack.judge.result.Check}, and a failure names the requirement that
 * bound the verdict and where.
 *
 * @author Mark Pollack
 * @since 0.16.0
 */
package io.github.markpollack.judge.ai.requirements;
