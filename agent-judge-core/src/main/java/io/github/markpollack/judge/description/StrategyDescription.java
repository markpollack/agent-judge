/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.description;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.jspecify.annotations.Nullable;

import io.github.markpollack.judge.jury.ErrorPolicy;
import io.github.markpollack.judge.jury.NotApplicablePolicy;
import io.github.markpollack.judge.jury.VotingStrategy;

/**
 * A voting strategy as configured: what it is called, which class implements it, and the
 * parameters that decide how it aggregates.
 *
 * <p>
 * Obtained from {@link VotingStrategy#describe()}. Every built-in strategy declares its
 * {@link ErrorPolicy}, its threshold where it has one, and any other parameter such as
 * majority's tie policy. A strategy that does not override {@code describe()} is
 * {@linkplain #undeclared(VotingStrategy) undeclared}: its name and implementation are known,
 * its parameters are not.
 * </p>
 *
 * <p>
 * A subclass of a built-in strategy inherits the built-in's declaration. Its implementation
 * identity still names the subclass, so a reader can see that the declaration was inherited.
 * </p>
 *
 * <h2>Portable form</h2>
 * <pre>
 * {
 *   "descriptionVersion": 1,
 *   "name": "majority",
 *   "implementation": {"form": "NAMED", "className": "..."},
 *   "parameters": {"declared": true, "values": {"errorPolicy": "propagate",
 *                    "notApplicablePolicy": "refuse", "tiePolicy": "FAIL"}}
 * }
 * </pre>
 * <p>
 * Declared {@code values} hold {@code errorPolicy} and {@code notApplicablePolicy} (their
 * {@linkplain ErrorPolicy#token() tokens}) and {@code threshold} when present, together with
 * the other parameters, in ascending key order. A strategy that has no threshold has no
 * {@code threshold} key: the declared map is the whole declaration. An undeclared strategy
 * carries {@code "parameters": {"declared": false}}.
 * </p>
 * <p>
 * {@code notApplicablePolicy} is what makes a jury's
 * {@code aggregateMayBeNotApplicable} derivable from its description alone: a reader holding
 * only the portable form can tell whether this strategy would honour an exclusion, without
 * having to run the jury to find out.
 * </p>
 *
 * @param name the strategy's {@link VotingStrategy#getName() name}
 * @param implementation the class that implements the strategy
 * @param errorPolicy the declared error policy, or null when undeclared or when the strategy
 * has none
 * @param notApplicablePolicy the declared not-applicable policy, or null when undeclared
 * @param threshold the declared normalized threshold, or null when undeclared or when the
 * strategy has none
 * @param parameters the other declared parameters, ordered by key; null when the strategy
 * declared nothing, empty when it declared no parameters beyond its error policy and threshold
 * @author Mark Pollack
 * @since 0.17.0
 */
public record StrategyDescription(String name, ImplementationIdentity implementation, @Nullable ErrorPolicy errorPolicy,
		@Nullable NotApplicablePolicy notApplicablePolicy, @Nullable Double threshold,
		@Nullable Map<String, Object> parameters) {

	private static final String ERROR_POLICY = "errorPolicy";

	private static final String NOT_APPLICABLE_POLICY = "notApplicablePolicy";

	private static final String THRESHOLD = "threshold";

	/**
	 * Validate the declaration and freeze its parameters.
	 * @throws IllegalArgumentException if an error policy or threshold is given without
	 * declared parameters, if the threshold is not finite, if the parameters use the reserved
	 * {@code errorPolicy} or {@code threshold} keys, or if a parameter is not portable
	 */
	public StrategyDescription {
		Objects.requireNonNull(name, "name must not be null");
		Objects.requireNonNull(implementation, "implementation must not be null");
		if (parameters == null) {
			if (errorPolicy != null || notApplicablePolicy != null || threshold != null) {
				throw new IllegalArgumentException("A strategy that declares a policy or a threshold has "
						+ "declared its parameters; pass an empty parameter map rather than null");
			}
		}
		else {
			if (parameters.containsKey(ERROR_POLICY) || parameters.containsKey(NOT_APPLICABLE_POLICY)
					|| parameters.containsKey(THRESHOLD)) {
				throw new IllegalArgumentException("'errorPolicy', 'notApplicablePolicy' and 'threshold' are declared "
						+ "through their own components, not as parameters");
			}
			parameters = PortableForm.ordered(parameters, "parameters");
		}
		if (threshold != null && !Double.isFinite(threshold)) {
			throw new IllegalArgumentException("threshold must be finite, but was " + threshold);
		}
	}

	/**
	 * Describe a strategy that declares nothing about how it aggregates.
	 * @param strategy the strategy
	 * @return a description carrying the strategy's name and implementation only
	 */
	public static StrategyDescription undeclared(VotingStrategy strategy) {
		Objects.requireNonNull(strategy, "strategy must not be null");
		return new StrategyDescription(strategy.getName(), ImplementationIdentity.of(strategy.getClass()), null, null,
				null, null);
	}

	/**
	 * Describe a strategy that declares its parameters.
	 * @param strategy the strategy
	 * @param errorPolicy its error policy, or null when it has none
	 * @param notApplicablePolicy its not-applicable policy, or null when it has none
	 * @param threshold its normalized threshold, or null when it has none
	 * @param parameters any other parameters; empty when there are none
	 * @return a declared description
	 * @since 0.17.0
	 */
	public static StrategyDescription declared(VotingStrategy strategy, @Nullable ErrorPolicy errorPolicy,
			@Nullable NotApplicablePolicy notApplicablePolicy, @Nullable Double threshold,
			Map<String, Object> parameters) {
		Objects.requireNonNull(strategy, "strategy must not be null");
		Objects.requireNonNull(parameters, "parameters must not be null");
		return new StrategyDescription(strategy.getName(), ImplementationIdentity.of(strategy.getClass()), errorPolicy,
				notApplicablePolicy, threshold, parameters);
	}

	/**
	 * The portable form described in the class documentation.
	 * @return an ordered, validated, immutable map
	 */
	public Map<String, Object> toPortable() {
		return PortableForm.freezeRoot(portableTree(), "strategy");
	}

	Map<String, Object> portableTree() {
		Map<String, Object> tree = new LinkedHashMap<>();
		tree.put("name", name);
		tree.put("implementation", implementation.portableTree());
		Map<String, Object> declared = parameters;
		if (declared == null) {
			tree.put("parameters", PortableForm.undeclared());
		}
		else {
			Map<String, Object> values = new TreeMap<>(declared);
			ErrorPolicy policy = errorPolicy;
			if (policy != null) {
				values.put(ERROR_POLICY, policy.token());
			}
			NotApplicablePolicy exclusions = notApplicablePolicy;
			if (exclusions != null) {
				values.put(NOT_APPLICABLE_POLICY, exclusions.token());
			}
			Double bar = threshold;
			if (bar != null) {
				values.put(THRESHOLD, bar);
			}
			tree.put("parameters", PortableForm.declaredValues(new LinkedHashMap<>(values)));
		}
		return tree;
	}

}
