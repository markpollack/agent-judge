# Migrating to typed evidence and requirement assertions

The `0.18.0-SNAPSHOT` source line makes `Judge<E>` and `Jury<E>` generic and adds native
requirements plus optional AssertJ integration. This is a coordinated **source and binary
compatibility break**. Recompile implementations, wrappers, integrations and callers together;
do not mix old implementations with the new interfaces.

## Give judges and juries an evidence type

```java
Judge<JudgmentContext> fileJudge = new FileExistsJudge("pom.xml");
SimpleJury<JudgmentContext> jury = SimpleJury.<JudgmentContext>builder()
    .judge(fileJudge)
    .votingStrategy(new AllMustPassStrategy())
    .build();
Verdict verdict = jury.vote(context);
```

Execution-oriented judges retain `JudgmentContext`. An unrelated domain can use
`Judge<String>`, `Judge<MyEvidence>` or another concrete type without a marker interface.
`judge(E)` and `vote(E)` receive that type. Use an explicit builder type witness when Java
cannot infer the intended type through a chained builder call; avoid raw `Judge`/`Jury` types.

Apply the same evidence type to `AsyncJudge`, `JudgeWithMetadata`, `NamedJudge`, configured
wrappers, `SimpleJury`, `MetaJury`, `NamedJury`, `TierConfig` and `CascadedJury`. Factory and
composition methods infer the type from their arguments. A typed Jury cannot combine judges
requiring unrelated evidence types. Metadata, exclusion declarations, failure containment and
the one-seat identity rule still apply.

## Preserve native requirements

`Requirement<S>` is in `io.github.markpollack.judge.requirement`, alongside
`RequirementEvidence<R,E>` and `RequirementSource`. `PolicyBinding` is in
`io.github.markpollack.judge.result`. Update imports from the earlier assertions package.

Replace the earlier plain-text constructor with:

```java
Requirement<String> requirement = Requirement.text("response-ready", "1", "The response is READY");
```

For native requirements, construct the envelope with its ID, semantic revision, display text,
complete native specification and `RequirementSource` artifact/native identity. RFC2119 keywords,
rationale and applicability, and EARS structure remain in that native specification. Supply a
stable snapshot; the envelope does not freeze an arbitrary mutable `S`. Text equality does not
establish identity. There is no shared flattened requirement view.

A requirement-aware judge uses the ordinary Judge interface:

```java
Judge<RequirementEvidence<Requirement<MySpecification>, MyEvidence>> judge;
```

The assertion pairs the exact supplied requirement and evidence. Evaluators own any rendering
they need. For Jev, the direct type is `Judge<RequirementEvidence<String,JevEvidence>>`, where
the String is the exact provider-rendered requirement. `jev.bind(requirement, renderer)` adapts
a native envelope while retaining binding checks. Jev consumes typed pairs only; migrate any
execution-context route to supply the typed requirement/evidence input explicitly.
Do not regenerate an evidence requirement digest to conceal changed semantics.

## Separate internal and final policy

`PolicyJudges.apply(...)` intentionally configures internal policy, which may affect voting
and cascade routing. Assertion policy is resolved EXPLICIT → ASSOCIATED → DEFAULT and runs
after the completed Jury. `.withAcceptancePolicy(...)` never reconfigures supplied Jury seats
or tiers. Heterogeneous internal policies remain intact. The single-Judge assertion follows
the same rule through a one-seat Jury.

The optional `agent-judge-assertj` module supplies the ordinary static
`assertThat(requirement).judgedBy(judge).withEvidence(evidence).isSatisfied()` entry.
Its immutable default is USE_ASSESSMENT without a confidence threshold; application-specific
defaults use `Assertions.using(new RequirementAssertions(binding))`. Core stays free of AssertJ.

`AssertionResult` now holds the original Verdict and authoritative Interpretation plus a
separate `ApplicationDecision`. Retain the final reference, source, action/reason or failure,
or explicit bypass, as well as the full Verdict. Earlier constructors that inferred final
application from the root's policy are replaced: a root's internal policy is not the assertion's
final policy. `policy()` and `policySource()` refer to the separate final decision.

To evaluate final policy against an already completed Verdict, call
`AssertionResult.applyPolicy(requirement, binding, source, verdict)`. It makes no Judge call.
To reconstruct without running policy, pass the previously retained `ApplicationDecision` and
Verdict to `new AssertionResult(requirement, applicationDecision, verdict)`. The constructor
checks coherence and derives the authoritative Interpretation; an overload accepts and validates
the retained Interpretation too. Do not invent missing policy execution facts during migration.

`SemanticAssertions.requireSatisfied(result)` still performs zero evaluation/policy calls.
It now requires supported ACCEPTED plus successful final USE_ASSESSMENT. Final ABSTAIN/ESCALATE
is inconclusive with the original Interpretation retained; final failure is an application
instrument failure. N/A, evaluation failure and unsupported readings bypass final policy.
Use structured errors/accessors rather than parsing diagnostic message text.

The evidence-first `SemanticAssertions` facade remains available with typed
`Judge<JudgmentContext>` routes and the same final policy scope. Its eager `satisfies(...)`
terminal still evaluates each time. Use `evaluate(...)` then `requireSatisfied(...)` to retain
and assert one observation.

Generic evidence and separate assertion context require no change to the Judgment/Verdict V2
wire format. AssertionResult is a runtime value, not a new wire format; persist the surrounding
requirement/application facts explicitly and never serialize a policy function. Unknown or legacy
wire values still use the tolerant stored-map Interpretation reader, without fabricated modern
facts. See [portable results V2](portable-results-v2.md).

The [AssertJ guide](agent-judge-assertj/README.md) and its compiled tutorial-facing tests describe
the new progression. External tutorial and consumer repositories migrate separately against
the matching producer revision; changing this source tree does not migrate those repositories.
