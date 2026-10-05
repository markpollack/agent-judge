package io.github.markpollack.judge.rag;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.ai.model.*;
import io.github.markpollack.judge.judgment.JudgmentStatus;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class PortableRagRuntimeTest {
 @Test void allHelpersUseThePortablePathAndAcquireFreshEvidence() {
  var calls=new AtomicInteger();var acquired=new AtomicInteger();
  EvalModel runtime=q -> {calls.incrementAndGet();return new EvalModelResponse("Answer: YES\nReasoning: supported",null,null,Map.of("native","retained"));};
  java.util.function.Supplier<RagEvidence> evidence=() -> {acquired.incrementAndGet();return new RagEvidence("question","context","answer");};
  List<Judge> judges=List.of(FaithfulnessJudge.builder().runtime(runtime).evidenceSupplier(evidence).build(),ContextualRelevanceJudge.builder().runtime(runtime).evidenceSupplier(evidence).build(),HallucinationJudge.builder().runtime(runtime).evidenceSupplier(evidence).build());
  assertThat(calls).hasValue(0);assertThat(acquired).hasValue(0);
  for(var judge:judges) for(int i=0;i<2;i++) {
   var result=judge.judge();assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
   assertThat(result.invocations()).hasSize(1);assertThat(result.invocations().getFirst().nativeFacts()).containsEntry("native","retained");
  }
  assertThat(calls).hasValue(6);assertThat(acquired).hasValue(6);
 }
 @Test void noAnswerAndCancellationAreNotParsedAsSubjectFindings() {
  EvalModel failed=q -> {throw new IllegalStateException("failure");};
  var result=FaithfulnessJudge.builder().runtime(failed).evidence(new RagEvidence("q","c","a")).build().judge();
  assertThat(result.status()).isEqualTo(JudgmentStatus.ERROR);assertThat(result.finding()).isNull();
  assertThat(result.invocations().getFirst().nativeFacts()).containsEntry("answerState","NO_ANSWER").doesNotContainKey("text");
  var cancellation=new CancellationException("cancel");
  assertThatThrownBy(() -> FaithfulnessJudge.builder().runtime(q -> {throw cancellation;}).evidence(new RagEvidence("q","c","a")).build().judge()).isSameAs(cancellation);
 }
}
