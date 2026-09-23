/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.jury.interpretation;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.jspecify.annotations.Nullable;

import io.github.markpollack.judge.jury.*;
import io.github.markpollack.judge.result.*;

/**
 * Strict version-2 semantic reading; historical records never pass through this reader.
 */
final class ModernInterpreter {

	private static final JsonMapper MAPPER = JsonMapper.builder()
		.disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
		.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
		.enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
		.enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
		.build();

	private final List<Defect> defects = new ArrayList<>();

	private final List<Stage> stages = new ArrayList<>();

	private boolean undetermined;

	private ModernInterpreter() {
	}

	static boolean containsModern(Map<String, Object> value) {
		return containsModern(value, new IdentityHashMap<>(), 0);
	}

	private static boolean containsModern(@Nullable Object value, IdentityHashMap<Object, Boolean> seen, int depth) {
		if (depth > 64 || value == null || seen.put(value, Boolean.TRUE) != null)
			return false;
		if (!(value instanceof Map<?, ?> map))
			return false;
		if (map.containsKey("schemaVersion"))
			return true;
		if (modernJudgment(map.get("aggregated"), seen, depth + 1))
			return true;
		if (map.get("individual") instanceof List<?> inputs)
			for (Object input : inputs)
				if (modernJudgment(input, seen, depth + 1))
					return true;
		if (map.get("individualByName") instanceof Map<?, ?> inputs)
			for (Object input : inputs.values())
				if (modernJudgment(input, seen, depth + 1))
					return true;
		if (map.get("compositeAttempts") instanceof List<?> attempts)
			for (Object attempt : attempts)
				if (attempt instanceof Map<?, ?> entry && containsModern(entry.get("verdict"), seen, depth + 1))
					return true;
		if (map.get("subVerdicts") instanceof List<?> children)
			for (Object child : children)
				if (containsModern(child, seen, depth + 1))
					return true;
		return false;
	}

	private static boolean modernJudgment(@Nullable Object value, IdentityHashMap<Object, Boolean> seen, int depth) {
		if (!(value instanceof Map<?, ?> map) || depth > 64 || seen.put(value, Boolean.TRUE) != null)
			return false;
		for (String field : List.of("schemaVersion", "producerStatus", "assessment", "certainty", "distribution",
				"provenance", "policyApplication"))
			if (map.containsKey(field))
				return true;
		if (map.get("checks") instanceof List<?> checks)
			for (Object check : checks)
				if (check instanceof Map<?, ?> c && c.containsKey("judgment"))
					return true;
		return false;
	}

	static Interpretation interpret(Map<String, Object> stored) {
		ModernInterpreter reader = new ModernInterpreter();
		Object stamp = stored.get("schemaVersion");
		int source = stamp instanceof Integer integer ? integer
				: stamp instanceof Long number && number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE
						? number.intValue() : -1;
		try {
			bounded(stored, new IdentityHashMap<>(), 0, new int[] { 0 });
			requireVersions(stored, "verdict");
			Verdict verdict = MAPPER.convertValue(stored, Verdict.class);
			Stage root = reader.stage(verdict, null, List.of(), null);
			reader.validate(verdict, "verdict");
			reader.attempts(verdict, List.of(), "verdict");
			VerdictReading reading = reading(verdict);
			DecidedBy decided = reader.decidedBy(verdict);
			return reader.finish(2, reading, root, decided);
		}
		catch (IllegalArgumentException ex) {
			reader.defects.add(new Defect("verdict", "schemaVersion/semantics", DefectKind.UNPARSEABLE,
					"Unsupported modern result: " + Objects.toString(ex.getMessage(), ex.getClass().getSimpleName())));
			reader.undetermined = true;
			return reader.finish(source, null,
					new Stage(null, List.of(), null, null, null, null, null, null, null, null, null, null, List.of()),
					null);
		}
	}

	private Interpretation finish(int source, @Nullable VerdictReading reading, Stage root,
			@Nullable DecidedBy decided) {
		// Modern defects never authorize a successful subject determination.
		boolean supported = defects.isEmpty() && !undetermined;
		Interpretation draft = new Interpretation(2, source, supported ? reading : null,
				supported ? ReadingSupport.SUPPORTED : ReadingSupport.UNDETERMINED, decided, root, stages, defects, "");
		return new Interpretation(2, source, draft.reading(), draft.readingSupport(), decided, root, stages, defects,
				Summaries.of(draft));
	}

	private static void bounded(@Nullable Object value, IdentityHashMap<Object, Boolean> active, int depth,
			int[] count) {
		if (++count[0] > 100000 || depth > 128)
			throw new IllegalArgumentException("Result exceeds portable traversal bounds");
		if (value instanceof Map<?, ?> map) {
			if (active.put(map, Boolean.TRUE) != null)
				throw new IllegalArgumentException("Cyclic result");
			for (var entry : map.entrySet()) {
				if (!(entry.getKey() instanceof String))
					throw new IllegalArgumentException("Non-string object key");
				bounded(entry.getValue(), active, depth + 1, count);
			}
			active.remove(map);
		}
		else if (value instanceof List<?> list) {
			if (active.put(list, Boolean.TRUE) != null)
				throw new IllegalArgumentException("Cyclic result");
			for (Object element : list)
				bounded(element, active, depth + 1, count);
			active.remove(list);
		}
	}

	private static Map<?, ?> object(@Nullable Object value, String path) {
		if (!(value instanceof Map<?, ?> map))
			throw new IllegalArgumentException(path + " must be an object");
		return map;
	}

	private static List<?> array(@Nullable Object value, String path) {
		if (!(value instanceof List<?> list))
			throw new IllegalArgumentException(path + " must be an array");
		return list;
	}

	private static void version(Map<?, ?> value, String path) {
		Object version = value.get("schemaVersion");
		if (!(version instanceof Integer || version instanceof Long || version instanceof java.math.BigInteger)
				|| !"2".equals(version.toString()))
			throw new IllegalArgumentException(path + ".schemaVersion must be integer 2");
	}

	private static void requireVersions(Map<?, ?> verdict, String path) {
		version(verdict, path);
		if (!verdict.containsKey("declaredCardinality"))
			throw new IllegalArgumentException(path + ".declaredCardinality is required");
		judgmentVersion(object(verdict.get("aggregated"), path + ".aggregated"), path + ".aggregated");
		for (Object judgment : array(verdict.get("individual"), path + ".individual"))
			judgmentVersion(object(judgment, path), path + ".individual");
		for (Object judgment : object(verdict.get("individualByName"), path + ".individualByName").values())
			judgmentVersion(object(judgment, path), path + ".individualByName");
		object(verdict.get("weights"), path + ".weights");
		object(verdict.get("decision"), path + ".decision");
		for (Object seat : array(verdict.get("seats"), path + ".seats")) {
			Map<?, ?> seated = object(seat, path + ".seats");
			if (!seated.containsKey("position") || !seated.containsKey("execution"))
				throw new IllegalArgumentException(path + ".seat requires position and execution");
		}
		for (Object attempt : array(verdict.get("compositeAttempts"), path + ".compositeAttempts")) {
			Map<?, ?> entry = object(attempt, path);
			if (entry.get("verdict") != null)
				requireVersions(object(entry.get("verdict"), path), path + ".attempt.verdict");
		}
	}

	private static void judgmentVersion(Map<?, ?> value, String path) {
		version(value, path);
		for (Object child : array(value.get("checks"), path + ".checks")) {
			judgmentVersion(object(object(child, path).get("judgment"), path), path + ".check.judgment");
		}
	}

	private Stage stage(Verdict verdict, @Nullable String name, List<String> path, @Nullable CompositeAttempt attempt) {
		Judgment j = verdict.aggregated();
		List<JudgeSeat> judges = new ArrayList<>();
		for (int index = 0; index < verdict.seats().size(); index++) {
			Seat seat = verdict.seats().get(index);
			Judgment input = verdict.individual().get(index);
			JudgmentView view = JudgmentView.of(input);
			judges.add(new JudgeSeat(seat.position(), seat.verdictKey(), seat.keySource().wireName(), view.status(),
					view.reasonCode(), input.score(), null, input.operationalReasoning(), view.checks(), view,
					seat.execution().name()));
		}
		Evidence evidence = verdict.decision().kind() == DecisionKind.OWN && !identity(verdict)
				? Interpreter.reduction(j).evidence() : null;
		return new Stage(name, path, attempt == null ? null : attempt.relation().wireName(),
				attempt == null || attempt.policy() == null ? null : attempt.policy().wireName(),
				attempt == null ? null : attempt.disposition().wireName(),
				attempt == null || attempt.dispositionReason() == null ? null : attempt.dispositionReason().wireName(),
				null, attempt == null ? null : attempt.disposition() == AttemptDisposition.USED, j.status().wireName(),
				j.operationalReasonCode() == null ? null : j.operationalReasonCode().wireName(),
				j.operationalReasoning(), evidence, judges, JudgmentView.of(j), verdict.declaredCardinality(),
				verdict.decision());
	}

	private void attempts(Verdict verdict, List<String> parent, String path) {
		int index = 0;
		for (CompositeAttempt attempt : verdict.compositeAttempts()) {
			List<String> own = new ArrayList<>(parent);
			own.add(attempt.name());
			String childPath = path + ".compositeAttempts[" + index++ + "].verdict";
			Verdict child = attempt.verdict();
			if (child == null) {
				stages.add(new Stage(attempt.name(), own, attempt.relation().wireName(),
						attempt.policy() == null ? null : attempt.policy().wireName(), attempt.disposition().wireName(),
						attempt.dispositionReason() == null ? null : attempt.dispositionReason().wireName(),
						attempt.failure() == null ? null : attempt.failure().code().wireName(), false, null, null, null,
						null, List.of()));
			}
			else {
				stages.add(stage(child, attempt.name(), own, attempt));
				if (attempt.disposition() == AttemptDisposition.USED)
					validate(child, childPath);
				attempts(child, own, childPath);
			}
		}
	}

	private void defect(String path, String field, String explanation) {
		defects.add(new Defect(path, field, DefectKind.INCONSISTENT, explanation));
	}

	private void validate(Verdict v, String path) {
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
			defect(path, "decision", "A valid single declared result requires OWN whole-value identity");
		if (v.decision().kind() == DecisionKind.OWN) {
			if (identity(v))
				return;
			if (count == 1 && v.seats().size() == 1 && v.seats().get(0).execution() == SeatExecution.RETURNED) {
				defect(path, "aggregated", "One valid declared input requires complete semantic identity");
				return;
			}
			Judgment rawAggregate = raw(v.aggregated());
			Interpreter.Reduction reduction = Interpreter.reduction(rawAggregate);
			for (Defect d : reduction.defects())
				defects.add(new Defect(path + ".aggregated", d.field(), d.kind(), d.note()));
			if (reduction.undetermined())
				undetermined = true;
			Evidence e = reduction.evidence();
			if (e != null)
				validateReduction(v, rawAggregate, e, path);
		}
		else if (v.decision().kind() == DecisionKind.TIER) {
			CompositeAttempt selected = v.compositeAttempts()
				.stream()
				.filter(a -> a.name().equals(v.decision().tier()))
				.findFirst()
				.orElseThrow();
			Verdict child = selected.verdict();
			if (child != null && count != child.declaredCardinality())
				defect(path, "declaredCardinality", "Selected tier cardinality was not copied");
		}
	}

	private static Judgment raw(Judgment j) {
		return new Judgment(j.producerStatus(), j.assessment(), j.certainty(), j.distribution(), j.reasonCode(),
				j.reasoning(), j.checks(), j.provenance(), null, j.metadata());
	}

	private void validateReduction(Verdict v, Judgment aggregate, Evidence e, String path) {
		if (e.strategy() == null || e.errorPolicy() == null || e.notApplicablePolicy() == null) {
			undetermined = true;
			return;
		}
		ErrorPolicy errors = java.util.Arrays.stream(ErrorPolicy.values())
			.filter(p -> p.token().equals(e.errorPolicy()))
			.findFirst()
			.orElse(null);
		NotApplicablePolicy exclusions = java.util.Arrays.stream(NotApplicablePolicy.values())
			.filter(p -> p.token().equals(e.notApplicablePolicy()))
			.findFirst()
			.orElse(null);
		if (errors == null || exclusions == null) {
			undetermined = true;
			return;
		}
		double threshold = e.threshold() == null ? 0.5 : e.threshold();
		// Historical majority evidence omits the configured tie policy. Each documented
		// tie
		// outcome is admissible; no particular configured policy is inferred or reported.
		TiePolicy tie = aggregate.producerStatus() == JudgmentStatus.PASS ? TiePolicy.PASS
				: aggregate.producerStatus() == JudgmentStatus.FAIL ? TiePolicy.FAIL : TiePolicy.ABSTAIN;
		VotingStrategy strategy = switch (e.strategy()) {
			case "consensus" -> new ConsensusStrategy(errors, exclusions);
			case "majority" -> new MajorityVotingStrategy(tie, errors, exclusions);
			case "allMustPass" -> new AllMustPassStrategy(errors, exclusions);
			case "average" -> new AverageVotingStrategy(threshold, errors, exclusions);
			case "median" -> new MedianVotingStrategy(threshold, errors, exclusions);
			case "weightedAverage" -> new WeightedAverageStrategy(threshold, errors, exclusions);
			case "conjunctive" -> new ConjunctiveStrategy(threshold, errors, exclusions);
			default -> null;
		};
		if (strategy == null) {
			undetermined = true;
			return;
		}
		Judgment recomputed = strategy.aggregate(v.individual(), v.weights());
		if (aggregate.producerStatus() != recomputed.producerStatus()
				|| !Objects.equals(aggregate.assessment(), recomputed.assessment())
				|| aggregate.reasonCode() != recomputed.reasonCode()
				|| !Objects.equals(aggregate.metadata().get(Judgment.AGGREGATION_KEY),
						recomputed.metadata().get(Judgment.AGGREGATION_KEY))) {
			defect(path, "aggregation",
					"Recorded reduction contradicts its retained operational inputs, weights or policies");
		}
	}

	private static boolean contained(Judgment j) {
		return j.producerStatus() == JudgmentStatus.ERROR && j.policyApplication() == null && j.assessment() == null
				&& j.certainty() == null && j.distribution() == null && j.provenance() == null && j.checks().isEmpty()
				&& j.metadata().isEmpty()
				&& Set
					.of(JudgmentReasonCode.JUDGE_FAILED, JudgmentReasonCode.JUDGE_METADATA_UNREADABLE,
							JudgmentReasonCode.UNDECLARED_NOT_APPLICABLE)
					.contains(j.reasonCode());
	}

	private static boolean identity(Verdict v) {
		return v.declaredCardinality() == 1 && v.individual().size() == 1 && v.seats().size() == 1
				&& v.seats().get(0).position() == 0 && v.seats().get(0).execution() == SeatExecution.RETURNED
				&& v.decision().kind() == DecisionKind.OWN && v.aggregated().equals(v.individual().get(0))
				&& v.aggregated().equals(v.individualByName().get(v.seats().get(0).verdictKey()));
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
					|| !v.individual().get(index).equals(child.aggregated()))
				defect(path, "seats", "Meta seats must match used members at configured positions");
			index++;
		}
		if (index != v.seats().size())
			defect(path, "seats", "Unexpected meta seat");
		if (failed && (v.decision().kind() != DecisionKind.UNDECIDED
				|| v.aggregated().operationalReasonCode() != JudgmentReasonCode.STAGE_FAILED))
			defect(path, "decision", "Failed meta member requires terminal stage failure");
	}

	private static boolean boundedTier(Verdict child) {
		if (!identity(child) || !child.compositeAttempts().isEmpty())
			return false;
		Judgment j = child.aggregated();
		return j.status() == JudgmentStatus.ERROR || j.status() == JudgmentStatus.NOT_APPLICABLE
				|| j.policyApplication() instanceof AppliedPolicy;
	}

	private void validateCascade(Verdict v, String path) {
		boolean assessment = v.compositeAttempts()
			.stream()
			.anyMatch(a -> a.policy() == TierPolicy.STOP_ON_USABLE_ASSESSMENT);
		boolean stopped = false;
		for (int i = 0; i < v.compositeAttempts().size(); i++) {
			CompositeAttempt a = v.compositeAttempts().get(i);
			Verdict child = a.verdict();
			boolean bounded = a.policy() == TierPolicy.STOP_ON_USABLE_ASSESSMENT
					|| (assessment && a.policy() == TierPolicy.FINAL_TIER);
			if (stopped)
				defect(path, "compositeAttempts", "An attempt follows a terminal routing outcome");
			if (bounded && a.disposition() == AttemptDisposition.STAGE_FAILED) {
				stopped = true;
				if (a.dispositionReason() != DispositionReason.INVALID_TIER_RESULT
						&& a.dispositionReason() != DispositionReason.EXECUTION_FAILED)
					defect(path, "dispositionReason",
							"Bounded tier failure must retain execution or invalid-result cause");
				if (a.dispositionReason() == DispositionReason.INVALID_TIER_RESULT && child != null
						&& boundedTier(child))
					defect(path, "dispositionReason", "Valid bounded identity cannot be marked invalid");
				if (v.decision().kind() != DecisionKind.UNDECIDED
						|| v.aggregated().operationalReasonCode() != JudgmentReasonCode.STAGE_FAILED
						|| v.declaredCardinality() != 0 || !v.seats().isEmpty())
					defect(path, "decision", "Bounded tier failure requires an empty parent machinery error");
				continue;
			}
			if (bounded && child != null && !boundedTier(child))
				defect(path, "compositeAttempts", "Bounded tier requires a one-seat applied-policy identity");
			boolean stop = a.policy() == TierPolicy.FINAL_TIER;
			if (child != null) {
				if (a.policy() == TierPolicy.STOP_ON_USABLE_ASSESSMENT) {
					Judgment j = child.aggregated();
					stop = !(j.status() == JudgmentStatus.ABSTAIN && j.policyApplication() instanceof AppliedPolicy p
							&& p.action() == AcceptanceAction.ESCALATE);
				}
				else if (a.policy() == TierPolicy.REJECT_ON_ANY_FAIL)
					stop = child.individual().stream().anyMatch(j -> j.status() == JudgmentStatus.FAIL);
				else if (a.policy() == TierPolicy.ACCEPT_ON_ALL_PASS)
					stop = a.disposition() == AttemptDisposition.USED && !child.individual().isEmpty()
							&& child.individual().stream().allMatch(j -> j.status() == JudgmentStatus.PASS);
			}
			if (stop) {
				stopped = true;
				if (a.disposition() == AttemptDisposition.USED
						&& (v.decision().kind() != DecisionKind.TIER || !a.name().equals(v.decision().tier())))
					defect(path, "decision", "The first stopping tier must be selected");
			}
			else if (a.name().equals(v.decision().tier()))
				defect(path, "decision", "Selected tier's policy requires continuation");
		}
		if (!stopped)
			defect(path, "compositeAttempts", "Cascade ended without a stopping or final attempt");
		if (v.decision().kind() == DecisionKind.OWN)
			defect(path, "decision", "Cascade cannot claim an own reduction");
	}

	private @Nullable DecidedBy decidedBy(Verdict v) {
		List<String> path = new ArrayList<>();
		Verdict current = v;
		Decision last = v.decision();
		while (current.decision().kind() == DecisionKind.TIER) {
			last = current.decision();
			String name = Objects.requireNonNull(last.tier());
			path.add(name);
			Verdict next = current.compositeAttempts()
				.stream()
				.filter(a -> a.name().equals(name))
				.findFirst()
				.orElseThrow()
				.verdict();
			if (next == null || last.basis() == DecisionBasis.INDIVIDUAL_REJECTION)
				break;
			current = next;
		}
		return path.isEmpty() ? null
				: new DecidedBy(path.get(path.size() - 1), path, Objects.requireNonNull(last.basis()).wireName());
	}

	private static VerdictReading reading(Verdict v) {
		Verdict deciding = v;
		while (deciding.decision().kind() == DecisionKind.TIER) {
			if (deciding.decision().basis() == DecisionBasis.INDIVIDUAL_REJECTION)
				return VerdictReading.REJECTED;
			String tier = deciding.decision().tier();
			Verdict next = deciding.compositeAttempts()
				.stream()
				.filter(a -> a.name().equals(tier))
				.findFirst()
				.orElseThrow()
				.verdict();
			if (next == null)
				break;
			deciding = next;
		}
		return switch (v.aggregated().status()) {
			case PASS -> VerdictReading.ACCEPTED;
			case FAIL -> VerdictReading.REJECTED;
			case ABSTAIN -> VerdictReading.UNDECIDED;
			case NOT_APPLICABLE -> VerdictReading.NOT_APPLICABLE;
			case ERROR -> VerdictReading.NOT_ASSESSED;
		};
	}

}
