# Configured assertions with AssertJ

```java
import static io.github.markpollack.judge.assertj.Assertions.assertThat;
import io.github.markpollack.judge.construction.NonEmptyJudge;
import io.github.markpollack.judge.jury.SimpleJury;

var judge = NonEmptyJudge.builder().evidence("READY").build();
var jury = SimpleJury.builder().judge("ready", judge).build();
assertThat(judge).isPassed();
assertThat(jury).isPassed();
var result = assertThat(jury).evaluate();
assertThat(result).isPassed();
```

Ready Judge/Jury stages optionally select `withPolicy(policy)` before execution. Requirement-first assertions use `assertThat(actual).judgedBy(typedRecipe).withEvidence(evidence).isSatisfied()`. The recipe constructs the actual public producer; it cannot silently discard the Requirement. Evidence suppliers are also supported.

Construction and incomplete chains execute nothing. Each completed stage executes once and caches either its complete evaluation or thrown failure, including cancellation/fatal errors. Separate branches prepared before execution are separate evaluations. Policy sees the whole usable Verdict once. Changing policy after execution is refused.

Retained Verdict/EvaluationResult assertions inspect `hasConclusion(...)` and `isPassed()` only; they cannot attach a policy or request new satisfaction evaluation. Use core `Evaluations.apply(verdict, policy)` to make a new, explicit policy decision. Reporting and retained assertions invoke nothing.

Live `isSatisfied()` requires actual requirement context, PASS, and either no requested policy or RELY. RELY on FAIL remains rejection. Requested policy failures retain the original cause. Compiler tests reject premature terminals, wrong specification/evidence, double evidence selection and input arguments on ready execution. Ordinary ready Juries can mix independently configured requirements/evidence.
