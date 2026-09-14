package io.github.markpollack.judge.ai;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import io.github.markpollack.judge.JudgeType;
import io.github.markpollack.judge.Judges;
import io.github.markpollack.judge.ai.model.JudgeModel;
import io.github.markpollack.judge.ai.model.JudgeModelResponse;
import io.github.markpollack.judge.ai.prompt.JudgePromptTemplate;
import io.github.markpollack.judge.ai.prompt.JudgePromptTemplate.MissingVariablePolicy;
import io.github.markpollack.judge.ai.prompt.TextSources;
import io.github.markpollack.judge.description.ImplementationIdentity;
import io.github.markpollack.judge.description.JudgeDescription;
import io.github.markpollack.judge.result.Judgment;

import static io.github.markpollack.judge.ai.ModelBackedJudge.JUDGMENT_CLASSIFIER_KEY;
import static io.github.markpollack.judge.ai.ModelBackedJudge.MISSING_VARIABLE_POLICY_KEY;
import static io.github.markpollack.judge.ai.ModelBackedJudge.PROMPT_TEMPLATE_KEY;
import static io.github.markpollack.judge.ai.ModelBackedJudge.PROMPT_TEMPLATE_SHA256_KEY;
import static org.assertj.core.api.Assertions.assertThat;

class ModelBackedJudgeDescriptionTests {

	private static final ObjectMapper JSON = new ObjectMapper();

	@Test
	void declaresPromptDigestPolicyAndClassifierButNoModel() throws Exception {
		String text = "Is {{output}} relevant to {{goal}}?";
		ModelBackedJudge judge = judge(JudgePromptTemplate.fromString("relevance-v2", text),
				JudgmentClassifiers.passFail("relevant", "irrelevant"), stubModel());

		JudgeDescription description = Judges.describe(judge);

		assertThat(description.name()).isEqualTo("relevance");
		assertThat(description.type()).isEqualTo(JudgeType.LLM_POWERED);
		assertThat(description.implementation()).isEqualTo(
				new ImplementationIdentity(ImplementationIdentity.Form.NAMED, ModelBackedJudge.class.getName(), null));
		assertThat(description.configuration())
			.containsOnlyKeys(PROMPT_TEMPLATE_KEY, PROMPT_TEMPLATE_SHA256_KEY, MISSING_VARIABLE_POLICY_KEY,
					JUDGMENT_CLASSIFIER_KEY)
			.containsEntry(PROMPT_TEMPLATE_KEY, "relevance-v2")
			.containsEntry(PROMPT_TEMPLATE_SHA256_KEY, sha256(text))
			.containsEntry(MISSING_VARIABLE_POLICY_KEY, "STRICT")
			.containsEntry(JUDGMENT_CLASSIFIER_KEY,
					Map.of("form", "NAMED", "className", LabelJudgmentClassifier.class.getName()));
	}

	@Test
	void theDigestIsSha256OfTheUtf8TemplateText() {
		JudgePromptTemplate inline = JudgePromptTemplate.fromString("t", "abc");
		JudgePromptTemplate built = JudgePromptTemplate.builder().name("t").source(TextSources.string("abc")).build();
		JudgePromptTemplate edited = JudgePromptTemplate.fromString("t", "abd");

		Object digest = configuration(inline).get(PROMPT_TEMPLATE_SHA256_KEY);

		assertThat(digest).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
		assertThat(configuration(built).get(PROMPT_TEMPLATE_SHA256_KEY)).isEqualTo(digest);
		assertThat(configuration(edited).get(PROMPT_TEMPLATE_SHA256_KEY)).isNotEqualTo(digest);
	}

	@Test
	void aClasspathTemplateIsDigestedFromTheResourceText() throws Exception {
		String text;
		try (InputStream resource = getClass().getClassLoader().getResourceAsStream("judges/test-relevance.md")) {
			assertThat(resource).isNotNull();
			text = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
		}

		JudgePromptTemplate template = JudgePromptTemplate.fromClasspath("judges/test-relevance.md");

		assertThat(configuration(template)).containsEntry(PROMPT_TEMPLATE_KEY, "test-relevance.md")
			.containsEntry(PROMPT_TEMPLATE_SHA256_KEY, sha256(text));
	}

	@Test
	void aLambdaClassifierRecordsNoClassName() throws Exception {
		ModelBackedJudge judge = judge(JudgePromptTemplate.fromString("t", "{{goal}}"),
				response -> Judgment.pass(response.text()), stubModel());

		JudgeDescription description = Judges.describe(judge);

		assertThat(description.configuration()).containsEntry(JUDGMENT_CLASSIFIER_KEY, Map.of("form", "HIDDEN"));
		assertThat(JSON.writeValueAsString(description.toPortable())).doesNotContain("$$Lambda");
	}

	@Test
	void declaresTheMissingVariablePolicyItWasBuiltWith() {
		JudgePromptTemplate template = JudgePromptTemplate.builder()
			.name("lenient")
			.source(TextSources.string("{{goal}} {{metadata.optional}}"))
			.missingVariablePolicy(MissingVariablePolicy.EMPTY_STRING)
			.build();

		assertThat(configuration(template)).containsEntry(MISSING_VARIABLE_POLICY_KEY, "EMPTY_STRING");
	}

	@Test
	void aNamedWrapperStillCarriesTheModelBackedConfiguration() {
		ModelBackedJudge judge = judge(JudgePromptTemplate.fromString("t", "{{goal}}"),
				JudgmentClassifiers.passFail("yes", "no"), stubModel());

		JudgeDescription description = Judges.describe(Judges.named(judge, "relevance-2"));

		assertThat(description.name()).isEqualTo("relevance-2");
		assertThat(description.type()).isEqualTo(JudgeType.DETERMINISTIC);
		assertThat(description.delegateName()).isEqualTo("relevance");
		assertThat(description.delegateType()).isEqualTo(JudgeType.LLM_POWERED);
		assertThat(description.implementation().className()).isEqualTo(ModelBackedJudge.class.getName());
		assertThat(description.configuration()).containsKey(PROMPT_TEMPLATE_SHA256_KEY);
	}

	@Test
	void describingNeverCallsTheModelAndIsRepeatable() throws Exception {
		AtomicInteger calls = new AtomicInteger();
		JudgeModel countingModel = request -> {
			calls.incrementAndGet();
			return new JudgeModelResponse("yes", "counted-model", null, null);
		};
		ModelBackedJudge first = judge(JudgePromptTemplate.fromString("t", "{{goal}}"),
				JudgmentClassifiers.passFail("yes", "no"), countingModel);
		ModelBackedJudge second = judge(JudgePromptTemplate.fromString("t", "{{goal}}"),
				JudgmentClassifiers.passFail("yes", "no"), countingModel);

		String firstJson = JSON.writeValueAsString(Judges.describe(first).toPortable());
		String secondJson = JSON.writeValueAsString(Judges.describe(second).toPortable());

		assertThat(calls).hasValue(0);
		assertThat(firstJson).isEqualTo(secondJson).doesNotContain("counted-model");
	}

	private static Map<String, Object> configuration(JudgePromptTemplate template) {
		return judge(template, JudgmentClassifiers.passFail("yes", "no"), stubModel()).configuration();
	}

	private static ModelBackedJudge judge(JudgePromptTemplate template, JudgmentClassifier classifier,
			JudgeModel model) {
		return ModelBackedJudge.builder()
			.name("relevance")
			.promptTemplate(template)
			.judgmentClassifier(classifier)
			.model(model)
			.build();
	}

	private static JudgeModel stubModel() {
		return request -> new JudgeModelResponse("yes", "test-model", null, null);
	}

	private static String sha256(String text) throws Exception {
		return HexFormat.of()
			.formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
	}

}
