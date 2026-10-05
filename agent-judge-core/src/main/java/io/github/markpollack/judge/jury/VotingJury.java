/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.jury;
import io.github.markpollack.judge.voting.VotingStrategy;

import java.util.List;
import io.github.markpollack.judge.Judge;

/**
 * A jury that reduces opinions with a declared voting strategy.
 *
 */
public interface VotingJury extends Jury {

	/**
	 * Begins ordinary ready-member assembly.
	 * @return composition builder
	 */
	static SimpleJury.Builder builder() {
		return SimpleJury.builder();
	}

	/**
	 * Configured direct judges; meta-juries describe their members separately.
	 * @return direct judges
	 */
	List<Judge> getJudges();

	/**
	 * Declared voting configuration.
	 * @return non-null strategy
	 */
	VotingStrategy getVotingStrategy();

}
