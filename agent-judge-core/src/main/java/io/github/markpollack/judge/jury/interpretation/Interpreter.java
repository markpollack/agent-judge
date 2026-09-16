/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury.interpretation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.markpollack.judge.description.KeySource;
import io.github.markpollack.judge.jury.AggregationEvidence;
import io.github.markpollack.judge.jury.AttemptDisposition;
import io.github.markpollack.judge.jury.CompositeFailureCode;
import io.github.markpollack.judge.jury.CompositeRelation;
import io.github.markpollack.judge.jury.DecisionBasis;
import io.github.markpollack.judge.jury.DecisionKind;
import io.github.markpollack.judge.jury.DispositionReason;
import io.github.markpollack.judge.jury.ErrorPolicy;
import io.github.markpollack.judge.jury.NotApplicablePolicy;
import io.github.markpollack.judge.jury.TierPolicy;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentReasonCode;
import io.github.markpollack.judge.result.JudgmentStatus;

/**
 * Reads one stored verdict into its {@link Interpretation}.
 *
 * <p>Every reader here is absence-tolerant by construction: an absent member is an
 * {@code ABSENT} defect, an unreadable one {@code UNPARSEABLE}, an unrecognised token
 * {@code UNKNOWN_VOCABULARY}, and a member the vocabulary does not know is ignored. Nothing here
 * throws on the content of the map.
 */
final class Interpreter {

	private static final int MAX_DEPTH = 64;

	private static final String PASS = JudgmentStatus.PASS.wireName();

	private static final String FAIL = JudgmentStatus.FAIL.wireName();

	private static final String ABSTAIN = JudgmentStatus.ABSTAIN.wireName();

	private static final String NOT_APPLICABLE = JudgmentStatus.NOT_APPLICABLE.wireName();

	private static final String ERROR = JudgmentStatus.ERROR.wireName();

	private static final String TIER = DecisionKind.TIER.wireName();

	private static final String OWN = DecisionKind.OWN.wireName();

	private static final String UNDECIDED = DecisionKind.UNDECIDED.wireName();

	private static final String TIER_OUTCOME = DecisionBasis.TIER_OUTCOME.wireName();

	private static final String INDIVIDUAL_REJECTION = DecisionBasis.INDIVIDUAL_REJECTION.wireName();

	private static final String USED = AttemptDisposition.USED.wireName();

	private static final String STAGE_FAILED = AttemptDisposition.STAGE_FAILED.wireName();

	private static final String CASCADE_TIER = CompositeRelation.CASCADE_TIER.wireName();

	private final List<Defect> defects = new ArrayList<>();

	private final List<Stage> stages = new ArrayList<>();

	/** A fact the support check needed was absent, or the rule it needed is not closed-form. */
	private boolean undeterminable;

	private Interpreter() {
	}

	static Interpretation interpret(Map<String, Object> stored) {
		return new Interpreter().run(stored);
	}

	// ==================== The whole ====================

	private Interpretation run(Map<String, Object> verdict) {
		int sourceVersion = verdict.get("decision") != null && verdict.get("seats") != null ? 1 : 0;

		Node rootNode = readVerdict(verdict, "verdict", true);
		Stage root = new Stage(null, List.of(), null, null, null, null, null, null, rootNode.status(),
				rootNode.reasonCode(), rootNode.reasoning(), rootNode.evidence(), rootNode.judges());
		readAttempts(verdict, "verdict", List.of(), 1);

		Walk walk = walk(verdict);
		@Nullable VerdictReading reading = reading(walk, rootNode.status());
		checkSupport(walk, verdict, rootNode);

		boolean contradicted = this.defects.stream().anyMatch(defect -> defect.kind() == DefectKind.INCONSISTENT);
		ReadingSupport support = contradicted ? ReadingSupport.CONTRADICTED
				: (this.undeterminable || reading == null) ? ReadingSupport.UNDETERMINED : ReadingSupport.SUPPORTED;

		Interpretation draft = new Interpretation(Interpretation.SCHEMA_VERSION, sourceVersion, reading, support,
				walk.decidedBy(), root, this.stages, this.defects, "");
		return new Interpretation(draft.schemaVersion(), draft.sourceVersion(), draft.reading(), draft.readingSupport(),
				draft.decidedBy(), draft.root(), draft.stages(), draft.defects(), Summaries.of(draft));
	}

	// ==================== One verdict node ====================

	/** What one verdict node — the root or an attempt's verdict — says on its own. */
	private record Node(@Nullable String status, @Nullable String reasonCode, @Nullable String reasoning,
			@Nullable Evidence evidence, List<JudgeSeat> judges, @Nullable Map<String, Object> aggregated,
			List<String> individualStatuses, boolean evidenceBlockPresent) {
	}

	private Node readVerdict(Map<String, Object> verdict, String path, boolean root) {
		@Nullable Map<String, Object> aggregated = map(verdict.get("aggregated"));
		Facts facts;
		boolean blockPresent = false;
		if (aggregated == null) {
			absent(path, "aggregated", "No aggregate judgment is recorded, so the node has no status to read.");
			facts = new Facts(null, null, null, null, null, List.of());
		}
		else {
			facts = readJudgment(aggregated, path + ".aggregated");
			@Nullable Map<String, Object> metadata = map(aggregated.get("metadata"));
			blockPresent = metadata != null && metadata.get(Judgment.AGGREGATION_KEY) != null;
		}
		@Nullable Evidence evidence = aggregated == null ? null : readEvidence(aggregated, path + ".aggregated");
		List<JudgeSeat> judges = readJudges(verdict, path, root);
		List<String> individualStatuses = new ArrayList<>();
		for (JudgeSeat judge : judges) {
			if (judge.status() != null) {
				individualStatuses.add(judge.status());
			}
		}
		return new Node(facts.status(), facts.reasonCode(), facts.reasoning(), evidence, judges, aggregated,
				individualStatuses, blockPresent);
	}

	// ==================== One judgment ====================

	private record Facts(@Nullable String status, @Nullable String reasonCode, @Nullable String reasoning,
			@Nullable Double score, @Nullable ScoreScale scale, List<Check> checks) {
	}

	private Facts readJudgment(Map<String, Object> judgment, String path) {
		@Nullable String status = readStatus(judgment, path);
		@Nullable String reasonCode = readReasonCode(judgment, path, status);
		@Nullable String reasoning = null;
		Object reasoningValue = judgment.get("reasoning");
		if (reasoningValue == null) {
			absent(path, "reasoning", "No reasoning is recorded.");
		}
		else if (reasoningValue instanceof String text) {
			reasoning = text;
		}
		else {
			unparseable(path, "reasoning", "The reasoning is not text.");
		}
		Score score = readScore(judgment, path);
		return new Facts(status, reasonCode, reasoning, score.value(), score.scale(), readChecks(judgment, path));
	}

	private @Nullable String readStatus(Map<String, Object> judgment, String path) {
		Object value = judgment.get("status");
		if (value == null) {
			absent(path, "status", "No status is recorded.");
			return null;
		}
		if (!(value instanceof String token)) {
			unparseable(path, "status", "The status is not a token.");
			return null;
		}
		for (JudgmentStatus status : JudgmentStatus.values()) {
			if (status.wireName().equals(token) || status.name().equals(token)) {
				return status.wireName();
			}
		}
		unknown(path, "status", "'" + token + "' is not a judgment status this version defines.");
		return token;
	}

	private @Nullable String readReasonCode(Map<String, Object> judgment, String path, @Nullable String status) {
		Object value = judgment.get("reasonCode");
		if (value == null) {
			if (ERROR.equals(status)) {
				absent(path, "reasonCode", "An error requires a reason code naming the instrument cause, and none is "
						+ "recorded; a failure nobody can count is a failure nobody fixes.");
			}
			return null;
		}
		if (!(value instanceof String token)) {
			unparseable(path, "reasonCode", "The reason code is not a token.");
			return null;
		}
		for (JudgmentReasonCode code : JudgmentReasonCode.values()) {
			if (code.wireName().equals(token)) {
				return token;
			}
		}
		unknown(path, "reasonCode", "'" + token + "' is not a reason code this version defines.");
		return token;
	}

	private record Score(@Nullable Double value, @Nullable ScoreScale scale) {
	}

	private Score readScore(Map<String, Object> judgment, String path) {
		Object value = judgment.get("score");
		if (value == null) {
			return new Score(null, null);
		}
		if (value instanceof Number number) {
			double score = number.doubleValue();
			if (Double.isFinite(score) && score >= 0.0 && score <= 1.0) {
				return new Score(score, null);
			}
			unparseable(path, "score", "A bare score must be a number in [0, 1], but was " + value + ". Ignored.");
			return new Score(null, null);
		}
		@Nullable Map<String, Object> object = map(value);
		if (object == null) {
			unparseable(path, "score", "The score is neither a number nor a score object. Ignored.");
			return new Score(null, null);
		}
		Object inner = object.get("value");
		if (inner instanceof Boolean) {
			unparseable(path, "score", "0.13 Score object {value: " + inner + "}; 0.17 scores are numbers in [0,1]. Ignored.");
			return new Score(null, null);
		}
		if (!(inner instanceof Number raw) || !(object.get("min") instanceof Number min)
				|| !(object.get("max") instanceof Number max)) {
			unparseable(path, "score", "A score object must carry a numeric value with its min and max; this one does "
					+ "not. Ignored.");
			return new Score(null, null);
		}
		double low = min.doubleValue();
		double high = max.doubleValue();
		if (!Double.isFinite(low) || !Double.isFinite(high) || high <= low) {
			unparseable(path, "score", "The recorded bounds [" + low + ", " + high + "] are not a usable scale. Ignored.");
			return new Score(null, null);
		}
		ScoreScale scale = new ScoreScale(low, high);
		double normalised = (raw.doubleValue() - low) / (high - low);
		if (!Double.isFinite(normalised) || normalised < 0.0 || normalised > 1.0) {
			unparseable(path, "score", "The value " + raw + " lies outside its recorded scale [" + low + ", " + high
					+ "]. Ignored.");
			return new Score(null, scale);
		}
		return new Score(normalised, scale);
	}

	private List<Check> readChecks(Map<String, Object> judgment, String path) {
		Object value = judgment.get("checks");
		if (value == null) {
			absent(path, "checks", "No checks list is recorded.");
			return List.of();
		}
		@Nullable List<Object> entries = list(value);
		if (entries == null) {
			unparseable(path, "checks", "The checks are not a list.");
			return List.of();
		}
		List<Check> checks = new ArrayList<>();
		for (int index = 0; index < entries.size(); index++) {
			String checkPath = path + ".checks[" + index + "]";
			@Nullable Map<String, Object> entry = map(entries.get(index));
			if (entry == null || !(entry.get("name") instanceof String name) || name.isBlank()
					|| !(entry.get("passed") instanceof Boolean passed)) {
				unparseable(path, "checks[" + index + "]", "A check needs a non-blank name and a boolean result; this "
						+ "one does not have both. Ignored.");
				continue;
			}
			String detail = "";
			Object message = entry.get("message");
			if (message instanceof String text) {
				detail = text;
			}
			else if (message == null) {
				absent(checkPath, "message", "No message is recorded.");
			}
			else {
				unparseable(checkPath, "message", "The message is not text.");
			}
			checks.add(new Check(name, passed, detail));
		}
		return checks;
	}

	// ==================== Evidence ====================

	private @Nullable Evidence readEvidence(Map<String, Object> aggregated, String path) {
		@Nullable Map<String, Object> metadata = map(aggregated.get("metadata"));
		if (metadata == null) {
			return null;
		}
		Object value = metadata.get(Judgment.AGGREGATION_KEY);
		if (value == null) {
			return null;
		}
		@Nullable Map<String, Object> block = map(value);
		String field = "metadata." + Judgment.AGGREGATION_KEY;
		if (block == null) {
			unparseable(path, field, "The aggregation evidence is not an object.");
			return null;
		}
		return new Evidence(text(block, AggregationEvidence.STRATEGY, path, field),
				token(block, AggregationEvidence.ERROR_POLICY, path, field, errorPolicyTokens()),
				token(block, AggregationEvidence.NOT_APPLICABLE_POLICY, path, field, notApplicablePolicyTokens()),
				count(block, AggregationEvidence.INPUT_COUNT, path, field),
				count(block, AggregationEvidence.ELIGIBLE_COUNT, path, field),
				count(block, AggregationEvidence.EXPLICIT_ABSTAIN_COUNT, path, field),
				count(block, AggregationEvidence.NOT_APPLICABLE_COUNT, path, field),
				count(block, AggregationEvidence.ERROR_COUNT, path, field),
				count(block, AggregationEvidence.IGNORED_ERROR_COUNT, path, field),
				count(block, AggregationEvidence.ERRORS_TREATED_AS_ABSTAIN_COUNT, path, field),
				count(block, AggregationEvidence.ERRORS_TREATED_AS_FAIL_COUNT, path, field),
				count(block, AggregationEvidence.NOT_APPLICABLE_TREATED_AS_FAIL_COUNT, path, field),
				originCounts(block, path, field), count(block, AggregationEvidence.PASS_COUNT, path, field),
				count(block, AggregationEvidence.FAIL_COUNT, path, field),
				decimal(block, AggregationEvidence.THRESHOLD, path, field),
				count(block, AggregationEvidence.BINDING_ELIGIBLE_INDEX, path, field),
				decimal(block, AggregationEvidence.INPUT_WEIGHT, path, field),
				decimal(block, AggregationEvidence.ELIGIBLE_WEIGHT, path, field));
	}

	private static Set<String> errorPolicyTokens() {
		Set<String> tokens = new java.util.LinkedHashSet<>();
		for (ErrorPolicy policy : ErrorPolicy.values()) {
			tokens.add(policy.token());
		}
		return tokens;
	}

	private static Set<String> notApplicablePolicyTokens() {
		Set<String> tokens = new java.util.LinkedHashSet<>();
		for (NotApplicablePolicy policy : NotApplicablePolicy.values()) {
			tokens.add(policy.token());
		}
		return tokens;
	}

	private @Nullable String text(Map<String, Object> block, String key, String path, String field) {
		Object value = block.get(key);
		if (value == null) {
			return null;
		}
		if (value instanceof String text) {
			return text;
		}
		unparseable(path, field + "." + key, "Expected text, but the recorded value is " + value + ".");
		return null;
	}

	private @Nullable String token(Map<String, Object> block, String key, String path, String field,
			Set<String> known) {
		@Nullable String value = text(block, key, path, field);
		if (value != null && !known.contains(value)) {
			unknown(path, field + "." + key, "'" + value + "' is not a token this version defines.");
		}
		return value;
	}

	private @Nullable Integer count(Map<String, Object> block, String key, String path, String field) {
		Object value = block.get(key);
		if (value == null) {
			return null;
		}
		if (value instanceof Number number && number.doubleValue() == Math.rint(number.doubleValue())
				&& Math.abs(number.doubleValue()) <= Integer.MAX_VALUE) {
			return number.intValue();
		}
		unparseable(path, field + "." + key, "Expected a whole number, but the recorded value is " + value + ".");
		return null;
	}

	private @Nullable Double decimal(Map<String, Object> block, String key, String path, String field) {
		Object value = block.get(key);
		if (value == null) {
			return null;
		}
		if (value instanceof Number number && Double.isFinite(number.doubleValue())) {
			return number.doubleValue();
		}
		unparseable(path, field + "." + key, "Expected a finite number, but the recorded value is " + value + ".");
		return null;
	}

	private @Nullable Map<String, Long> originCounts(Map<String, Object> block, String path, String field) {
		Object value = block.get(AggregationEvidence.ERROR_CODE_COUNTS);
		if (value == null) {
			return null;
		}
		@Nullable Map<String, Object> counts = map(value);
		String key = field + "." + AggregationEvidence.ERROR_CODE_COUNTS;
		if (counts == null) {
			unparseable(path, key, "Expected an object of cause to count.");
			return null;
		}
		Map<String, Long> origin = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entry : counts.entrySet()) {
			if (entry.getValue() instanceof Number number) {
				origin.put(entry.getKey(), number.longValue());
			}
			else {
				unparseable(path, key + "." + entry.getKey(), "Expected a count, but the recorded value is "
						+ entry.getValue() + ".");
			}
		}
		return origin;
	}

	// ==================== Judges ====================

	private List<JudgeSeat> readJudges(Map<String, Object> verdict, String path, boolean root) {
		@Nullable List<Object> individual = list(verdict.get("individual"));
		@Nullable Map<String, Object> byName = map(verdict.get("individualByName"));
		Object seatsValue = verdict.get("seats");
		List<JudgeSeat> judges = new ArrayList<>();

		if (seatsValue != null) {
			@Nullable List<Object> seats = list(seatsValue);
			if (seats == null) {
				unparseable(path, "seats", "The seats are not a list.");
				return judges;
			}
			for (int index = 0; index < seats.size(); index++) {
				String seatPath = path + ".seats[" + index + "]";
				@Nullable Map<String, Object> seat = map(seats.get(index));
				if (seat == null) {
					unparseable(path, "seats[" + index + "]", "The seat is not an object. Ignored.");
					continue;
				}
				int position = index;
				if (seat.get("position") instanceof Number number) {
					position = number.intValue();
				}
				else {
					unparseable(seatPath, "position", "No readable position; the seat is listed at its index.");
				}
				String name = "";
				if (seat.get("verdictKey") instanceof String key) {
					name = key;
				}
				else {
					absent(seatPath, "verdictKey", "No verdict key; the seat cannot be joined to a judgment by name.");
				}
				@Nullable String keySource = null;
				Object source = seat.get("keySource");
				if (source == null) {
					absent(seatPath, "keySource", "No key source; whether the name is a declared identity is unrecorded.");
				}
				else if (source instanceof String token) {
					keySource = token;
					if (!isKeySource(token)) {
						unknown(seatPath, "keySource", "'" + token + "' is not a key source this version defines.");
					}
				}
				else {
					unparseable(seatPath, "keySource", "The key source is not a token.");
				}
				@Nullable Map<String, Object> judgment = null;
				String judgmentPath = path + ".individual[" + index + "]";
				if (individual != null && index < individual.size()) {
					judgment = map(individual.get(index));
				}
				if (judgment == null && byName != null && !name.isEmpty()) {
					judgment = map(byName.get(name));
					judgmentPath = path + ".individualByName[" + name + "]";
				}
				judges.add(seatFor(position, name, keySource, judgment, judgmentPath));
			}
			return judges;
		}

		if (root) {
			absent(path, "seats", "No seats; judges are keyed by name but whether each name is a declared identity "
					+ "is unrecorded.");
		}
		if (byName != null) {
			int position = 0;
			for (Map.Entry<String, Object> entry : byName.entrySet()) {
				String judgmentPath = path + ".individualByName[" + entry.getKey() + "]";
				judges.add(seatFor(position++, entry.getKey(), null, map(entry.getValue()), judgmentPath));
			}
			return judges;
		}
		if (individual != null) {
			absent(path, "individualByName", "No keyed judgments; the judges are listed by position without names.");
			for (int index = 0; index < individual.size(); index++) {
				judges.add(seatFor(index, "", null, map(individual.get(index)), path + ".individual[" + index + "]"));
			}
			return judges;
		}
		absent(path, "individualByName", "No judgments are recorded.");
		return judges;
	}

	private static boolean isKeySource(String token) {
		for (KeySource source : KeySource.values()) {
			if (source.wireName().equals(token)) {
				return true;
			}
		}
		return false;
	}

	private JudgeSeat seatFor(int position, String name, @Nullable String keySource,
			@Nullable Map<String, Object> judgment, String judgmentPath) {
		if (judgment == null) {
			absent(judgmentPath, "judgment", "The seat has no judgment to read.");
			return new JudgeSeat(position, name, keySource, null, null, null, null, "", List.of());
		}
		Facts facts = readJudgment(judgment, judgmentPath);
		return new JudgeSeat(position, name, keySource, facts.status(), facts.reasonCode(), facts.score(),
				facts.scale(), facts.reasoning() == null ? "" : facts.reasoning(), facts.checks());
	}

	// ==================== Attempts, recursively ====================

	private void readAttempts(Map<String, Object> verdict, String path, List<String> parentPath, int depth) {
		if (depth > MAX_DEPTH) {
			unparseable(path, "compositeAttempts", "The tree is deeper than " + MAX_DEPTH + " levels; not followed.");
			return;
		}
		@Nullable List<Object> attempts = list(verdict.get("compositeAttempts"));
		if (attempts != null) {
			for (int index = 0; index < attempts.size(); index++) {
				String attemptPath = path + ".compositeAttempts[" + index + "]";
				@Nullable Map<String, Object> attempt = map(attempts.get(index));
				if (attempt == null) {
					unparseable(path, "compositeAttempts[" + index + "]", "The attempt is not an object. Ignored.");
					continue;
				}
				readAttempt(attempt, attemptPath, parentPath, depth);
			}
		}
		@Nullable List<Object> subVerdicts = list(verdict.get("subVerdicts"));
		if (subVerdicts != null) {
			for (int index = 0; index < subVerdicts.size(); index++) {
				String subPath = path + ".subVerdicts[" + index + "]";
				@Nullable Map<String, Object> sub = map(subVerdicts.get(index));
				if (sub == null) {
					unparseable(path, "subVerdicts[" + index + "]", "The sub-verdict is not an object. Ignored.");
					continue;
				}
				absent(subPath, "name", "Legacy subVerdicts carry no stage name, relation, policy or disposition.");
				Node node = readVerdict(sub, subPath, false);
				this.stages.add(new Stage(null, parentPath, null, null, null, null, null, null, node.status(),
						node.reasonCode(), node.reasoning(), node.evidence(), node.judges()));
				readAttempts(sub, subPath, parentPath, depth + 1);
			}
		}
	}

	private void readAttempt(Map<String, Object> attempt, String path, List<String> parentPath, int depth) {
		@Nullable String name = null;
		if (attempt.get("name") instanceof String text) {
			name = text;
		}
		else {
			absent(path, "name", "The attempt records no stage name.");
		}
		@Nullable String relation = vocabulary(attempt, "relation", path, relationTokens(), true);
		@Nullable String policy = vocabulary(attempt, "policy", path, policyTokens(), false);
		if (policy == null && CASCADE_TIER.equals(relation)) {
			absent(path, "policy", "A cascade tier requires a policy, and none is recorded.");
		}
		@Nullable String disposition = vocabulary(attempt, "disposition", path, dispositionTokens(), true);
		@Nullable Boolean usedByParent = USED.equals(disposition) ? Boolean.TRUE
				: STAGE_FAILED.equals(disposition) ? Boolean.FALSE : null;
		@Nullable String reason = vocabulary(attempt, "dispositionReason", path, reasonTokens(), false);
		if (reason == null && STAGE_FAILED.equals(disposition)) {
			absent(path, "dispositionReason", "A stage failure requires a reason, and none is recorded.");
		}
		@Nullable String failure = readFailure(attempt, path);

		List<String> ownPath = new ArrayList<>(parentPath);
		if (name != null) {
			ownPath.add(name);
		}
		@Nullable Map<String, Object> verdict = map(attempt.get("verdict"));
		if (verdict == null) {
			if (failure == null) {
				absent(path, "verdict", "The attempt carries neither a verdict nor a failure.");
			}
			this.stages.add(new Stage(name, ownPath, relation, policy, disposition, reason, failure, usedByParent, null,
					null, null, null, List.of()));
			return;
		}
		Node node = readVerdict(verdict, path + ".verdict", false);
		this.stages.add(new Stage(name, ownPath, relation, policy, disposition, reason, failure, usedByParent,
				node.status(), node.reasonCode(), node.reasoning(), node.evidence(), node.judges()));
		readAttempts(verdict, path + ".verdict", ownPath, depth + 1);
	}

	private @Nullable String readFailure(Map<String, Object> attempt, String path) {
		Object value = attempt.get("failure");
		@Nullable String code = null;
		String field = "failure";
		if (value == null) {
			// The consumer's stored projection flattens the code onto the attempt.
			value = attempt.get("failureCode");
			field = "failureCode";
		}
		if (value == null) {
			return null;
		}
		if (value instanceof String text) {
			code = text;
		}
		else {
			@Nullable Map<String, Object> failure = map(value);
			if (failure != null && failure.get("code") instanceof String text) {
				code = text;
			}
		}
		if (code == null) {
			unparseable(path, field, "The failure carries no readable code.");
			return null;
		}
		boolean known = false;
		for (CompositeFailureCode candidate : CompositeFailureCode.values()) {
			known |= candidate.wireName().equals(code);
		}
		if (!known) {
			unknown(path, field, "'" + code + "' is not a failure code this version defines.");
		}
		return code;
	}

	private @Nullable String vocabulary(Map<String, Object> attempt, String key, String path, Set<String> known,
			boolean required) {
		Object value = attempt.get(key);
		if (value == null) {
			if (required) {
				absent(path, key, "The attempt records no " + key + ".");
			}
			return null;
		}
		if (!(value instanceof String token)) {
			unparseable(path, key, "The " + key + " is not a token.");
			return null;
		}
		if (!known.contains(token)) {
			unknown(path, key, "'" + token + "' is not a " + key + " token this version defines.");
		}
		return token;
	}

	private static Set<String> relationTokens() {
		Set<String> tokens = new java.util.LinkedHashSet<>();
		for (CompositeRelation relation : CompositeRelation.values()) {
			tokens.add(relation.wireName());
		}
		return tokens;
	}

	private static Set<String> policyTokens() {
		Set<String> tokens = new java.util.LinkedHashSet<>();
		for (TierPolicy policy : TierPolicy.values()) {
			tokens.add(policy.wireName());
		}
		return tokens;
	}

	private static Set<String> dispositionTokens() {
		Set<String> tokens = new java.util.LinkedHashSet<>();
		for (AttemptDisposition disposition : AttemptDisposition.values()) {
			tokens.add(disposition.wireName());
		}
		return tokens;
	}

	private static Set<String> reasonTokens() {
		Set<String> tokens = new java.util.LinkedHashSet<>();
		for (DispositionReason reason : DispositionReason.values()) {
			tokens.add(reason.wireName());
		}
		return tokens;
	}

	// ==================== The decision walk ====================

	/**
	 * Where the recorded decision chain stops.
	 *
	 * @param decidedBy the deciding stage, or null
	 * @param rejection whether the chain stopped on an individual rejection
	 * @param stopping the verdict node the chain stopped at
	 * @param stoppingPath its path
	 * @param stoppingKind the stopping decision's kind token, or null when none is readable
	 * @param namedTier the tier the stopping decision names, for a rejection
	 */
	private record Walk(@Nullable DecidedBy decidedBy, boolean rejection, Map<String, Object> stopping,
			String stoppingPath, @Nullable String stoppingKind, @Nullable String namedTier) {
	}

	private Walk walk(Map<String, Object> root) {
		Map<String, Object> current = root;
		String path = "verdict";
		List<String> ownPath = new ArrayList<>();
		@Nullable String lastEdge = null;
		for (int hop = 0; hop <= MAX_DEPTH; hop++) {
			Object value = current.get("decision");
			@Nullable DecidedBy viaEdge = lastEdge == null ? null : new DecidedBy(lastEdge, ownPath, TIER_OUTCOME);
			if (value == null) {
				if (lastEdge == null) {
					// A record with no decision at all: the root's own evidence block is the one
					// closed form left, and §7.2 rules a 0.14–0.16 block that agrees SUPPORTED.
					absent(path, "decision", "No decision recorded; which stage decided cannot be established. Not "
							+ "inferred from the root equalling a sub-verdict, from attempt order, or from reasoning "
							+ "text.");
				}
				else {
					absent(path, "decision", "The adopted tier records no decision of its own, so the chain stops at "
							+ "the edge that reached it.");
					this.undeterminable = true;
				}
				return new Walk(viaEdge, false, current, path, null, null);
			}
			@Nullable Map<String, Object> decision = map(value);
			if (decision == null) {
				unparseable(path, "decision", "The decision is not an object.");
				this.undeterminable = true;
				return new Walk(viaEdge, false, current, path, null, null);
			}
			String decisionPath = path + ".decision";
			Object kindValue = decision.get("kind");
			if (!(kindValue instanceof String kind)) {
				if (kindValue == null) {
					absent(decisionPath, "kind", "The decision records no kind.");
				}
				else {
					unparseable(decisionPath, "kind", "The decision kind is not a token.");
				}
				this.undeterminable = true;
				return new Walk(viaEdge, false, current, path, null, null);
			}
			if (OWN.equals(kind) || UNDECIDED.equals(kind)) {
				return new Walk(viaEdge, false, current, path, kind, null);
			}
			if (!TIER.equals(kind)) {
				unknown(decisionPath, "kind", "'" + kind + "' is not a decision kind this version defines.");
				this.undeterminable = true;
				return new Walk(viaEdge, false, current, path, kind, null);
			}
			@Nullable String tier = decision.get("tier") instanceof String name ? name : null;
			if (tier == null) {
				absent(decisionPath, "tier", "A tier decision names no tier; an adopted outcome nobody can attribute "
						+ "is not attributable.");
				this.undeterminable = true;
				return new Walk(viaEdge, false, current, path, kind, null);
			}
			List<String> tierPath = new ArrayList<>(ownPath);
			tierPath.add(tier);
			Object basisValue = decision.get("basis");
			if (!(basisValue instanceof String basis)) {
				absent(decisionPath, "basis", "A tier decision records no basis.");
				this.undeterminable = true;
				return new Walk(viaEdge, false, current, path, kind, null);
			}
			if (INDIVIDUAL_REJECTION.equals(basis)) {
				return new Walk(new DecidedBy(tier, tierPath, basis), true, current, path, kind, tier);
			}
			if (!TIER_OUTCOME.equals(basis)) {
				unknown(decisionPath, "basis", "'" + basis + "' is not a decision basis this version defines; the "
						+ "edge is not followed.");
				this.undeterminable = true;
				return new Walk(new DecidedBy(tier, tierPath, basis), false, current, path, kind, tier);
			}
			// TIER_OUTCOME: the chain continues into the named tier, if the record holds it.
			int index = attemptIndex(current, tier);
			@Nullable Map<String, Object> attempt = index < 0 ? null : attemptAt(current, index);
			if (attempt == null) {
				inconsistent(decisionPath, "tier", "The decision names tier '" + tier
						+ "', which is not a direct cascade tier of this verdict.");
				return new Walk(new DecidedBy(tier, tierPath, basis), false, current, path, kind, tier);
			}
			String attemptPath = path + ".compositeAttempts[" + index + "]";
			Object disposition = attempt.get("disposition");
			if (disposition == null) {
				this.undeterminable = true;
			}
			else if (!USED.equals(disposition)) {
				inconsistent(decisionPath, "basis", "TIER_OUTCOME adopts a tier's own determination, so tier '" + tier
						+ "' must be used, but was " + disposition + ".");
			}
			@Nullable Map<String, Object> child = map(attempt.get("verdict"));
			if (child == null) {
				inconsistent(decisionPath, "tier", "The decision names tier '" + tier
						+ "', which returned no verdict to determine an outcome from.");
				return new Walk(new DecidedBy(tier, tierPath, basis), false, current, path, kind, tier);
			}
			if (!Objects.equals(current.get("aggregated"), child.get("aggregated"))) {
				inconsistent(decisionPath, "basis", "TIER_OUTCOME copies tier '" + tier
						+ "' exactly, but the aggregate differs from the tier's.");
			}
			checkStopCondition(decisionPath, tier, attempt, child);
			@Nullable Map<String, Object> childDecision = map(child.get("decision"));
			if (childDecision != null && UNDECIDED.equals(childDecision.get("kind"))) {
				inconsistent(decisionPath, "basis", "Tier '" + tier + "' was used as a determination, but its own "
						+ "decision says it decided nothing.");
			}
			lastEdge = tier;
			ownPath = tierPath;
			current = child;
			path = attemptPath + ".verdict";
		}
		unparseable(path, "decision", "The decision chain is longer than " + MAX_DEPTH + " edges; not followed.");
		this.undeterminable = true;
		return new Walk(null, false, current, path, null, null);
	}

	/** A used tier stopped the cascade only if its policy's condition held over its individuals. */
	private void checkStopCondition(String decisionPath, String tier, Map<String, Object> attempt,
			Map<String, Object> child) {
		Object policy = attempt.get("policy");
		List<String> statuses = individualStatuses(child);
		if (statuses == null) {
			this.undeterminable = true;
			return;
		}
		if (TierPolicy.REJECT_ON_ANY_FAIL.wireName().equals(policy) && !statuses.contains(FAIL)) {
			inconsistent(decisionPath, "basis", "The outcome of tier '" + tier + "' was adopted under "
					+ "REJECT_ON_ANY_FAIL, which stops only on a failing judge, but no judge in the tier failed.");
		}
		else if (TierPolicy.ACCEPT_ON_ALL_PASS.wireName().equals(policy)
				&& !statuses.stream().allMatch(PASS::equals)) {
			inconsistent(decisionPath, "basis", "The outcome of tier '" + tier + "' was adopted under "
					+ "ACCEPT_ON_ALL_PASS, which stops only when every judge passes, but one did not.");
		}
	}

	private static int attemptIndex(Map<String, Object> verdict, String tier) {
		@Nullable List<Object> attempts = list(verdict.get("compositeAttempts"));
		if (attempts == null) {
			return -1;
		}
		for (int index = 0; index < attempts.size(); index++) {
			@Nullable Map<String, Object> attempt = map(attempts.get(index));
			if (attempt != null && tier.equals(attempt.get("name"))
					&& (attempt.get("relation") == null || CASCADE_TIER.equals(attempt.get("relation")))) {
				return index;
			}
		}
		return -1;
	}

	private static @Nullable Map<String, Object> attemptAt(Map<String, Object> verdict, int index) {
		@Nullable List<Object> attempts = list(verdict.get("compositeAttempts"));
		return attempts == null ? null : map(attempts.get(index));
	}

	/** The statuses of a verdict node's ordered individuals, or null when they cannot be read. */
	private static @Nullable List<String> individualStatuses(Map<String, Object> verdict) {
		@Nullable List<Object> individual = list(verdict.get("individual"));
		if (individual == null) {
			@Nullable Map<String, Object> byName = map(verdict.get("individualByName"));
			if (byName == null) {
				return null;
			}
			individual = new ArrayList<>(byName.values());
		}
		List<String> statuses = new ArrayList<>();
		for (Object entry : individual) {
			@Nullable Map<String, Object> judgment = map(entry);
			if (judgment == null || !(judgment.get("status") instanceof String token)) {
				return null;
			}
			statuses.add(normalisedStatus(token));
		}
		return statuses;
	}

	private static String normalisedStatus(String token) {
		for (JudgmentStatus status : JudgmentStatus.values()) {
			if (status.wireName().equals(token) || status.name().equals(token)) {
				return status.wireName();
			}
		}
		return token;
	}

	// ==================== The reading ====================

	private static @Nullable VerdictReading reading(Walk walk, @Nullable String rootStatus) {
		if (walk.rejection()) {
			return VerdictReading.REJECTED;
		}
		if (rootStatus == null) {
			return null;
		}
		if (ERROR.equals(rootStatus)) {
			return VerdictReading.NOT_ASSESSED;
		}
		if (NOT_APPLICABLE.equals(rootStatus)) {
			return VerdictReading.NOT_APPLICABLE;
		}
		if (FAIL.equals(rootStatus)) {
			return VerdictReading.REJECTED;
		}
		if (PASS.equals(rootStatus)) {
			return VerdictReading.ACCEPTED;
		}
		if (ABSTAIN.equals(rootStatus)) {
			return VerdictReading.UNDECIDED;
		}
		return null;
	}

	// ==================== Reading support ====================

	private void checkSupport(Walk walk, Map<String, Object> root, Node rootNode) {
		if (walk.rejection()) {
			checkRejection(walk);
		}
		else if (UNDECIDED.equals(walk.stoppingKind())) {
			checkUndecided(walk);
		}
		// The evidence test always reads the root's own block, never one found elsewhere in the
		// tree. It is required only where nothing else is closed-form: an own reduction, or a
		// record with no decision at all.
		boolean ownReduction = walk.stoppingKind() == null || OWN.equals(walk.stoppingKind());
		if (rootNode.aggregated() == null) {
			this.undeterminable = true;
			return;
		}
		if (rootNode.evidence() == null) {
			if (ownReduction && !walk.rejection()) {
				if (!rootNode.evidenceBlockPresent()) {
					absent("verdict.aggregated", "metadata." + Judgment.AGGREGATION_KEY, "No aggregation evidence; "
							+ "the recorded status cannot be checked against the reduction that produced it, so the "
							+ "reading is taken from the recorded status alone and not re-derived from prose.");
				}
				this.undeterminable = true;
			}
			return;
		}
		checkEvidence(rootNode.evidence(), rootNode.status(), rootNode.reasonCode(), rootNode.aggregated(),
				"verdict.aggregated");
	}

	private void checkUndecided(Walk walk) {
		@Nullable Map<String, Object> aggregated = map(walk.stopping().get("aggregated"));
		if (aggregated == null) {
			this.undeterminable = true;
			return;
		}
		Object status = aggregated.get("status");
		String statusToken = status instanceof String token ? normalisedStatus(token) : "";
		if (!ERROR.equals(statusToken)) {
			inconsistent(walk.stoppingPath() + ".decision", "kind", "An undecided decision reports that the "
					+ "instrument reached no outcome, so its aggregate must be an error with a machinery reason code, "
					+ "but the recorded status is " + status + ".");
			return;
		}
		Object code = aggregated.get("reasonCode");
		if (!(code instanceof String token)) {
			this.undeterminable = true;
			return;
		}
		@Nullable JudgmentReasonCode known = knownCode(token);
		if (known == null) {
			this.undeterminable = true;
		}
		else if (known.originFamily() != JudgmentReasonCode.OriginFamily.MACHINERY) {
			inconsistent(walk.stoppingPath() + ".decision", "kind", "An undecided decision requires a machinery reason "
					+ "code, but the recorded code " + token + " names " + known.originFamily() + ".");
		}
	}

	private void checkRejection(Walk walk) {
		String decisionPath = walk.stoppingPath() + ".decision";
		String tier = Objects.requireNonNull(walk.namedTier(), "a rejection names its tier");
		int index = attemptIndex(walk.stopping(), tier);
		@Nullable Map<String, Object> attempt = index < 0 ? null : attemptAt(walk.stopping(), index);
		if (attempt == null) {
			inconsistent(decisionPath, "tier", "The decision names tier '" + tier
					+ "', which is not a direct cascade tier of this verdict.");
			return;
		}
		Object disposition = attempt.get("disposition");
		Object reason = attempt.get("dispositionReason");
		Object policy = attempt.get("policy");
		if (disposition == null || reason == null) {
			this.undeterminable = true;
		}
		else if (!STAGE_FAILED.equals(disposition)
				|| DispositionReason.EXECUTION_FAILED.wireName().equals(reason)) {
			inconsistent(decisionPath, "basis", "INDIVIDUAL_REJECTION means tier '" + tier
					+ "' returned a verdict the cascade could not use, so the attempt must be stage_failed with "
					+ "child_undecided or undeclared_not_applicable, but was " + disposition + " / " + reason + ".");
		}
		else if (!DispositionReason.CHILD_UNDECIDED.wireName().equals(reason)
				&& !DispositionReason.UNDECLARED_NOT_APPLICABLE.wireName().equals(reason)) {
			this.undeterminable = true;
		}
		if (policy == null) {
			this.undeterminable = true;
		}
		else if (!TierPolicy.REJECT_ON_ANY_FAIL.wireName().equals(policy)) {
			inconsistent(decisionPath, "basis", "Only REJECT_ON_ANY_FAIL stops on an individual rejection, but tier '"
					+ tier + "' uses " + policy + ".");
		}
		@Nullable Map<String, Object> child = map(attempt.get("verdict"));
		if (child == null) {
			inconsistent(decisionPath, "tier", "The decision names tier '" + tier
					+ "', which returned no verdict to establish a rejection in.");
			return;
		}
		@Nullable List<String> statuses = individualStatuses(child);
		if (statuses == null) {
			this.undeterminable = true;
		}
		else if (!statuses.contains(FAIL)) {
			inconsistent(decisionPath, "basis", "INDIVIDUAL_REJECTION requires a genuine failing judge in tier '" + tier
					+ "', and none is recorded; a broken stage on its own justifies nothing.");
		}
		@Nullable Map<String, Object> rootAggregate = map(walk.stopping().get("aggregated"));
		@Nullable Map<String, Object> childAggregate = map(child.get("aggregated"));
		if (rootAggregate == null || childAggregate == null) {
			this.undeterminable = true;
			return;
		}
		if (DispositionReason.CHILD_UNDECIDED.wireName().equals(reason) && !rootAggregate.equals(childAggregate)) {
			inconsistent(decisionPath, "basis", "A child_undecided rejection keeps the child's own machinery error as "
					+ "the root aggregate, but tier '" + tier + "' differs.");
		}
		if (DispositionReason.UNDECLARED_NOT_APPLICABLE.wireName().equals(reason)) {
			Object childStatus = childAggregate.get("status");
			if (childStatus instanceof String token && !NOT_APPLICABLE.equals(normalisedStatus(token))) {
				inconsistent(decisionPath, "basis", "undeclared_not_applicable says tier '" + tier
						+ "' returned an exclusion, but its aggregate is " + token + ".");
			}
			if (!JudgmentReasonCode.STAGE_FAILED.wireName().equals(rootAggregate.get("reasonCode"))) {
				inconsistent(decisionPath, "basis", "A rejection on a boundary-refused exclusion builds a "
						+ "parent-authored error coded stage_failed, but the root is coded "
						+ rootAggregate.get("reasonCode") + ".");
			}
		}
	}

	/** The closed-form expectation a strategy's evidence produces. */
	private record Expectation(@Nullable String status, @Nullable String reasonCode, boolean tie,
			@Nullable String missing) {

		static Expectation of(String status) {
			return new Expectation(status, null, false, null);
		}

		static Expectation error(JudgmentReasonCode code) {
			return new Expectation(ERROR, code.wireName(), false, null);
		}

		static Expectation missing(String key) {
			return new Expectation(null, null, false, key);
		}

		static Expectation open() {
			return new Expectation(null, null, false, null);
		}

	}

	private void checkEvidence(Evidence evidence, @Nullable String status, @Nullable String reasonCode,
			Map<String, Object> aggregated, String path) {
		if (status == null || knownStatus(status) == null) {
			this.undeterminable = true;
			return;
		}
		Expectation expected = expect(evidence, aggregated);
		if (expected.missing() != null) {
			absent(path, "metadata." + Judgment.AGGREGATION_KEY + "." + expected.missing(), "The " + evidence.strategy()
					+ " rule needs " + expected.missing() + " to check the recorded status, and it is not recorded.");
			this.undeterminable = true;
			return;
		}
		if (expected.tie()) {
			if (!(PASS.equals(status) || FAIL.equals(status) || ABSTAIN.equals(status))) {
				inconsistent(path, "status", "A majority tie resolves to pass, fail or abstain by the tie policy, but "
						+ "the recorded status is " + status + ".");
			}
			return;
		}
		if (expected.status() == null) {
			this.undeterminable = true;
			return;
		}
		if (!expected.status().equals(status)) {
			inconsistent(path, "status", describe(evidence) + " is " + expected.status() + ", but the recorded status is "
					+ status + ".");
			return;
		}
		if (expected.reasonCode() != null && reasonCode != null && !expected.reasonCode().equals(reasonCode)) {
			inconsistent(path, "reasonCode", describe(evidence) + " exits with " + expected.reasonCode()
					+ ", but the recorded reason code is " + reasonCode + ".");
		}
	}

	private static Expectation expect(Evidence e, Map<String, Object> aggregated) {
		if (e.strategy() == null) {
			return Expectation.missing(AggregationEvidence.STRATEGY);
		}
		@Nullable Integer notApplicable = e.notApplicableCount();
		@Nullable Integer errors = e.errorCount();
		if (NotApplicablePolicy.REFUSE.token().equals(e.notApplicablePolicy()) && notApplicable != null
				&& notApplicable > 0) {
			return Expectation.error(JudgmentReasonCode.NOT_APPLICABLE_REFUSED);
		}
		if (ErrorPolicy.PROPAGATE.token().equals(e.errorPolicy()) && errors != null && errors > 0) {
			return Expectation.error(JudgmentReasonCode.ERRORS_PROPAGATED);
		}
		if (ErrorPolicy.TREAT_AS_FAIL.token().equals(e.errorPolicy()) && errors != null && errors > 0
				&& e.errorCodeCounts() != null && hasMachineryOrigin(e.errorCodeCounts())) {
			return Expectation.error(JudgmentReasonCode.ERRORS_PROPAGATED);
		}
		@Nullable Integer eligible = e.eligibleCount();
		if (eligible == null) {
			return Expectation.missing(AggregationEvidence.ELIGIBLE_COUNT);
		}
		if (eligible == 0) {
			boolean allExcluded = NotApplicablePolicy.EXCLUDE.token().equals(e.notApplicablePolicy())
					&& notApplicable != null && notApplicable > 0 && notApplicable.equals(e.inputCount());
			return Expectation.of(allExcluded ? NOT_APPLICABLE : ABSTAIN);
		}
		@Nullable Integer passes = e.passCount();
		@Nullable Integer fails = e.failCount();
		switch (e.strategy()) {
			case "consensus" -> {
				if (passes == null) {
					return Expectation.missing(AggregationEvidence.PASS_COUNT);
				}
				if (fails == null) {
					return Expectation.missing(AggregationEvidence.FAIL_COUNT);
				}
				return Expectation.of(passes.equals(eligible) ? PASS : fails.equals(eligible) ? FAIL : ABSTAIN);
			}
			case "majority" -> {
				if (passes == null) {
					return Expectation.missing(AggregationEvidence.PASS_COUNT);
				}
				if (fails == null) {
					return Expectation.missing(AggregationEvidence.FAIL_COUNT);
				}
				if (passes.equals(fails)) {
					return new Expectation(null, null, true, null);
				}
				return Expectation.of(passes > fails ? PASS : FAIL);
			}
			case "allMustPass" -> {
				if (fails == null) {
					return Expectation.missing(AggregationEvidence.FAIL_COUNT);
				}
				return Expectation.of(fails == 0 ? PASS : FAIL);
			}
			case "average", "weightedAverage", "median", "conjunctive" -> {
				if (e.threshold() == null) {
					return Expectation.missing(AggregationEvidence.THRESHOLD);
				}
				if (!(aggregated.get("score") instanceof Number score)) {
					return Expectation.open();
				}
				return Expectation.of(score.doubleValue() >= e.threshold() ? PASS : FAIL);
			}
			default -> {
				return Expectation.open();
			}
		}
	}

	private static boolean hasMachineryOrigin(Map<String, Long> origin) {
		if (origin.isEmpty()) {
			return true;
		}
		for (String key : origin.keySet()) {
			@Nullable JudgmentReasonCode code = knownCode(key);
			if (code == null || code.originFamily() == JudgmentReasonCode.OriginFamily.MACHINERY) {
				return true;
			}
		}
		return false;
	}

	private static String describe(Evidence e) {
		StringBuilder text = new StringBuilder(String.valueOf(e.strategy()));
		if (e.passCount() != null || e.failCount() != null) {
			text.append(" of ").append(e.passCount()).append(" pass and ").append(e.failCount()).append(" fail");
		}
		if (e.eligibleCount() != null) {
			text.append(" among ").append(e.eligibleCount()).append(" eligible");
		}
		if (e.errorCount() != null && e.errorCount() > 0) {
			text.append(" with ").append(e.errorCount()).append(" error(s) under ").append(e.errorPolicy());
		}
		if (e.notApplicableCount() != null && e.notApplicableCount() > 0) {
			text.append(" with ").append(e.notApplicableCount()).append(" not applicable under ")
				.append(e.notApplicablePolicy());
		}
		return text.toString();
	}

	private static @Nullable JudgmentStatus knownStatus(String token) {
		for (JudgmentStatus status : JudgmentStatus.values()) {
			if (status.wireName().equals(token)) {
				return status;
			}
		}
		return null;
	}

	private static @Nullable JudgmentReasonCode knownCode(String token) {
		for (JudgmentReasonCode code : JudgmentReasonCode.values()) {
			if (code.wireName().equals(token)) {
				return code;
			}
		}
		return null;
	}

	// ==================== Defects and map access ====================

	private void absent(String path, String field, String note) {
		this.defects.add(new Defect(path, field, DefectKind.ABSENT, note));
	}

	private void unparseable(String path, String field, String note) {
		this.defects.add(new Defect(path, field, DefectKind.UNPARSEABLE, note));
	}

	private void unknown(String path, String field, String note) {
		this.defects.add(new Defect(path, field, DefectKind.UNKNOWN_VOCABULARY, note));
	}

	private void inconsistent(String path, String field, String note) {
		this.defects.add(new Defect(path, field, DefectKind.INCONSISTENT, note));
	}

	@SuppressWarnings("unchecked")
	private static @Nullable Map<String, Object> map(@Nullable Object value) {
		if (!(value instanceof Map<?, ?> map)) {
			return null;
		}
		for (Object key : map.keySet()) {
			if (!(key instanceof String)) {
				return null;
			}
		}
		return (Map<String, Object>) map;
	}

	@SuppressWarnings("unchecked")
	private static @Nullable List<Object> list(@Nullable Object value) {
		return value instanceof List<?> list ? (List<Object>) list : null;
	}

}
