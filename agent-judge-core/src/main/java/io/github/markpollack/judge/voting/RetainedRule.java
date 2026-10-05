/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.voting;

import java.util.*;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.portable.PortableForm;

/**
 * Immutable rule identity/configuration bound to a pure implementation. Implementations
 * must be immutable and deterministic, with all behavior-affecting settings declared.
 * Validation invokes this same rule, never a guessed rule inferred from its result.
 */
public final class RetainedRule {

	private final String token;

	private final Map<String, Object> configuration;

	private final VotingStrategy implementation;

	private RetainedRule(VotingStrategy rule) {
		implementation = Objects.requireNonNull(rule);
		token = io.github.markpollack.judge.portable.ValueRequirements.text(rule.getName(), "rule token");
		configuration = PortableForm.ordered(rule.configuration(), "rule.configuration");
	}

	/**
	 * Retain the complete immutable declaration before execution.
	 * @param rule pure configured rule
	 * @return bound immutable rule value
	 */
	public static RetainedRule of(VotingStrategy rule) {
		return new RetainedRule(rule);
	}

	/**
	 * Stable rule identity.
	 * @return trusted reconstruction token
	 */
	public String token() {
		return token;
	}

	/**
	 * Complete portable configuration bytes are carried inline by the codec.
	 * @return immutable canonical configuration
	 */
	public Map<String, Object> configuration() {
		return configuration;
	}

	/**
	 * Apply exactly the retained rule without invoking producers or external services.
	 * @param ballots typed inputs
	 * @return deterministic aggregate
	 */
	public Judgment aggregate(List<Ballot> ballots) {
		if (!token.equals(implementation.getName())
				|| !configuration.equals(PortableForm.ordered(implementation.configuration(), "rule.configuration")))
			throw new IllegalArgumentException("Voting rule changed after retention");
		return implementation.aggregate(List.copyOf(ballots));
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof RetainedRule rule && token.equals(rule.token)
				&& configuration.equals(rule.configuration);
	}

	@Override
	public int hashCode() {
		return Objects.hash(token, configuration);
	}

}
