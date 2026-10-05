/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.verdict;

import io.github.markpollack.judge.voting.Participation;
import io.github.markpollack.judge.voting.Ballot;
import io.github.markpollack.judge.voting.AllEligiblePassStrategy;
import io.github.markpollack.judge.voting.AverageVotingStrategy;
import io.github.markpollack.judge.voting.ConjunctiveStrategy;
import io.github.markpollack.judge.voting.ConsensusStrategy;
import io.github.markpollack.judge.voting.ErrorHandling;
import io.github.markpollack.judge.voting.ExclusionHandling;
import io.github.markpollack.judge.voting.MajorityVotingStrategy;
import io.github.markpollack.judge.voting.MedianVotingStrategy;
import io.github.markpollack.judge.voting.TieBreakRule;
import io.github.markpollack.judge.voting.VotingStrategy;
import io.github.markpollack.judge.voting.WeightedAverageStrategy;

import java.util.*;
import io.github.markpollack.judge.judgment.*;
import io.github.markpollack.judge.requirement.*;

/** Shared typed rules for live and successfully decoded verdicts. */
final class VerdictSemantics {

	private VerdictSemantics() {
	}

	static Verdict.Conclusion conclusion(Verdict v) {
		new VerdictSemantics().validate(v, "verdict");
		if (v.provenance().kind() == VerdictProvenanceKind.ROSTER)
			return rosterConclusion(v);
		if (v.requirement() != null && v.requirement().specification() instanceof AllOf specification) {
			if (!specification.applicable())
				return Verdict.Conclusion.NOT_APPLICABLE;
			boolean incomplete = false;
			for (CompositeAttempt attempt : v.compositeAttempts()) {
				Verdict child = attempt.verdict();
				if (child == null || attempt.dispositionReason() == DispositionReason.INVALID_TIER_RESULT) {
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
		Verdict delegated = identityChild(v);
		if (delegated != null)
			return delegated.conclusion();
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

	// semantic projection only; raw seats, attempts and judgments remain unchanged.
	private static Verdict identityChild(Verdict v) {
		if (v.declaredCardinality() != 1 || v.compositeAttempts().size() != 1)
			return null;
		CompositeAttempt a = v.compositeAttempts().get(0);
		Verdict child = a.verdict();
		if (a.relation() != CompositeRelation.META_MEMBER || child == null
				|| a.dispositionReason() == DispositionReason.INVALID_TIER_RESULT
				|| a.dispositionReason() == DispositionReason.UNDECLARED_NOT_APPLICABLE)
			return null;
		// No USED gate in identity recognition. Validate the emitted treatment
		// separately.
		VerdictProvenanceKind expected = child.provenance().kind() == VerdictProvenanceKind.UNDECIDED
				? VerdictProvenanceKind.UNDECIDED : VerdictProvenanceKind.OWN;
		if (v.provenance().kind() != expected || v.individual().size() != 1 || v.seats().size() != 1
				|| v.seats().get(0).position() != 0 || v.seats().get(0).execution() != SeatExecution.RETURNED
				|| v.seats().get(0).participation() != Participation.IDENTITY
				|| !v.seats().get(0).verdictKey().equals(a.name()) || !v.judgment().equals(child.judgment())
				|| !v.individual().get(0).equals(child.judgment())
				|| !v.individualByName().equals(Map.of(a.name(), child.judgment())))
			return null;
		return child;
	}

	static List<Judgment> routingOpinions(Verdict v) {
		Verdict delegated = identityChild(v);
		if (delegated != null)
			return routingOpinions(delegated);
		if (v.provenance().kind() == VerdictProvenanceKind.TIER) {
			Verdict selected = v.compositeAttempts()
				.stream()
				.filter(a -> a.name().equals(v.provenance().tier()))
				.findFirst()
				.orElseThrow()
				.verdict();
			return routingOpinions(Objects.requireNonNull(selected));
		}
		return reductionInputs(v);
	}

	public static boolean routingStops(RoutingRule rule, Verdict child, boolean accepted) {
		return routingDecision(rule, child, accepted).stops();
	}

	static RoutingDecision routingDecision(RoutingRule rule, Verdict child, boolean accepted) {
		if (rule == RoutingRule.FINAL_TIER)
			return new RoutingDecision(true, RoutingDecision.Reason.FINAL_TIER);
		if (child == null || !accepted)
			return new RoutingDecision(false, RoutingDecision.Reason.REFUSED_TIER);
		Verdict.Conclusion conclusion;
		try {
			conclusion = child.conclusion();
		}
		catch (IllegalArgumentException invalid) {
			io.github.markpollack.judge.portable.PreservationLimitException.propagate(invalid);
			return new RoutingDecision(false, RoutingDecision.Reason.INVALID_TIER);
		}
		if (rule == RoutingRule.STOP_ON_ANY_OPINION_FAIL || rule == RoutingRule.STOP_ON_ALL_OPINIONS_PASS) {
			var opinions = routingOpinions(child);
			if (opinions.isEmpty())
				return new RoutingDecision(false, RoutingDecision.Reason.NO_ROOT_OPINIONS);
			if (rule == RoutingRule.STOP_ON_ANY_OPINION_FAIL
					&& opinions.stream().anyMatch(j -> j.status() == JudgmentStatus.FAIL))
				return new RoutingDecision(true, RoutingDecision.Reason.OPINION_FAIL);
			if (rule == RoutingRule.STOP_ON_ALL_OPINIONS_PASS
					&& opinions.stream().allMatch(j -> j.status() == JudgmentStatus.PASS)) {
				if (child.provenance().kind() == VerdictProvenanceKind.UNDECIDED)
					return new RoutingDecision(false, RoutingDecision.Reason.ROOT_UNDECIDED);
				return new RoutingDecision(true, RoutingDecision.Reason.ALL_OPINIONS_PASS);
			}
			return new RoutingDecision(false, RoutingDecision.Reason.CONTINUE);
		}
		boolean stop = switch (rule) {
			case STOP_ON_CONCLUSIVE -> conclusion == Verdict.Conclusion.PASS || conclusion == Verdict.Conclusion.FAIL;
			case STOP_ON_CONCLUSION_PASS -> conclusion == Verdict.Conclusion.PASS;
			case STOP_ON_CONCLUSION_FAIL -> conclusion == Verdict.Conclusion.FAIL;
			default -> throw new IllegalArgumentException("Unhandled routing rule " + rule);
		};
		return new RoutingDecision(stop,
				stop ? RoutingDecision.Reason.CONCLUSION_STOP : RoutingDecision.Reason.CONTINUE);
	}

	private static Verdict.Conclusion rosterConclusion(Verdict v) {
		boolean incomplete = false, applicable = false;
		for (CompositeAttempt attempt : v.compositeAttempts()) {
			if (attempt.disposition() != AttemptDisposition.USED || attempt.verdict() == null) {
				incomplete = true;
				continue;
			}
			var conclusion = attempt.verdict().conclusion();
			if (conclusion == Verdict.Conclusion.FAIL)
				return conclusion;
			if (conclusion != Verdict.Conclusion.NOT_APPLICABLE)
				applicable = true;
			if (conclusion != Verdict.Conclusion.PASS && conclusion != Verdict.Conclusion.NOT_APPLICABLE)
				incomplete = true;
		}
		return incomplete ? Verdict.Conclusion.INCONCLUSIVE
				: applicable ? Verdict.Conclusion.PASS : Verdict.Conclusion.NOT_APPLICABLE;
	}

	private void validateRoster(Verdict v, String path) {
		if (v.requirement() != null || !v.individual().isEmpty() || !v.seats().isEmpty())
			defect(path, "roster",
					"A roster has actual requirements and complete constituent records, without parent/opinion seats");
		if (v.roster().isEmpty() || v.roster().size() != v.declaredCardinality()
				|| v.roster().size() != v.compositeAttempts().size()
				|| v.roster().stream().map(Requirement::id).distinct().count() != v.roster().size())
			defect(path, "roster", "Complete unique declared roster required");
		Set<String> invocationIds = new HashSet<>();
		for (var invocation : v.invocations())
			if (!invocationIds.add(invocation.id()))
				defect(path, "invocations", "Duplicate owner identity");
		for (int i = 0; i < v.roster().size(); i++) {
			Requirement<?> requirement = v.roster().get(i);
			CompositeAttempt attempt = v.compositeAttempts().get(i);
			if (attempt.relation() != CompositeRelation.ROSTER_ITEM || !attempt.name().equals(requirement.id()))
				defect(path, "roster", "Order/identity mismatch");
			Verdict child = attempt.verdict();
			if (child != null) {
				if (attempt.disposition() == AttemptDisposition.USED
						&& (child.requirement() == null || !Requirement.equivalent(requirement, child.requirement())))
					defect(path, "requirement", "Roster association mismatch");
				if (attempt.disposition() != AttemptDisposition.USED
						&& attempt.dispositionReason() != DispositionReason.PROTOCOL_UNBOUND)
					defect(path, "roster", "Unsupported returned roster refusal");
				child.conclusion();
				if (!invocationIds.containsAll(child.judgment().invocationIds()))
					defect(path, "invocations", "Unknown shared invocation reference");
			}
		}
		JudgmentStatus expected = switch (rosterConclusion(v)) {
			case PASS -> JudgmentStatus.PASS;
			case FAIL -> JudgmentStatus.FAIL;
			case NOT_APPLICABLE -> JudgmentStatus.NOT_APPLICABLE;
			case INCONCLUSIVE -> JudgmentStatus.ABSTAIN;
		};
		if (v.judgment().status() != expected)
			defect(path, "judgment", "Roster rollup contradicts retained accepted constituents");
	}

	private static void defect(String path, String field, String explanation) {
		throw new IllegalArgumentException(path + "." + field + ": " + explanation);
	}

	// A returned constituent is checked before its conclusion can contribute. The
	// rejected record remains unchanged; this temporary association is only validation.
	record CheckedConstituent(Verdict verdict, Verdict.Conclusion conclusion) {
	}

	static CheckedConstituent associateConstituent(Requirement<?> requirement, Verdict original) {
		Verdict associated = original.forRequirement(requirement);
		return new CheckedConstituent(associated, associated.conclusion());
	}

	private static boolean invalidConstituent(Requirement<?> requirement, Verdict original) {
		try {
			associateConstituent(requirement, original);
			return false;
		}
		catch (IllegalArgumentException rejected) {
			io.github.markpollack.judge.portable.PreservationLimitException.propagate(rejected);
			return true;
		}
	}

	private void validateConstituents(Verdict v, String path) {
		Requirement<?> parent = Objects.requireNonNull(v.requirement());
		AllOf spec = (AllOf) parent.specification();
		if (v.provenance().kind() != VerdictProvenanceKind.CONSTITUENTS)
			defect(path, "provenance", "All-of requires constituent provenance");
		if (!v.individual().isEmpty() || !v.seats().isEmpty())
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
			if (attempt.dispositionReason() == DispositionReason.INVALID_TIER_RESULT) {
				if (!invalidConstituent(requirement, child))
					defect(path, "constituents", "Invalid-result refusal requires an invalid original or association");
				incomplete = true;
				continue;
			}
			if (attempt.disposition() != AttemptDisposition.USED
					&& attempt.dispositionReason() != DispositionReason.CHILD_UNDECIDED)
				defect(path, "constituents", "Unsupported returned constituent refusal");
			if (child.requirement() == null || !Requirement.equivalent(requirement, child.requirement()))
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
		if (v.provenance().kind() == VerdictProvenanceKind.ROSTER) {
			validateRoster(v, path);
			return;
		}
		if (!v.roster().isEmpty())
			defect(path, "roster", "Only roster provenance declares audit coverage");
		Verdict delegatedIdentity = identityChild(v);
		if (delegatedIdentity != null) {
			if (v.compositeAttempts().get(0).disposition() != AttemptDisposition.USED)
				defect(path, "compositeAttempts",
						"A boundary-valid identity records input use, not a reduction refusal");
			validate(delegatedIdentity, path + "/identity");
			return;
		}
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
			if (seat.execution() == SeatExecution.RETURNED_REJECTED) {
				Judgment rejected = Objects.requireNonNull(seat.rejection());
				boolean complete = rejected.refusedReturn() != null && rejected.refusedReturn().original().equals(j)
						&& rejected.reasonCode() == JudgmentReasonCode.RETURNED_RESULT_REJECTED && seat.cause() == null
						&& seat.participation() == Participation.NOT_RECORDED;
				boolean exclusion = j.status() == JudgmentStatus.NOT_APPLICABLE && seat.notApplicableWhen() == null
						&& contained(rejected) && rejected.reasonCode() == JudgmentReasonCode.UNDECLARED_NOT_APPLICABLE;
				if (!complete && !exclusion)
					defect(path, "seats.rejection", "Rejection contradicts complete original and separate treatment");
			}
			if (seat.execution() == SeatExecution.RETURNED && j.status() == JudgmentStatus.NOT_APPLICABLE
					&& seat.notApplicableWhen() == null
					&& !v.compositeAttempts().stream().anyMatch(a -> a.relation() == CompositeRelation.META_MEMBER)) {
				// Hand-authored Verdict.single is an observation; built-in seat
				// permissions are validated by their producers.
			}

			if (seat.execution() == SeatExecution.CONTAINED_FAILURE && !contained(j))
				defect(path, "seats.execution", "Contained failure must have the synthetic containment shape");
		}
		if (!expectedNames.equals(v.individualByName()))
			defect(path, "individualByName", "Named inputs differ from ordered inputs");
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
						&& recorded != Participation.forJudgment(reductionInputs(v).get(i), v.judgment(), identity(v)))
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
		if (v.rule() == null)
			throw new IllegalArgumentException(path + ": missing retained voting rule");
		var ballots = new ArrayList<Ballot>();
		for (int i = 0; i < v.seats().size(); i++)
			ballots.add(v.seats().get(i).ballot(v.individual().get(i)));
		Judgment recomputed = v.rule().aggregate(ballots);
		if (aggregate.status() != recomputed.status() || !Objects.equals(aggregate.finding(), recomputed.finding())
				|| aggregate.reasonCode() != recomputed.reasonCode()
				|| !Objects.equals(e, recomputed.metadata().get(Judgment.AGGREGATION_KEY)))
			defect(path, "aggregation", "Recorded reduction contradicts retained inputs, weights or rules");
	}

	static List<Judgment> reductionInputs(Verdict v) {
		List<Judgment> inputs = new ArrayList<>();
		for (int i = 0; i < v.seats().size(); i++)
			inputs.add(v.seats().get(i).rejection() == null ? v.individual().get(i) : v.seats().get(i).rejection());
		return List.copyOf(inputs);
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
				if (v.declaredCardinality() == 1 && a.dispositionReason() == DispositionReason.CHILD_UNDECIDED) {
					a.verdict().conclusion();
					defect(path, "compositeAttempts",
							"One boundary-valid UNDECIDED child requires identity, not reduction refusal");
				}
				failed = true;
				continue;
			}
			Verdict child = a.verdict();
			if (v.declaredCardinality() > 1 && child != null
					&& child.provenance().kind() == VerdictProvenanceKind.UNDECIDED)
				defect(path, "compositeAttempts", "Multi-member reduction cannot consume an UNDECIDED aggregate");
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
			boolean stop = routingStops(a.routingRule(), child, a.disposition() == AttemptDisposition.USED);
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
