/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury.interpretation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * The deterministic prose summary of an {@link Interpretation}.
 *
 * <p>{@link #of(Interpretation)} reads the interpretation's fields alone — no model, no clock,
 * no access to the original verdict — so the same input always yields the same text, and the
 * text can be checked against the fields it describes. Every stage name, judge name and deciding
 * stage is mentioned in single quotes; every status as a fixed phrase ({@code passed},
 * {@code failed}, {@code abstained}, {@code was not applicable}, {@code errored}); every reason
 * code in square brackets; the reading and the support value as their own tokens; and every
 * recorded reasoning verbatim in parentheses.
 *
 * @author Mark Pollack
 * @since 0.17.0
 */
public final class Summaries {

	private Summaries() {
	}

	/**
	 * Summarise an interpretation from its fields alone.
	 * <p>
	 * Every field except {@link Interpretation#summary()} is read; that one is what this method
	 * produces, so {@code Summaries.of(i).equals(i.summary())} holds for every interpretation
	 * {@link Verdicts} returns.
	 * </p>
	 * @param interpretation the interpretation
	 * @return the summary, the same text for the same fields
	 */
	public static String of(Interpretation interpretation) {
		Objects.requireNonNull(interpretation, "interpretation must not be null");
		List<String> sentences = new ArrayList<>();
		sentences.add(whatRan(interpretation));
		for (Stage stage : interpretation.stages()) {
			sentences.add(stageSentence(stage));
			addEvidence(sentences, stage.evidence());
		}
		sentences.add(rootSentence(interpretation.root()));
		addEvidence(sentences, interpretation.root().evidence());
		sentences.add(decidedBySentence(interpretation));
		sentences.add(readingSentence(interpretation.reading()));
		sentences.add(supportSentence(interpretation.readingSupport()));
		sentences.add(interpretation.sourceVersion() == 1
				? "Source: the seven-component form 0.17 writes (sourceVersion 1)."
				: "Source: an unstamped record written before 0.17 (sourceVersion 0).");
		sentences.add(defectsSentence(interpretation.defects()));
		return String.join(" ", sentences);
	}

	private static String whatRan(Interpretation interpretation) {
		List<Stage> stages = interpretation.stages();
		if (stages.isEmpty()) {
			int seated = interpretation.root().judges().size();
			return seated == 0 ? "No composite stage ran, and the root seats no judge."
					: "No composite stage ran; the root seated " + seated + " judge(s) directly.";
		}
		List<String> entries = new ArrayList<>();
		for (Stage stage : stages) {
			String facts = attemptFacts(stage);
			entries.add(stageRef(stage) + (facts.isEmpty() ? "" : " (" + facts + ")"));
		}
		return stages.size() + " stage(s) ran: " + String.join(", ", entries) + ".";
	}

	private static String attemptFacts(Stage stage) {
		List<String> facts = new ArrayList<>();
		if (stage.relation() != null) {
			facts.add(stage.relation());
		}
		if (stage.policy() != null) {
			facts.add(stage.policy());
		}
		if (Boolean.TRUE.equals(stage.usedByParent())) {
			facts.add("used");
		}
		else if (Boolean.FALSE.equals(stage.usedByParent())) {
			facts.add("not used" + (stage.reason() == null ? "" : ": " + stage.reason()));
		}
		else if (stage.disposition() != null) {
			facts.add("disposition " + stage.disposition());
		}
		else if (stage.relation() != null || stage.policy() != null) {
			facts.add("use unrecorded");
		}
		if (stage.failure() != null) {
			facts.add("no verdict: " + stage.failure());
		}
		return String.join(", ", facts);
	}

	private static String stageRef(Stage stage) {
		if (stage.path().isEmpty()) {
			return stage.stage() == null ? "an unnamed stage" : quote(stage.stage());
		}
		return quotedPath(stage.path());
	}

	private static String quotedPath(List<String> path) {
		List<String> quoted = new ArrayList<>();
		for (String segment : path) {
			quoted.add(quote(segment));
		}
		return String.join(" > ", quoted);
	}

	private static String quote(String name) {
		return "'" + name + "'";
	}

	private static String stageSentence(Stage stage) {
		String subject = capitalise(stageRef(stage));
		if (stage.status() == null) {
			if (stage.failure() != null) {
				return subject + " entered and produced no verdict; its failure is " + stage.failure() + ".";
			}
			if (stage.reasoning() == null && stage.judges().isEmpty()) {
				return subject + " produced no verdict.";
			}
			return subject + " records no readable status" + judgesClause(stage) + ".";
		}
		return subject + " " + outcome(stage.status(), stage.reasonCode(), stage.reasoning()) + judgesClause(stage) + ".";
	}

	private static String rootSentence(Stage root) {
		if (root.status() == null) {
			return "The root records no readable status" + judgesClause(root) + ".";
		}
		return "The root " + outcome(root.status(), root.reasonCode(), root.reasoning()) + judgesClause(root) + ".";
	}

	private static String outcome(String status, @Nullable String reasonCode, @Nullable String reasoning) {
		return phrase(status) + (reasonCode == null ? "" : " [" + reasonCode + "]")
				+ (reasoning == null || reasoning.isEmpty() ? "" : " (" + reasoning + ")");
	}

	private static String judgesClause(Stage stage) {
		if (stage.judges().isEmpty()) {
			return " with no seated judge";
		}
		List<String> clauses = new ArrayList<>();
		for (JudgeSeat judge : stage.judges()) {
			String said = judge.status() == null ? "records no readable status"
					: outcome(judge.status(), judge.reasonCode(), judge.reasoning());
			clauses.add(quote(judge.name()) + " " + said);
		}
		return "; its judges: " + String.join("; ", clauses);
	}

	/** The fixed phrase for a status token; the agreement test holds its own copy as the oracle. */
	private static String phrase(String status) {
		return switch (status) {
			case "pass" -> "passed";
			case "fail" -> "failed";
			case "abstain" -> "abstained";
			case "not_applicable" -> "was not applicable";
			case "error" -> "errored";
			default -> "recorded status " + status;
		};
	}

	private static void addEvidence(List<String> sentences, @Nullable Evidence e) {
		if (e == null) {
			return;
		}
		List<String> parts = new ArrayList<>();
		parts.add(e.strategy() == null ? "an unnamed strategy" : e.strategy());
		if (e.errorPolicy() != null) {
			parts.add("errors " + e.errorPolicy());
		}
		if (e.notApplicablePolicy() != null) {
			parts.add("exclusions " + e.notApplicablePolicy());
		}
		if (e.threshold() != null) {
			parts.add("threshold " + e.threshold());
		}
		addCount(parts, e.inputCount(), "in");
		addCount(parts, e.eligibleCount(), "eligible");
		addCount(parts, e.passCount(), "pass");
		addCount(parts, e.failCount(), "fail");
		addCount(parts, e.errorCount(), "error(s)");
		addCount(parts, e.notApplicableCount(), "not applicable");
		addCount(parts, e.explicitAbstainCount(), "explicit abstention(s)");
		if (e.errorCodeCounts() != null && !e.errorCodeCounts().isEmpty()) {
			List<String> causes = new ArrayList<>();
			e.errorCodeCounts().forEach((code, count) -> causes.add(code + "=" + count));
			parts.add("causes " + String.join(" ", causes));
		}
		sentences.add("Evidence: " + String.join(", ", parts) + ".");
	}

	private static void addCount(List<String> parts, @Nullable Integer count, String noun) {
		if (count != null) {
			parts.add(count + " " + noun);
		}
	}

	private static String decidedBySentence(Interpretation interpretation) {
		DecidedBy decidedBy = interpretation.decidedBy();
		if (decidedBy != null) {
			String where = quotedPath(decidedBy.path().isEmpty() ? List.of(decidedBy.stage()) : decidedBy.path());
			return switch (decidedBy.basis()) {
				case "tier_outcome" -> "The root adopted the outcome of " + where + " (tier_outcome).";
				case "individual_rejection" -> "The root stopped on an individual rejection established in " + where
						+ " (individual_rejection).";
				default -> "The root's decision names " + where + " with basis " + decidedBy.basis() + ".";
			};
		}
		boolean unreadable = interpretation.defects()
			.stream()
			.anyMatch(defect -> (defect.path().equals("verdict") && defect.field().equals("decision"))
					|| defect.path().equals("verdict.decision"));
		return unreadable ? "Which stage decided is not recorded and has not been inferred."
				: "The root's own reduction decided; no stage is named.";
	}

	private static String readingSentence(@Nullable VerdictReading reading) {
		if (reading == null) {
			return "Reading: none; the root records no readable status.";
		}
		String gloss = switch (reading) {
			case ACCEPTED -> "the subject was judged and accepted";
			case REJECTED -> "the subject was judged and rejected";
			case UNDECIDED -> "the subject was judged and the jury could not decide; not an instrument failure";
			case NOT_APPLICABLE -> "the criteria did not apply to the subject";
			case NOT_ASSESSED -> "the instrument did not reach an assessment of the subject";
		};
		return "Reading: " + reading + " — " + gloss + ".";
	}

	private static String supportSentence(ReadingSupport support) {
		String gloss = switch (support) {
			case SUPPORTED -> "the recorded facts agree with the reading";
			case CONTRADICTED -> "a recorded fact contradicts the reading; see the INCONSISTENT defect(s)";
			case UNDETERMINED -> "the facts needed to check the reading are absent or their rule is not closed-form";
		};
		return "Support: " + support + " — " + gloss + ".";
	}

	private static String defectsSentence(List<Defect> defects) {
		if (defects.isEmpty()) {
			return "Defects: none.";
		}
		List<String> entries = new ArrayList<>();
		for (Defect defect : defects) {
			entries.add(defect.path() + "." + defect.field() + " " + defect.kind());
		}
		return defects.size() + " defect(s): " + String.join("; ", entries) + ".";
	}

	private static String capitalise(String text) {
		return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}

}
