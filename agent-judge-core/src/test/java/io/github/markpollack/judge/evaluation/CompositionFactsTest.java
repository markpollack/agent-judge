/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.evaluation;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import io.github.markpollack.judge.*;
import io.github.markpollack.judge.judgment.*;
import io.github.markpollack.judge.jury.*;
import io.github.markpollack.judge.requirement.*;
import io.github.markpollack.judge.policy.*;
import io.github.markpollack.judge.serialization.VerdictCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.assertj.core.api.Assertions.*;

class CompositionFactsTest {

	@ParameterizedTest
	@EnumSource(ErrorHandling.class)
	void errorTreatmentIsOnSeatAndNeverChangesProducer(ErrorHandling errors) {
		Judgment original = Judgment.error("provider unavailable");
		var verdict = SimpleJury.<String>builder()
			.judge(e -> Judgment.pass("one opinion"))
			.judge(e -> original)
			.parallel(false)
			.votingStrategy(new MajorityVotingStrategy(TieBreakRule.ABSTAIN, errors))
			.build()
			.vote("e");
		assertThat(verdict.individual().get(1)).isSameAs(original);
		assertThat(original.status()).isEqualTo(JudgmentStatus.ERROR);
		Participation expected = switch (errors) {
			case PROPAGATE -> Participation.NOT_REDUCED;
			case IGNORE -> Participation.ERROR_IGNORED;
			case TREAT_AS_ABSTAIN -> Participation.ERROR_AS_ABSTENTION;
			case TREAT_AS_FAIL -> Participation.ERROR_AS_FAIL;
		};
		assertThat(verdict.seats().get(1).participation()).isEqualTo(expected);
		assertThat(new VerdictCodec().read(new VerdictCodec().write(verdict))).isEqualTo(verdict);
	}

	@Test
	void explicitExclusionAndAbstentionAreDifferentSeatFacts() {
		Judgment exclusion = Judgment.notApplicable("no applicable sources");
		JudgeWithMetadata<String> conditional = new JudgeWithMetadata<>() {
			public JudgeMetadata metadata() {
				return new JudgeMetadata("conditional", "conditional check", JudgeType.DETERMINISTIC,
						"No applicable sources");
			}

			public Judgment judge(String evidence) {
				return exclusion;
			}
		};
		for (ExclusionHandling rule : List.of(ExclusionHandling.EXCLUDE, ExclusionHandling.TREAT_AS_FAIL)) {
			var verdict = SimpleJury.<String>builder()
				.judge(e -> Judgment.abstain("unknown"))
				.judge(conditional)
				.votingStrategy(new AllMustPassStrategy(ErrorHandling.PROPAGATE, rule))
				.build()
				.vote("e");
			assertThat(verdict.individual().get(1)).isSameAs(exclusion);
			assertThat(verdict.seats()).extracting(Seat::participation)
				.containsExactly(Participation.ABSTAINED,
						rule == ExclusionHandling.EXCLUDE ? Participation.EXCLUDED : Participation.EXCLUSION_AS_FAIL);
		}
	}

	@Test
	void thrownJudgeAndStrategyFailuresKeepOriginalExceptionsInMemory() {
		var failure = new IllegalStateException("original");
		var leaf = Evaluations.evaluate((Judge<String>) e -> {
			throw failure;
		}, "e").verdict();
		assertThat(leaf.seats().getFirst().cause()).isSameAs(failure);
		var actual = Requirement.text("r", "1", "r");
		var required = Evaluations.evaluate(actual, (RequirementJudge<String, String>) (r, e) -> {
			throw failure;
		}, "e").verdict();
		assertThat(required.seats().getFirst().cause()).isSameAs(failure);
		assertThat(required.seats().getFirst().execution()).isEqualTo(SeatExecution.CONTAINED_FAILURE);
		var strategy = new VotingStrategy() {
			public Judgment aggregate(List<Judgment> inputs, Map<String, Double> weights) {
				throw failure;
			}

			public String getName() {
				return "failed-reduction";
			}
		};
		var verdict = SimpleJury.<String>builder()
			.judge(e -> Judgment.pass("a"))
			.judge(e -> Judgment.fail("b"))
			.votingStrategy(strategy)
			.build()
			.vote("e");
		assertThat(verdict.reductionFailure().cause()).isSameAs(failure);
		assertThat(verdict.individual()).extracting(Judgment::status)
			.containsExactly(JudgmentStatus.PASS, JudgmentStatus.FAIL);
		assertThat(verdict.conclusion()).isEqualTo(Verdict.Conclusion.INCONCLUSIVE);
		var codec = new VerdictCodec();
		var restored = codec.read(codec.write(verdict));
		assertThat(restored).isEqualTo(verdict);
		assertThat(restored.reductionFailure().cause()).isNull();
		assertThat(restored.reductionFailure().code()).isEqualTo(CompositeFailureCode.AGGREGATION_FAILED);
	}

	@Test
	void childJuryFailuresRetainCauseWithoutTransportingThrowable() {
		var failure = new IllegalStateException("private details");
		Jury<String> failed = e -> {
			throw failure;
		};
		for (Jury<String> jury : List.of(
				CascadedJury.<String>builder().tier("final", failed, RoutingRule.FINAL_TIER).build(),
				Juries.meta(new ConsensusStrategy(), new NamedJury<>("member", failed)))) {
			var verdict = jury.vote("e");
			assertThat(verdict.compositeAttempts().getFirst().failure().cause()).isSameAs(failure);
			assertThat(new VerdictCodec().write(verdict)).doesNotContain("private details", "stackTrace");
		}
	}

	@Test
	void invalidConstituentResultThrowsInsteadOfBecomingAnExecutionFailure() {
		var child = Requirement.text("child", "1", "child");
		var parent = new Requirement<>("parent", "1", "child required", new AllOf(List.of(child)), child.source());
		RequirementJury<String, String> invalid = (r, e) -> Verdict.of(Judgment.pass("forged"),
				Map.of("original", Judgment.fail("actual")));
		var prepared = Assignments.<String>forRequirement(parent).jury(child, invalid).validate();
		var policies = new AtomicInteger();
		assertThatThrownBy(() -> Evaluations.apply(prepared.vote("e"), v -> {
			policies.incrementAndGet();
			return new PolicyDecision(PolicyAction.RELY, "yes");
		})).isInstanceOf(IllegalArgumentException.class);
		assertThat(policies).hasValue(0);
		RequirementJury<String, String> wrongAssociation = (r, e) -> Verdict.single("seat", Judgment.pass("yes"))
			.forRequirement(Requirement.text("other", "1", "other"));
		assertThatThrownBy(
				() -> Assignments.<String>forRequirement(parent).jury(child, wrongAssociation).validate().vote("e"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("another requirement");
	}

	@Test
	void storedParticipationCannotLieAboutErrorTreatment() {
		var verdict = SimpleJury.<String>builder()
			.judge(e -> Judgment.pass("one"))
			.judge(e -> Judgment.error("two"))
			.votingStrategy(new MajorityVotingStrategy(TieBreakRule.ABSTAIN, ErrorHandling.IGNORE))
			.build()
			.vote("e");
		var codec = new VerdictCodec();
		String json = codec.write(verdict).replace("ERROR_IGNORED", "ERROR_AS_FAIL");
		assertThatThrownBy(() -> codec.read(json)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("treatment contradicts");
	}

	@Test
	void descriptionExposesVotingOnlyWhereConfiguredWithoutInvokingAnything() {
		var calls = new AtomicInteger();
		RequirementJudge<String, String> judge = (r, e) -> {
			calls.incrementAndGet();
			return Judgment.pass("yes");
		};
		var voting = RequirementJuries.voting(new MajorityVotingStrategy(), List.of(judge));
		assertThat(voting.votingStrategy()).isInstanceOf(MajorityVotingStrategy.class);
		assertThat(voting.judges()).containsExactly(judge);
		assertThat(voting.describe().toPortable().toString()).contains("majority");
		Jury<String> opaque = e -> {
			throw new AssertionError("no execution");
		};
		assertThat(opaque.describe().toPortable().toString()).contains("declared=false");
		var cascade = CascadedJury.<String>builder().tier("last", opaque, RoutingRule.FINAL_TIER).build();
		assertThat(cascade).isNotInstanceOf(VotingJury.class);
		assertThat(cascade.describe().toPortable().toString()).contains("FINAL_TIER");
		assertThat(calls).hasValue(0);
	}

	@Test
	void impossibleRosterAndInvalidAttemptNamesAreRejectedBeforeExecution() {
		var calls = new AtomicInteger();
		RequirementJudge<String, String> judge = (r, e) -> {
			calls.incrementAndGet();
			return Judgment.pass("yes");
		};
		var roster = new ArrayList<Requirement<?>>();
		for (int n = 0; n < 33; n++)
			roster.add(Requirement.text("child-" + n, "1", "child"));
		var source = Requirement.text("source", "1", "reviewed fixture roster").source();
		var parent = new Requirement<>("parent", "1", "all children", new AllOf(roster), source);
		assertThatThrownBy(() -> Assignments.<String>forRequirement(parent).validate())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("attempt limit");
		var invalid = Requirement.text(" invalid ", "1", "child");
		var badParent = new Requirement<>("parent", "1", "invalid child", new AllOf(List.of(invalid)), source);
		assertThatThrownBy(() -> Assignments.<String>forRequirement(badParent).judge(invalid, judge).validate())
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(calls).hasValue(0);
	}

	@Test
	void meaningAndReadOnlyLayersDoNotDependOnStoredDiagnosticsOrAssertions() {
		var classes = new com.tngtech.archunit.core.importer.ClassFileImporter()
			.withImportOption(new com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests())
			.importPackages("io.github.markpollack.judge.evaluation", "io.github.markpollack.judge.reporting",
					"io.github.markpollack.judge.policy", "io.github.markpollack.judge.jury");
		com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
			.that()
			.haveSimpleName("VerdictSemantics")
			.should()
			.dependOnClassesThat()
			.resideInAnyPackage("io.github.markpollack.judge.serialization..", "com.fasterxml.jackson..",
					"org.assertj..")
			.check(classes);
		com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
			.that()
			.resideInAnyPackage("..evaluation..", "..reporting..", "..policy..")
			.should()
			.dependOnClassesThat()
			.resideInAnyPackage("..serialization..", "..assertions..", "..assertj..", "..jev..")
			.check(classes);
	}

}
