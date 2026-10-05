/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.verdict;

import io.github.markpollack.judge.voting.Participation;
import io.github.markpollack.judge.voting.RetainedRule;
import io.github.markpollack.judge.voting.VotingStrategy;
import io.github.markpollack.judge.voting.Ballot;
import io.github.markpollack.judge.voting.AllEligiblePassStrategy;
import io.github.markpollack.judge.voting.ErrorHandling;
import io.github.markpollack.judge.voting.ExclusionHandling;

import java.util.ArrayList;
import org.jspecify.annotations.Nullable;
import io.github.markpollack.judge.requirement.Requirement;
import io.github.markpollack.judge.provenance.Invocation;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentReasonCode;
import io.github.markpollack.judge.judgment.JudgmentStatus;

/**
 * The Jury's complete conclusion: its collective Judgment, individual Judgments and the
 * composition history explaining how that conclusion was reached.
 *
 * <p>
 * The judgment and individual values describe the root result. {@code seats} records
 * where each judgment sat and under what key, so the ordered list and the keyed map can
 * be joined without guessing. {@code provenance} says how the collective Judgment was
 * produced. {@code compositeAttempts} contains the complete ordered evidence for each
 * direct stage entered by a composite jury; a leaf verdict has an empty attempt list.
 * </p>
 *
 * <h2>Reading a composite verdict</h2>
 * <p>
 * A cascade copies its aggregate from the tier that stopped it, so counting the root
 * <em>and</em> the tiers counts that tier twice. {@link #provenance()} is what makes the
 * copy visible: follow a {@link VerdictProvenanceKind#TIER} provenance into the attempt
 * it names rather than counting the root as a reduction of its own.
 * </p>
 *
 * @param requirement optional actual requirement for this node
 * @param reductionFailure failed reduction code with original exception in memory, or
 * null
 * @param declaredCardinality original configured population, before any failed or
 * excluded input
 * @param judgment the final collective judgment
 * @param individual the ordered judgments contributing at this root
 * @param individualByName those judgments keyed by configured identity in insertion order
 * @param seats one seat per entry of {@code individual}, joining position to verdict key
 * @param provenance what produced {@code judgment}
 * @param compositeAttempts complete ordered direct composite attempts
 * @author Mark Pollack
 * @since 0.1.0
 * @param roster complete ordered independent requirement coverage, empty for ordinary
 * composition
 * @param invocations owned immutable shared native observations
 * @param rule complete retained reduction rule, or null when no reduction happened
 */
@JsonPropertyOrder({ "schemaVersion", "declaredCardinality", "judgment", "individual", "individualByName", "seats",
		"provenance", "compositeAttempts" })

public record Verdict(Judgment judgment, List<Judgment> individual, Map<String, Judgment> individualByName,
		List<Seat> seats, VerdictProvenance provenance, List<CompositeAttempt> compositeAttempts,
		@JsonProperty(required = true) int declaredCardinality, @Nullable Requirement<?> requirement,
		@Nullable CompositeFailure reductionFailure, List<Requirement<?>> roster, List<Invocation> invocations,
		@Nullable RetainedRule rule) {

	/**
	 * Construct a complete advanced record with no declared reduction rule. This path can
	 * retain refused records; requireUsable refuses unsupported reductions.
	 * @param judgment aggregate
	 * @param individual originals
	 * @param individualByName named originals
	 * @param seats complete seat facts
	 * @param provenance origin
	 * @param compositeAttempts retained children
	 * @param declaredCardinality configured population
	 * @param requirement actual requirement
	 * @param reductionFailure failed reduction
	 * @param roster ordered coverage
	 * @param invocations shared native owners
	 */
	public Verdict(Judgment judgment, List<Judgment> individual, Map<String, Judgment> individualByName,
			List<Seat> seats, VerdictProvenance provenance, List<CompositeAttempt> compositeAttempts,
			int declaredCardinality, @Nullable Requirement<?> requirement, @Nullable CompositeFailure reductionFailure,
			List<Requirement<?>> roster, List<Invocation> invocations) {
		this(judgment, individual, individualByName, seats, provenance, compositeAttempts, declaredCardinality,
				requirement, reductionFailure, roster, invocations, null);
	}

	/**
	 * Validate conclusion, complete preservation bounds and native-reference closure.
	 * @return this unchanged complete Verdict
	 */
	public Verdict requireUsable() {
		var roots = new ArrayList<Judgment>();
		collectJudgments(this, roots);
		io.github.markpollack.judge.judgment.JudgmentBounds.validateForest(roots, this);
		conclusion();
		InvocationRecords.of(this);
		return this;
	}

	private static void collectJudgments(Verdict node, List<Judgment> roots) {
		roots.add(node.judgment());
		roots.addAll(node.individual());
		for (var seat : node.seats())
			if (seat.rejection() != null)
				roots.add(seat.rejection());
		for (var attempt : node.compositeAttempts())
			if (attempt.verdict() != null)
				collectJudgments(attempt.verdict(), roots);
	}

	/**
	 * Constructs a non-roster record with no shared invocation ownership.
	 * @param judgment collective judgment
	 * @param individual ordered opinions
	 * @param individualByName names
	 * @param seats seats
	 * @param provenance composition provenance
	 * @param compositeAttempts entered children
	 * @param declaredCardinality declared population
	 * @param requirement actual parent, if any
	 * @param reductionFailure original reduction failure, if any
	 */
	public Verdict(Judgment judgment, List<Judgment> individual, Map<String, Judgment> individualByName,
			List<Seat> seats, VerdictProvenance provenance, List<CompositeAttempt> compositeAttempts,
			int declaredCardinality, @Nullable Requirement<?> requirement,
			@Nullable CompositeFailure reductionFailure) {
		this(judgment, individual, individualByName, seats, provenance, compositeAttempts, declaredCardinality,
				requirement, reductionFailure, List.of(), List.of());
	}

	/** The conclusion established by the retained judging/composition record. */
	public enum Conclusion {

		/** The check passed. */
		PASS,
		/** The check failed. */
		FAIL,
		/** Neither pass nor fail was established. */
		INCONCLUSIVE,
		/** The check does not apply. */
		NOT_APPLICABLE

	}

	/**
	 * Derive the conclusion using typed domain rules.
	 * @return the conclusion
	 * @throws IllegalArgumentException if the record is contradictory or its aggregation
	 * semantics are unavailable
	 */
	public Conclusion conclusion() {
		return VerdictSemantics.conclusion(this);
	}

	/**
	 * Associate the actual supplied requirement with this node, preserving its complete
	 * record.
	 * @param supplied actual requirement
	 * @return associated verdict
	 * @throws IllegalArgumentException if this node already names another requirement
	 */
	public Verdict forRequirement(Requirement<?> supplied) {
		Objects.requireNonNull(supplied, "requirement");
		if (requirement != null && !Requirement.equivalent(requirement, supplied))
			throw new IllegalArgumentException("Verdict already belongs to another requirement");
		return new Verdict(judgment, individual, individualByName, seats, provenance, compositeAttempts,
				declaredCardinality, supplied, reductionFailure, roster, invocations, rule);
	}

	/**
	 * Construct a record without a reduction failure.
	 * @param judgment collective judgment
	 * @param individual ordered producer judgments
	 * @param individualByName named judgments
	 * @param seats seat facts
	 * @param provenance collective origin
	 * @param compositeAttempts entered children
	 * @param declaredCardinality configured population
	 * @param requirement actual requirement or null for ordinary checks
	 */
	public Verdict(Judgment judgment, List<Judgment> individual, Map<String, Judgment> individualByName,
			List<Seat> seats, VerdictProvenance provenance, List<CompositeAttempt> compositeAttempts,
			int declaredCardinality, @Nullable Requirement<?> requirement) {
		this(judgment, individual, individualByName, seats, provenance, compositeAttempts, declaredCardinality,
				requirement, null);
	}

	/**
	 * Construct an evidence-only record with explicit cardinality.
	 * @param judgment collective judgment
	 * @param individual individual judgments
	 * @param individualByName keyed judgments
	 * @param seats seat facts
	 * @param provenance collective origin
	 * @param compositeAttempts complete attempts
	 * @param declaredCardinality configured population
	 */
	public Verdict(Judgment judgment, List<Judgment> individual, Map<String, Judgment> individualByName,
			List<Seat> seats, VerdictProvenance provenance, List<CompositeAttempt> compositeAttempts,
			int declaredCardinality) {
		this(judgment, individual, individualByName, seats, provenance, compositeAttempts, declaredCardinality, null);
	}

	/**
	 * Construct a domain verdict declaring exactly the supplied seat population.
	 * @param judgment aggregate
	 * @param individual ordered inputs
	 * @param individualByName named inputs
	 * @param seats seats
	 * @param provenance provenance
	 * @param compositeAttempts attempts
	 */
	public Verdict(Judgment judgment, List<Judgment> individual, Map<String, Judgment> individualByName,
			List<Seat> seats, VerdictProvenance provenance, List<CompositeAttempt> compositeAttempts) {
		this(judgment, individual, individualByName, seats, provenance, compositeAttempts, seats.size(), null);
	}

	/** Validate, bound, and defensively copy all verdict components. */
	public Verdict {
		roster = List.copyOf(Objects.requireNonNull(roster, "roster"));
		roster.forEach(Requirement::validate);
		invocations = List.copyOf(Objects.requireNonNull(invocations, "invocations"));
		if (requirement != null)
			Requirement.validate(requirement);
		if (declaredCardinality < 0)
			throw new IllegalArgumentException("negative declaredCardinality");
		Objects.requireNonNull(judgment, "aggregated judgment must not be null");
		if (reductionFailure != null && (reductionFailure.code() != CompositeFailureCode.AGGREGATION_FAILED
				|| judgment.reasonCode() != JudgmentReasonCode.AGGREGATION_FAILED
				|| provenance.kind() != VerdictProvenanceKind.UNDECIDED))
			throw new IllegalArgumentException("Reduction failure must accompany an undecided aggregation failure");
		individual = List.copyOf(Objects.requireNonNull(individual, "individual must not be null"));
		individualByName = immutableLinkedMap(
				Objects.requireNonNull(individualByName, "individualByName must not be null"));

		Objects.requireNonNull(seats, "seats must not be null");
		seats = List.copyOf(seats);
		Objects.requireNonNull(provenance, "provenance must not be null");
		Objects.requireNonNull(compositeAttempts, "compositeAttempts must not be null");
		compositeAttempts = List.copyOf(compositeAttempts);
		CompositeBounds.validateTree(compositeAttempts);
		requireCoherentSeats(individual, individualByName, seats);
		requireCoherentDecision(judgment, individual, individualByName, seats, provenance, compositeAttempts);
		if (provenance.kind() == VerdictProvenanceKind.TIER) {
			Verdict selected = compositeAttempts.stream()
				.filter(a -> a.name().equals(provenance.tier()))
				.findFirst()
				.orElseThrow()
				.verdict();
			if (selected != null && !Objects.equals(rule, selected.rule()))
				throw new IllegalArgumentException("Selected tier retained rule differs from copied root rule");
		}
	}

	/**
	 * Enforce that the seats really do join the ordered list to the keyed map.
	 * <p>
	 * A seat list that does not line up is worse than none: a reader would attribute a
	 * judgment to the wrong judge and have no way to notice.
	 * </p>
	 * @param individual the ordered judgments
	 * @param individualByName the keyed judgments
	 * @param seats the seats
	 */
	private static void requireCoherentSeats(List<Judgment> individual, Map<String, Judgment> individualByName,
			List<Seat> seats) {
		if (seats.size() != individual.size()) {
			throw new IllegalArgumentException("seats must have one entry per individual judgment, but had "
					+ seats.size() + " for " + individual.size());
		}
		int previous = -1;
		Set<String> keys = new LinkedHashSet<>();
		for (Seat seat : seats) {
			if (seat.position() <= previous) {
				throw new IllegalArgumentException("seat positions must be unique and strictly increasing, but "
						+ seat.position() + " followed " + previous);
			}
			previous = seat.position();
			if (!individualByName.containsKey(seat.verdictKey())) {
				throw new IllegalArgumentException("seat " + seat.position() + " is keyed '" + seat.verdictKey()
						+ "', which is not a key of individualByName");
			}
			keys.add(seat.verdictKey());
		}
		// Duplicate keys are legal — two seats can share one map entry when their judges
		// declare
		// the same name — so the map holds the distinct keys, in first-occurrence order.
		if (!keys.equals(individualByName.keySet())) {
			throw new IllegalArgumentException(
					"individualByName holds " + individualByName.keySet() + ", but the seats are keyed " + keys);
		}
	}

	/**
	 * Enforce what each provenance kind claims.
	 * <p>
	 * A provenance is a claim about where the aggregate came from, and a reader counts on
	 * it without being able to check it. So each claim is checked here, once, against the
	 * evidence the verdict itself carries.
	 * </p>
	 * @param judgment the aggregate
	 * @param individual the ordered judgments
	 * @param individualByName the keyed judgments
	 * @param seats the seats
	 * @param provenance the provenance
	 * @param attempts the direct attempts
	 */
	private static void requireCoherentDecision(Judgment judgment, List<Judgment> individual,
			Map<String, Judgment> individualByName, List<Seat> seats, VerdictProvenance provenance,
			List<CompositeAttempt> attempts) {
		if (provenance.kind() == VerdictProvenanceKind.UNDECIDED) {
			JudgmentReasonCode code = judgment.reasonCode();
			if (judgment.status() != JudgmentStatus.ERROR || code == null
					|| code.originFamily() != JudgmentReasonCode.OriginFamily.MACHINERY) {
				throw new IllegalArgumentException("an UNDECIDED verdict reports that the instrument reached no "
						+ "outcome, so its aggregate must be an ERROR with a machinery reason code, but was "
						+ judgment.status() + " / " + code);
			}
			return;
		}
		if (provenance.kind() != VerdictProvenanceKind.TIER) {
			return;
		}

		String name = Objects.requireNonNull(provenance.tier());
		CompositeAttempt attempt = attempts.stream()
			.filter(candidate -> candidate.relation() == CompositeRelation.CASCADE_TIER
					&& candidate.name().equals(name))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("decision names tier '" + name
					+ "', which is not a direct cascade tier of this verdict; tier names are local"));
		Verdict tierVerdict = attempt.verdict();
		if (tierVerdict == null) {
			throw new IllegalArgumentException(
					"decision names tier '" + name + "', which returned no verdict to determine an outcome from");
		}

		if (provenance.basis() == VerdictProvenanceBasis.TIER_OUTCOME) {
			if (attempt.disposition() != AttemptDisposition.USED) {
				throw new IllegalArgumentException("TIER_OUTCOME adopts a tier's own determination, so tier '" + name
						+ "' must be USED, but was " + attempt.disposition());
			}
			if (!judgment.equals(tierVerdict.judgment())) {
				throw new IllegalArgumentException(
						"TIER_OUTCOME copies tier '" + name + "' exactly, but the aggregate differs");
			}
		}
		else {
			requireIndividualRejection(judgment, name, attempt, tierVerdict);
		}
		requireCopiedFrom(name, individual, individualByName, seats, tierVerdict);
	}

	/**
	 * Enforce the preconditions of a stop on an individual rejection.
	 * @param judgment the root aggregate
	 * @param name the tier's name
	 * @param attempt the tier's attempt
	 * @param tierVerdict the tier's verdict
	 */
	private static void requireIndividualRejection(Judgment judgment, String name, CompositeAttempt attempt,
			Verdict tierVerdict) {
		DispositionReason reason = attempt.dispositionReason();
		boolean acceptedUndecided = attempt.disposition() == AttemptDisposition.USED
				&& tierVerdict.provenance().kind() == VerdictProvenanceKind.UNDECIDED;
		if (!acceptedUndecided) {
			throw new IllegalArgumentException("INDIVIDUAL_REJECTION means tier '" + name
					+ "' must be an accepted UNDECIDED input for individual rejection, but was " + attempt.disposition()
					+ " / " + reason);
		}
		if (attempt.routingRule() != RoutingRule.STOP_ON_ANY_OPINION_FAIL) {
			throw new IllegalArgumentException(
					"only STOP_ON_ANY_OPINION_FAIL stops on an individual rejection, but tier '" + name + "' uses "
							+ attempt.routingRule());
		}
		if (VerdictSemantics.routingOpinions(tierVerdict)
			.stream()
			.noneMatch(individualJudgment -> individualJudgment.status() == JudgmentStatus.FAIL)) {
			throw new IllegalArgumentException("INDIVIDUAL_REJECTION requires a genuine FAIL among tier '" + name
					+ "' individuals; a broken stage on its own justifies nothing");
		}
		if (acceptedUndecided || reason == DispositionReason.CHILD_UNDECIDED) {
			if (!judgment.equals(tierVerdict.judgment())) {
				throw new IllegalArgumentException("a CHILD_UNDECIDED rejection keeps the child's own machinery error "
						+ "as the root aggregate, but tier '" + name + "' differs");
			}
			return;
		}
		// UNDECLARED_NOT_APPLICABLE: the child's aggregate is an exclusion the cascade
		// refused,
		// so the root cannot be a copy of it. The parent authors a machinery error
		// instead, and
		// the child's verdict stays unchanged on its attempt.
		if (judgment.reasonCode() != JudgmentReasonCode.STAGE_FAILED) {
			throw new IllegalArgumentException("a rejection on a boundary-refused exclusion builds a parent-authored "
					+ "ERROR stage_failed root, but tier '" + name + "' produced " + judgment.reasonCode());
		}
	}

	/**
	 * Enforce that everything a cascade copies really was copied.
	 * @param name the tier's name
	 * @param individual the root's ordered judgments
	 * @param individualByName the root's keyed judgments
	 * @param seats the root's seats
	 * @param tierVerdict the tier's verdict
	 */
	private static void requireCopiedFrom(String name, List<Judgment> individual,
			Map<String, Judgment> individualByName, List<Seat> seats, Verdict tierVerdict) {
		if (!individual.equals(tierVerdict.individual()) || !individualByName.equals(tierVerdict.individualByName())
				|| !seats.equals(tierVerdict.seats())) {
			throw new IllegalArgumentException("a cascade that stops on tier '" + name
					+ "' copies its individuals, map, weights and seats; they differ here");
		}
	}

	private static <K, V> Map<K, V> immutableLinkedMap(Map<K, V> source) {
		if (source == null || source.isEmpty()) {
			return Map.of();
		}
		return Collections.unmodifiableMap(new LinkedHashMap<>(source));
	}

	/**
	 * Deliberate advanced retained-record construction, including refused children.
	 * @return new builder instance
	 */
	public static Builder advancedBuilder() {
		return new Builder();
	}

	/**
	 * Retains an already observed answer with a local applicability declaration, without
	 * invoking a Judge or fabricating an opinion execution. Useful for shared native
	 * batches.
	 * @param name declared item/seat identity
	 * @param original original domain answer
	 * @param notApplicableWhen explicit local exclusion permission, or null
	 * @return complete leaf retaining original and any separate treatment
	 */
	public static Verdict observed(String name, Judgment original, @Nullable String notApplicableWhen) {
		name = CompositeNames.requireValidName(name);
		Objects.requireNonNull(original);
		Judgment returned = original;
		boolean complete = original.refusedReturn() != null;
		if (complete)
			original = original.refusedReturn().original();
		boolean rejected = complete || original.notApplicable() && notApplicableWhen == null;
		Judgment treatment = complete ? returned
				: rejected ? Judgment.error(
						io.github.markpollack.judge.judgment.JudgmentReasonCode.UNDECLARED_NOT_APPLICABLE,
						"Seat '" + name + "' returned NOT_APPLICABLE without local permission") : original;
		Judgment collective = rejected ? new AllEligiblePassStrategy(ErrorHandling.PROPAGATE, ExclusionHandling.EXCLUDE)
			.aggregate(io.github.markpollack.judge.voting.Ballots.of(List.of(treatment))) : original;
		Seat seat = new Seat(0, name, KeySource.DECLARED,
				rejected ? SeatExecution.RETURNED_REJECTED : SeatExecution.RETURNED,
				complete ? io.github.markpollack.judge.voting.Participation.NOT_RECORDED
						: Participation.forJudgment(treatment, collective, !rejected),
				null, notApplicableWhen, rejected ? treatment : null);
		return Verdict.advancedBuilder()
			.judgment(collective)
			.rule(rejected
					? RetainedRule.of(new AllEligiblePassStrategy(ErrorHandling.PROPAGATE, ExclusionHandling.EXCLUDE))
					: null)
			.individual(List.of(original))
			.individualByName(Map.of(name, original))
			.seats(List.of(seat))
			.declaredCardinality(1)
			.provenance(rejected ? VerdictProvenance.decisionFor(collective) : VerdictProvenance.own())
			.build();
	}

	/**
	 * Create the complete verdict of a one-member jury.
	 * @param name non-blank identity of the sole judge
	 * @param judgment the judge's result and therefore the jury's aggregate
	 * @return a complete one-member verdict
	 */
	public static Verdict single(String name, Judgment judgment) {
		Objects.requireNonNull(name, "name must not be null");
		Objects.requireNonNull(judgment, "judgment must not be null");
		if (judgment.refusedReturn() != null)
			return observed(name, judgment, null);
		if (name.isBlank()) {
			throw new IllegalArgumentException("name must be non-blank");
		}
		return advancedBuilder().judgment(judgment)
			.individual(List.of(judgment))
			.individualByName(Map.of(name, judgment))
			.seats(List.of(new Seat(0, name, KeySource.DECLARED).treated(Participation.IDENTITY)))
			.provenance(VerdictProvenance.own())
			.build();
	}

	/**
	 * Create the complete verdict of a jury whose judges all declared a distinct name.
	 * <p>
	 * This is the ordinary hand-built case: one judgment per declared name, seated in the
	 * order the map hands them over, judgment by this jury itself. The ordered
	 * {@link #individual()} list is the map's values in encounter order, seat <em>i</em>
	 * keys the <em>i</em>-th entry as {@link KeySource#DECLARED}, and the provenance is
	 * {@link VerdictProvenance#own()}. It produces exactly what the full builder call
	 * produces:
	 * </p>
	 * <pre>{@code
	 * Map<String, Judgment> byName = new LinkedHashMap<>();
	 * byName.put("style", styleResult);
	 * byName.put("coverage", coverageResult);
	 *
	 * Verdict.of(aggregate, byName);
	 *
	 * // the same verdict, written out
	 * Verdict.advancedBuilder()
	 *     .judgment(aggregate)
	 *     .individual(List.of(styleResult, coverageResult))
	 *     .individualByName(byName)
	 *     .seats(List.of(new Seat(0, "style", KeySource.DECLARED),
	 *                    new Seat(1, "coverage", KeySource.DECLARED)))
	 *     .provenance(VerdictProvenance.own())
	 *     .build();
	 * }</pre>
	 * <p>
	 * <b>Pass an ordered map.</b> Both the ordered list and the seats are taken from the
	 * same encounter order, so whatever order the map iterates in is the order the
	 * judgments are reported and attributed in. {@link java.util.LinkedHashMap} and
	 * {@link java.util.SequencedMap} keep the order they were populated in;
	 * {@link Map#of(Object, Object) Map.of} does not specify one, so use it only when the
	 * positions genuinely do not matter.
	 * </p>
	 * <p>
	 * <b>What this does not cover.</b> A map key is unique and a map value is one
	 * judgment, so this factory cannot express two seats sharing one key — which is how a
	 * duplicate declared name is recorded — and it claims {@code DECLARED} for every key,
	 * which a positional key such as {@code "Judge#2"} is not. It also seats the entries
	 * at 0..n-1 with no gaps, where a meta-jury that could not use a member leaves one.
	 * Those verdicts are built with {@link #builder()} and explicit seats. For a
	 * one-judge jury, {@link #single} says so more directly.
	 * </p>
	 * @param judgment the jury's own aggregate of the given judgments
	 * @param individualByName one judgment per declared judge name, in seating order
	 * @return a complete multi-judgment verdict
	 * @throws NullPointerException if either argument, a key, or a judgment is null
	 * @throws IllegalArgumentException if the map is empty or any key is blank
	 *
	 * @since 0.17.0
	 */
	public static Verdict of(Judgment judgment, Map<String, Judgment> individualByName) {
		Objects.requireNonNull(judgment, "aggregated judgment must not be null");
		Objects.requireNonNull(individualByName, "individualByName must not be null");
		if (individualByName.isEmpty()) {
			throw new IllegalArgumentException("individualByName must hold at least one judgment; "
					+ "a verdict that reduced nothing is built with the builder and an UNDECIDED decision");
		}
		List<Judgment> individual = new ArrayList<>(individualByName.size());
		List<Seat> seats = new ArrayList<>(individualByName.size());
		for (Map.Entry<String, Judgment> entry : individualByName.entrySet()) {
			String name = Objects.requireNonNull(entry.getKey(), "a judge name must not be null");
			if (name.isBlank()) {
				throw new IllegalArgumentException("a judge name must be non-blank");
			}
			individual
				.add(Objects.requireNonNull(entry.getValue(), "the judgment for '" + name + "' must not be null"));
			seats.add(new Seat(seats.size(), name, KeySource.DECLARED));
		}
		return advancedBuilder().judgment(judgment)
			.individual(individual)
			.individualByName(individualByName)
			.seats(seats)
			.provenance(VerdictProvenance.own())
			.build();
	}

	/**
	 * Guided ordinary result construction.
	 * @return choice of single observation or panel reduction
	 */
	public static KindStage builder() {
		return new KindStage();
	}

	/**
	 * Select one compatible composition shape; advanced retained children use
	 * advancedBuilder.
	 */
	public static final class KindStage {

		private KindStage() {
		}

		/**
		 * Select a single observed producer.
		 * @param name retained name
		 * @return judgment stage
		 */
		public SingleStage single(String name) {
			return judgment -> () -> Verdict.single(name, judgment);
		}

		/**
		 * Select an actual immutable pure panel rule.
		 * @param strategy configured rule
		 * @return first opinion stage
		 */
		public FirstOpinionStage panel(VotingStrategy strategy) {
			return new PanelBuilder(RetainedRule.of(strategy));
		}

	}

	/** Supply the actual original before building a single observation. */
	public interface SingleStage {

		/**
		 * Retain the original without execution.
		 * @param judgment complete original
		 * @return ready single
		 */
		ReadyVerdict judgment(Judgment judgment);

	}

	/** Complete inert result construction. */
	public interface ReadyVerdict {

		/**
		 * Build a usable completed record.
		 * @return complete result
		 */
		Verdict build();

	}

	/** Add the first observed opinion before a panel can be built. */
	public interface FirstOpinionStage {

		/**
		 * Add an unweighted observed opinion.
		 * @param name label
		 * @param original complete original
		 * @return ready panel
		 */
		PanelStage opinion(String name, Judgment original);

		/**
		 * Add a weighted observed opinion.
		 * @param name label
		 * @param original complete original
		 * @param weight positive finite declaration
		 * @return ready panel
		 */
		PanelStage opinion(String name, Judgment original, double weight);

	}

	/**
	 * Extend or build an observed panel; incompatible provenance fields are not exposed.
	 */
	public interface PanelStage extends FirstOpinionStage, ReadyVerdict {

	}

	private static final class PanelBuilder implements PanelStage {

		private final RetainedRule rule;

		private final List<Ballot> ballots = new ArrayList<>();

		PanelBuilder(RetainedRule rule) {
			this.rule = rule;
		}

		public PanelStage opinion(String name, Judgment original) {
			return add(name, original, null);
		}

		private PanelStage add(String name, Judgment returned, @Nullable Double weight) {
			var original = returned.refusedReturn() == null ? returned : returned.refusedReturn().original();
			ballots.add(new Ballot(ballots.size(), CompositeNames.requireValidName(name), original, returned,
					Participation.NOT_RECORDED, weight));
			return this;
		}

		public PanelStage opinion(String name, Judgment original, double weight) {
			return add(name, original, weight);
		}

		public Verdict build() {
			if (ballots.size() == 1) {
				var ballot = ballots.getFirst();
				var result = Verdict.single(ballot.label(), ballot.treatment());
				var seat = result.seats().getFirst();
				if (ballot.declaredWeight() != null)
					seat = seat.weighted(ballot.declaredWeight());
				return advancedBuilder().judgment(result.judgment())
					.individual(result.individual())
					.individualByName(result.individualByName())
					.seats(List.of(seat))
					.provenance(result.provenance())
					.rule(result.rule())
					.build()
					.requireUsable();
			}
			var aggregate = rule.aggregate(ballots);
			var originals = new ArrayList<Judgment>();
			var named = new LinkedHashMap<String, Judgment>();
			var seats = new ArrayList<Seat>();
			for (var ballot : ballots) {
				originals.add(ballot.original());
				named.put(ballot.label(), ballot.original());
				boolean refused = ballot.treatment().refusedReturn() != null;
				seats.add(new Seat(ballot.position(), ballot.label(), KeySource.DECLARED,
						refused ? SeatExecution.RETURNED_REJECTED : SeatExecution.RETURNED,
						refused ? Participation.NOT_RECORDED
								: Participation.forJudgment(ballot.treatment(), aggregate, false),
						null, null, refused ? ballot.treatment() : null, ballot.declaredWeight()));
			}
			return advancedBuilder().judgment(aggregate)
				.individual(originals)
				.individualByName(named)
				.seats(seats)
				.provenance(VerdictProvenance.decisionFor(aggregate))
				.rule(rule)
				.build()
				.requireUsable();
		}

	}

	/** Builder for {@link Verdict}. */
	public static class Builder {

		private @Nullable CompositeFailure reductionFailure;

		private @Nullable RetainedRule rule;

		/**
		 * Retain the actual immutable reduction rule, absent when no reduction happened.
		 * @param rule bound rule or null
		 * @return this advanced builder
		 */
		/**
		 * Retain the exact rule that performed this reduction.
		 * @param rule immutable declaration, or null for identity/failed reduction
		 * @return this advanced builder
		 */
		public Builder rule(@Nullable RetainedRule rule) {
			this.rule = rule;
			return this;
		}

		private List<Requirement<?>> roster = List.of();

		private List<Invocation> invocations = List.of();

		private Judgment judgment;

		private @Nullable Requirement<?> requirement;

		private List<Judgment> individual = new ArrayList<>();

		private Map<String, Judgment> individualByName = new LinkedHashMap<>();

		private List<Seat> seats = new ArrayList<>();

		private VerdictProvenance provenance;

		private Integer declaredCardinality;

		private List<CompositeAttempt> compositeAttempts = new ArrayList<>();

		/**
		 * Declares a real audit roster without a synthetic parent.
		 * @param roster declared requirements
		 * @return this builder
		 */
		public Builder roster(List<? extends Requirement<?>> roster) {
			this.roster = List.copyOf(roster);
			return this;
		}

		/**
		 * Retains each shared native invocation once at its owner.
		 * @param invocations original native observations
		 * @return this builder
		 */
		public Builder invocations(List<Invocation> invocations) {
			this.invocations = List.copyOf(invocations);
			return this;
		}

		/**
		 * Retain a failed reduction separately from producer judgments.
		 * @param failure reduction failure, or null when no reduction failed
		 * @return this builder
		 */
		public Builder reductionFailure(@Nullable CompositeFailure failure) {
			this.reductionFailure = failure;
			return this;
		}

		/**
		 * Set the actual requirement associated with this node.
		 * @param requirement actual requirement
		 * @return this builder
		 */
		public Builder requirement(Requirement<?> requirement) {
			this.requirement = Objects.requireNonNull(requirement);
			return this;
		}

		/** Create an empty verdict builder. */
		public Builder() {
		}

		/**
		 * Set the collective judgment.
		 * @param judgment collective judgment
		 * @return this builder
		 */
		public Builder judgment(Judgment judgment) {
			this.judgment = Objects.requireNonNull(judgment, "aggregated judgment must not be null");
			return this;
		}

		/**
		 * Set ordered individual judgments.
		 * @param individual individual judgments
		 * @return this builder
		 */
		public Builder individual(List<Judgment> individual) {
			this.individual = new ArrayList<>(individual);
			return this;
		}

		/**
		 * Set named individual judgments.
		 * @param individualByName judgments by name
		 * @return this builder
		 */
		public Builder individualByName(Map<String, Judgment> individualByName) {
			this.individualByName = new LinkedHashMap<>(individualByName);
			return this;
		}

		/**
		 * Set the seats, one per individual judgment.
		 * @param seats the seats, in position order
		 * @return this builder
		 *
		 * @since 0.17.0
		 */
		public Builder seats(List<Seat> seats) {
			this.seats = new ArrayList<>(seats);
			return this;
		}

		/**
		 * Set what produced the aggregate.
		 * @param provenance the provenance
		 * @return this builder
		 *
		 * @since 0.17.0
		 */
		public Builder provenance(VerdictProvenance provenance) {
			this.provenance = Objects.requireNonNull(provenance, "provenance must not be null");
			return this;
		}

		/**
		 * Set complete ordered direct composite attempts.
		 * @param compositeAttempts composite attempts
		 * @return this builder
		 */
		public Builder compositeAttempts(List<CompositeAttempt> compositeAttempts) {
			this.compositeAttempts = new ArrayList<>(compositeAttempts);
			return this;
		}

		/**
		 * Set the original configured input count.
		 * @param count declared population
		 * @return this builder
		 */
		public Builder declaredCardinality(int count) {
			this.declaredCardinality = count;
			return this;
		}

		/**
		 * Build the verdict.
		 * <p>
		 * A provenance is required. There is no default, because every default would be a
		 * claim about where the aggregate came from that nobody made.
		 * </p>
		 * @return immutable verdict
		 */
		public Verdict build() {
			if (provenance == null) {
				throw new IllegalStateException("a verdict must say what produced its aggregate; "
						+ "set a decision (VerdictProvenance.own() for an ordinary reduction)");
			}
			return new Verdict(judgment, individual, individualByName, seats, provenance, compositeAttempts,
					declaredCardinality == null ? seats.size() : declaredCardinality, requirement, reductionFailure,
					roster, invocations, rule);
		}

	}

	/**
	 * Routing decision from a complete retained child.
	 * @param rule configured routing rule
	 * @param child retained child
	 * @param accepted whether boundary admission succeeded
	 * @return whether routing stops
	 */
	public static boolean routingStops(RoutingRule rule, Verdict child, boolean accepted) {
		return VerdictSemantics.routingStops(rule, child, accepted);
	}

}
