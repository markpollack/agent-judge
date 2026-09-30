# Requirement assertions with AssertJ

Use the staged entry point with an ordinary typed Judge:

```java
import static io.github.markpollack.judge.assertj.Assertions.assertThat;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.requirement.Requirement;

Requirement<String> READY_REQUIREMENT = Requirement.text(
    "response-ready", "1", "The response is exactly READY");
Judge<String> READY_RESPONSE_JUDGE = response -> "READY".equals(response)
    ? Judgment.pass("The response is READY") : Judgment.fail("The response differs");

assertThat(READY_REQUIREMENT)
    .judgedBy(READY_RESPONSE_JUDGE)
    .withEvidence("READY")
    .isSatisfied();
```

The grammar is `Requirement → Judge<E> or Jury<E> → E → optional AcceptancePolicy → isSatisfied()`.
The Judge selects the evidence type. A wrong evidence type, premature terminal, or policy
before evidence fails compilation. Selecting stages performs no evaluation. Each terminal
call evaluates once. Forgetting the terminal is legal Java and performs no assertion.

The default relies on the Judgment as rendered, with no statistical threshold. Supply an
`AcceptancePolicy` lambda after evidence to change reliance for that assertion. Configure
an application default with `Assertions.using(new RequirementAssertions(policy))`.
Requirements remain pure and never carry policies. Optional recording identity is outside
the minimal policy interface.

A requirement-aware evaluator uses `Judge<RequirementEvidence<S,E>>` and
`judgedByRequirement(...)`. It receives the exact Requirement and evidence objects.
This explicit entry keeps ordinary Judge declarations short and avoids a second Judge
hierarchy or an ambiguous erased Java overload.

Jury evaluation preserves its internal rules and full Verdict. The final application rule
cannot replace seat policies, trigger a fallback tier, or repair an unsupported reading.
`RELY` on FAIL establishes a violation; ABSTAIN/ESCALATE are inconclusive; ERROR remains
an instrument failure. N/A and unsupported interpretation have distinct failures.
The AssertJ error preserves the complete `RequirementAssertionError` as its cause.

For retained inspection, use `RequirementAssertions.evaluate(...)` and
`RequirementAssertions.requireSatisfied(result)`. The retained terminal invokes no Judge,
model or policy. See [five progressive API examples](src/test/java/io/github/markpollack/judge/assertj/AssertJApiExperienceTest.java),
[compile grammar probes](src/test/java/io/github/markpollack/judge/assertj/CompileGrammarTest.java)
and the [root quickstart](../README.md).
