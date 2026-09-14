/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.consumer;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.Judgment;

/**
 * A judge class declared by a consumer, outside every library package. It declares neither
 * metadata nor configuration.
 */
public class KeywordJudge implements Judge {

	private final String keyword;

	public KeywordJudge(String keyword) {
		this.keyword = keyword;
	}

	@Override
	public Judgment judge(JudgmentContext context) {
		boolean found = context.agentOutput().orElse("").contains(this.keyword);
		return Judgment.verdict(found).reasoning("looked for '" + this.keyword + "'").build();
	}

}
