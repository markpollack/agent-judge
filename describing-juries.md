# Describing a jury before it votes

`Jury.describe()` returns the structure a jury was configured with, before any vote: its
strategy and parameters, its seats, tiers or members, and for each judge its metadata,
implementation and declared configuration. Store it beside the verdicts the jury produces and
compare the two. That comparison is what catches a jury that scored with fewer judges than it
lists.

## Call it

```java
Jury jury = SimpleJury.builder()
    .judge(Judges.named(new BuildJudge(), "build"), 2.0)
    .judge(ctx -> Judgment.pass("smoke"))
    .votingStrategy(new WeightedAverageStrategy(0.7, ErrorPolicy.IGNORE))
    .build();

JuryDescription description = jury.describe();            // before any vote
Map<String, Object> portable = description.toPortable();  // ordered, JSON-compatible
String json = new ObjectMapper().writeValueAsString(portable);
```

For a single judge, call `Judges.describe(judge)`. For a single strategy, call
`strategy.describe()`.

`toPortable()` is validated with the same rules as `Judgment` metadata. It is ordered, and the
same configuration gives the same JSON bytes in every JVM run. The library does not hash it;
hash the JSON the way your store needs.

Every description returned by `toPortable()` starts with `"descriptionVersion": 1`
(`JuryDescription.DESCRIPTION_VERSION`). Hash it along with the rest. Juries nested inside tiers
and members do not repeat it.

The version is an integer that starts at 1. It is incremented whenever the same configured jury
could produce a different `toPortable()` map because the library changed the format:

- a key is added, removed or renamed, at any level;
- a value vocabulary is added to or changed, such as a new `form`, `keySource` or `declared`
  value;
- the way an existing value is derived changes, such as the implementation identity rules or the
  key-source rules.

A release that leaves every map unchanged keeps the same version.

The version does **not** cover what a judge declares. A judge that starts declaring
configuration, or changes the values it declares, produces a different map under the same
version, and that includes a library judge in a later release. That is a change in the
description of the instrument, not a change of format.

For the jury above:

```json
{"descriptionVersion": 1,
 "kind": "SIMPLE",
 "strategy": {"name": "weightedAverage",
              "implementation": {"form": "NAMED", "className": "io.github.markpollack.judge.jury.WeightedAverageStrategy"},
              "parameters": {"declared": true, "values": {"errorPolicy": "ignore", "threshold": 0.7}}},
 "seats": [
   {"position": 0, "verdictKey": "build", "keySource": "DECLARED", "weight": 2.0,
    "judge": {"metadata": {"declared": true, "values": {"name": "build", "type": "DETERMINISTIC"}},
              "delegateMetadata": {"declared": false},
              "implementation": {"form": "NAMED", "className": "com.example.BuildJudge"},
              "configuration": {"declared": false}}},
   {"position": 1, "verdictKey": "Judge#2", "keySource": "POSITIONAL", "weight": 1.0,
    "judge": {"metadata": {"declared": false},
              "delegateMetadata": {"declared": false},
              "implementation": {"form": "HIDDEN"},
              "configuration": {"declared": false}}}]}
```

A seat's `position` is the key of `Verdict.weights()`. Its `verdictKey` is the key of
`Verdict.individualByName()`. The seat is where the two join.

## What the markers mean

| You see | It means |
|---|---|
| `"keySource": "DECLARED"` | The judge declared the name its judgment is stored under. |
| `"keySource": "POSITIONAL"` | The judge declared no name, so its key is `Judge#` + (position + 1). That key joins a verdict to this one configuration. **It is not an identity**: insert a judge above it and the key moves. |
| `"keySource": "DEDUPLICATED"` | `Juries.fromJudges` suffixed a colliding name with `-2`, `-3`, …, so the key depends on judge order. The wrapper labels the judge `DETERMINISTIC`; `delegateMetadata` still carries the type the judge declared. |
| `"form": "HIDDEN"` | The judge is a lambda or another hidden class. That includes every `Judges` combinator. No class name is recorded, because a hidden class's name changes on every run, so two lambdas look the same. Name them. |
| `"form": "ANONYMOUS"` or `"LOCAL"` | Only the enclosing top-level class is recorded. The `Outer$1` binary name moves when an unrelated class is added. |
| `"configuration": {"declared": false}` | The judge does not implement `ConfiguredJudge`, so **it said nothing**. It does not mean the judge has no configuration. |
| `"configuration": {"declared": true, "values": {}}` | The judge implements `ConfiguredJudge` and declared that nothing configurable affects its verdict. |
| `"parameters": {"declared": false}` | A custom strategy that does not override `describe()`. |
| `"kind": "OPAQUE"` | A custom jury that does not override `describe()`. You get its implementation, strategy and flattened judges, but not its seats, keys or weights. |

A description can show that a judge declared a model, or that it declared nothing. It cannot
look inside a model client. `ModelBackedJudge` declares its prompt template name, a SHA-256 of
the template text, its missing-variable policy and its classifier. It declares no model, because
a `JudgeModel` does not say which model it will call. The model a call actually used is in
`JudgeModelResponse.model()`.

## What to count

- **Simple jury:** compare `seats.size()` with the aggregate's `aggregation.inputCount`.
- **Cascade:** ⚠️ **count per tier, and never also count the top-level aggregate.** A cascade's
  top-level `aggregated`, `individual` and `weights` are copied from the tier that stopped it.
  Compare each `tiers[i]` with the `compositeAttempts` entry of the same name, and count that
  tier's verdict once. Counting the top-level verdict as well counts the stopping tier twice. A
  tier with no attempt was never entered, which means the cascade stopped early.
- **Meta-jury:** compare `members` with `compositeAttempts`. `Jury.getJudges()` is empty for a
  meta-jury, so the description is its only roster. A member that failed to execute makes the
  aggregate a bare `ERROR` judgment with no aggregation block. Take the member count from the
  description, not from the evidence.

## Declaring a judge's configuration

```java
public final class RubricJudge implements ConfiguredJudge {

    @Override
    public Map<String, Object> configuration() {
        return Map.of("rubricVersion", "2026-09", "passMark", 0.75, "model", "the-model-id-this-judge-pins");
    }

    @Override
    public Judgment judge(JudgmentContext context) { ... }
}
```

Declare only what the judge knows before it is called. Values must be strings, booleans,
interoperable integers, finite numbers, lists and string-keyed maps, with no `null`. Describing a
judge that declares anything else fails, and the message names the path, for example
`configuration.rubric.levels[1]`. Key order does not matter, because the description sorts
declared keys.

A custom jury that composes other juries should override `describe()`. It can return one of the
structural descriptions, so its members describe themselves.
