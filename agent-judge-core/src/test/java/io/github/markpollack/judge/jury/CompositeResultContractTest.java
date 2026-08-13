/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;

import static io.github.markpollack.judge.JudgeTestFixtures.booleanPass;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Contract tests for complete, portable composite-result evidence. */
class CompositeResultContractTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final JudgmentContext CONTEXT = JudgmentContext.builder().goal("test composite result").build();

	@Test
	void verdictDeclarationAndJsonExposeOnlyTheCorrectedFiveComponentTruth() throws Exception {
		assertThat(Arrays.stream(Verdict.class.getRecordComponents()).map(RecordComponent::getName))
			.containsExactly("aggregated", "individual", "individualByName", "weights", "compositeAttempts");

		JsonNode json = MAPPER.readTree(MAPPER.writeValueAsString(Verdict.single("leaf", booleanPass("passed"))));
		assertThat(json.fieldNames()).toIterable()
			.containsExactly("aggregated", "individual", "individualByName", "weights", "compositeAttempts");
		assertThat(json.has("sub" + "Verdicts")).isFalse();
	}

	@Test
	void attemptRequiresExactlyOneOutcomeAndLegalRelationPolicyPair() {
		Verdict verdict = leaf("returned");
		CompositeFailure failure = executionFailure();

		assertThatThrownBy(() -> new CompositeAttempt("stage", CompositeRelation.META_MEMBER, null, null, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("exactly one");
		assertThatThrownBy(
				() -> new CompositeAttempt("stage", CompositeRelation.META_MEMBER, null, verdict, failure))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("exactly one");
		assertThatThrownBy(() -> new CompositeAttempt("stage", CompositeRelation.CASCADE_TIER, null, verdict, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("requires a policy");
		assertThatThrownBy(() -> new CompositeAttempt("stage", CompositeRelation.META_MEMBER,
				TierPolicy.FINAL_TIER, verdict, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("forbids a policy");
	}

	@Test
	void relationPolicyAndFailureTokensAreExactAndCaseSensitive() throws Exception {
		assertThat(MAPPER.writeValueAsString(CompositeRelation.CASCADE_TIER)).isEqualTo("\"cascade_tier\"");
		assertThat(MAPPER.writeValueAsString(CompositeRelation.META_MEMBER)).isEqualTo("\"meta_member\"");
		assertThat(MAPPER.writeValueAsString(TierPolicy.REJECT_ON_ANY_FAIL))
			.isEqualTo("\"REJECT_ON_ANY_FAIL\"");
		assertThat(MAPPER.writeValueAsString(executionFailure()))
			.isEqualTo("{\"code\":\"jury_execution_failed\"}");
		assertThatThrownBy(() -> CompositeRelation.fromWire("CASCADE_TIER"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> CompositeFailureCode.fromWire("JURY_EXECUTION_FAILED"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> TierPolicy.fromWire("final_tier")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void failureEvidenceDeclaresAndSerializesOnlyItsStableCode() throws Exception {
		assertThat(Arrays.stream(CompositeFailure.class.getRecordComponents()).map(RecordComponent::getName))
			.containsExactly("code");
		assertThat(MAPPER.readTree(MAPPER.writeValueAsString(executionFailure())).fieldNames()).toIterable()
			.containsExactly("code");

		List<String> hostileMessages = List.of("/home/alice/.ssh/id_ed25519", "C:\\Users\\alice\\secret.txt",
				"java.lang.IllegalStateException: exploded", "password=hunter2 token=abc",
				"0123456789abcdef0123456789abcdef", "QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVo=", "bad\uD800text");
		for (String message : hostileMessages) {
			Verdict verdict = Juries.combine(throwing(new IllegalStateException(message)), returning(leaf("ok")),
					new ConsensusStrategy()).vote(CONTEXT);
			String json = MAPPER.writeValueAsString(verdict.compositeAttempts().get(0).failure());
			assertThat(json).isEqualTo("{\"code\":\"jury_execution_failed\"}");
			assertThat(json).doesNotContain(message).doesNotContain("message").doesNotContain("stackTrace");
		}
	}

	@Test
	void configuredNamesUseOneStrictUnicodeValidatorAndRejectDuplicatesBeforeInvocation() {
		assertThat(new NamedJury("a/b~c-\uD83D\uDE80", returning(leaf("ok"))).name()).isEqualTo("a/b~c-\uD83D\uDE80");
		assertThat(new NamedJury("a".repeat(64), returning(leaf("ok"))).name()).hasSize(64);

		for (String invalid : List.of("", " ", " leading", "trailing\u2003", "\u00A0leading", "trailing\u00A0",
				"e\u0301", "bad\u0001name",
				"bad\u200Ename", "bad\u2028name", "bad\uD800name", "a".repeat(65))) {
			assertThatThrownBy(() -> new NamedJury(invalid, returning(leaf("ok"))))
				.as("invalid configured name %s", printable(invalid))
				.isInstanceOfAny(IllegalArgumentException.class, NullPointerException.class);
		}

		AtomicInteger invocations = new AtomicInteger();
		Jury counted = countingLeaf(invocations);
		assertThatThrownBy(() -> CascadedJury.builder()
			.tier("duplicate", counted, TierPolicy.REJECT_ON_ANY_FAIL)
			.tier("duplicate", counted, TierPolicy.FINAL_TIER)
			.build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Duplicate");
		assertThatThrownBy(() -> Juries.meta(new ConsensusStrategy(), new NamedJury("same", counted),
				new NamedJury("same", counted)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Duplicate");
		assertThat(invocations).hasValue(0);
	}

	@Test
	void equalValuedTiersRetainDistinctConfiguredIdentityAndOrder() {
		Verdict equal = leaf("same value");
		CascadedJury cascade = CascadedJury.builder()
			.tier("alpha", returning(equal), TierPolicy.REJECT_ON_ANY_FAIL)
			.tier("beta", returning(equal), TierPolicy.FINAL_TIER)
			.build();

		Verdict verdict = cascade.vote(CONTEXT);

		assertThat(verdict.compositeAttempts()).extracting(CompositeAttempt::name).containsExactly("alpha", "beta");
		assertThat(verdict.compositeAttempts()).extracting(CompositeAttempt::verdict).containsExactly(equal, equal);
	}

	@Test
	void cascadeRecordsFailuresCopiesTheStoppingChildAndPropagatesErrors() {
		Judgment contained = booleanPass("fallback passed");
		Map<String, Judgment> byName = new LinkedHashMap<>();
		byName.put("second", contained);
		Map<String, Double> weights = new LinkedHashMap<>();
		weights.put("second", 0.75);
		Verdict stopping = Verdict.builder()
			.aggregated(contained)
			.individual(List.of(contained))
			.individualByName(byName)
			.weights(weights)
			.build();
		CascadedJury cascade = CascadedJury.builder()
			.tier("broken", throwing(new IllegalStateException("must not disappear")),
					TierPolicy.REJECT_ON_ANY_FAIL)
			.tier("fallback", returning(stopping), TierPolicy.FINAL_TIER)
			.build();

		Verdict verdict = cascade.vote(CONTEXT);

		assertThat(verdict.compositeAttempts()).extracting(CompositeAttempt::name)
			.containsExactly("broken", "fallback");
		assertThat(verdict.compositeAttempts().get(0).failure()).isEqualTo(executionFailure());
		assertThat(verdict.aggregated()).isSameAs(contained);
		assertThat(verdict.individual().get(0)).isSameAs(contained);
		assertThat(verdict.individualByName()).containsExactlyEntriesOf(byName);
		assertThat(verdict.weights()).containsExactlyEntriesOf(weights);

		CascadedJury errorCascade = CascadedJury.builder()
			.tier("fatal", throwingError(new AssertionError("fatal")), TierPolicy.FINAL_TIER)
			.build();
		assertThatThrownBy(() -> errorCascade.vote(CONTEXT)).isInstanceOf(AssertionError.class).hasMessage("fatal");
	}

	@Test
	void aThrowingFinalTierReturnsTheFixedErrorAndItsFailedAttempt() {
		Verdict verdict = CascadedJury.builder()
			.tier("final", throwing(new IllegalStateException("caller text")), TierPolicy.FINAL_TIER)
			.build()
			.vote(CONTEXT);

		assertThat(verdict.aggregated().status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(verdict.aggregated().reasoning()).isEqualTo("The final cascade tier failed to execute.");
		assertThat(verdict.compositeAttempts()).singleElement().satisfies(attempt -> {
			assertThat(attempt.name()).isEqualTo("final");
			assertThat(attempt.verdict()).isNull();
			assertThat(attempt.failure()).isEqualTo(executionFailure());
		});
	}

	@Test
	void metaJuryContinuesAfterFailuresAndNeverInvokesTheStrategy() {
		AtomicInteger strategyCalls = new AtomicInteger();
		VotingStrategy forbiddenStrategy = new VotingStrategy() {
			@Override
			public Judgment aggregate(List<Judgment> judgments, Map<String, Double> weights) {
				strategyCalls.incrementAndGet();
				return booleanPass("must not aggregate");
			}

			@Override
			public String getName() {
				return "forbidden";
			}
		};
		Judgment first = booleanPass("first succeeded");
		Judgment last = booleanPass("last succeeded");
		Jury meta = Juries.meta(forbiddenStrategy, new NamedJury("first", returning(Verdict.single("first", first))),
				new NamedJury("broken", throwing(new IllegalArgumentException("boom"))),
				new NamedJury("last", returning(Verdict.single("last", last))));

		Verdict verdict = meta.vote(CONTEXT);

		assertThat(strategyCalls).hasValue(0);
		assertThat(verdict.aggregated().status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(verdict.aggregated().reasoning()).isEqualTo("One or more jury members failed to execute.");
		assertThat(verdict.compositeAttempts()).extracting(CompositeAttempt::name)
			.containsExactly("first", "broken", "last");
		assertThat(verdict.individual()).containsExactly(first, last);
		assertThat(verdict.individualByName()).containsExactly(Map.entry("first", first), Map.entry("last", last));
		assertThat(verdict.weights()).isEmpty();
	}

	@Test
	void metaJuryUsesTheSameCompleteFailureRuleAtEveryPositionAndForMultipleFailures() {
		for (int failureIndex = 0; failureIndex < 3; failureIndex++) {
			AtomicInteger calls = new AtomicInteger();
			AtomicInteger strategyCalls = new AtomicInteger();
			Jury meta = Juries.meta(countingStrategy(strategyCalls),
					metaMember("first", 0, failureIndex, calls), metaMember("middle", 1, failureIndex, calls),
					metaMember("final", 2, failureIndex, calls));

			Verdict verdict = meta.vote(CONTEXT);

			assertThat(calls).hasValue(3);
			assertThat(strategyCalls).hasValue(0);
			assertThat(verdict.compositeAttempts()).hasSize(3);
			assertThat(verdict.compositeAttempts().get(failureIndex).failure()).isEqualTo(executionFailure());
			assertThat(verdict.individual()).hasSize(2);
		}

		AtomicInteger calls = new AtomicInteger();
		AtomicInteger strategyCalls = new AtomicInteger();
		Jury multiple = Juries.meta(countingStrategy(strategyCalls),
				new NamedJury("first", countedThrowing(calls)), new NamedJury("middle", countedReturning(calls)),
				new NamedJury("final", countedThrowing(calls)));
		Verdict verdict = multiple.vote(CONTEXT);
		assertThat(calls).hasValue(3);
		assertThat(strategyCalls).hasValue(0);
		assertThat(verdict.compositeAttempts()).extracting(CompositeAttempt::failure)
			.containsExactly(executionFailure(), null, executionFailure());
		assertThat(verdict.individual()).hasSize(1);
	}

	@Test
	void pathsAreStrictReversibleAndPreorderAcrossMetaCascadeMeta() {
		Jury inner = Juries.meta(new ConsensusStrategy(), new NamedJury("c~d", returning(leaf("inner"))));
		Jury cascade = CascadedJury.builder().tier("a/b", inner, TierPolicy.FINAL_TIER).build();
		Jury outer = Juries.meta(new ConsensusStrategy(), new NamedJury("outer", cascade));

		List<CompositePathEntry> flattened = CompositePaths.flatten(outer.vote(CONTEXT));

		assertThat(flattened).extracting(CompositePathEntry::path)
			.containsExactly("/outer", "/outer/a~1b", "/outer/a~1b/c~0d");
		assertThat(flattened).extracting(entry -> entry.attempt().relation())
			.containsExactly(CompositeRelation.META_MEMBER, CompositeRelation.CASCADE_TIER,
					CompositeRelation.META_MEMBER);
		assertThat(flattened).isUnmodifiable();

		for (String name : List.of("~0", "~1", "a/b", "a~b", "\u00E9", "\uD83D\uDE80")) {
			assertThat(CompositePaths.decodeSegment(CompositePaths.encodeSegment(name))).isEqualTo(name);
		}
		assertThat(CompositePaths.encodeSegment(CompositePaths.decodeSegment("a~0b~1c"))).isEqualTo("a~0b~1c");
		for (String invalid : List.of("~", "~2", "a~x", "~~0")) {
			assertThatThrownBy(() -> CompositePaths.decodeSegment(invalid)).isInstanceOf(IllegalArgumentException.class);
		}
	}

	@Test
	void executionAcceptsDepthEightAndRefusesDepthNineBeforeDestinationInvocation() {
		AtomicInteger acceptedLeafCalls = new AtomicInteger();
		Verdict accepted = nestedCascade(8, acceptedLeafCalls).vote(CONTEXT);
		assertThat(acceptedLeafCalls).hasValue(1);
		assertThat(CompositePaths.flatten(accepted)).hasSize(8);

		AtomicInteger rejectedLeafCalls = new AtomicInteger();
		assertThatThrownBy(() -> nestedCascade(9, rejectedLeafCalls).vote(CONTEXT))
			.isInstanceOf(CompositeLimitExceededException.class)
			.hasMessageContaining("depth");
		assertThat(rejectedLeafCalls).hasValue(0);
		assertThat(nestedCascade(1, new AtomicInteger()).vote(CONTEXT).compositeAttempts()).hasSize(1);
	}

	@Test
	void executionAcceptsAttemptThirtyTwoAndRefusesThirtyThreeBeforeDestinationInvocation() {
		AtomicInteger acceptedLastCalls = new AtomicInteger();
		Verdict accepted = flatCascade(32, acceptedLastCalls).vote(CONTEXT);
		assertThat(accepted.compositeAttempts()).hasSize(32);
		assertThat(acceptedLastCalls).hasValue(1);

		AtomicInteger rejectedLastCalls = new AtomicInteger();
		assertThatThrownBy(() -> flatCascade(33, rejectedLastCalls).vote(CONTEXT))
			.isInstanceOf(CompositeLimitExceededException.class)
			.hasMessageContaining("attempt");
		assertThat(rejectedLastCalls).hasValue(0);
		assertThat(flatCascade(1, new AtomicInteger()).vote(CONTEXT).compositeAttempts()).hasSize(1);
	}

	@Test
	void manuallyAuthoredTreesEnforceDepthCountAndSiblingIdentity() {
		assertThat(CompositePaths.flatten(manualChain(8))).hasSize(8);
		assertThatThrownBy(() -> manualChain(9)).isInstanceOf(CompositeLimitExceededException.class);
		assertThat(manualFlat(32).compositeAttempts()).hasSize(32);
		assertThatThrownBy(() -> manualFlat(33)).isInstanceOf(CompositeLimitExceededException.class);
		assertThat(CompositePaths.flatten(manualBranched(31, 1))).hasSize(32);
		assertThatThrownBy(() -> manualBranched(31, 2))
			.isInstanceOf(CompositeLimitExceededException.class)
			.hasMessageContaining("attempt");

		CompositeAttempt first = successAttempt("same", leaf("one"), CompositeRelation.META_MEMBER, null);
		CompositeAttempt second = successAttempt("same", leaf("two"), CompositeRelation.META_MEMBER, null);
		assertThatThrownBy(() -> Verdict.builder()
			.aggregated(booleanPass("root"))
			.compositeAttempts(List.of(first, second))
			.build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Duplicate");
	}

	private static Verdict manualChain(int depth) {
		Verdict verdict = leaf("leaf");
		for (int current = depth; current > 0; current--) {
			verdict = Verdict.builder()
				.aggregated(verdict.aggregated())
				.compositeAttempts(List.of(successAttempt("level-" + current, verdict,
						CompositeRelation.META_MEMBER, null)))
				.build();
		}
		return verdict;
	}

	private static Verdict manualFlat(int attempts) {
		List<CompositeAttempt> authored = new ArrayList<>();
		for (int index = 1; index <= attempts; index++) {
			authored.add(successAttempt("member-" + index, leaf("leaf"), CompositeRelation.META_MEMBER, null));
		}
		return Verdict.builder().aggregated(booleanPass("root")).compositeAttempts(authored).build();
	}

	private static Verdict manualBranched(int rootAttempts, int descendantDepth) {
		Verdict descendant = leaf("leaf");
		for (int level = descendantDepth; level > 0; level--) {
			descendant = Verdict.builder()
				.aggregated(descendant.aggregated())
				.compositeAttempts(List.of(successAttempt("descendant-" + level, descendant,
						CompositeRelation.META_MEMBER, null)))
				.build();
		}
		List<CompositeAttempt> authored = new ArrayList<>();
		for (int index = 1; index < rootAttempts; index++) {
			authored.add(successAttempt("member-" + index, leaf("leaf"), CompositeRelation.META_MEMBER, null));
		}
		authored.add(successAttempt("member-" + rootAttempts, descendant, CompositeRelation.META_MEMBER, null));
		return Verdict.builder().aggregated(booleanPass("root")).compositeAttempts(authored).build();
	}

	private static Jury nestedCascade(int depth, AtomicInteger leafCalls) {
		Jury jury = countingLeaf(leafCalls);
		for (int current = depth; current > 0; current--) {
			jury = CascadedJury.builder().tier("level-" + current, jury, TierPolicy.FINAL_TIER).build();
		}
		return jury;
	}

	private static CascadedJury flatCascade(int attempts, AtomicInteger lastCalls) {
		CascadedJury.Builder builder = CascadedJury.builder();
		for (int index = 1; index <= attempts; index++) {
			Jury jury = index == attempts ? countingLeaf(lastCalls) : returning(leaf("pass"));
			TierPolicy policy = index == attempts ? TierPolicy.FINAL_TIER : TierPolicy.REJECT_ON_ANY_FAIL;
			builder.tier("tier-" + index, jury, policy);
		}
		return builder.build();
	}

	private static CompositeAttempt successAttempt(String name, Verdict verdict, CompositeRelation relation,
			TierPolicy policy) {
		return new CompositeAttempt(name, relation, policy, verdict, null);
	}

	private static CompositeFailure executionFailure() {
		return new CompositeFailure(CompositeFailureCode.JURY_EXECUTION_FAILED);
	}

	private static Verdict leaf(String reasoning) {
		return Verdict.single("leaf", booleanPass(reasoning));
	}

	private static Jury countingLeaf(AtomicInteger calls) {
		return jury(() -> {
			calls.incrementAndGet();
			return leaf("counted");
		});
	}

	private static NamedJury metaMember(String name, int index, int failureIndex, AtomicInteger calls) {
		return new NamedJury(name, index == failureIndex ? countedThrowing(calls) : countedReturning(calls));
	}

	private static Jury countedReturning(AtomicInteger calls) {
		return jury(() -> {
			calls.incrementAndGet();
			return leaf("returned");
		});
	}

	private static Jury countedThrowing(AtomicInteger calls) {
		return jury(() -> {
			calls.incrementAndGet();
			throw new IllegalStateException("failed");
		});
	}

	private static VotingStrategy countingStrategy(AtomicInteger calls) {
		return new VotingStrategy() {
			@Override
			public Judgment aggregate(List<Judgment> judgments, Map<String, Double> weights) {
				calls.incrementAndGet();
				return booleanPass("aggregated");
			}

			@Override
			public String getName() {
				return "counting";
			}
		};
	}

	private static Jury returning(Verdict verdict) {
		return jury(() -> verdict);
	}

	private static Jury throwing(RuntimeException failure) {
		return jury(() -> {
			throw failure;
		});
	}

	private static Jury throwingError(Error failure) {
		return jury(() -> {
			throw failure;
		});
	}

	private static Jury jury(java.util.function.Supplier<Verdict> vote) {
		return new Jury() {
			@Override
			public List<Judge> getJudges() {
				return List.of();
			}

			@Override
			public VotingStrategy getVotingStrategy() {
				return new ConsensusStrategy();
			}

			@Override
			public Verdict vote(JudgmentContext context) {
				return vote.get();
			}
		};
	}

	private static String printable(String value) {
		return value.codePoints()
			.mapToObj(codePoint -> codePoint >= 0x20 && codePoint < 0x7f ? Character.toString(codePoint)
					: String.format("U+%04X", codePoint))
			.reduce("", (left, right) -> left + right);
	}

}
