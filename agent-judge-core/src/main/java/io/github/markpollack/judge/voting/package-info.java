/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

/**
 * Pure deterministic reduction over typed Ballot inputs. Each ballot retains its original
 * configured position, label, complete producer opinion, separate treatment and optional
 * declared weight. Absence means effective 1.0; explicit weights are finite and positive.
 * Unweighted rules retain and ignore declarations. Population policy resolution and
 * universal evidence are public contracts for custom rules. RetainedRule binds complete
 * immutable identity/configuration before execution; VotingRuleFactory reconstructs it
 * only through explicitly trusted registrations. Rules must never invoke producers,
 * suppliers, models or external services.
 * @see io.github.markpollack.judge.voting.Ballot
 * @see io.github.markpollack.judge.voting.AggregationPopulation
 * @see io.github.markpollack.judge.voting.RetainedRule
 */
package io.github.markpollack.judge.voting;
