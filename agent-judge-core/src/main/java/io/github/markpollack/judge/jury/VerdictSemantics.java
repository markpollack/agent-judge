/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.jury;

import java.util.*;
import io.github.markpollack.judge.judgment.*;
import io.github.markpollack.judge.requirement.*;

/** Shared typed rules for live and successfully decoded verdicts. */
final class VerdictSemantics {

	private VerdictSemantics() {
	}

	static Verdict.Conclusion conclusion(Verdict v) {
		new VerdictSemantics().validate(v, "verdict");
		if (v.requirement() != null && v.requirement().specification() instanceof AllOf specification) {
			if (!specification.applicable())
				return Verdict.Conclusion.NOT_APPLICABLE;
			boolean incomplete = false;
			for (CompositeAttempt attempt : v.compositeAttempts()) {
				Verdict child = attempt.verdict();
				if (child == null) {
					incomplete = true;
					continue;
				}
				Verdict.Conclusion c = child.conclusion();
				if (c == Verdict.Conclusion.FAIL)
					return c;
				if (c != Verdict.Conclusion.PASS)
					incomplete = true;
			}
			return incomplete ? Verdict.Conclusion.INCONCLUSIVE : Verdict.Conclusion.PASS;
		}
		if (v.provenance().kind() == VerdictProvenanceKind.TIER) {
			if (v.provenance().basis() == VerdictProvenanceBasis.INDIVIDUAL_REJECTION)
				return Verdict.Conclusion.FAIL;
			String selected = v.provenance().tier();
			return Objects
				.requireNonNull(v.compositeAttempts()
					.stream()
					.filter(a -> a.name().equals(selected))
					.findFirst()
					.orElseThrow()
					.verdict())
				.conclusion();
		}
		return switch (v.judgment().status()) {
			case PASS -> Verdict.Conclusion.PASS;
			case FAIL -> Verdict.Conclusion.FAIL;
			case ABSTAIN, ERROR -> Verdict.Conclusion.INCONCLUSIVE;
			case NOT_APPLICABLE -> Verdict.Conclusion.NOT_APPLICABLE;
		};
	}

	private static void defect(String path, String field, String explanation) {
		throw new IllegalArgumentException(path + "." + field + ": " + explanation);
	}

	private void validateConstituents(Verdict v, String path) {
		Requirement<?> parent = Objects.requireNonNull(v.requirement());
		AllOf spec = (AllOf) parent.specification();
		if (v.provenance().kind() != VerdictProvenanceKind.CONSTITUENTS)
			defect(path, "provenance", "All-of requires constituent provenance");
		if (!v.individual().isEmpty() || !v.seats().isEmpty() || !v.weights().isEmpty())
			defect(path, "seats", "Constituents retain child Verdicts, not flattened opinions");
		if (v.declaredCardinality() != spec.constituents().size())
			defect(path, "declaredCardinality", "All-of roster cardinality differs");
		if (!spec.applicable()) {
			if (!v.compositeAttempts().isEmpty() || v.judgment().status() != JudgmentStatus.NOT_APPLICABLE)
				defect(path, "applicability", "Inapplicable parent must retain exclusion and no attempts");
			return;
		}
		if (v.declaredCardinality() != spec.constituents().size()
				|| v.compositeAttempts().size() != spec.constituents().size())
			defect(path, "constituents", "Complete constituent coverage is required");
		boolean failed = false, incomplete = false;
		for (int i = 0; i < spec.constituents().size(); i++) {
			Requirement<?> requirement = spec.constituents().get(i);
			CompositeAttempt attempt = v.compositeAttempts().get(i);
			if (attempt.relation() != CompositeRelation.CONSTITUENT || !attempt.name().equals(requirement.id()))
				defect(path, "constituents", "Constituent identity/order mismatch");
			Verdict child = attempt.verdict();
			if (child == null) {
				incomplete = true;
				continue;
			}
			if (!requirement.equals(child.requirement()))
				defect(path, "requirement", "Child association differs from parent specification");
			Verdict.Conclusion c = child.conclusion();
			failed |= c == Verdict.Conclusion.FAIL;
			incomplete |= c != Verdict.Conclusion.PASS;
		}
		JudgmentStatus expected = failed ? JudgmentStatus.FAIL
				: incomplete ? JudgmentStatus.ABSTAIN : JudgmentStatus.PASS;
		if (v.judgment().status() != expected)
			defect(path, "judgment", "Collective judgment contradicts all-of constituents");
	}

	private void validate(Verdict v, String path) {
		if (v.requirement() != null
				&& v.requirement().specification() instanceof io.github.markpollack.judge.requirement.AllOf) {
			validateConstituents(v, path);
			return;
		}
		if (v.provenance().kind() == VerdictProvenanceKind.CONSTITUENTS
				|| v.compositeAttempts().stream().anyMatch(a -> a.relation() == CompositeRelation.CONSTITUENT))
			defect(path, "requirement", "Constituent provenance requires an AllOf requirement");
		for (CompositeAttempt a : v.compositeAttempts())
			if (a.verdict() != null && a.disposition() == AttemptDisposition.USED)
				validate(a.verdict(), path + "/" + a.name());
		int count = v.declaredCardinality();
		for (Seat seat : v.seats())
			if (seat.position() >= count)
				defect(path, "declaredCardinality", "Seat lies outside declared population");
		Map<String, Judgment> expectedNames = new LinkedHashMap<>();
		for (int i = 0; i < v.seats().size(); i++) {
			Seat seat = v.seats().get(i);
			Judgment j = v.individual().get(i);
			expectedNames.put(seat.verdictKey(), j);
			if (seat.execution() == SeatExecution.CONTAINED_FAILURE && !contained(j))
				defect(path, "seats.execution", "Contained failure must have the synthetic containment shape");
		}
		if (!expectedNames.equals(v.individualByName()))
			defect(path, "individualByName", "Named inputs differ from ordered inputs");
		if (!v.weights().isEmpty()) {
			if (v.weights().size() != count)
				defect(path, "weights", "Weight population differs from declared population");
			for (int i = 0; i < v.weights().size(); i++) {
				Double weight = v.weights().get(Integer.toString(i));
				if (weight == null || !Double.isFinite(weight) || weight < 0)
					defect(path, "weights", "Invalid configured weight");
			}
		}
		boolean meta = v.compositeAttempts().stream().anyMatch(a -> a.relation() == CompositeRelation.META_MEMBER);
		boolean cascade = v.compositeAttempts().stream().anyMatch(a -> a.relation() == CompositeRelation.CASCADE_TIER);
		if (meta && cascade)
			defect(path, "compositeAttempts", "Mixed composite relations");
		if (meta)
			validateMeta(v, path);
		if (cascade)
			validateCascade(v, path);
		if (!meta && !cascade && count != v.seats().size())
			defect(path, "declaredCardinality", "Leaf must retain every declared invocation");
		if (!cascade && count == 1 && v.seats().size() == 1 && v.seats().get(0).execution() == SeatExecution.RETURNED
				&& v.compositeAttempts().stream().noneMatch(a -> a.disposition() == AttemptDisposition.STAGE_FAILED)
				&& !identity(v))
			defect(path, "provenance", "A valid single declared result requires OWN whole-value identity");
		if (v.provenance().kind() == VerdictProvenanceKind.OWN) {
			for (int i = 0; i < v.seats().size(); i++) {
				Participation recorded = v.seats().get(i).participation();
				if (recorded != Participation.NOT_RECORDED
						&& recorded != Participation.forJudgment(v.individual().get(i), v.judgment(), identity(v)))
					defect(path, "seats.participation",
							"Recorded treatment contradicts retained producer facts and reduction");
			}
			if (identity(v))
				return;
			if (count == 1 && v.seats().size() == 1 && v.seats().get(0).execution() == SeatExecution.RETURNED) {
				defect(path, "judgment", "One valid declared input requires complete semantic identity");
				return;
			}
			validateReduction(v, path);
		}
		else if (v.provenance().kind() == VerdictProvenanceKind.TIER) {
			CompositeAttempt selected = v.compositeAttempts()
				.stream()
				.filter(a -> a.name().equals(v.provenance().tier()))
				.findFirst()
				.orElseThrow();
			Verdict child = selected.verdict();
			if (child != null && count != child.declaredCardinality())
				defect(path, "declaredCardinality", "Selected tier cardinality was not copied");
		}
	}

	private void validateReduction(Verdict v, String path) {
		Judgment aggregate = v.judgment();
		if (!(aggregate.metadata().get(Judgment.AGGREGATION_KEY) instanceof Map<?, ?> e))
			throw new IllegalArgumentException(path + ": missing aggregation semantics");
		ErrorHandling errors = java.util.Arrays.stream(ErrorHandling.values())
			.filter(x -> x.token().equals(e.get("errorPolicy")))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Unavailable error handling"));
		ExclusionHandling exclusions = java.util.Arrays.stream(ExclusionHandling.values())
			.filter(x -> x.token().equals(e.get("notApplicablePolicy")))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Unavailable exclusion handling"));
		double threshold = e.get("threshold") instanceof Number n ? n.doubleValue() : 0.5;
		TieBreakRule tie = aggregate.status() == JudgmentStatus.PASS ? TieBreakRule.PASS
				: aggregate.status() == JudgmentStatus.FAIL ? TieBreakRule.FAIL : TieBreakRule.ABSTAIN;
		VotingStrategy strategy = switch (Objects.toString(e.get("strategy"))) {
			case "consensus" -> new ConsensusStrategy(errors, exclusions);
			case "majority" -> new MajorityVotingStrategy(tie, errors, exclusions);
			case "allMustPass" -> new AllMustPassStrategy(errors, exclusions);
			case "average" -> new AverageVotingStrategy(threshold, errors, exclusions);
			case "median" -> new MedianVotingStrategy(threshold, errors, exclusions);
			case "weightedAverage" -> new WeightedAverageStrategy(threshold, errors, exclusions);
			case "conjunctive" -> new ConjunctiveStrategy(threshold, errors, exclusions);
			default ->
				throw new IllegalArgumentException(path + ": unavailable aggregation semantics: " + e.get("strategy"));
		};
		Judgment recomputed = strategy.aggregate(v.individual(), v.weights());
		if (aggregate.status() != recomputed.status() || !Objects.equals(aggregate.finding(), recomputed.finding())
				|| aggregate.reasonCode() != recomputed.reasonCode()
				|| !Objects.equals(e, recomputed.metadata().get(Judgment.AGGREGATION_KEY)))
			defect(path, "aggregation", "Recorded reduction contradicts retained inputs, weights or rules");
	}

	private static boolean contained(Judgment j) {
		return j.producerStatus() == JudgmentStatus.ERROR && j.finding() == null && j.confidence() == null
				&& j.probabilityDistribution() == null && j.provenance() == null && j.checks().isEmpty()
				&& j.metadata().isEmpty()
				&& Set
					.of(JudgmentReasonCode.JUDGE_FAILED, JudgmentReasonCode.JUDGE_METADATA_UNREADABLE,
							JudgmentReasonCode.UNDECLARED_NOT_APPLICABLE)
					.contains(j.reasonCode());
	}

	private static boolean identity(Verdict v) {
		return v.declaredCardinality() == 1 && v.individual().size() == 1 && v.seats().size() == 1
				&& v.seats().get(0).position() == 0 && v.seats().get(0).execution() == SeatExecution.RETURNED
				&& v.provenance().kind() == VerdictProvenanceKind.OWN && v.judgment().equals(v.individual().get(0))
				&& v.judgment().equals(v.individualByName().get(v.seats().get(0).verdictKey()));
	}

	private void validateMeta(Verdict v, String path) {
		if (v.declaredCardinality() != v.compositeAttempts().size())
			defect(path, "declaredCardinality", "Meta population must equal all member attempts");
		int index = 0;
		boolean failed = false;
		for (int position = 0; position < v.compositeAttempts().size(); position++) {
			CompositeAttempt a = v.compositeAttempts().get(position);
			if (a.disposition() == AttemptDisposition.STAGE_FAILED) {
				failed = true;
				continue;
			}
			Verdict child = a.verdict();
			if (index >= v.seats().size() || child == null || v.seats().get(index).position() != position
					|| v.seats().get(index).execution() != SeatExecution.RETURNED
					|| !v.seats().get(index).verdictKey().equals(a.name())
					|| !v.individual().get(index).equals(child.judgment()))
				defect(path, "seats", "Meta seats must match used members at configured positions");
			index++;
		}
		if (index != v.seats().size())
			defect(path, "seats", "Unexpected meta seat");
		if (failed && (v.provenance().kind() != VerdictProvenanceKind.UNDECIDED
				|| v.judgment().reasonCode() != JudgmentReasonCode.STAGE_FAILED))
			defect(path, "provenance", "Failed meta member requires terminal stage failure");
	}

	private void validateCascade(Verdict v, String path) {
		boolean stopped = false;
		for (int i = 0; i < v.compositeAttempts().size(); i++) {
			CompositeAttempt a = v.compositeAttempts().get(i);
			Verdict child = a.verdict();
			if (stopped)
				defect(path, "compositeAttempts", "An attempt follows a terminal routing outcome");
			boolean stop = a.routingRule() == RoutingRule.FINAL_TIER;
			if (child != null) {
				if (a.routingRule() == RoutingRule.STOP_ON_CONCLUSIVE && a.disposition() == AttemptDisposition.USED) {
					stop = child.conclusion() == Verdict.Conclusion.PASS
							|| child.conclusion() == Verdict.Conclusion.FAIL;
				}
				else if (a.routingRule() == RoutingRule.REJECT_ON_ANY_FAIL)
					stop = child.individual().stream().anyMatch(j -> j.status() == JudgmentStatus.FAIL);
				else if (a.routingRule() == RoutingRule.ACCEPT_ON_ALL_PASS)
					stop = a.disposition() == AttemptDisposition.USED && !child.individual().isEmpty()
							&& child.individual().stream().allMatch(j -> j.status() == JudgmentStatus.PASS);
			}
			if (stop) {
				stopped = true;
				if (a.disposition() == AttemptDisposition.USED && (v.provenance().kind() != VerdictProvenanceKind.TIER
						|| !a.name().equals(v.provenance().tier())))
					defect(path, "provenance", "The first stopping tier must be selected");
			}
			else if (a.name().equals(v.provenance().tier()))
				defect(path, "provenance", "Selected tier's policy requires continuation");
		}
		if (!stopped)
			defect(path, "compositeAttempts", "Cascade ended without a stopping or final attempt");
		if (v.provenance().kind() == VerdictProvenanceKind.OWN)
			defect(path, "provenance", "Cascade cannot claim an own reduction");
	}

}
