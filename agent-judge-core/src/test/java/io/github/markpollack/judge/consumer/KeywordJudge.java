/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.consumer;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.completion.CompletionEvidence;
import io.github.markpollack.judge.judgment.Judgment;

/**
 * A judge class declared by a consumer, outside every library package. It declares
 * neither metadata nor configuration.
 */
public class KeywordJudge implements Judge {

	private final String keyword;

	private final CompletionEvidence context;

	public KeywordJudge(CompletionEvidence context, String keyword) {
		this.context = context;
		this.keyword = keyword;
	}

	@Override
	public Judgment judge() {
		boolean found = java.util.Optional.ofNullable(context.response()).orElse("").contains(this.keyword);
		return Judgment.verdict(found).reasoning("looked for '" + this.keyword + "'").build();
	}

}
