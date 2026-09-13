/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.consumer;

import java.util.Map;

import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.description.ConfiguredJudge;
import io.github.markpollack.judge.result.Judgment;

/**
 * A consumer judge that opts in to declaring configuration, returning whatever map it was
 * given — including, in tests, maps a description must refuse.
 */
public class DeclaringJudge implements ConfiguredJudge {

	private final Map<String, Object> configuration;

	public DeclaringJudge(Map<String, Object> configuration) {
		this.configuration = configuration;
	}

	@Override
	public Map<String, Object> configuration() {
		return this.configuration;
	}

	@Override
	public Judgment judge(JudgmentContext context) {
		return Judgment.pass("declared");
	}

}
