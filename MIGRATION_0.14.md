# Migrating to Agent Judge 0.14

Agent Judge 0.14 is a breaking pre-1.0 release. It replaces the former `Score` hierarchy with the
portable normalized `Judgment` model and replaces the unreleased lossy composite projection with a
complete named attempt tree. Recompile every consumer.

For the full normalized-result migration, including status/score semantics, `ErrorPolicy`, usage,
timing, metadata, and judge behavior, read
[consumer-handoff-normalized-judgment.md](consumer-handoff-normalized-judgment.md).

## Composite results

The historical `subVerdicts()` projection is removed, not deprecated or retained for compatibility.
Use `Verdict.compositeAttempts()` for direct children or `CompositePaths.flatten(verdict)` for an
immutable preorder depth-first view of the complete nested result.

```java
for (CompositePathEntry entry : CompositePaths.flatten(verdict)) {
    CompositeAttempt attempt = entry.attempt();
    if (attempt.verdict() != null) {
        processReturnedVerdict(entry.path(), attempt.verdict());
    }
    else {
        processExecutionFailure(entry.path(), attempt.failure().code());
    }
}
```

Each attempt has a stable configured name and exactly one outcome:

- `verdict`: the exact complete child result; or
- `failure`: the code-only value `JURY_EXECUTION_FAILED`, serialized as
  `{ "code": "jury_execution_failed" }`.

There is no failure message, exception class, stack trace, path, or live exception object in the
result. Do not infer a failed judgment from an execution failure: no child `Judgment` is fabricated.

Cascade attempts use relation `cascade_tier` and include their exact policy token:
`REJECT_ON_ANY_FAIL`, `ACCEPT_ON_ALL_PASS`, or `FINAL_TIER`. Meta attempts use relation
`meta_member` and omit policy. Null alternatives are omitted from JSON.

## Names, paths, order, and bounds

Use `Juries.meta(VotingStrategy, NamedJury...)` when configuring a meta-jury. Existing `combine` and
`allOf` calls remain available but are deprecated and generate the stable names `member-1`,
`member-2`, and so on.

Sibling names must be unique and already NFC-normalized. They contain 1–64 Unicode scalar values,
may contain `/` and `~`, and may not contain malformed Unicode, controls, format characters,
line/paragraph separators, or leading/trailing Unicode whitespace. Execution and result order is
configuration order even when different stages return equal values.

`CompositePaths.flatten(...)` omits the root and derives RFC 6901 paths. It escapes `~` as `~0` and
`/` as `~1`; decoding is strict. For nested names `outer`, `a/b`, and `c~d`, the final path is
`/outer/a~1b/c~0d`.

Composite execution accepts depth 8 and 32 total attempts. It refuses depth 9 or attempt 33 before
invoking or allocating the rejected stage. Construction and flattening also reject manually authored
trees beyond those bounds.

## Exception behavior

A cascade records every entered tier. A caught exception in a non-final tier records failure and
continues; a caught exception in the final tier returns an `ERROR` aggregate with the fixed reasoning
`The final cascade tier failed to execute.` Successful cascade roots copy all four root result fields
from the stopping child while retaining the complete attempt list.

A meta-jury executes every named member sequentially and records caught exceptions. If any member
fails, the voting strategy is not invoked; the root is `ERROR` with fixed reasoning
`One or more jury members failed to execute.` Successful members remain in ordered individual and
named evidence, while all successful and failed attempts remain in the attempt list. `Error` and
internal composite-limit refusals propagate.

## Wire fixtures

The fixed `normalized-judgment-0.14.json` resource is retained only as historical normalized-field
accounting evidence and is deliberately rejected as a corrected production `Verdict`. The corrected
`composite-verdict-0.14.json` fixture comes from real nested `MetaJury`/`CascadedJury` execution and
contains successful and failed attempts in the five-component `Verdict` shape.
