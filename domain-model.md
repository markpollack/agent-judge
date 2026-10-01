# Configured domain and execution boundaries

One non-generic Judge/Jury contract executes already configured inputs. Generic recipes describe construction only. Requirements are pure immutable values; native specifications own no duplicate outer identity.

```mermaid
classDiagram
    class Requirement~S~ {
        id
        revision
        text
        specification
        source
    }
    class JudgeRecipe~S,E~ {
        requirement(actual) EvidenceStep
    }
    class EvidenceStep~E~ {
        evidence(value) ReadyJudge
        evidenceSupplier(provider) ReadyJudge
    }
    class Judge {
        judge() Judgment
    }
    class Jury {
        vote() Verdict
    }
    class Judgment {
        original producer facts
        actual optional Requirement
        native invocation ownership or references
    }
    class Verdict {
        original judgments and seats
        complete child attempts
        explicit optional parent or roster
        conclusion() Conclusion
    }
    JudgeRecipe --> Requirement
    JudgeRecipe --> EvidenceStep
    EvidenceStep --> Judge
    Judge --> Judgment
    Jury --> Verdict
    Verdict --> Judgment
```

A mixed ordinary Jury retains independent actual requirements/evidence and no asserted parent. AllOf declares a pure parent and complete constituent roster, with separately configured evaluators. RFC2119/EARS audits declare distinct required items without root opinion seats.

```mermaid
flowchart TB
    Native[Configured native harness] --> Protocol[Typed native execution]
    Protocol --> Answer[Original native answer and Invocation]
    Answer --> Judge[Actual configured Judge]
    Answer --> Roster[Actual roster Jury]
    Roster --> Items[Ordered complete item Verdicts]
    Roster --> Owner[Shared Invocation owner]
    Items --> Refs[Invocation references]
    Judge --> Judgment[Original Judgment with actual Requirement]
    Judgment --> Voting[Ordinary voting or explicit AllOf]
    Voting --> Verdict[Complete retained Verdict]
    Verdict --> Policy[Independent Policy exactly once]
    Verdict --> Report[Read-only assertions and reports]
```

Generated investigative rosters call the native harness once. Per-item structured execution is explicitly separate, acquiring one complete evidence snapshot and invoking once per item. Native observations survive answer-decoding failure. Usage is counted once per invocation identity, including wrapper copies/references. Original exceptions remain in memory; portable observations never serialize Throwable graphs.

Seat-local undeclared exclusion preserves the original NOT_APPLICABLE answer and a separate ERROR treatment. A whole-tier refusal preserves the complete child but supplies no routing evidence. Valid accepted UNDECIDED inputs can still supply genuine FAIL opinions; accepted input is distinct from selected tier and participation in reduction.

One-declared-member Meta identity projects the complete child's conclusion and routing opinions. Multi-member Meta reduces member aggregates. Cascades route with one shared derived predicate, preserving original selected child facts. AllOf/roster audits declare KNOWN_NONE opinions; opaque custom descriptions declare UNKNOWN. Applicability permission derives from the structured description.

Conclusions are PASS, FAIL, INCONCLUSIVE and NOT_APPLICABLE. Policy consumes the whole usable Verdict without rewriting it. Retained assertions/reports execute no producers or policies. Live assertion stages cache results or thrown failures. No universal context map carries typed construction inputs.

Depth 8 and executed composite-attempt 32 bounds remain. The independent native roster bound is 256 items. Storage of the new retained shapes is an explicit version boundary; frozen historical artifacts retain their original semantics.


The public implementations share those two execution operations:

```mermaid
classDiagram
    Judge <|.. NonEmptyJudge
    Judge <|.. DeterministicJudge
    Judge <|.. ModelBackedJudge
    Judge <|.. Rfc2119Judge
    Judge <|.. EarsJudge
    Judge <|.. JevJudge
    Jury <|.. SimpleJury
    Jury <|.. MetaJury
    Jury <|.. CascadedJury
    Jury <|.. Rfc2119Jury
    Jury <|.. EarsJury
    NativeRuntime <|-- JudgeModel
    NativeRuntime --> NativeExecution
    NativeExecution --> Invocation
    Rfc2119Judge --> NativeRuntime
    EarsJudge --> NativeRuntime
    JevJudge --> JevRuntime
    JevRuntime --> NativeRuntime : typed rendering protocol
    Assertions --> JudgeRecipe : actual requirement first
    Assertions --> Jury : ready composition
    Assertions --> EvaluationResult : cached once
```

`construction` owns recipes and evidence stages; `execution` owns typed protocol requests/answers; `provenance` owns native facts/artifact references. `jury` owns seats, routing, complete attempts and invocation ownership validation. `ai.requirements` owns pure native specifications, their actual Judges/Juries and explicit reconstruction. `evaluation`, `policy`, `reporting`, `serialization` and `assertj` keep execution, reliance and retained reading distinct.

Construction snapshots configuration and collection membership. Arbitrary application evidence must itself be immutable or safely owned; the library cannot deep-copy an unknown Java type. Each direct operation is fresh, and a dynamic provider resolves once at its declared acquisition boundary. Native clients, providers and artifact retainers remain caller-owned and must support the caller's concurrency. A fluent assertion stage synchronizes its one completion; separate pre-execution branches represent separate evaluations.
