/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.consumer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.JudgeType;
import io.github.markpollack.judge.Judges;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.jury.AverageVotingStrategy;
import io.github.markpollack.judge.jury.CascadedJury;
import io.github.markpollack.judge.jury.ConjunctiveStrategy;
import io.github.markpollack.judge.jury.ConsensusStrategy;
import io.github.markpollack.judge.jury.ErrorPolicy;
import io.github.markpollack.judge.jury.Juries;
import io.github.markpollack.judge.jury.Jury;
import io.github.markpollack.judge.jury.MajorityVotingStrategy;
import io.github.markpollack.judge.jury.NamedJury;
import io.github.markpollack.judge.jury.SimpleJury;
import io.github.markpollack.judge.jury.TiePolicy;
import io.github.markpollack.judge.jury.TierPolicy;
import io.github.markpollack.judge.jury.WeightedAverageStrategy;
import io.github.markpollack.judge.result.Judgment;

/**
 * The jury whose description must be byte-identical across JVM runs, and the entry point a
 * separate JVM runs to write that description.
 *
 * <p>
 * The jury deliberately holds everything whose runtime identity varies between runs: a lambda,
 * whose hidden class name carries a memory address; an anonymous class; a library combinator;
 * and a declared configuration built with {@code Map.of}, whose iteration order is salted per
 * JVM.
 * </p>
 */
public final class DescriptionFixture {

	private DescriptionFixture() {
	}

	/**
	 * Write the fixture jury's portable description, as Jackson JSON, to the given file.
	 * @param args the output file
	 * @throws IOException if the file cannot be written
	 */
	public static void main(String[] args) throws IOException {
		Files.write(Path.of(args[0]), describe());
	}

	static byte[] describe() throws IOException {
		return new ObjectMapper().writeValueAsBytes(jury().describe().toPortable());
	}

	static Jury jury() {
		Judge lambda = ctx -> Judgment.pass("lambda");
		Judge anonymous = new Judge() {
			@Override
			public Judgment judge(JudgmentContext context) {
				return Judgment.fail("anonymous");
			}
		};
		Map<String, Object> rubric = Map.of("passMark", 0.75, "criteria", List.of("correct", "complete"), "version", 3,
				"strict", true, "levels", Map.of("high", 1.0, "mid", 0.5, "low", 0.0));

		Jury gate = SimpleJury.builder()
			.judge(lambda)
			.judge(anonymous, 2.0)
			.judge(Judges.allOf(lambda, anonymous), 0.5)
			.votingStrategy(new WeightedAverageStrategy(0.5, ErrorPolicy.IGNORE))
			.build();
		Jury rubricJury = SimpleJury.builder()
			.judge(Judges.named(new DeclaringJudge(rubric), "rubric", "declares its rubric", JudgeType.LLM_POWERED))
			.votingStrategy(new AverageVotingStrategy(0.8))
			.build();
		Jury duplicates = Juries.fromJudges(new ConsensusStrategy(), Judges.named(lambda, "same"),
				Judges.named(anonymous, "same"));
		Jury review = Juries.meta(new MajorityVotingStrategy(TiePolicy.ABSTAIN, ErrorPolicy.TREAT_AS_ABSTAIN),
				new NamedJury("rubric", rubricJury), new NamedJury("duplicates", duplicates));
		Jury last = Juries.fromJudges(new ConjunctiveStrategy(0.6), new KeywordJudge("done"), lambda);

		return CascadedJury.builder()
			.tier("gate", gate, TierPolicy.REJECT_ON_ANY_FAIL)
			.tier("review", review, TierPolicy.ACCEPT_ON_ALL_PASS)
			.tier("final", last, TierPolicy.FINAL_TIER)
			.build();
	}

}
