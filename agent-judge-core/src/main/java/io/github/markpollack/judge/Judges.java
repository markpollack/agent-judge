/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.github.markpollack.judge.description.ConfiguredJudge;
import io.github.markpollack.judge.description.ImplementationIdentity;
import io.github.markpollack.judge.description.JudgeDescription;
import io.github.markpollack.judge.result.Judgment;

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
 * A judgment carries four statuses, and these combinators do not distinguish the other
 * two. {@code ABSTAIN} and {@code ERROR} are "not passed" here, which has two consequences
 * worth knowing before you use them:
 * </p>
 * <ul>
 * <li>{@code allOf} and {@code and} short-circuit on an abstaining or errored judge and
 * return that judgment, so later judges do not run;</li>
 * <li>{@code anyOf} and {@code or} return a {@code FAIL} when no judge passed, including
 * when every judge <em>abstained</em>.</li>
 * </ul>
 * <p>
 * ⚠️ If any of your judges can abstain or error, <b>do not compose them here.</b> Use a
 * {@link io.github.markpollack.judge.jury.Jury} with an explicit
 * {@link io.github.markpollack.judge.jury.ErrorPolicy}: a jury resolves the population by
 * status, publishes what it actually reduced over in its aggregation evidence, and
 * {@link io.github.markpollack.judge.jury.AllMustPassStrategy} expresses "every applicable
 * judge must pass" without collapsing an abstention into a negative finding.
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
 * Executable examples are maintained in the Agent Judge Tutorial: https://github.com/markpollack/agent-judge-tutorial.
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
	 * @param name the judge name
	 * @return named judge with metadata
	 */
	public static NamedJudge named(Judge judge, String name) {
		return named(judge, name, null, JudgeType.DETERMINISTIC);
	}

	/**
	 * Wrap a judge with name and description.
	 * @param judge the judge to wrap
	 * @param name the judge name
	 * @param description the judge description
	 * @return named judge with metadata
	 */
	public static NamedJudge named(Judge judge, String name, String description) {
		return named(judge, name, description, JudgeType.DETERMINISTIC);
	}

	/**
	 * Wrap a judge with complete metadata.
	 * @param judge the judge to wrap
	 * @param name the judge name
	 * @param description the judge description
	 * @param type the judge type
	 * @return named judge with metadata
	 */
	public static NamedJudge named(Judge judge, String name, String description, JudgeType type) {
		return new NamedJudge(judge, new JudgeMetadata(name, description, type));
	}

	/**
	 * Create a judge that always passes with the given reasoning.
	 * @param reasoning the reasoning to include in judgment
	 * @return judge that always passes
	 */
	public static Judge alwaysPass(String reasoning) {
		return ctx -> Judgment.pass(reasoning);
	}

	/**
	 * Create a judge that always fails with the given reasoning.
	 * @param reasoning the reasoning to include in judgment
	 * @return judge that always fails
	 */
	public static Judge alwaysFail(String reasoning) {
		return ctx -> Judgment.fail(reasoning);
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
	 * Describe a judge as configured, looking through {@link NamedJudge} wrappers.
	 * <p>
	 * The description carries the outer metadata, which names the judge in a verdict, and the
	 * metadata of the judge the outer wrapper wraps directly, which can differ:
	 * {@code Juries.fromJudges} re-wraps a judge whose name collides as {@code DETERMINISTIC},
	 * whatever its real type, and the wrapped judge's metadata still states that type. The
	 * implementation is the innermost judge that is not a {@code NamedJudge}, identified by
	 * {@link ImplementationIdentity#of(Class)}, so a lambda, including every combinator in
	 * this class, is described as {@code HIDDEN} with no class name. The configuration is that
	 * judge's {@link ConfiguredJudge#configuration()}, or undeclared when it does not
	 * implement {@link ConfiguredJudge}.
	 * </p>
	 * @param judge the judge to describe
	 * @return its description
	 * @throws IllegalArgumentException if the judge declares a configuration that is not
	 * portable; the message names the judge and the path of the offending value
	 * @since 0.17.0
	 */
	public static JudgeDescription describe(Judge judge) {
		Objects.requireNonNull(judge, "judge must not be null");
		JudgeMetadata outer = metadataOf(judge);
		Judge innermost = judge;
		while (innermost instanceof NamedJudge named) {
			innermost = Objects.requireNonNull(named.delegate(), "a NamedJudge must wrap a judge");
		}
		// The delegate metadata is what the directly wrapped judge declares. For the NamedJudge
		// around a NamedJudge that Juries.fromJudges builds for a duplicate name, that is the
		// caller's own label and type, not the innermost implementation's (usually none).
		JudgeMetadata inner = (judge instanceof NamedJudge wrapper) ? metadataOf(wrapper.delegate()) : null;
		ImplementationIdentity implementation = ImplementationIdentity.of(innermost.getClass());
		Map<String, Object> configuration = null;
		if (innermost instanceof ConfiguredJudge configured) {
			configuration = configured.configuration();
			if (configuration == null) {
				throw new NullPointerException("ConfiguredJudge " + implementation.toPortable()
						+ " returned a null configuration; return an empty map to declare no values");
			}
		}
		try {
			return new JudgeDescription(outer == null ? null : outer.name(), outer == null ? null : outer.type(),
					inner == null ? null : inner.name(), inner == null ? null : inner.type(), implementation,
					configuration);
		}
		catch (IllegalArgumentException ex) {
			String label = (outer != null && outer.name() != null) ? "'" + outer.name() + "'"
					: "implemented by " + implementation.toPortable();
			throw new IllegalArgumentException(
					"Judge " + label + " declared a configuration that is not portable: " + ex.getMessage(), ex);
		}
	}

	private static JudgeMetadata metadataOf(Judge judge) {
		return (judge instanceof JudgeWithMetadata withMetadata) ? withMetadata.metadata() : null;
	}

	/**
	 * Compose two judges with AND logic.
	 * <p>
	 * Returns a judge that executes the first judge, and only if it passes, executes the
	 * second judge. If the first fails, its judgment is returned immediately
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
		return ctx -> {
			Judgment firstResult = first.judge(ctx);
			return firstResult.pass() ? second.judge(ctx) : firstResult;
		};
	}

	/**
	 * Compose two judges with OR logic.
	 * <p>
	 * Returns a judge that executes the first judge, and only if it fails, executes the
	 * second judge. If the first passes, its judgment is returned immediately
	 * (short-circuit evaluation).
	 * </p>
	 * <p>
	 * Example usage:
	 * </p>
	 * See the Agent Judge Tutorial for compiled composition examples.
	 * @param first the first judge to execute
	 * @param second the second judge to execute (only if first fails)
	 * @return composed judge with OR logic
	 */
	public static Judge or(Judge first, Judge second) {
		return ctx -> {
			Judgment firstResult = first.judge(ctx);
			return firstResult.pass() ? firstResult : second.judge(ctx);
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
		return ctx -> {
			for (Judge judge : judges) {
				Judgment judgment = judge.judge(ctx);
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
	 * judgment is returned immediately (short-circuit evaluation). If all judges fail, a
	 * failing judgment is returned. This is analogous to Stream.anyMatch().
	 * </p>
	 * <p>
	 * Example usage:
	 * </p>
	 * See the Agent Judge Tutorial for compiled composition examples.
	 * @param judges the judges to compose (varargs)
	 * @return composed judge with OR logic
	 */
	public static Judge anyOf(Judge... judges) {
		return ctx -> {
			for (Judge judge : judges) {
				Judgment judgment = judge.judge(ctx);
				if (judgment.pass()) {
					return judgment;
				}
			}
			return Judgment.fail("All checks failed");
		};
	}

}
