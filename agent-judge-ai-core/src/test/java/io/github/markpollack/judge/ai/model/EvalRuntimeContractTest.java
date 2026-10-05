package io.github.markpollack.judge.ai.model;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EvalRuntimeContractTest {
 @Test void requestsAreSnapshotsAndOptionsAreObservedWithoutInventedDefaults() {
  var nested=new ArrayList<>(List.of("before"));
  var request=new EvalModelRequest(List.of(new EvalMessage(EvalMessageRole.USER,"prompt")),
    new EvalModelOptions("configured",0.2,40,null,null),Map.of("nested",nested));
  nested.add("after");
  var execution=((EvalModel)q -> new EvalModelResponse("answer",null,null,Map.of())).execute(request);
  assertThat(execution.invocation().nativeFacts().get("requestMetadata")).isEqualTo(Map.of("nested",List.of("before")));
  assertThat(execution.invocation().nativeFacts().get("options")).isEqualTo(Map.of("model","configured","temperature",0.2,"maxTokens",40));
  assertThat(execution.invocation().nativeFacts()).doesNotContainKey("usage");
 }
 @Test void noAnswerIsSeparateFromReturnedAnswerWithMappingFailure() {
  var cause=new IllegalStateException("backend never returned");
  var failed=((EvalModel)q -> {throw cause;}).execute(EvalModelRequest.user("prompt"));
  assertThat(failed.answer().hasAnswer()).isFalse();
  assertThat(failed.answer().failure()).isSameAs(cause);
  assertThat(failed.invocation().nativeFacts()).doesNotContainKey("text");
  var returned=((EvalModel)q -> new EvalModelResponse("native answer","reported",null,Map.of("raw","bytes"),false,List.of(),cause)).execute(EvalModelRequest.user("prompt"));
  assertThat(returned.answer().hasAnswer()).isTrue();
  assertThat(returned.invocation().nativeFacts()).containsEntry("text","native answer").containsEntry("raw","bytes");
 }
 @Test void directExecutionsAreFreshUnderConcurrentReuse() throws Exception {
  var calls=new AtomicInteger();
  EvalModel runtime=q -> {calls.incrementAndGet();return new EvalModelResponse("answer",null,null,Map.of());};
  try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
   var tasks=new ArrayList<Future<String>>();
   for(int i=0;i<12;i++) tasks.add(executor.submit(() -> runtime.execute(EvalModelRequest.user("prompt")).invocation().id()));
   var ids=new HashSet<String>();for(var task:tasks) ids.add(task.get());
   assertThat(ids).hasSize(12);assertThat(calls).hasValue(12);
  }
 }
 @Test void cancellationAndPostReturnInterruptionEscape() {
  var original=new CancellationException("cancel");
  assertThatThrownBy(() -> ((EvalModel)q -> {throw original;}).execute(EvalModelRequest.user("prompt"))).isSameAs(original);
  try {
   assertThatThrownBy(() -> ((EvalModel)q -> {Thread.currentThread().interrupt();return new EvalModelResponse("answer",null,null,Map.of());}).execute(EvalModelRequest.user("prompt"))).isInstanceOf(CancellationException.class);
   assertThat(Thread.currentThread().isInterrupted()).isTrue();
  } finally {Thread.interrupted();}
 }
}
