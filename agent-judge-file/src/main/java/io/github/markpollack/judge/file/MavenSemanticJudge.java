/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.file;

import io.github.markpollack.judge.file.comparator.MavenSemanticComparator;

/**
 * Judge that compares a single pom.xml pair using Maven semantic comparison.
 */
public class MavenSemanticJudge extends SemanticFileJudge {

	private final MavenSemanticComparator comparator = new MavenSemanticComparator();

	/**
	 * Create a Maven semantic judge.
	 * @param source fresh evidence acquisition provider, invoked once per direct
	 * execution
	 */
	public MavenSemanticJudge(java.util.function.Supplier<? extends FileComparison> source) {
		super(source, "MavenSemanticJudge", "Compares pom.xml files using Maven model-based semantic comparison", "pom.xml");
	}

	@Override
	Comparison compare(String expected, String actual) {
		MavenSemanticComparator.ComparisonResult result = comparator.compare(expected, actual);
		return new Comparison(result.equivalent(), result.differences());
	}

	/**
	 * Configures a producer without executing or acquiring evidence.
	 * @return typed evidence stage
	 */
	public static io.github.markpollack.judge.construction.EvidenceStep<FileComparison> builder() {
		return io.github.markpollack.judge.construction.EvidenceSteps.of(source -> new MavenSemanticJudge(source));
	}

}
