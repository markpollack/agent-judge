/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury.interpretation;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.markpollack.judge.Judges;
import io.github.markpollack.judge.jury.CascadedJury;
import io.github.markpollack.judge.jury.ConsensusStrategy;
import io.github.markpollack.judge.jury.SimpleJury;
import io.github.markpollack.judge.jury.TierPolicy;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.result.Judgment;

import static io.github.markpollack.judge.jury.interpretation.Fixtures.CONTEXT;
import static io.github.markpollack.judge.jury.interpretation.Fixtures.EXAMPLE_ONE;
import static io.github.markpollack.judge.jury.interpretation.Fixtures.MAPPER;
import static io.github.markpollack.judge.jury.interpretation.Fixtures.stored;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A1, A12, A13: example one, the 0.17 cascade, field for field.
 *
 * <p>The stored verdict was generated through the real jury, recorder and store. One tier,
 * {@code structure} ({@code REJECT_ON_ANY_FAIL}), two declared judges under consensus: one
 * passed, one failed, consensus of the two is {@code abstain}, and the cascade adopted it.
 */
@DisplayName("Example one: the 0.17 cascade")
class ExampleOneTest {

	private static final Evidence EVIDENCE = new Evidence("consensus", "propagate", "refuse", 2, 2, 0, 0, 0, 0, 0, 0,
			0, Map.of(), 1, 1, null, null, null, null);

	private static final List<JudgeSeat> JUDGES = List.of(
			new JudgeSeat(0, "structure:ddd-review.md", "DECLARED", "pass", null, null, null, "report present", List.of()),
			new JudgeSeat(1, "reportStructure", "DECLARED", "fail", null, null, null, "report has no bounded contexts",
					List.of()));

	private static final String REASONING = "No consensus: 1 passed, 1 failed among 2 applicable judge(s)";

	/** The jury that produced example one: the structure tier stops on its FAIL, the final tier never runs. */
	static Verdict sameJury() {
		return CascadedJury.builder()
			.tier("structure", SimpleJury.builder()
				.judge(Judges.named(context -> Judgment.pass("report present"), "structure:ddd-review.md"))
				.judge(Judges.named(context -> Judgment.fail("report has no bounded contexts"), "reportStructure"))
				.votingStrategy(new ConsensusStrategy())
				.build(), TierPolicy.REJECT_ON_ANY_FAIL)
			.tier("quality", Fixtures.passingTier("expertRecall", "never reached"), TierPolicy.FINAL_TIER)
			.build()
			.vote(CONTEXT);
	}

	@Test
	@DisplayName("A1: the stored verdict produces the §1.1 block, field for field")
	void theStoredVerdictProducesTheBlock() {
		Interpretation interpretation = Verdicts.interpret(stored(EXAMPLE_ONE));

		assertThat(interpretation.schemaVersion()).isEqualTo(1);
		assertThat(interpretation.sourceVersion()).as("the seven-component form 0.17 writes").isEqualTo(1);
		assertThat(interpretation.reading()).isEqualTo(VerdictReading.UNDECIDED);
		assertThat(interpretation.readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);
		assertThat(interpretation.decidedBy()).isEqualTo(new DecidedBy("structure", List.of("structure"), "tier_outcome"));
		assertThat(interpretation.defects()).isEmpty();

		assertThat(interpretation.root()).isEqualTo(new Stage(null, List.of(), null, null, null, null, null, null,
				"abstain", null, REASONING, EVIDENCE, JUDGES));
		assertThat(interpretation.stages()).containsExactly(new Stage("structure", List.of("structure"), "cascade_tier",
				"REJECT_ON_ANY_FAIL", "used", null, null, true, "abstain", null, REASONING, EVIDENCE, JUDGES));
		assertThat(interpretation.summary()).isEqualTo(Summaries.of(interpretation));
	}

	@Test
	@DisplayName("A1: the live path on the same jury agrees byte for byte")
	void theLivePathAgreesByteForByte() throws Exception {
		Interpretation fromStore = Verdicts.interpret(stored(EXAMPLE_ONE));
		Interpretation fromJury = Verdicts.interpret(sameJury());

		assertThat(fromJury).isEqualTo(fromStore);
		assertThat(MAPPER.writeValueAsString(fromJury)).isEqualTo(MAPPER.writeValueAsString(fromStore));
	}

	@Test
	@DisplayName("A12: every judge carries its reasoning and checks as recorded")
	void judgesCarryTheirReasons() {
		Interpretation interpretation = Verdicts.interpret(stored(EXAMPLE_ONE));

		assertThat(interpretation.stages().get(0).judges()).extracting(JudgeSeat::reasoning)
			.containsExactly("report present", "report has no bounded contexts");
		assertThat(interpretation.stages().get(0).judges()).extracting(JudgeSeat::checks)
			.containsExactly(List.of(), List.of());
		assertThat(interpretation.stages().get(0).reasoning()).isEqualTo(REASONING);
		assertThat(interpretation.stages().get(0).evidence()).isEqualTo(EVIDENCE);
	}

	@Test
	@DisplayName("A13: the recorded evidence supports the reading")
	void theReadingIsSupported() {
		assertThat(Verdicts.interpret(stored(EXAMPLE_ONE)).readingSupport()).isEqualTo(ReadingSupport.SUPPORTED);
	}

}
