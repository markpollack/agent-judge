/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.conformance;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.markpollack.judge.Judges;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.description.KeySource;
import io.github.markpollack.judge.jury.AllMustPassStrategy;
import io.github.markpollack.judge.jury.AttemptDisposition;
import io.github.markpollack.judge.jury.CascadedJury;
import io.github.markpollack.judge.jury.CompositeAttempt;
import io.github.markpollack.judge.jury.ConsensusStrategy;
import io.github.markpollack.judge.jury.Decision;
import io.github.markpollack.judge.jury.DecisionBasis;
import io.github.markpollack.judge.jury.DispositionReason;
import io.github.markpollack.judge.jury.Jury;
import io.github.markpollack.judge.jury.Seat;
import io.github.markpollack.judge.jury.SimpleJury;
import io.github.markpollack.judge.jury.TierPolicy;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.jury.VotingStrategy;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentReasonCode;
import io.github.markpollack.judge.result.JudgmentStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * New construction is strict, and reading old data is a separate decision.
 *
 * <p>
 * The two must not be the same code path. A reader that is lenient enough to load a 0.14 document
 * is lenient enough to accept a 0.17 result that lost a required fact somewhere in transit, and it
 * will accept it silently — which is the one outcome worth engineering against, because the value
 * that comes back looks exactly like a value that was recorded. So the live types refuse anything
 * incomplete, loudly and by name, and a historical document is read into shapes that can
 * <em>hold</em> absence without pretending it is a value.
 * </p>
 *
 * <p>
 * The goldens below pin what a 0.17 result looks like on the wire. They exist so that a change to
 * the format is a decision somebody makes rather than a diff somebody notices later.
 * </p>
 */
@DisplayName("The historical boundary")
class HistoricalBoundaryTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final String VOCABULARY_GOLDEN = "/conformance/result-vocabulary-0.17.json";

	private static final String BOUNDARY_GOLDEN = "/conformance/boundary-rejection-0.17.json";

	private static final JudgmentContext CONTEXT = JudgmentContext.builder().goal("pin the result format").build();

	// ==================== The new vocabulary ====================

	/** Every judgment shape 0.17 adds, in one document. */
	static List<Judgment> vocabulary() {
		return List.of(Judgment.pass("the build succeeded"),
				Judgment.fail("the answer contradicted the retrieved context"),
				Judgment.builder()
					.fail()
					.reasonCode(JudgmentReasonCode.SUBJECT_EMPTY)
					.reasoning("The diff contained no changed files, so there was nothing to assess")
					.build(),
				Judgment.builder()
					.notApplicable()
					.reasoning("The change set contains no Java sources, so the Java style rules do not apply")
					.label("no_java_sources")
					.build(),
				Judgment.error(JudgmentReasonCode.JUDGE_FAILED, "Judge 'flaky' threw java.lang.IllegalStateException"),
				Judgment.propagatedError(
						Map.of(JudgmentReasonCode.JUDGE_REPORTED, 2, JudgmentReasonCode.JUDGE_FAILED, 1),
						"3 of 4 judgments errored and the error policy is propagate"));
	}

	/**
	 * A cascade that stopped on a rejection a boundary-refused tier had already established: the
	 * seven-component verdict, the dispositions, and the parent-authored stage-failed root, all in
	 * one document.
	 */
	static Verdict boundaryRejection() {
		Judgment failing = Judgment.fail("a requirement was not met");
		Verdict excluded = Verdict.builder()
			.aggregated(Judgment.notApplicable("nothing in this rubric applies"))
			.individual(List.of(failing))
			.individualByName(Map.of("strict", failing))
			.seats(List.of(new Seat(0, "strict", KeySource.DECLARED)))
			.decision(Decision.own())
			.build();

		return CascadedJury.builder()
			.tier("rubric", opaque(excluded), TierPolicy.REJECT_ON_ANY_FAIL)
			.tier("semantic", passing(), TierPolicy.FINAL_TIER)
			.build()
			.vote(CONTEXT);
	}

	@Nested
	@DisplayName("Goldens")
	class Goldens {

		@Test
		@DisplayName("every judgment shape 0.17 adds is pinned on the wire")
		void theVocabularyIsPinned() throws Exception {
			assertThat(MAPPER.readTree(writeVocabulary()))
				.as("the result vocabulary changed; review before repinning %s", VOCABULARY_GOLDEN)
				.isEqualTo(goldenTree(VOCABULARY_GOLDEN));
		}

		@Test
		@DisplayName("the vocabulary round-trips through the ordinary mapper with every fact intact")
		void theVocabularyRoundTrips() throws Exception {
			List<Judgment> parsed = MAPPER.readValue(writeVocabulary(),
					MAPPER.getTypeFactory().constructCollectionType(List.class, Judgment.class));

			assertThat(parsed).isEqualTo(vocabulary());
			assertThat(parsed).extracting(Judgment::status)
				.containsExactly(JudgmentStatus.PASS, JudgmentStatus.FAIL, JudgmentStatus.FAIL,
						JudgmentStatus.NOT_APPLICABLE, JudgmentStatus.ERROR, JudgmentStatus.ERROR);
			assertThat(parsed).extracting(Judgment::reasonCode)
				.containsExactly(null, null, JudgmentReasonCode.SUBJECT_EMPTY, null,
						JudgmentReasonCode.JUDGE_FAILED, JudgmentReasonCode.ERRORS_PROPAGATED);
		}

		@Test
		@DisplayName("an absent optional is omitted, never emitted as a JSON null")
		void absentOptionalsAreOmitted() {
			JsonNode uncodedFail = vocabularyTree().get(1);
			JsonNode excluded = vocabularyTree().get(3);

			assertThat(uncodedFail.has("reasonCode")).as("an uncoded rejection records no code").isFalse();
			assertThat(excluded.has("reasonCode")).as("nothing failed").isFalse();
			assertThat(excluded.has("score")).isFalse();
			assertThat(vocabularyTree().toString()).doesNotContain("null");
		}

		@Test
		@DisplayName("the seven-component verdict, its dispositions and its parent-authored root are pinned")
		void theBoundaryRejectionIsPinned() throws Exception {
			assertThat(MAPPER.readTree(writeBoundaryRejection()))
				.as("the boundary-rejection projection changed; review before repinning %s", BOUNDARY_GOLDEN)
				.isEqualTo(goldenTree(BOUNDARY_GOLDEN));
		}

		@Test
		@DisplayName("the boundary rejection round-trips, keeping the child's claim and the parent's marker apart")
		void theBoundaryRejectionRoundTrips() throws Exception {
			Verdict parsed = MAPPER.readValue(writeBoundaryRejection(), Verdict.class);

			assertThat(parsed.decision().basis()).isEqualTo(DecisionBasis.INDIVIDUAL_REJECTION);
			assertThat(parsed.aggregated().reasonCode()).isEqualTo(JudgmentReasonCode.STAGE_FAILED);
			CompositeAttempt refused = parsed.compositeAttempts().get(0);
			assertThat(refused.disposition()).isEqualTo(AttemptDisposition.STAGE_FAILED);
			assertThat(refused.dispositionReason()).isEqualTo(DispositionReason.UNDECLARED_NOT_APPLICABLE);
			assertThat(refused.verdict().aggregated().status()).isEqualTo(JudgmentStatus.NOT_APPLICABLE);
			assertThat(parsed.seats()).containsExactly(new Seat(0, "strict", KeySource.DECLARED));
		}

	}

	@Nested
	@DisplayName("Live types refuse an incomplete result")
	class LiveTypesAreStrict {

		@Test
		@DisplayName("a verdict with no decision cannot be read as one")
		void aMissingDecisionIsRefused() {
			assertThatThrownBy(() -> MAPPER.readValue(without(boundaryRejection(), "decision"), Verdict.class))
				.hasRootCauseInstanceOf(NullPointerException.class)
				.hasMessageContaining("decision");
		}

		@Test
		@DisplayName("a verdict with no seats cannot be read as one, and an empty seat list is a different fact")
		void missingSeatsAreRefused() {
			assertThatThrownBy(() -> MAPPER.readValue(without(boundaryRejection(), "seats"), Verdict.class))
				.hasRootCauseInstanceOf(NullPointerException.class)
				.hasMessageContaining("seats");
			assertThatCode(() -> Verdict.builder()
				.aggregated(Judgment.error(JudgmentReasonCode.NO_TIER_DECIDED, "no tier decided"))
				.decision(Decision.undecided())
				.build()).as("a recorded empty seat list is legal, and says something different")
				.doesNotThrowAnyException();
		}

		@Test
		@DisplayName("a judgment with no status cannot be read as one")
		void aMissingStatusIsRefused() {
			assertThatThrownBy(
					() -> MAPPER.readValue(withoutJudgmentField(vocabulary().get(0), "status"), Judgment.class))
				.hasRootCauseInstanceOf(NullPointerException.class)
				.hasMessageContaining("status");
		}

		@Test
		@DisplayName("an errored judgment with no reason code cannot be read as one")
		void anUncodedErrorIsRefused() {
			assertThatThrownBy(
					() -> MAPPER.readValue(withoutJudgmentField(vocabulary().get(4), "reasonCode"), Judgment.class))
				.hasRootCauseInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("ERROR requires a reasonCode");
		}

		@Test
		@DisplayName("a propagated error whose origin is missing or unreadable cannot be read as one")
		void anInvalidOriginIsRefusedAtTheBoundary() {
			// This is the shape the origin rule calls machinery: a wrapper that names no cause.
			// It can only come from stored data, and it stops here — which is why no live wrapper
			// ever needs fabricating to test the rule.
			String noOrigin = writeJudgment(vocabulary().get(5)).replaceAll("\"errorCodeCounts\":\\{[^}]*\\}",
					"\"errorCodeCounts\":{}");
			String unknownCause = writeJudgment(vocabulary().get(5)).replaceAll("\"errorCodeCounts\":\\{[^}]*\\}",
					"\"errorCodeCounts\":{\"timed_out\":2}");

			assertThatThrownBy(() -> MAPPER.readValue(noOrigin, Judgment.class))
				.hasRootCauseInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("errorCodeCounts");
			assertThatThrownBy(() -> MAPPER.readValue(unknownCause, Judgment.class))
				.hasRootCauseInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("timed_out");
		}

		@Test
		@DisplayName("a composite attempt with no disposition cannot be read as one")
		void aMissingDispositionIsRefused() throws Exception {
			com.fasterxml.jackson.databind.node.ObjectNode root = (com.fasterxml.jackson.databind.node.ObjectNode) MAPPER
				.readTree(writeBoundaryRejection());
			root.withArray("compositeAttempts")
				.forEach(attempt -> ((com.fasterxml.jackson.databind.node.ObjectNode) attempt).remove("disposition"));
			String json = MAPPER.writeValueAsString(root);

			assertThatThrownBy(() -> MAPPER.readValue(json, Verdict.class))
				.hasRootCauseInstanceOf(NullPointerException.class)
				.hasMessageContaining("disposition");
		}

		@Test
		@DisplayName("every new shape converts cleanly, so strictness costs nothing a real result has")
		void everyNewShapeConverts() throws Exception {
			for (Judgment judgment : vocabulary()) {
				assertThat(MAPPER.readValue(writeJudgment(judgment), Judgment.class)).isEqualTo(judgment);
			}
			assertThat(MAPPER.readValue(writeBoundaryRejection(), Verdict.class)).isEqualTo(boundaryRejection());
		}

	}

	// ==================== Helpers ====================

	private static String without(Verdict verdict, String field) {
		try {
			com.fasterxml.jackson.databind.node.ObjectNode node = (com.fasterxml.jackson.databind.node.ObjectNode) MAPPER
				.readTree(MAPPER.writeValueAsString(verdict));
			node.remove(field);
			return MAPPER.writeValueAsString(node);
		}
		catch (Exception ex) {
			throw new AssertionError("could not rewrite the verdict", ex);
		}
	}

	private static String withoutJudgmentField(Judgment judgment, String field) {
		try {
			com.fasterxml.jackson.databind.node.ObjectNode node = (com.fasterxml.jackson.databind.node.ObjectNode) MAPPER
				.readTree(writeJudgment(judgment));
			node.remove(field);
			return MAPPER.writeValueAsString(node);
		}
		catch (Exception ex) {
			throw new AssertionError("could not rewrite the judgment", ex);
		}
	}

	private static String writeJudgment(Judgment judgment) {
		try {
			return MAPPER.writeValueAsString(judgment);
		}
		catch (Exception ex) {
			throw new AssertionError("could not serialize the judgment", ex);
		}
	}

	static String writeVocabulary() {
		try {
			return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(vocabulary());
		}
		catch (Exception ex) {
			throw new AssertionError("could not serialize the vocabulary", ex);
		}
	}

	static String writeBoundaryRejection() {
		try {
			return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(boundaryRejection());
		}
		catch (Exception ex) {
			throw new AssertionError("could not serialize the boundary rejection", ex);
		}
	}

	private static JsonNode vocabularyTree() {
		try {
			return MAPPER.readTree(writeVocabulary());
		}
		catch (Exception ex) {
			throw new AssertionError("could not parse the vocabulary", ex);
		}
	}

	private static JsonNode goldenTree(String resource) {
		try (InputStream golden = HistoricalBoundaryTest.class.getResourceAsStream(resource)) {
			assertThat(golden).as("missing golden resource %s", resource).isNotNull();
			return MAPPER.readTree(new String(golden.readAllBytes(), StandardCharsets.UTF_8));
		}
		catch (Exception ex) {
			throw new AssertionError("could not read " + resource, ex);
		}
	}

	private static Jury passing() {
		return SimpleJury.builder()
			.judge(Judges.named(context -> Judgment.pass("the semantic tier accepted it"), "semantic"))
			.votingStrategy(new ConsensusStrategy())
			.build();
	}

	private static Jury opaque(Verdict verdict) {
		return new Jury() {
			@Override
			public List<Judge> getJudges() {
				return List.of();
			}

			@Override
			public @Nullable VotingStrategy getVotingStrategy() {
				return new AllMustPassStrategy();
			}

			@Override
			public Verdict vote(JudgmentContext context) {
				return verdict;
			}
		};
	}

}
