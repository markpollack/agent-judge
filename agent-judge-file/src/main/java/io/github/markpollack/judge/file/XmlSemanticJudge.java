/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.file;

import io.github.markpollack.judge.file.comparator.XmlSemanticComparator;

/**
 * Judge that compares non-POM XML files using DOM-based semantic comparison.
 */
public class XmlSemanticJudge extends SemanticFileJudge {

	private final XmlSemanticComparator comparator = new XmlSemanticComparator();

	/**
	 * Create an XML semantic judge.
	 * @param source fresh evidence acquisition provider, invoked once per direct
	 * execution
	 */
	public XmlSemanticJudge(java.util.function.Supplier<? extends FileComparison> source) {
		super(source, "XmlSemanticJudge", "Compares XML files using DOM-based semantic comparison", "XML");
	}

	@Override
	Comparison compare(String expected, String actual) {
		XmlSemanticComparator.ComparisonResult result = comparator.compare(expected, actual);
		return new Comparison(result.equivalent(), result.differences());
	}

	/**
	 * Configures a producer without executing or acquiring evidence.
	 * @return typed evidence stage
	 */
	public static io.github.markpollack.judge.construction.EvidenceStep<FileComparison> builder() {
		return io.github.markpollack.judge.construction.EvidenceSteps.of(source -> new XmlSemanticJudge(source));
	}

}
