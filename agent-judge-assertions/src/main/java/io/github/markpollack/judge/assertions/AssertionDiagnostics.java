/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertions;

import io.github.markpollack.judge.requirement.Requirement;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.markpollack.judge.result.AppliedPolicy;
import io.github.markpollack.judge.result.Assessment;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;
import io.github.markpollack.judge.result.PolicyFailure;
import io.github.markpollack.judge.result.PolicyRef;

/**
 * Renders a bounded explanation; the error's structured result remains the full record.
 */
final class AssertionDiagnostics {

	private AssertionDiagnostics() {
	}

	static String message(AssertionResult result, SemanticAssertionError.Category category) {
		var requirement = result.requirement();
		var root = result.verdict().aggregated();
		var interpretation = result.interpretation();
		var lines = new ArrayList<String>();
		lines.add("Semantic assertion did not pass: " + category);
		lines.add("Requirement: " + text(requirement.id(), 80) + "@" + text(requirement.revision(), 40) + " — "
				+ text(requirement.text(), 240));
		lines.add("Assessment (root): producer=" + root.producerStatus() + "; " + assessment(root.assessment()));
		lines.add("Support: " + support(root));
		lines.add(
				"Policy: " + reference(result.policy()) + " (resolved " + result.policySource() + "); " + policy(root));
		lines.add("Operational result: " + root.status() + "; Interpretation: "
				+ (interpretation.reading() == null ? "unavailable" : interpretation.reading()) + "; reading support="
				+ interpretation.readingSupport() + " (structural, not model confidence)");
		if (!root.reasoning().isBlank()) {
			lines.add("Producer reason: " + text(root.reasoning(), 180));
		}
		provenance(root, lines);
		if (root.policyApplication() == null && !result.verdict().individual().isEmpty()
				&& !result.verdict().individual().equals(List.of(root))) {
			contributingPolicies(result, lines);
		}
		if (!interpretation.defects().isEmpty()) {
			var defect = interpretation.defects().getFirst();
			lines.add("Reading defect: " + defect.kind() + " at " + text(defect.path(), 80) + "."
					+ text(defect.field(), 40) + "; " + text(defect.note(), 120) + " ("
					+ interpretation.defects().size() + " retained)");
		}
		lines.add("Full details: SemanticAssertionError.result()");
		return String.join("\n", lines);
	}

	private static String assessment(@Nullable Assessment assessment) {
		if (assessment == null) {
			return "no assessment";
		}
		var components = new ArrayList<String>();
		var proposition = assessment.proposition();
		if (proposition != null) {
			components.add("proposition=" + proposition.value());
		}
		var category = assessment.category();
		if (category != null) {
			components.add("category=" + text(category.selected(), 100));
		}
		var numeric = assessment.numeric();
		if (numeric != null) {
			components.add("numeric=" + numeric.value() + " on " + text(numeric.scaleId(), 80));
		}
		return String.join("; ", components);
	}

	private static String support(Judgment judgment) {
		if (judgment.producerStatus() == JudgmentStatus.ERROR
				|| judgment.producerStatus() == JudgmentStatus.NOT_APPLICABLE) {
			return "none (producer " + judgment.producerStatus() + ")";
		}
		var parts = new ArrayList<String>();
		var certainty = judgment.certainty();
		if (certainty != null) {
			parts.add(text(certainty.metricId(), 100) + "=" + certainty.value() + " (" + certainty.origin() + ", "
					+ certainty.target() + ")");
		}
		var distribution = judgment.distribution();
		if (distribution != null) {
			var masses = new ArrayList<String>();
			for (int i = 0; i < Math.min(3, distribution.masses().size()); i++) {
				var mass = distribution.masses().get(i);
				masses.add("p(" + text(mass.alternative(), 40) + ")=" + mass.probability());
			}
			if (distribution.masses().size() > 3) {
				masses.add("… " + distribution.masses().size() + " masses retained");
			}
			parts.add(text(distribution.domainId(), 100) + " [" + String.join(", ", masses) + "]");
		}
		return parts.isEmpty() ? "none retained" : String.join("; ", parts);
	}

	private static String policy(Judgment judgment) {
		var application = judgment.policyApplication();
		if (application instanceof AppliedPolicy applied) {
			String consequence = switch (applied.action()) {
				case USE_ASSESSMENT -> "use original assessment";
				case ABSTAIN -> "withheld; original assessment unchanged";
				case ESCALATE -> "escalation requested; original assessment unchanged; caller must act";
			};
			return applied.action() + " — " + consequence + "; " + text(applied.reason(), 160);
		}
		if (application instanceof PolicyFailure failure) {
			return "FAILED; producer assessment retained; " + text(failure.reason(), 160);
		}
		if (judgment.producerStatus() == JudgmentStatus.ERROR
				|| judgment.producerStatus() == JudgmentStatus.NOT_APPLICABLE) {
			return "no application (producer " + judgment.producerStatus() + " bypasses policy)";
		}
		return "no root application retained; configured identity does not prove policy execution";
	}

	private static void provenance(Judgment judgment, List<String> lines) {
		var provenance = judgment.provenance();
		if (provenance == null) {
			return;
		}
		lines.add("Provenance: " + text(provenance.instrumentId(), 80) + "@" + text(provenance.revision(), 100)
				+ "; evidence references=" + provenance.evidence().size());
		var response = provenance.response();
		if (response != null) {
			lines.add("Response: " + text(response.id(), 100) + "; sha256=" + response.sha256());
		}
	}

	private static void contributingPolicies(AssertionResult result, List<String> lines) {
		var judgments = result.verdict().individual();
		for (int i = 0; i < Math.min(2, judgments.size()); i++) {
			var judgment = judgments.get(i);
			var application = judgment.policyApplication();
			lines.add("Contributing judgment " + i + ": producer=" + judgment.producerStatus() + "; operational="
					+ judgment.status() + "; policy=" + (application == null ? "none retained"
							: reference(application.policy()) + "; " + policy(judgment)));
		}
		if (judgments.size() > 2) {
			lines.add("Contributing judgments retained: " + judgments.size() + " (first 2 shown)");
		}
	}

	private static String reference(PolicyRef policy) {
		return text(policy.id(), 80) + "@" + text(policy.revision(), 40) + "#"
				+ policy.configurationDigest().substring(0, 12);
	}

	/** Bound user-controlled text and keep each field on its own diagnostic line. */
	private static String text(@Nullable String value, int limit) {
		if (value == null) {
			return "not selected";
		}
		var result = new StringBuilder();
		int offset = 0;
		while (offset < value.length() && result.length() < limit) {
			int codePoint = value.codePointAt(offset);
			offset += Character.charCount(codePoint);
			result.appendCodePoint(
					Character.isISOControl(codePoint) || codePoint == 0x2028 || codePoint == 0x2029 ? ' ' : codePoint);
		}
		if (offset < value.length()) {
			result.append('…');
		}
		return result.toString();
	}

}
