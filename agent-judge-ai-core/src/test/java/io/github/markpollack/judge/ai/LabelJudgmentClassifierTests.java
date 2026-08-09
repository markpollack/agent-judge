package io.github.markpollack.judge.ai;

import io.github.markpollack.judge.ai.model.JudgeModelResponse;
import io.github.markpollack.judge.ai.model.Usage;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LabelJudgmentClassifierTests {

	@Test
	void passFailExactMatch() {
		var classifier = LabelJudgmentClassifier.passFail("yes", "no");

		assertThat(classifier.classify(response("yes")).status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(classifier.classify(response("no")).status()).isEqualTo(JudgmentStatus.FAIL);
	}

	@Test
	void caseInsensitiveMatch() {
		var classifier = LabelJudgmentClassifier.passFail("YES", "NO");

		assertThat(classifier.classify(response("yes")).status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(classifier.classify(response("Yes")).status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(classifier.classify(response("NO")).status()).isEqualTo(JudgmentStatus.FAIL);
	}

	@Test
	void whitespaceTrimming() {
		var classifier = LabelJudgmentClassifier.passFail("pass", "fail");

		assertThat(classifier.classify(response("  pass  ")).status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(classifier.classify(response("\nfail\n")).status()).isEqualTo(JudgmentStatus.FAIL);
	}

	@Test
	void abstainOnNoMatch() {
		var classifier = LabelJudgmentClassifier.passFail("relevant", "irrelevant");

		Judgment judgment = classifier.classify(response("maybe somewhat relevant"));

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(judgment.reasoning()).contains("maybe somewhat relevant");
		assertThat(judgment.metadata()).containsEntry("rawJudgeOutput", "maybe somewhat relevant");
	}

	@Test
	void matchRecordsTheLabelAndNoScore() {
		var classifier = LabelJudgmentClassifier.passFail("good", "bad");

		Judgment judgment = classifier.classify(response("good"));

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(judgment.label()).isEqualTo("good");
		assertThat(judgment.score()).isNull();
	}

	/**
	 * A label is not implicitly a number. It acquires one only where the classifier that owns
	 * the vocabulary declares it, which is the contrast this pair pins.
	 */
	@Test
	void declaredNumericMeaningIsRecordedAlongsideTheLabel() {
		var classifier = LabelJudgmentClassifier.builder()
			.pass("excellent", 1.0)
			.pass("good", 0.6)
			.fail("poor", 0.0)
			.build();

		Judgment judgment = classifier.classify(response("good"));

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(judgment.label()).isEqualTo("good");
		assertThat(judgment.score()).isEqualTo(0.6);
		assertThat(classifier.scoreFor("good")).hasValue(0.6);
		assertThat(classifier.scoreFor("unknown")).isEmpty();
	}

	@Test
	void usageMetadataPreserved() {
		var classifier = LabelJudgmentClassifier.passFail("yes", "no");
		Usage usage = Usage.builder().inputTokens(10).outputTokens(5).reportedTotalTokens(15).build();
		var resp = new JudgeModelResponse("yes", "gpt-4o", usage, null);

		Judgment judgment = classifier.classify(resp);

		assertThat(judgment.metadata()).containsEntry("model", "gpt-4o");
		// The Usage record is a Java identity; the result carries its portable projection.
		// An unreported category is omitted rather than carried as a null.
		assertThat(judgment.metadata()).containsEntry("usage",
				Map.of("inputTokens", 10L, "outputTokens", 5L, "reportedTotalTokens", 15L));
	}

	/**
	 * Every category the model reported reaches the result. The classifier does not decide
	 * which quantities are interesting, so a source that reports reasoning and cache
	 * activity is not flattened back down to a prompt/completion pair.
	 */
	@Test
	void usageProjectionCarriesEveryReportedCategory() {
		var classifier = LabelJudgmentClassifier.passFail("yes", "no");
		Usage usage = Usage.builder()
			.inputTokens(10)
			.outputTokens(5)
			.reasoningTokens(4)
			.cacheCreationTokens(3)
			.cacheReadTokens(2)
			.build();
		var resp = new JudgeModelResponse("yes", "gpt-4o", usage, null);

		Judgment judgment = classifier.classify(resp);

		assertThat(judgment.metadata().get("usage")).isEqualTo(Map.of("inputTokens", 10L, "outputTokens", 5L,
				"reasoningTokens", 4L, "cacheCreationTokens", 3L, "cacheReadTokens", 2L));
	}

	@Test
	void usageThatReportedNothingAddsNoKey() {
		var classifier = LabelJudgmentClassifier.passFail("yes", "no");
		var resp = new JudgeModelResponse("yes", "gpt-4o", Usage.builder().build(), null);

		Judgment judgment = classifier.classify(resp);

		assertThat(judgment.metadata()).doesNotContainKey("usage");
	}

	@Test
	void absentUsageAddsNoKey() {
		var classifier = LabelJudgmentClassifier.passFail("yes", "no");

		Judgment judgment = classifier.classify(new JudgeModelResponse("yes", "gpt-4o", null, null));

		assertThat(judgment.metadata()).doesNotContainKey("usage");
	}

	@Test
	void builderCustomMappings() {
		var classifier = LabelJudgmentClassifier.builder()
			.pass("correct")
			.fail("incorrect")
			.abstain("unsure")
			.build();

		assertThat(classifier.classify(response("correct")).status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(classifier.classify(response("incorrect")).status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(classifier.classify(response("unsure")).status()).isEqualTo(JudgmentStatus.ABSTAIN);
	}

	@Test
	void builderRequiresAtLeastOneMapping() {
		assertThatThrownBy(() -> LabelJudgmentClassifier.builder().build())
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void builderRejectsBlankLabels() {
		assertThatThrownBy(() -> LabelJudgmentClassifier.builder().pass("  "))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("non-blank");
		assertThatThrownBy(() -> LabelJudgmentClassifier.passFail("  ", "no"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("non-blank");
	}

	@Test
	void laterUnscoredMappingClearsEarlierDeclaredScore() {
		var classifier = LabelJudgmentClassifier.builder().pass("review", 0.8).abstain("review").build();

		assertThat(classifier.classify(response("review")).status()).isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(classifier.scoreFor("review")).isEmpty();
	}

	@Test
	void categoriesReturned() {
		var classifier = LabelJudgmentClassifier.passFail("yes", "no");

		assertThat(classifier.categories()).containsExactlyInAnyOrder("yes", "no");
	}

	private JudgeModelResponse response(String text) {
		return new JudgeModelResponse(text, null, null, null);
	}

}
