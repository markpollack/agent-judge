/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury;

import io.github.markpollack.judge.verdict.CompositeAttempt;
import io.github.markpollack.judge.verdict.Verdict;
import io.github.markpollack.judge.voting.ConsensusStrategy;
import io.github.markpollack.judge.voting.MajorityVotingStrategy;
import io.github.markpollack.judge.voting.TieBreakRule;

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
		Judge unnamedJudge1 = () -> booleanPass("Pass 1");
		Judge unnamedJudge2 = () -> booleanFail("Fail 2");

		Jury jury = Juries.fromJudges(new MajorityVotingStrategy(), unnamedJudge1, unnamedJudge2);

		CompletionEvidence context = simpleContext("Test goal");
		Verdict verdict = jury.vote();

		assertThat(verdict.individualByName()).containsKeys("Judge#1", "Judge#2");
	}

	@Test
	void fromJudgesShouldPreserveNamedJudges() {
		Judge named1 = alwaysPass("FileExists");
		Judge named2 = alwaysFail("Correctness");
		Judge named3 = alwaysPass("BuildSuccess");

		Jury jury = Juries.fromJudges(new MajorityVotingStrategy(), named1, named2, named3);

		CompletionEvidence context = simpleContext("Test goal");
		Verdict verdict = jury.vote();

		assertThat(verdict.individualByName()).containsKeys("FileExists", "Correctness", "BuildSuccess");
	}

	@Test
	void fromJudgesShouldHandleDuplicateNamesWithSuffixes() {
		Judge judge1 = alwaysPass("FileCheck");
		Judge judge2 = alwaysFail("FileCheck"); // duplicate name
		Judge judge3 = alwaysPass("FileCheck"); // another duplicate

		Jury jury = Juries.fromJudges(new MajorityVotingStrategy(), judge1, judge2, judge3);

		CompletionEvidence context = simpleContext("Test goal");
		Verdict verdict = jury.vote();

		// First keeps original, duplicates get -2, -3 suffixes
		assertThat(verdict.individualByName()).containsKeys("FileCheck", "FileCheck-2", "FileCheck-3");
	}

	@Test
	void fromJudgesShouldHandleMixedNamedAndUnnamed() {
		Judge named = alwaysPass("NamedJudge");
		Judge unnamed = () -> booleanFail("Unnamed");

		Jury jury = Juries.fromJudges(new MajorityVotingStrategy(), named, unnamed);

		CompletionEvidence context = simpleContext("Test goal");
		Verdict verdict = jury.vote();

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
		assertThatThrownBy(() -> Juries.fromJudges(new MajorityVotingStrategy(), (Judge[]) null))
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
	void namedMetaShouldCreateMetaJury() {
		Jury jury1 = Juries.fromJudges(new MajorityVotingStrategy(), alwaysPass("J1"), alwaysPass("J2"));

		Jury jury2 = Juries.fromJudges(new MajorityVotingStrategy(), alwaysFail("J3"), alwaysFail("J4"));

		Jury metaJury = Juries.meta(new MajorityVotingStrategy(), new NamedJury("member-1", jury1),
				new NamedJury("member-2", jury2));

		CompletionEvidence context = simpleContext("Test goal");
		Verdict verdict = metaJury.vote();

		// jury1 → PASS, jury2 → FAIL, majority → FAIL (tie resolved by TieBreakRule)
		assertThat(verdict.compositeAttempts()).extracting(CompositeAttempt::name)
			.containsExactly("member-1", "member-2");
	}

	@Test
	void namedMetaShouldRequireNonNullJuries() {
		Jury jury = Juries.fromJudges(new MajorityVotingStrategy(), alwaysPass("J1"));

		assertThatThrownBy(() -> Juries.meta(new MajorityVotingStrategy(), null, new NamedJury("member-2", jury)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Named jury must not be null");

		assertThatThrownBy(() -> Juries.meta(new MajorityVotingStrategy(), new NamedJury("member-1", jury), null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Named jury must not be null");
	}

	// ==================== allOf() Tests ====================

	@Test
	void namedPopulationShouldCreateMetaJuryFromMultiple() {
		Jury jury1 = Juries.fromJudges(new MajorityVotingStrategy(), alwaysPass("J1"));
		Jury jury2 = Juries.fromJudges(new MajorityVotingStrategy(), alwaysPass("J2"));
		Jury jury3 = Juries.fromJudges(new MajorityVotingStrategy(), alwaysPass("J3"));

		Jury metaJury = Juries.meta(new ConsensusStrategy(), new NamedJury("member-1", jury1),
				new NamedJury("member-2", jury2), new NamedJury("member-3", jury3));

		CompletionEvidence context = simpleContext("Test goal");
		Verdict verdict = metaJury.vote();

		// All juries pass → consensus pass
		assertThat(verdict.compositeAttempts()).extracting(CompositeAttempt::name)
			.containsExactly("member-1", "member-2", "member-3");
		assertThat(verdict.judgment().status()).isEqualTo(JudgmentStatus.PASS);
	}

	@Test
	void namedPopulationShouldRequireAtLeastOneJury() {
		assertThatThrownBy(() -> Juries.meta(new ConsensusStrategy())).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("At least one named jury is required");
	}

	@Test
	void namedPopulationShouldRejectNullJuries() {
		assertThatThrownBy(() -> Juries.meta(new ConsensusStrategy(), (NamedJury[]) null))
			.isInstanceOf(IllegalArgumentException.class);
	}

	// ==================== Integration Tests ====================

	@Test
	void shouldSupportComplexJuryComposition() {
		// Create specialized juries
		Jury fileJury = Juries.fromJudges(new MajorityVotingStrategy(), alwaysPass("FileExists"),
				alwaysPass("FileContent"));

		Jury buildJury = Juries.fromJudges(new MajorityVotingStrategy(), alwaysPass("MavenBuild"),
				alwaysPass("GradleBuild"));

		Jury correctnessJury = Juries.fromJudges(new ConsensusStrategy(), alwaysPass("Correctness1"),
				alwaysPass("Correctness2"));

		// Combine into meta-jury
		Jury metaJury = Juries.meta(new MajorityVotingStrategy(), new NamedJury("member-1", fileJury),
				new NamedJury("member-2", buildJury), new NamedJury("member-3", correctnessJury));

		CompletionEvidence context = simpleContext("Complex evaluation");
		Verdict verdict = metaJury.vote();

		// All sub-juries pass → majority passes
		assertThat(verdict.compositeAttempts()).hasSize(3);
		assertThat(verdict.judgment().status()).isEqualTo(JudgmentStatus.PASS);
	}

	@Test
	void shouldPreserveJudgeIdentityThroughComposition() {
		Judge judge1 = alwaysPass("UniqueJudge1");
		Judge judge2 = alwaysFail("UniqueJudge2");

		Jury jury = Juries.fromJudges(new MajorityVotingStrategy(), judge1, judge2);

		CompletionEvidence context = simpleContext("Test");
		Verdict verdict = jury.vote();

		assertThat(verdict.individualByName().get("UniqueJudge1").status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(verdict.individualByName().get("UniqueJudge2").status()).isEqualTo(JudgmentStatus.FAIL);
	}

}
