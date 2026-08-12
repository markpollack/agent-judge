# Agent Judge 0.14.0

Agent Judge 0.14 replaces the former sealed `Score` hierarchy with a normalized `Judgment` contract built for portable evaluation results.
This is a breaking pre-1.0 release; consumers must recompile and follow the [0.13 to 0.14 migration guide](consumer-handoff-normalized-judgment.md).

## Highlights

- `Judgment` now carries required `status` plus optional normalized `score` and optional `label` as independent facts.
- `PASS`, `FAIL`, `ABSTAIN`, and `ERROR` have explicit semantics. Mixed applicable consensus votes aggregate to `ABSTAIN`; downstream gate or tier policy decides whether disagreement rejects or escalates.
- All voting strategies expose an `ErrorPolicy`, defaulting to `PROPAGATE`, and aggregate results include structured population and error-accounting evidence.
- Judgment metadata is validated and recursively frozen at construction as ordinary JSON-compatible values. Rejected values report their exact metadata path.
- Result timing uses the portable integer metadata key `elapsedMillis`; `Judgment.elapsed()` remains the Java `Duration` view.
- Model usage records optional provider-reported input, output, reasoning, cache-creation, cache-read, and total token quantities. It stores no volatile price estimate.
- `agent-judge-core` no longer depends on Reactor. It remains framework-neutral while declaring its actual Jackson Databind, SLF4J API, and JSpecify dependencies.
- JSpecify makes `score` and `label` visibly nullable in the public result API, with NullAway enforcing the adopted package during compilation.

## Removed API

The complete `io.github.markpollack.judge.score` package and `ReactiveJudge` are removed.
Use outcome-specific `Judgment` factories/builders, `effectiveScore()` where a numeric PASS/FAIL view is intentional, and a runtime-specific async wrapper when needed.

## Samples

The [Agent Judge Tutorial](https://github.com/markpollack/agent-judge-tutorial) contains the canonical executable examples.
Ten credential-free Maven modules cover the normalized API, jury behavior, model-backed judges, and evaluated-side Koog and LangChain4j bridges.

## Dependencies

Use `io.github.markpollack:agent-judge-*:0.14.0` for every Agent Judge module.
Runtime bridge dependencies remain provided where practical, so applications choose their framework runtime versions.

## License

Agent Judge 0.14.0 is licensed under the project-specific Business Source License terms in the repository root `LICENSE` file.
