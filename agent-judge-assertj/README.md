# Requirement assertions with AssertJ

```java
import static io.github.markpollack.judge.assertj.Assertions.assertThat;
import io.github.markpollack.judge.RequirementJudge;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.requirement.Requirement;

var requirement = Requirement.text("ready", "1", "READY");
RequirementJudge<String, String> matches = (actual, response) -> actual.specification().equals(response)
    ? Judgment.pass("matches") : Judgment.fail("differs");
assertThat(requirement).judgedBy(matches).withEvidence("READY").isSatisfied();
```

The grammar is `Requirement<S> → RequirementJudge<S,E> or RequirementJury<S,E> → E → optional Policy → terminal`. `evaluate()` retains a result without asserting; `isSatisfied()` asserts it. Stages do not execute until a terminal. Repeating terminals on the same stage reuses its completed result, including any policy failure. Select `withPolicy(...)` before execution; changing it afterward is refused. Separate branches configured before execution are separate evaluations.

The requirement path rejects ordinary Judges/Juries instead of discarding a supplied specification. Use `assertThatEvidence(evidence).judgedBy(ordinaryJudgeOrJury).isPassed()` for an evidence-only check. Both retained `Verdict` and `EvaluationResult` have `assertThat(...)` overloads with `hasConclusion(...)`.

No policy is requested by default. `withPolicy(v -> new PolicyDecision(PolicyAction.RELY, "reviewed"))` receives the entire usable Verdict. RELY preserves negative conclusions; ABSTAIN/ESCALATE withhold reliance; a thrown exception or null return becomes a failed requested policy. None rewrite producer judgments or restart routing.

`isSatisfied()` requires PASS plus either no requested policy or RELY, and an actual Requirement association. `isPassed()` checks the conclusion alone. Failures preserve the complete evaluation; an AssertJ description wraps the retained `RequirementAssertionError` as its cause. Reporting or asserting a retained result invokes no evaluator.

The compiler rejects evidence/specification mismatches, premature terminals, ordinary-Judge misuse, Jury-as-Judge assignment, and selector output mismatches. Dynamic all-of coverage is validated at runtime before execution. [API examples](src/test/java/io/github/markpollack/judge/assertj/AssertJApiExperienceTest.java), [compiler probes](src/test/java/io/github/markpollack/judge/assertj/CompileGrammarTest.java), and the [root tutorial](../README.md) exercise these paths.
