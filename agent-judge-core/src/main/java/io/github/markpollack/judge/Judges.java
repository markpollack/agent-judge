/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.github.markpollack.judge.portable.ImplementationIdentity;

import io.github.markpollack.judge.judgment.Judgment;

/**
 * Utility class for creating and composing judges.
 *
 * <p>
 * Provides factory methods for common judge operations:
 * </p>
 * <ul>
 * <li>Wrapping lambda judges with metadata via {@link NamedJudge}</li>
 * <li>Creating simple pass/fail judges</li>
 * <li>Extracting metadata from judges</li>
 * </ul>
 *
 * <h2>The combinators are Boolean, and that is deliberate</h2>
 * <p>
 * {@link #and}, {@link #or}, {@link #allOf} and {@link #anyOf} branch on
 * {@link Judgment#pass()}, so they reason about two outcomes: passed, and did not pass.
 * They are short-circuit Boolean composition and nothing more.
 * </p>
 * <p>
 * A judgment carries five statuses, and these combinators do not distinguish the other
 * four. {@code FAIL}, {@code ABSTAIN}, {@code NOT_APPLICABLE} and {@code ERROR} are all
 * "not passed" here. What each combinator then does with such a judgment is worth reading
 * before composing anything that can produce one:
 * </p>
 * <table border="1">
 * <caption>What each combinator returns, for any non-PASS X</caption>
 * <tr>
 * <th>Combinator</th>
 * <th>Result</th>
 * </tr>
 * <tr>
 * <td>{@code and(a, b)}</td>
 * <td>{@code a} <b>unchanged</b> unless it passes; otherwise {@code b}</td>
 * </tr>
 * <tr>
 * <td>{@code allOf(...)}</td>
 * <td>the first non-PASS judgment <b>unchanged</b>; if all pass, a fabricated
 * {@code PASS("All checks passed")}</td>
 * </tr>
 * <tr>
 * <td>{@code or(a, b)}</td>
 * <td>{@code a} if it passes; otherwise {@code b} <b>unchanged</b>, whatever {@code b}
 * is</td>
 * </tr>
 * <tr>
 * <td>{@code anyOf(...)}</td>
 * <td>the first passing judgment; if none passes, a <b>fabricated</b>
 * {@code FAIL("All checks failed")}</td>
 * </tr>
 * </table>
 * <p>
 * The last row is the one that surprises people. {@code anyOf} manufactures a
 * {@code FAIL} even when every judge abstained, excluded itself, or errored — so a
 * composition that never established anything about the subject reports a rejection of
 * it. {@code or} does not: it returns whatever the second judge said, including an
 * {@code ERROR}.
 * </p>
 * <p>
 * The same applies to {@code allOf} and {@code and}, which short-circuit on the first
 * non-PASS judgment, so later judges do not run.
 * </p>
 * <p>
 * ⚠️ If any of your judges can abstain, exclude a subject, or error, <b>do not compose
 * them here.</b> These combinators bypass the seat guard as well: a judge reached through
 * a combinator returns {@code NOT_APPLICABLE} directly to the caller, with nothing
 * checking that it declared it may. Use a {@link io.github.markpollack.judge.jury.Jury}
 * with an explicit {@link io.github.markpollack.judge.voting.ErrorHandling}: a jury
 * resolves the population by status, publishes what it actually reduced over in its
 * aggregation evidence, and
 * {@link io.github.markpollack.judge.voting.AllEligiblePassStrategy} expresses "every
 * applicable judge must pass" without collapsing an abstention into a negative finding.
 * </p>
 * <p>
 * This behaviour is pinned by tests rather than changed. Widening the combinators to be
 * status-aware would alter the outcome of every existing composition, and the jury API
 * already covers the case properly.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 * Executable examples are maintained in the Agent Judge Tutorial:
 * https://github.com/markpollack/agent-judge-tutorial.
 *
 * @author Mark Pollack
 * @since 0.1.0
 */
public final class Judges {

	private Judges() {
		// Utility class
	}

	/**
	 * Wrap a judge with a custom name.
	 * <p>
	 * Useful for lambda judges that need identifiable names for logging, monitoring, or
	 * display purposes.
	 * </p>
	 * @param judge the judge to wrap
	 * @param name the judge name; must be non-blank, since a jury stores the judgment
	 * under it
	 * @return named judge with metadata
	 * @throws IllegalArgumentException if the name is blank
	 */
	public static NamedJudge named(Judge judge, String name) {
		return named(judge, name, null, JudgeType.DETERMINISTIC);
	}

	/**
	 * Wrap a judge with name and description.
	 * @param judge the judge to wrap
	 * @param name the judge name; must be non-blank, since a jury stores the judgment
	 * under it
	 * @param description the judge description
	 * @return named judge with metadata
	 * @throws IllegalArgumentException if the name is blank
	 */
	public static NamedJudge named(Judge judge, String name, String description) {
		return named(judge, name, description, JudgeType.DETERMINISTIC);
	}

	/**
	 * Wrap a judge with complete metadata.
	 * @param judge the judge to wrap
	 * @param name the judge name; must be non-blank, since a jury stores the judgment
	 * under it
	 * @param description the judge description
	 * @param type the judge type
	 * @return named judge with metadata
	 * @throws IllegalArgumentException if the name is blank
	 */
	public static NamedJudge named(Judge judge, String name, String description, JudgeType type) {
		// Absence, deliberately: a wrapper that manufactured a capability would let any
		// judge
		// exclude a criterion simply by being renamed.
		return new NamedJudge(judge, new JudgeMetadata(name, description, type, null));
	}

	/**
	 * Create a judge that always passes with the given reasoning.
	 * @param reasoning the reasoning to include in judgment
	 * @return judge that always passes
	 */
	public static Judge alwaysPass(String reasoning) {
		return () -> Judgment.pass(reasoning);
	}

	/**
	 * Create a judge that always fails with the given reasoning.
	 * @param reasoning the reasoning to include in judgment
	 * @return judge that always fails
	 */
	public static Judge alwaysFail(String reasoning) {
		return () -> Judgment.fail(reasoning);
	}

	/**
	 * Attempt to extract metadata from a judge.
	 * <p>
	 * Returns metadata if the judge implements {@link JudgeWithMetadata}, otherwise
	 * returns empty. This uses Spring's marker interface pattern for semantic type
	 * checking. Useful for infrastructure code that needs to log or display judge
	 * information.
	 * </p>
	 * @param judge the judge to extract metadata from
	 * @return metadata if available, otherwise empty
	 */
	public static Optional<JudgeMetadata> tryMetadata(Judge judge) {
		return (judge instanceof JudgeWithMetadata jwm) ? Optional.of(jwm.metadata()) : Optional.empty();
	}

	/**
	 * Read a judge's declared exclusion capability, looking through {@link NamedJudge}
	 * wrappers.
	 * <p>
	 * This is the one lookup. {@link io.github.markpollack.judge.jury.Jury#describe()},
	 * jury construction and the seat guard all read the capability through it, so a judge
	 * cannot be described as incapable and then be honoured as capable, or the reverse.
	 * </p>
	 * <p>
	 * The chain is walked from the outside in, and the first declaration present wins. A
	 * wrapper that declares nothing is transparent — which is what keeps a deduplicating
	 * rename from stripping the capability off the judge underneath — and a wrapper that
	 * declares something overrides what it wraps, because the outer declaration is the
	 * one the jury seated. If the outer condition differs from the inner one, the outer
	 * is the effective claim; the difference is an author's assertion worth inspecting
	 * rather than something this method can adjudicate.
	 * </p>
	 * <p>
	 * Absence is not a blank declaration. A judge that says nothing never excludes, and a
	 * judge that tries to say nothing in a non-empty way is refused when its metadata is
	 * constructed.
	 * </p>
	 * @param judge the judge
	 * @return the condition under which the judge may return {@code NOT_APPLICABLE}, or
	 * empty when it declares none
	 * @throws IllegalArgumentException if a judge in the chain is a
	 * {@link JudgeWithMetadata} whose {@code metadata()} returns null or throws, since
	 * reading that as absence would silently turn an unreadable judge into an incapable
	 * one
	 *
	 * @since 0.17.0
	 */
	public static Optional<String> notApplicableCapability(Judge judge) {
		Objects.requireNonNull(judge, "judge must not be null");
		Judge current = judge;
		while (true) {
			JudgeMetadata metadata = readableMetadataOf(current,
					"Judge implemented by " + ImplementationIdentity.of(current.getClass()).toPortable());
			if (metadata != null && metadata.notApplicableWhen() != null) {
				return Optional.of(metadata.notApplicableWhen());
			}
			if (!(current instanceof NamedJudge wrapper)) {
				return Optional.empty();
			}
			current = Objects.requireNonNull(wrapper.delegate(), "a NamedJudge must wrap a judge");
		}
	}

	/**
	 * Read a judge's metadata for a description.
	 * @param judge the judge
	 * @param subject how to name the judge if its metadata cannot be read
	 * @return its metadata, or null when the judge does not implement
	 * {@link JudgeWithMetadata}
	 * @throws IllegalArgumentException if {@code metadata()} returns null or throws
	 */
	public static JudgeMetadata readableMetadataOf(Judge judge, String subject) {
		if (!(judge instanceof JudgeWithMetadata withMetadata)) {
			return null;
		}
		JudgeMetadata metadata;
		try {
			metadata = withMetadata.metadata();
		}
		catch (Exception ex) {
			io.github.markpollack.judge.portable.PreservationLimitException.propagate(ex);
			String message = ex.getMessage();
			throw new IllegalArgumentException(subject + " cannot be described: metadata() threw "
					+ ex.getClass().getName() + ((message == null || message.isBlank()) ? "" : ": " + message), ex);
		}
		if (metadata == null) {
			throw new IllegalArgumentException(subject + " cannot be described: metadata() returned null");
		}
		return metadata;
	}

	/**
	 * Compose two judges with AND logic.
	 * <p>
	 * Returns a judge that executes the first judge, and only if it passes, executes the
	 * second judge. If the first does not pass — for any reason, including an abstention,
	 * an exclusion or an error — its judgment is returned unchanged and immediately
	 * (short-circuit evaluation). This is analogous to Spring Security's CompositeVoter
	 * or JUnit's RuleChain pattern.
	 * </p>
	 * <p>
	 * Example usage:
	 * </p>
	 * See the Agent Judge Tutorial for compiled composition examples.
	 * @param first the first judge to execute
	 * @param second the second judge to execute (only if first passes)
	 * @return composed judge with AND logic
	 */
	public static Judge and(Judge first, Judge second) {
		return () -> {
			Judgment firstResult = first.judge();
			return firstResult.pass() ? second.judge() : firstResult;
		};
	}

	/**
	 * Compose two judges with OR logic.
	 * <p>
	 * Returns a judge that executes the first judge, and only if it <em>does not
	 * pass</em>, executes the second. That is a wider condition than "fails": an
	 * abstention, an exclusion and an error all reach the second judge too, and whatever
	 * it returns is then the result, unchanged. If the first passes, its judgment is
	 * returned immediately (short-circuit evaluation).
	 * </p>
	 * <p>
	 * Example usage:
	 * </p>
	 * See the Agent Judge Tutorial for compiled composition examples.
	 * @param first the first judge to execute
	 * @param second the second judge to execute (only if the first does not pass)
	 * @return composed judge with OR logic
	 */
	public static Judge or(Judge first, Judge second) {
		return () -> {
			Judgment firstResult = first.judge();
			return firstResult.pass() ? firstResult : second.judge();
		};
	}

	/**
	 * Compose multiple judges with AND logic (all must pass).
	 * <p>
	 * Returns a judge that executes all judges in sequence. If any judge fails, its
	 * judgment is returned immediately (short-circuit evaluation). If all judges pass, a
	 * passing judgment is returned. This is analogous to Stream.allMatch().
	 * </p>
	 * <p>
	 * Example usage:
	 * </p>
	 * See the Agent Judge Tutorial for compiled composition examples.
	 * @param judges the judges to compose (varargs)
	 * @return composed judge with AND logic
	 */
	public static Judge allOf(Judge... judges) {
		return () -> {
			for (Judge judge : judges) {
				Judgment judgment = judge.judge();
				if (!judgment.pass()) {
					return judgment;
				}
			}
			return Judgment.pass("All checks passed");
		};
	}

	/**
	 * Compose multiple judges with OR logic (any must pass).
	 * <p>
	 * Returns a judge that executes all judges in sequence. If any judge passes, its
	 * judgment is returned immediately (short-circuit evaluation). If none passes, a
	 * {@code FAIL} is <em>fabricated</em> — including when every judge abstained,
	 * excluded the subject, or errored, so a composition that established nothing reports
	 * a rejection. This is analogous to Stream.anyMatch(), and inherits its vacuous case.
	 * </p>
	 * <p>
	 * Example usage:
	 * </p>
	 * See the Agent Judge Tutorial for compiled composition examples.
	 * @param judges the judges to compose (varargs)
	 * @return composed judge with OR logic
	 */
	public static Judge anyOf(Judge... judges) {
		return () -> {
			for (Judge judge : judges) {
				Judgment judgment = judge.judge();
				if (judgment.pass()) {
					return judgment;
				}
			}
			return Judgment.fail("All checks failed");
		};
	}

}
