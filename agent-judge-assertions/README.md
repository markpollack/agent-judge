# Retained requirement assertions

Core's `Evaluations` invokes a Judge or Jury and optionally applies a `Policy` to its complete Verdict. `RequirementAssertions` only inspects the retained `EvaluationResult`:

```java
Requirement<String> requirement = Requirement.text("ready", "1", "READY");
RequirementJudge<String, String> judge = (actual, evidence) -> actual.specification().equals(evidence)
    ? Judgment.pass("matches") : Judgment.fail("differs");
EvaluationResult result = Evaluations.evaluate(requirement, judge, "READY");
RequirementAssertions.requireSatisfied(result);
```

The overload with a final `Policy` requests policy execution. Omitting it means `NotRequested`, without an implicit decision. The other policy results are `Decided(originalDecision)` and `Failed(originalThrowable)`; there is no skipped state. Every usable conclusion reaches a requested policy. Invalid configuration and unusable stored records throw without creating an evaluation.

Satisfaction requires an associated Requirement, a PASS conclusion, and either no requested policy or a RELY decision. A requested policy failure prevents success. RELY on a negative result remains rejection. `RequirementAssertionError.result()` retains the complete unchanged result, and a policy failure retains its original exception as the cause. ERROR, abstention, non-applicability, and failed attempts remain inspectable in domain records.

Use `Evaluations.evaluate(Judge<E>, E)` for ordinary checks and the sibling `RequirementJudge<S,E>`/`RequirementJury<S,E>` overloads when supplying a requirement. Requirements never carry policies or execution configuration. A Jury always retains its complete Verdict.

Repeated assertions and `VerdictReport` inspection invoke nothing. The [AssertJ integration](../agent-judge-assertj/README.md) adds staged execution and caches a completed fluent stage. [Storage](../portable-results-v4.md) is independent of assertions. Tests use local fixtures; the conference runner remains separately invoked and requires explicit credentials. Frozen fixture vocabulary is translated only by the test loader.
