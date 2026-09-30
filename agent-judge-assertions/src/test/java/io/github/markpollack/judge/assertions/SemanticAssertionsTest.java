/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertions;

import io.github.markpollack.judge.requirement.Requirement;
import io.github.markpollack.judge.result.PolicyBinding;

import io.github.markpollack.judge.*;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.jury.*;
import io.github.markpollack.judge.description.KeySource;
import io.github.markpollack.judge.jury.interpretation.*;
import io.github.markpollack.judge.result.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.assertj.core.api.Assertions.*;

class SemanticAssertionsTest {

	static final Requirement<?> REQUIREMENT = Requirement.text("requirement", "1", "exact text");
	static final JudgmentContext EVIDENCE = JudgmentContext.builder()
		.goal(REQUIREMENT.text())
		.metadata("retained", "yes")
		.build();

	static PolicyBinding policy(AcceptanceAction action) {
		return new PolicyBinding(new PolicyRef("application", action.name(), "a".repeat(64)),
				j -> new Acceptance(action, "explicit application consequence"));
	}

	static SemanticAssertions facade(Judge<JudgmentContext> judge) {
		return new SemanticAssertions(r -> judge, policy(AcceptanceAction.USE_ASSESSMENT));
	}

	@Test
	void supportedAcceptedPassesAndPreservesWholeValue() {
		Judgment raw = Judgment.pass("source supports requirement");
		AtomicInteger calls = new AtomicInteger();
		var f = facade(c -> {
			calls.incrementAndGet();
			assertThat(c).isSameAs(EVIDENCE);
			return raw;
		});
		var result = f.evaluate(EVIDENCE, REQUIREMENT);
		assertThat(calls).hasValue(1);
		assertThat(result.interpretation().readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);
		assertThat(result.verdict().aggregated()).isSameAs(result.verdict().individual().getFirst());
		assertThat(result.verdict().aggregated().producerStatus()).isEqualTo(raw.producerStatus());
		assertThat(result.requirement()).isSameAs(REQUIREMENT);
		assertThat(result.policySource()).isEqualTo(AssertionResult.PolicySource.DEFAULT);
		SemanticAssertions.requireSatisfied(result);
		f.assertThat(EVIDENCE).satisfies(REQUIREMENT);
		f.assertThat(EVIDENCE).satisfies("exact text");
		assertThat(calls).hasValue(3);
	}

	@Test
	void acceptedNegativeCarriesAuthoritativeRejection() {
		var error = catchThrowableOfType(
				() -> facade(c -> Judgment.fail("violation")).assertThat(EVIDENCE).satisfies(REQUIREMENT),
				SemanticAssertionError.Rejected.class);
		assertThat(error.category()).isEqualTo(SemanticAssertionError.Category.REJECTED);
		assertThat(error.interpretation().reading()).isEqualTo(VerdictReading.REJECTED);
		assertThat(error.interpretation()).isSameAs(error.result().interpretation());
		assertThat(error.result().verdict().aggregated().status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(error).isInstanceOf(org.opentest4j.AssertionFailedError.class);
	}

	@ParameterizedTest
	@EnumSource(value = AcceptanceAction.class, names = { "ABSTAIN", "ESCALATE" })
	void withholdingNegativeNeverBecomesViolation(AcceptanceAction action) {
		var named = REQUIREMENT.under(policy(action));
		var error = catchThrowableOfType(
				() -> facade(c -> Judgment.fail("violation")).assertThat(EVIDENCE).satisfies(named),
				SemanticAssertionError.Inconclusive.class);
		assertThat(error.category()).isEqualTo(SemanticAssertionError.Category.INCONCLUSIVE);
		assertThat(error.result().policySource()).isEqualTo(AssertionResult.PolicySource.ASSOCIATED);
		assertThat(error.result().policy()).isEqualTo(named.acceptancePolicy().reference());
		var j = error.result().verdict().aggregated();
		assertThat(j.producerStatus()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(j.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(error.interpretation().reading()).isEqualTo(VerdictReading.REJECTED);
		assertThat(((AppliedPolicy) error.result().applicationDecision().application()).action()).isEqualTo(action);
		assertThat(REQUIREMENT.acceptancePolicy()).isNull();
	}

	@Test
	void rawAbstainCannotBeUsedIntoPass() {
		var error = catchThrowableOfType(
				() -> facade(c -> Judgment.abstain("missing evidence")).assertThat(EVIDENCE).satisfies(REQUIREMENT),
				SemanticAssertionError.Inconclusive.class);
		assertThat(error.result().verdict().aggregated().producerStatus()).isEqualTo(JudgmentStatus.ABSTAIN);
	}

	@Test
	void failuresRemainInstrumentFailures() {
		for (Judge<JudgmentContext> judge : List.<Judge<JudgmentContext>>of(c -> Judgment.error(JudgmentReasonCode.JUDGE_REPORTED, "unavailable"), c -> {
			throw new IllegalStateException("transport");
		}, c -> null)) {
			var error = catchThrowableOfType(() -> facade(judge).assertThat(EVIDENCE).satisfies(REQUIREMENT),
					SemanticAssertionError.InstrumentFailure.class);
			assertThat(error.category()).isEqualTo(SemanticAssertionError.Category.INSTRUMENT_FAILURE);
			assertThat(error.result().verdict().aggregated().status()).isEqualTo(JudgmentStatus.ERROR);
		}
		var broken = new PolicyBinding(policy(AcceptanceAction.USE_ASSESSMENT).reference(), j -> {
			throw new IllegalStateException("policy");
		});
		var error = catchThrowableOfType(
				() -> facade(c -> Judgment.fail("violation")).assertThat(EVIDENCE).satisfies(REQUIREMENT.under(broken)),
				SemanticAssertionError.InstrumentFailure.class);
		assertThat(error.result().verdict().aggregated().producerStatus()).isEqualTo(JudgmentStatus.FAIL);
	}

	@Test
	void notApplicableHasOwnDiagnosticAndUndeclaredIsInstrumentFailure() {
		Judge<JudgmentContext> raw = c -> Judgment.notApplicable("outside declared domain");
		var declared = new NamedJudge<JudgmentContext>(raw,
				new JudgeMetadata("optional", "domain", JudgeType.DETERMINISTIC, "outside domain"));
		var error = catchThrowableOfType(() -> facade(declared).assertThat(EVIDENCE).satisfies(REQUIREMENT),
				SemanticAssertionError.NotApplicable.class);
		assertThat(error.category()).isEqualTo(SemanticAssertionError.Category.NOT_APPLICABLE);
		assertThat(error.result().verdict().aggregated().status()).isEqualTo(JudgmentStatus.NOT_APPLICABLE);
		assertThatThrownBy(() -> facade(raw).assertThat(EVIDENCE).satisfies(REQUIREMENT))
			.isInstanceOf(SemanticAssertionError.InstrumentFailure.class);
	}

	@Test
	void missingPolicyAndStaleGoalFailBeforeRouteOrInference() {
		AtomicInteger calls = new AtomicInteger();
		var f = new SemanticAssertions(r -> {
			calls.incrementAndGet();
			return c -> Judgment.pass("");
		}, null);
		assertThatThrownBy(() -> f.assertThat(EVIDENCE).satisfies("exact text"))
			.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> f.evaluate(EVIDENCE, REQUIREMENT)).isInstanceOf(IllegalStateException.class);
		assertThat(calls).hasValue(0);
		f.assertThat(EVIDENCE).satisfies(REQUIREMENT.under(policy(AcceptanceAction.USE_ASSESSMENT)));
		assertThat(calls).hasValue(1);
		var other = facade(c -> {
			calls.incrementAndGet();
			return Judgment.pass("");
		});
		assertThatThrownBy(() -> other.assertThat(EVIDENCE).satisfies(Requirement.text("requirement", "2", "changed")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("exactly match");
		assertThat(calls).hasValue(1);
		assertThat(EVIDENCE.goal()).isEqualTo("exact text");
		assertThat(EVIDENCE.metadata()).containsEntry("retained", "yes");
	}

	@Test
	void invalidSetupIsImmediate() {
		assertThatThrownBy(() -> Requirement.text(" ", "1", "x")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> REQUIREMENT.under(null)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new PolicyBinding(null, j -> null)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new SemanticAssertions(r -> null, policy(AcceptanceAction.USE_ASSESSMENT))
			.evaluate(EVIDENCE, REQUIREMENT)).isInstanceOf(NullPointerException.class);
	}

	@Test
	void concurrentCallsResolveOnCallerWithoutLeakingRequirementOrPolicy() throws Exception {
		var current = new ConcurrentHashMap<String, Thread>();
		var f = new SemanticAssertions(r -> {
			assertThat(Thread.currentThread()).isSameAs(current.get(r.id()));
			return c -> {
				assertThat(c.goal()).isEqualTo(r.text());
				return Judgment.pass(r.id());
			};
		}, policy(AcceptanceAction.USE_ASSESSMENT));
		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			List<Future<AssertionResult>> results = new ArrayList<>();
			for (int n = 0; n < 24; n++) {
				int i = n;
				results.add(executor.submit(() -> {
					String id = "r-" + i;
					current.put(id, Thread.currentThread());
					var req = Requirement.text(id, "1", id)
						.under(policy(i % 2 == 0 ? AcceptanceAction.USE_ASSESSMENT : AcceptanceAction.ESCALATE));
					Thread.currentThread().setContextClassLoader(new ClassLoader(null) {
					});
					return f.evaluate(JudgmentContext.builder().goal(id).build(), req);
				}));
			}
			for (int i = 0; i < results.size(); i++) {
				var result = results.get(i).get();
				assertThat(result.requirement().id()).isEqualTo("r-" + i);
				assertThat(result.verdict().aggregated().reasoning()).isEqualTo("r-" + i);
				assertThat(result.verdict().aggregated().status()).isEqualTo(JudgmentStatus.PASS);
				assertThat(((AppliedPolicy) result.applicationDecision().application()).action())
					.isEqualTo(i % 2 == 0 ? AcceptanceAction.USE_ASSESSMENT : AcceptanceAction.ESCALATE);
			}
		}
	}

	@Test
	void incompleteAndContradictoryModernVerdictsCannotPass() {
		// Custom verdicts can be structurally constructible without coherent reading
		// support.
		var good = Judgment.pass("pass");
		var bad = Judgment.fail("fail");
		var undetermined = Verdict.of(good, Map.of("one", good, "two", good));
		var contradicted = Verdict.builder()
			.aggregated(good)
			.individual(List.of(bad))
			.individualByName(Map.of("one", bad))
			.seats(List.of(new Seat(0, "one", KeySource.DECLARED)))
			.decision(Decision.own())
			.build();
		for (var verdict : List.of(undetermined, contradicted)) {
			var result = AssertionResult.applyPolicy(REQUIREMENT, policy(AcceptanceAction.USE_ASSESSMENT),
					AssertionResult.PolicySource.DEFAULT, verdict);
			assertThat(result.interpretation().readingSupport()).isNotEqualTo(ReadingSupport.SUPPORTED);
			var error = catchThrowableOfType(() -> SemanticAssertions.requireSatisfied(result),
					SemanticAssertionError.UnsupportedReading.class);
			assertThat(error.category()).isEqualTo(SemanticAssertionError.Category.UNSUPPORTED_READING);
			assertThat(error.result()).isSameAs(result);
		}
		assertThat(Verdicts.interpret(undetermined).readingSupport()).isEqualTo(ReadingSupport.UNDETERMINED);
		assertThat(Verdicts.interpret(contradicted).readingSupport()).isEqualTo(ReadingSupport.UNDETERMINED);

	}

	@Test
	void malformedUnicodeDoesNotAliasReplacementBytes() {
		var f = facade(c -> Judgment.pass(""));
		String malformed = "text" + Character.toString((char) 0xD800);
		assertThatThrownBy(() -> f.stringRequirement(malformed)).isInstanceOf(IllegalArgumentException.class);
		assertThat(f.stringRequirement("text?").id()).isNotBlank();
		assertThat(f.stringRequirement("text\uD83D\uDE00").text()).isEqualTo("text\uD83D\uDE00");
	}

	@Test
	void preInterruptedCallerReachesJudgeWithoutLosingCancellation() {
		Thread caller = Thread.currentThread();
		AtomicBoolean invoked = new AtomicBoolean();
		try {
			caller.interrupt();
			var result = facade(c -> {
				invoked.set(true);
				return Thread.currentThread().isInterrupted()
						? Judgment.error(JudgmentReasonCode.JUDGE_REPORTED, "pre-interrupted")
						: Judgment.pass("lost interruption");
			}).evaluate(EVIDENCE, REQUIREMENT);
			assertThat(invoked).isTrue();
			assertThat(result.verdict().aggregated().status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(caller.isInterrupted()).isTrue();
		}
		finally {
			Thread.interrupted();
		}
	}

}
