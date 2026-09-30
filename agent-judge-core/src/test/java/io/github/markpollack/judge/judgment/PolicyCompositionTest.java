/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.judgment;

import io.github.markpollack.judge.acceptance.Policies;
import io.github.markpollack.judge.acceptance.AcceptanceAction;
import io.github.markpollack.judge.acceptance.AcceptanceDecision;
import io.github.markpollack.judge.acceptance.AcceptancePolicy;
import io.github.markpollack.judge.acceptance.AppliedPolicy;
import io.github.markpollack.judge.acceptance.Policies;
import io.github.markpollack.judge.acceptance.PolicyFailure;
import io.github.markpollack.judge.provenance.ArtifactRef;
import io.github.markpollack.judge.provenance.CalibrationClaim;
import io.github.markpollack.judge.provenance.PolicyRef;
import io.github.markpollack.judge.provenance.Provenance;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.JudgeMetadata;
import io.github.markpollack.judge.JudgeType;
import io.github.markpollack.judge.Judges;
import io.github.markpollack.judge.NamedJudge;
import io.github.markpollack.judge.PolicyJudges;
import io.github.markpollack.judge.completion.CompletionEvidence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyCompositionTest {

	private static final PolicyRef POLICY = new PolicyRef("critical", "1", "a".repeat(64));

	private static final CompletionEvidence CONTEXT = CompletionEvidence.builder().request("policy").build();

	@ParameterizedTest
	@EnumSource(JudgmentStatus.class)
	void completeOutcomeActionTableAndRawView(JudgmentStatus status) {
		for (AcceptanceAction action : AcceptanceAction.values()) {
			Judgment raw = raw(status);
			AtomicInteger calls = new AtomicInteger();
			Judgment result = Policies.apply(raw, POLICY, view -> {
				calls.incrementAndGet();
				assertThat(view).isEqualTo(raw);
				assertThat(view.policyApplication()).isNull();
				return new AcceptanceDecision(action, "application consequence");
			});
			boolean bypass = status == JudgmentStatus.ERROR || status == JudgmentStatus.NOT_APPLICABLE;
			assertThat(calls.get()).isEqualTo(bypass ? 0 : 1);
			assertThat(result.status())
				.isEqualTo(bypass || action == AcceptanceAction.RELY ? status : JudgmentStatus.ABSTAIN);
			assertRawFactsEqual(raw, result);
			if (bypass) {
				assertThat(result).isSameAs(raw);
				assertThat(result.policyApplication()).isNull();
			}
			else {
				assertThat(result.policyApplication())
					.isEqualTo(new AppliedPolicy(POLICY, action, "application consequence"));
			}
		}
	}

	@Test
	void wrapperInvokesOnceAndPureReplacementIgnoresEarlierWithholding() {
		AtomicInteger calls = new AtomicInteger();
		Judgment raw = raw(JudgmentStatus.FAIL);
		Judge<CompletionEvidence> wrapped = PolicyJudges.apply(context -> {
			calls.incrementAndGet();
			return raw;
		}, POLICY, view -> new AcceptanceDecision(AcceptanceAction.ESCALATE, "critical route"));
		Judgment escalated = wrapped.judge(CONTEXT);
		assertThat(escalated.status()).isEqualTo(JudgmentStatus.ABSTAIN);
		PolicyRef alternative = new PolicyRef("low-consequence", "2", "b".repeat(64));
		Judgment accepted = Policies.apply(escalated, alternative, view -> {
			assertThat(view.status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(view.policyApplication()).isNull();
			assertRawFactsEqual(raw, view);
			return new AcceptanceDecision(AcceptanceAction.RELY, "negative finding is usable");
		});
		assertThat(calls.get()).isEqualTo(1);
		assertThat(accepted.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(((AppliedPolicy) accepted.policyApplication()).policy()).isEqualTo(alternative);
		assertRawFactsEqual(raw, accepted);
		assertThat(escalated.status()).isEqualTo(JudgmentStatus.ABSTAIN);
	}

	@Test
	void nullInvalidAndThrownPolicyResultsKeepRawFactsAndCanBeReplaced() {
		Judgment raw = raw(JudgmentStatus.FAIL);
		List<AcceptancePolicy> broken = List.of(view -> null, view -> new AcceptanceDecision(null, "invalid"), view -> {
			throw new IllegalStateException("configuration unavailable");
		});
		for (AcceptancePolicy policy : broken) {
			Judgment failed = Policies.apply(raw, POLICY, policy);
			assertThat(failed.status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(failed.policyApplication()).isInstanceOf(PolicyFailure.class);
			assertThat(failed.operationalReasonCode()).isEqualTo(JudgmentReasonCode.POLICY_FAILED);
			assertThat(failed.reasonCode()).isEqualTo(JudgmentReasonCode.SUBJECT_EMPTY);
			assertThat(failed.operationalReasoning()).contains("Acceptance policy failed");
			assertRawFactsEqual(raw, failed);
			assertThat(
					Policies.apply(failed, POLICY, view -> new AcceptanceDecision(AcceptanceAction.RELY, "recovered"))
						.status())
				.isEqualTo(JudgmentStatus.FAIL);
		}
	}

	@Test
	void policyWrapperPreservesEffectiveCapabilityThroughBothNamingOrders() {
		Judge<CompletionEvidence> capable = new NamedJudge<CompletionEvidence>(
				context -> Judgment.notApplicable("no Java"),
				new JudgeMetadata("source", "source judge", JudgeType.DETERMINISTIC, "subject has no Java"));
		Judge<CompletionEvidence> namedThenPolicy = PolicyJudges.apply(Judges.named(capable, "outer"), POLICY, view -> {
			throw new AssertionError("N/A must bypass");
		});
		Judge<CompletionEvidence> policyThenNamed = Judges.named(PolicyJudges.apply(capable, POLICY, view -> {
			throw new AssertionError("N/A must bypass");
		}), "outer");
		for (Judge<CompletionEvidence> wrapper : List.of(namedThenPolicy, policyThenNamed)) {
			assertThat(Judges.notApplicableCapability(wrapper)).contains("subject has no Java");
			assertThat(Judges.describe(wrapper).notApplicableWhen()).isEqualTo("subject has no Java");
			assertThat(Judges.describe(wrapper).name()).isEqualTo("outer");
			assertThat(wrapper.judge(CONTEXT).status()).isEqualTo(JudgmentStatus.NOT_APPLICABLE);
			var strategy = new io.github.markpollack.judge.jury.AverageVotingStrategy(0.5,
					io.github.markpollack.judge.jury.ErrorPolicy.PROPAGATE,
					io.github.markpollack.judge.jury.NotApplicablePolicy.TREAT_AS_FAIL);
			var simple = io.github.markpollack.judge.jury.SimpleJury.<CompletionEvidence>builder()
				.judge(wrapper)
				.votingStrategy(strategy)
				.build();
			var meta = io.github.markpollack.judge.jury.Juries.meta(strategy,
					new io.github.markpollack.judge.jury.NamedJury<>("member", simple));
			for (var jury : List.of(simple, meta)) {
				assertThat(jury.describe().aggregateMayBeNotApplicable()).isTrue();
				assertThat(jury.vote(CONTEXT).judgment().status()).isEqualTo(JudgmentStatus.NOT_APPLICABLE);
			}
		}
	}

	@Test
	void oneSeatAndMetaIdentityRetainEveryRichPolicyFact() {
		Judgment original = Policies.apply(raw(JudgmentStatus.FAIL), POLICY,
				view -> new AcceptanceDecision(AcceptanceAction.ESCALATE, "independent review"));
		var strategy = new io.github.markpollack.judge.jury.AverageVotingStrategy();
		var simple = io.github.markpollack.judge.jury.SimpleJury.<CompletionEvidence>builder()
			.judge(context -> original)
			.votingStrategy(strategy)
			.build();
		var meta = io.github.markpollack.judge.jury.Juries.meta(strategy,
				new io.github.markpollack.judge.jury.NamedJury<>("one", simple));
		for (var jury : List.of(simple, meta)) {
			Judgment aggregate = jury.vote(CONTEXT).judgment();
			assertThat(aggregate).isSameAs(original);
			assertRawFactsEqual(original, aggregate);
			assertThat(aggregate.policyApplication()).isEqualTo(original.policyApplication());
		}
	}

	private static Judgment raw(JudgmentStatus status) {
		ArtifactRef source = ArtifactRef.ofBytes("bundle", "exact evidence".getBytes(StandardCharsets.UTF_8), null);
		Provenance provenance = new Provenance("instrument", "revision", source.sha256(), List.of(source), source,
				List.of(new CalibrationClaim("claim:v1", "issuer", "scope", "declaration", List.of("native:v1"),
						List.of(source))));
		boolean assessed = status != JudgmentStatus.ERROR && status != JudgmentStatus.NOT_APPLICABLE;
		return new Judgment(status, assessed ? new Finding(new BooleanFinding(false), null, null) : null,
				assessed ? new Confidence(0.7, "native:v1", SupportOrigin.REPORTED, FindingTarget.BOOLEAN, null) : null,
				assessed ? new ProbabilityDistribution(FindingTarget.BOOLEAN, "truth:v1",
						List.of(new ProbabilityMass("false", 0.7), new ProbabilityMass("true", 0.3))) : null,
				status == JudgmentStatus.FAIL ? JudgmentReasonCode.SUBJECT_EMPTY
						: status == JudgmentStatus.ERROR ? JudgmentReasonCode.JUDGE_REPORTED : null,
				"producer explanation", List.of(new Check("criterion", Judgment.abstain("missing detail"))), provenance,
				null, Map.of("elapsedMillis", 9, "trace", List.of("a", "b")));
	}

	private static void assertRawFactsEqual(Judgment expected, Judgment actual) {
		ObjectMapper json = new ObjectMapper();
		ObjectNode before = json.valueToTree(expected);
		ObjectNode after = json.valueToTree(actual);
		before.remove("policyApplication");
		after.remove("policyApplication");
		assertThat(after).isEqualTo(before);
	}

}
