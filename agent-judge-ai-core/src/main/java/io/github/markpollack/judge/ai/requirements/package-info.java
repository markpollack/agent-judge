/**
 * Pure native requirements and configured RFC2119/EARS judging.
 *
 * <p>
 * {@link io.github.markpollack.judge.ai.requirements.Rfc2119Requirement} and
 * {@link io.github.markpollack.judge.ai.requirements.EarsRequirement} retain the actual
 * identity, revision, source and native specification. They contain no execution or
 * policy configuration. The corresponding public Judges assess one requirement and return
 * its associated Judgment from {@code judge()}.
 *
 * <h2>Explicit roster coverage</h2>
 *
 * <p>
 * {@link io.github.markpollack.judge.ai.requirements.Rfc2119Jury} and
 * {@link io.github.markpollack.judge.ai.requirements.EarsJury} assess an ordered,
 * nonempty roster and return a complete Verdict from {@code vote()}. A generated
 * investigative execution sends the entire roster once. Its root owns the native
 * invocation; every declared item retains its original Judgment and references that
 * invocation. A typed structured runtime executes separately for each item and retains
 * every native execution.
 *
 * <p>
 * A reliably identity-bound violation establishes FAIL despite a missing or failed
 * sibling. All satisfied plus a missing item is INCONCLUSIVE. Unknown or duplicate
 * response identities invalidate envelope binding: the native answer and protocol failure
 * remain retained, without promoting an unbound apparent violation. No synthetic parent
 * Requirement or root voting opinions are created. Roster descriptions declare KNOWN_NONE
 * routing opinions; callers can route on the derived conclusion.
 *
 * <h2>Original answers and applicability</h2>
 *
 * <p>
 * A declared applicability condition and a supplied reason authorize an exclusion. An
 * undeclared or unjustified NOT_APPLICABLE answer remains the original answer with a
 * separate ERROR treatment. It is never rewritten into a finding or a violation. Checks,
 * observations, native answers, available usage and failures survive classification and
 * storage. A policy consumes the complete Verdict independently of judging and routing.
 *
 * <p>
 * Construction validates coverage and supported input modes before native execution.
 * Prepared evidence and evidence acquisition belong to typed construction. Integrated
 * investigation needs no invented evidence bundle. Cancellation and interruption
 * propagate through single and roster judging. Pure native requirement reconstruction for
 * current portable results is registered by
 * {@link io.github.markpollack.judge.ai.requirements.NativeRequirementCodecs}.
 *
 * @author Mark Pollack
 * @since 0.16.0
 */
package io.github.markpollack.judge.ai.requirements;
