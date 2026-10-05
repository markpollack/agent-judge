/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

/**
 * Execution and construction of configured judging compositions. SimpleJury executes each
 * configured seat once and binds typed ballots to the retained reduction rule. MetaJury
 * reduces member aggregates; Assignments checks explicit AllOf coverage; native rosters
 * retain ordered independent requirements and shared observations; CascadedJury records
 * entered tiers and routing separately from policy reliance. Ordinary execution failures
 * are contained with their originals. Cancellation, fatal errors and
 * PreservationLimitException propagate, so bounds never replace complete available
 * originals with generic failure records.
 * @see io.github.markpollack.judge.jury.SimpleJury
 * @see io.github.markpollack.judge.jury.CascadedJury
 * @see io.github.markpollack.judge.portable.PreservationLimitException
 */
package io.github.markpollack.judge.jury;
