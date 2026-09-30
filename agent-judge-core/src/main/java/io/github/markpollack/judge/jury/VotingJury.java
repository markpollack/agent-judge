/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.jury;

import java.util.List;
import io.github.markpollack.judge.Judge;

/**
 * A jury that reduces opinions with a declared voting strategy.
 *
 * @param <E> evidence type
 */
public interface VotingJury<E> extends Jury<E> {

	/**
	 * Configured direct judges; meta-juries describe their members separately.
	 * @return direct judges
	 */
	List<Judge<E>> getJudges();

	/**
	 * Declared voting configuration.
	 * @return non-null strategy
	 */
	VotingStrategy getVotingStrategy();

}
