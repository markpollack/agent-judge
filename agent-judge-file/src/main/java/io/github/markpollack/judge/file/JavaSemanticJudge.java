/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.file;

import io.github.markpollack.judge.file.comparator.JavaSemanticComparator;

/**
 * Judge that compares Java source files using AST-based semantic comparison.
 * <p>
 * Tolerates differences in:
 * <ul>
 * <li>Whitespace and formatting</li>
 * <li>Import ordering</li>
 * <li>Comments</li>
 * </ul>
 */
public class JavaSemanticJudge extends SemanticFileJudge {

	private final JavaSemanticComparator comparator = new JavaSemanticComparator();

	/**
	 * Create a Java source semantic-comparison judge.
	 * @param source fresh evidence acquisition provider, invoked once per direct
	 * execution
	 */
	public JavaSemanticJudge(java.util.function.Supplier<? extends FileComparison> source) {
		super(source, "JavaSemanticJudge", "Compares Java files using AST-based semantic comparison", "Java");
	}

	@Override
	Comparison compare(String expected, String actual) {
		JavaSemanticComparator.ComparisonResult result = comparator.compare(expected, actual);
		return new Comparison(result.equivalent(), result.differences());
	}

	/**
	 * Configures a producer without executing or acquiring evidence.
	 * @return typed evidence stage
	 */
	public static io.github.markpollack.judge.construction.EvidenceStep<FileComparison> builder() {
		return io.github.markpollack.judge.construction.EvidenceSteps.of(source -> new JavaSemanticJudge(source));
	}

}
