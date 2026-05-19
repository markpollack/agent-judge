package io.github.markpollack.judge.ai;

import io.github.markpollack.judge.ai.model.JudgeModelResponse;
import io.github.markpollack.judge.ai.model.Usage;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;
import io.github.markpollack.judge.score.CategoricalScore;
import org.junit.jupiter.api.Test;

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
	void categoricalScoreOnMatch() {
		var classifier = LabelJudgmentClassifier.passFail("good", "bad");

		Judgment judgment = classifier.classify(response("good"));

		assertThat(judgment.score()).isInstanceOf(CategoricalScore.class);
		CategoricalScore score = (CategoricalScore) judgment.score();
		assertThat(score.value()).isEqualTo("good");
	}

	@Test
	void usageMetadataPreserved() {
		var classifier = LabelJudgmentClassifier.passFail("yes", "no");
		Usage usage = new Usage(10, 5, 15, null);
		var resp = new JudgeModelResponse("yes", "gpt-4o", usage, null);

		Judgment judgment = classifier.classify(resp);

		assertThat(judgment.metadata()).containsEntry("model", "gpt-4o");
		assertThat(judgment.metadata()).containsEntry("usage", usage);
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
	void categoriesReturned() {
		var classifier = LabelJudgmentClassifier.passFail("yes", "no");

		assertThat(classifier.categories()).containsExactlyInAnyOrder("yes", "no");
	}

	private JudgeModelResponse response(String text) {
		return new JudgeModelResponse(text, null, null, null);
	}

}
