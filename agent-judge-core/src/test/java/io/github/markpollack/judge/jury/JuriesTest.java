/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import org.junit.jupiter.api.Test;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.completion.CompletionEvidence;
import io.github.markpollack.judge.judgment.JudgmentStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static io.github.markpollack.judge.JudgeTestFixtures.*;

/**
 * Tests for {@link Juries} utility class.
 *
 * @author Mark Pollack
 * @since 0.1.0
 */
class JuriesTest {

	@Test
	void fromJudgesShouldCreateJuryWithAutoNaming() {
		Judge<CompletionEvidence> unnamedJudge1 = ctx -> booleanPass("Pass 1");
		Judge<CompletionEvidence> unnamedJudge2 = ctx -> booleanFail("Fail 2");

		Jury<CompletionEvidence> jury = Juries.fromJudges(new MajorityVotingStrategy(), unnamedJudge1, unnamedJudge2);

		CompletionEvidence context = simpleContext("Test goal");
		Verdict verdict = jury.vote(context);

		assertThat(verdict.individualByName()).containsKeys("Judge#1", "Judge#2");
	}

	@Test
	void fromJudgesShouldPreserveNamedJudges() {
		Judge<CompletionEvidence> named1 = alwaysPass("FileExists");
		Judge<CompletionEvidence> named2 = alwaysFail("Correctness");
		Judge<CompletionEvidence> named3 = alwaysPass("BuildSuccess");

		Jury<CompletionEvidence> jury = Juries.fromJudges(new MajorityVotingStrategy(), named1, named2, named3);

		CompletionEvidence context = simpleContext("Test goal");
		Verdict verdict = jury.vote(context);

		assertThat(verdict.individualByName()).containsKeys("FileExists", "Correctness", "BuildSuccess");
	}

	@Test
	void fromJudgesShouldHandleDuplicateNamesWithSuffixes() {
		Judge<CompletionEvidence> judge1 = alwaysPass("FileCheck");
		Judge<CompletionEvidence> judge2 = alwaysFail("FileCheck"); // duplicate name
		Judge<CompletionEvidence> judge3 = alwaysPass("FileCheck"); // another duplicate

		Jury<CompletionEvidence> jury = Juries.fromJudges(new MajorityVotingStrategy(), judge1, judge2, judge3);

		CompletionEvidence context = simpleContext("Test goal");
		Verdict verdict = jury.vote(context);

		// First keeps original, duplicates get -2, -3 suffixes
		assertThat(verdict.individualByName()).containsKeys("FileCheck", "FileCheck-2", "FileCheck-3");
	}

	@Test
	void fromJudgesShouldHandleMixedNamedAndUnnamed() {
		Judge<CompletionEvidence> named = alwaysPass("NamedJudge");
		Judge<CompletionEvidence> unnamed = ctx -> booleanFail("Unnamed");

		Jury<CompletionEvidence> jury = Juries.fromJudges(new MajorityVotingStrategy(), named, unnamed);

		CompletionEvidence context = simpleContext("Test goal");
		Verdict verdict = jury.vote(context);

		assertThat(verdict.individualByName()).containsKeys("NamedJudge", "Judge#2");
	}

	@Test
	void fromJudgesShouldRequireAtLeastOneJudge() {
		assertThatThrownBy(() -> Juries.fromJudges(new MajorityVotingStrategy()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("At least one judge is required");
	}

	@Test
	void fromJudgesShouldRejectNullJudges() {
		assertThatThrownBy(() -> Juries.fromJudges(new MajorityVotingStrategy(), (Judge<CompletionEvidence>[]) null))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void fromJudgesNamesThePositionOfAJudgeWithNullMetadata() {
		assertThatThrownBy(() -> Juries.fromJudges(new MajorityVotingStrategy(), alwaysPass("Build"),
				nullMetadata(booleanPass("never kept"))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("position 1")
			.hasMessageContaining("metadata() returned null");
	}

	@Test
	void fromJudgesNamesThePositionOfAJudgeWhoseMetadataThrows() {
		IllegalStateException failure = new IllegalStateException("registry offline");

		assertThatThrownBy(() -> Juries.fromJudges(new MajorityVotingStrategy(),
				throwingMetadata(failure, booleanPass("never kept")), alwaysPass("Build")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("position 0")
			.hasMessageContaining("metadata() threw")
			.hasMessageContaining("registry offline")
			.hasCause(failure);
	}

	// ==================== combine() Tests ====================

	@Test
	void combineShouldCreateMetaJury() {
		Jury<CompletionEvidence> jury1 = Juries.fromJudges(new MajorityVotingStrategy(), alwaysPass("J1"),
				alwaysPass("J2"));

		Jury<CompletionEvidence> jury2 = Juries.fromJudges(new MajorityVotingStrategy(), alwaysFail("J3"),
				alwaysFail("J4"));

		Jury<CompletionEvidence> metaJury = Juries.combine(jury1, jury2, new MajorityVotingStrategy());

		CompletionEvidence context = simpleContext("Test goal");
		Verdict verdict = metaJury.vote(context);

		// jury1 → PASS, jury2 → FAIL, majority → FAIL (tie resolved by TieBreakRule)
		assertThat(verdict.compositeAttempts()).extracting(CompositeAttempt::name)
			.containsExactly("member-1", "member-2");
	}

	@Test
	void combineShouldRequireNonNullJuries() {
		Jury<CompletionEvidence> jury = Juries.fromJudges(new MajorityVotingStrategy(), alwaysPass("J1"));

		assertThatThrownBy(() -> Juries.combine(null, jury, new MajorityVotingStrategy()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Both juries must be non-null");

		assertThatThrownBy(() -> Juries.combine(jury, null, new MajorityVotingStrategy()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Both juries must be non-null");
	}

	// ==================== allOf() Tests ====================

	@Test
	void allOfShouldCreateMetaJuryFromMultiple() {
		Jury<CompletionEvidence> jury1 = Juries.fromJudges(new MajorityVotingStrategy(), alwaysPass("J1"));
		Jury<CompletionEvidence> jury2 = Juries.fromJudges(new MajorityVotingStrategy(), alwaysPass("J2"));
		Jury<CompletionEvidence> jury3 = Juries.fromJudges(new MajorityVotingStrategy(), alwaysPass("J3"));

		Jury<CompletionEvidence> metaJury = Juries.allOf(new ConsensusStrategy(), jury1, jury2, jury3);

		CompletionEvidence context = simpleContext("Test goal");
		Verdict verdict = metaJury.vote(context);

		// All juries pass → consensus pass
		assertThat(verdict.compositeAttempts()).extracting(CompositeAttempt::name)
			.containsExactly("member-1", "member-2", "member-3");
		assertThat(verdict.judgment().status()).isEqualTo(JudgmentStatus.PASS);
	}

	@Test
	void allOfShouldRequireAtLeastOneJury() {
		assertThatThrownBy(() -> Juries.allOf(new ConsensusStrategy())).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("At least one jury is required");
	}

	@Test
	void allOfShouldRejectNullJuries() {
		assertThatThrownBy(() -> Juries.allOf(new ConsensusStrategy(), (Jury<CompletionEvidence>[]) null))
			.isInstanceOf(IllegalArgumentException.class);
	}

	// ==================== Integration Tests ====================

	@Test
	void shouldSupportComplexJuryComposition() {
		// Create specialized juries
		Jury<CompletionEvidence> fileJury = Juries.fromJudges(new MajorityVotingStrategy(), alwaysPass("FileExists"),
				alwaysPass("FileContent"));

		Jury<CompletionEvidence> buildJury = Juries.fromJudges(new MajorityVotingStrategy(), alwaysPass("MavenBuild"),
				alwaysPass("GradleBuild"));

		Jury<CompletionEvidence> correctnessJury = Juries.fromJudges(new ConsensusStrategy(),
				alwaysPass("Correctness1"), alwaysPass("Correctness2"));

		// Combine into meta-jury
		Jury<CompletionEvidence> metaJury = Juries.allOf(new MajorityVotingStrategy(), fileJury, buildJury,
				correctnessJury);

		CompletionEvidence context = simpleContext("Complex evaluation");
		Verdict verdict = metaJury.vote(context);

		// All sub-juries pass → majority passes
		assertThat(verdict.compositeAttempts()).hasSize(3);
		assertThat(verdict.judgment().status()).isEqualTo(JudgmentStatus.PASS);
	}

	@Test
	void shouldPreserveJudgeIdentityThroughComposition() {
		Judge<CompletionEvidence> judge1 = alwaysPass("UniqueJudge1");
		Judge<CompletionEvidence> judge2 = alwaysFail("UniqueJudge2");

		Jury<CompletionEvidence> jury = Juries.fromJudges(new MajorityVotingStrategy(), judge1, judge2);

		CompletionEvidence context = simpleContext("Test");
		Verdict verdict = jury.vote(context);

		assertThat(verdict.individualByName().get("UniqueJudge1").status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(verdict.individualByName().get("UniqueJudge2").status()).isEqualTo(JudgmentStatus.FAIL);
	}

}
