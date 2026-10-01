# Retained requirement assertions

Core executes configured producers and optionally applies an independent Policy. This module only inspects retained evaluations:

```java
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.requirement.Requirement;
import io.github.markpollack.judge.evaluation.Evaluations;
import io.github.markpollack.judge.assertions.RequirementAssertions;

var actual = Requirement.text("ready", "1", "READY");
// Retained values from an explicitly requirement-bound evaluation.
var original = Judgment.pass("READY observed").forRequirement(actual);
var retained = Verdict.single("ready", original).forRequirement(actual);
var evaluated = Evaluations.of(retained);
RequirementAssertions.requireSatisfied(evaluated);
```

`requireSatisfied(result)` additionally requires an actual Requirement association, PASS, and either no requested policy or RELY. An ordinary rule-only Judge supplies no invented Requirement. `RequirementAssertionError.result()` retains the complete unchanged evaluation, with an original failed-policy exception as its cause.

Policy results are NotRequested, Decided(originalDecision) and Failed(originalThrowable). Every usable conclusion reaches a requested policy; invalid records fail before policy execution. RELY preserves a negative conclusion. Requirements contain no evaluators or policies.

Repeated retained assertions and reports execute nothing. The [AssertJ integration](../agent-judge-assertj/README.md) provides typed construction and cached live terminals. Tests use local recorded fixtures; the separately invoked conference runner requires explicit credentials and is not part of verification.
