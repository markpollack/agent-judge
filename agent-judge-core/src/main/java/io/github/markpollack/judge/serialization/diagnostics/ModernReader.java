/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.serialization.diagnostics;

import java.util.*;
import org.jspecify.annotations.Nullable;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.markpollack.judge.jury.*;
import io.github.markpollack.judge.judgment.Judgment;

/** Stored diagnostics. Modern meaning is always derived by Verdict.conclusion(). */
final class ModernReader {

	private final List<Stage> stages = new ArrayList<>();

	private ModernReader() {
	}

	private static final ObjectMapper MAPPER = JsonMapper.builder()
		.disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
		.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
		.enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
		.enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
		.build();

	static Verdict read(Map<String, Object> stored) {
		bounded(stored, new IdentityHashMap<>(), 0, new int[] { 0 });
		return new io.github.markpollack.judge.serialization.VerdictCodec().read(stored);
	}

	static StoredReading interpret(Map<String, Object> stored) {
		try {
			return inspect(read(stored));
		}
		catch (IllegalArgumentException ex) {
			int version = stored.get("schemaVersion") instanceof Number n ? n.intValue() : -1;
			var root = new Stage(null, List.of(), null, null, null, null, null, null, null, null, null, null,
					List.of());
			return new StoredReading(3, version, null, ReadingSupport.UNDETERMINED, null, root, List.of(),
					List.of(new Defect("verdict", "schemaVersion/semantics", DefectKind.UNPARSEABLE,
							Objects.toString(ex.getMessage(), "Invalid record"))),
					"Source: unsupported version " + version + ". Unsupported stored verdict: " + ex.getMessage());
		}
	}

	static StoredReading inspect(Verdict verdict) {
		Verdict.Conclusion conclusion = verdict.conclusion();
		ModernReader reader = new ModernReader();
		Stage root = reader.stage(verdict, null, List.of(), null);
		reader.attempts(verdict, List.of(), "verdict");
		RequirementOutcome outcome = switch (conclusion) {
			case PASS -> RequirementOutcome.SATISFIED;
			case FAIL -> RequirementOutcome.VIOLATED;
			case INCONCLUSIVE ->
				verdict.judgment().status() == io.github.markpollack.judge.judgment.JudgmentStatus.ERROR
						? RequirementOutcome.NOT_ASSESSED : RequirementOutcome.UNRESOLVED;
			case NOT_APPLICABLE -> RequirementOutcome.NOT_APPLICABLE;
		};
		var draft = new StoredReading(3, 4, outcome, ReadingSupport.SUPPORTED, reader.decidedBy(verdict), root,
				reader.stages, List.of(), "");
		return new StoredReading(3, 4, outcome, ReadingSupport.SUPPORTED, draft.decidedBy(), root, reader.stages,
				List.of(), Summaries.of(draft));
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
		if (modernJudgment(map.get("judgment"), seen, depth + 1)
				|| modernJudgment(map.get("aggregated"), seen, depth + 1))
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
		for (String field : List.of("schemaVersion", "producerStatus", "finding", "confidence",
				"probabilityDistribution", "provenance", "policyApplication", "assessment", "certainty",
				"distribution"))
			if (map.containsKey(field))
				return true;
		if (map.get("checks") instanceof List<?> checks)
			for (Object check : checks)
				if (check instanceof Map<?, ?> c && c.containsKey("judgment"))
					return true;
		return false;
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

	private Stage stage(Verdict verdict, @Nullable String name, List<String> path, @Nullable CompositeAttempt attempt) {
		Judgment j = verdict.judgment();
		List<JudgeSeat> judges = new ArrayList<>();
		for (int index = 0; index < verdict.seats().size(); index++) {
			Seat seat = verdict.seats().get(index);
			Judgment input = verdict.individual().get(index);
			JudgmentView view = JudgmentView.of(input);
			judges.add(new JudgeSeat(seat.position(), seat.verdictKey(), seat.keySource().wireName(), view.status(),
					view.reasonCode(), input.score(), null, input.reasoning(), view.checks(), view,
					seat.execution().name()));
		}
		Evidence evidence = verdict.provenance().kind() == VerdictProvenanceKind.OWN
				&& verdict.declaredCardinality() != 1 ? Interpreter.reduction(j).evidence() : null;
		return new Stage(name, path, attempt == null ? null : attempt.relation().wireName(),
				attempt == null || attempt.routingRule() == null ? null : attempt.routingRule().wireName(),
				attempt == null ? null : attempt.disposition().wireName(),
				attempt == null || attempt.dispositionReason() == null ? null : attempt.dispositionReason().wireName(),
				null, attempt == null ? null : attempt.disposition() == AttemptDisposition.USED, j.status().wireName(),
				j.reasonCode() == null ? null : j.reasonCode().wireName(), j.reasoning(), evidence, judges,
				JudgmentView.of(j), verdict.declaredCardinality(), verdict.provenance());
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
						attempt.routingRule() == null ? null : attempt.routingRule().wireName(),
						attempt.disposition().wireName(),
						attempt.dispositionReason() == null ? null : attempt.dispositionReason().wireName(),
						attempt.failure() == null ? null : attempt.failure().code().wireName(), false, null, null, null,
						null, List.of()));
			}
			else {
				stages.add(stage(child, attempt.name(), own, attempt));
				if (attempt.disposition() == AttemptDisposition.USED)
					child.conclusion();
				attempts(child, own, childPath);
			}
		}
	}

	private @Nullable DecidedBy decidedBy(Verdict v) {
		List<String> path = new ArrayList<>();
		Verdict current = v;
		VerdictProvenance last = v.provenance();
		while (current.provenance().kind() == VerdictProvenanceKind.TIER) {
			last = current.provenance();
			String name = Objects.requireNonNull(last.tier());
			path.add(name);
			Verdict next = current.compositeAttempts()
				.stream()
				.filter(a -> a.name().equals(name))
				.findFirst()
				.orElseThrow()
				.verdict();
			if (next == null || last.basis() == VerdictProvenanceBasis.INDIVIDUAL_REJECTION)
				break;
			current = next;
		}
		return path.isEmpty() ? null
				: new DecidedBy(path.get(path.size() - 1), path, Objects.requireNonNull(last.basis()).wireName());
	}

}
