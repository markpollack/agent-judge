/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.voting;

import java.util.*;

/**
 * Explicit pure built-in reconstruction; callers register additional trusted factories on
 * each codec. No stored implementation class is loaded.
 */
public final class VotingRules {

	private VotingRules() {
	}

	/**
	 * The library's fixed built-in reconstruction vocabulary.
	 * @return immutable token/factory map
	 */
	public static Map<String, VotingRuleFactory> builtIns() {
		return Map.of("allMustPass", q -> new AllEligiblePassStrategy(errors(q), exclusions(q)), "consensus",
				q -> new ConsensusStrategy(errors(q), exclusions(q)), "majority",
				q -> new MajorityVotingStrategy(TieBreakRule.valueOf(text(q, "tiePolicy")), errors(q), exclusions(q)),
				"average", q -> new AverageVotingStrategy(threshold(q), errors(q), exclusions(q)), "median",
				q -> new MedianVotingStrategy(threshold(q), errors(q), exclusions(q)), "weightedAverage",
				q -> new WeightedAverageStrategy(threshold(q), errors(q), exclusions(q)), "conjunctive",
				q -> new ConjunctiveStrategy(threshold(q), errors(q), exclusions(q)));
	}

	/**
	 * Reopen through an explicitly trusted factory and verify the full declaration.
	 * @param token stable rule token
	 * @param configuration complete portable settings
	 * @param factories explicitly trusted reconstruction vocabulary
	 * @return immutable reconstructed rule
	 */
	public static RetainedRule reconstruct(String token, Map<String, Object> configuration,
			Map<String, VotingRuleFactory> factories) {
		var canonical = io.github.markpollack.judge.portable.PortableForm.ordered(configuration, "rule.configuration");
		var factory = factories.get(token);
		if (factory == null)
			throw new IllegalArgumentException("Unknown voting rule token: " + token);
		var retained = RetainedRule.of(factory.reconstruct(canonical));
		if (!token.equals(retained.token()) || !canonical.equals(retained.configuration()))
			throw new IllegalArgumentException(
					"Voting rule reconstruction contradicts retained configuration: " + token);
		return retained;
	}

	private static String text(Map<String, Object> q, String key) {
		if (!(q.get(key) instanceof String value))
			throw new IllegalArgumentException("Missing rule configuration: " + key);
		return value;
	}

	private static ErrorHandling errors(Map<String, Object> q) {
		return Arrays.stream(ErrorHandling.values())
			.filter(x -> x.token().equals(text(q, "errorPolicy")))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Unknown error policy"));
	}

	private static ExclusionHandling exclusions(Map<String, Object> q) {
		return Arrays.stream(ExclusionHandling.values())
			.filter(x -> x.token().equals(text(q, "notApplicablePolicy")))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Unknown exclusion policy"));
	}

	private static double threshold(Map<String, Object> q) {
		if (!(q.get("threshold") instanceof Number number))
			throw new IllegalArgumentException("Missing threshold");
		return number.doubleValue();
	}

}
