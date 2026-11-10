# Agent Eval 0.19.0 (unreleased)

> **On main, not yet released.** Everything here is in `0.19.0-SNAPSHOT` and none of it is in
> 0.18.0. Every push to `main` publishes the snapshot to
> `https://central.sonatype.com/repository/maven-snapshots/`; name the `main` commit you built
> against as provenance, because the snapshot's content changes as `main` moves. These notes
> are updated as changes land and become the release notes when 0.19.0 is cut.

Maven coordinates and Java packages still use `agent-judge` and `io.github.markpollack.judge`.

## Requirement documents are read whole, strictly and as CommonMark

The format is documented, with checked examples, in
[Requirement documents: syntax, strict loading and the linter](REQUIREMENT_DOCUMENTS.md).

- **Whole paragraphs.** An EARS criterion's requirement is its whole first paragraph, and an
  RFC 2119 statement continues through its paragraph. Wrapped lines are joined with single
  spaces; a hard break joins the same way, without its backslash. In 0.18.0 an EARS criterion
  was read from its first line only, and an RFC 2119 rule's specification was built from its
  title, which stops at the first clause break. **So a judge could be asked about part of a
  requirement.** EARS sentences written on one line read exactly as before.
- **RFC 2119 lines inside lists and block quotes.** Keyword, `**Reason:**` and `Applies when:`
  lines are recognised inside list items and block quotes; their markers are not part of the text.
- **CommonMark.** Fenced and indented code, HTML blocks and thematic breaks are never read, so an
  example inside a fence of any length is documentation, not a requirement. 0.18.0 could load a
  fenced example as a rule. `agent-judge-ai-core` gains the runtime dependency
  `org.commonmark:commonmark` 0.30.0.
- **Strict loading.** `EarsRequirement.fromStrict(path, revision)` and
  `Rfc2119Requirement.fromStrict(path, revision)` refuse a document with any problem, throwing a
  `RequirementDocumentException` that names each problem with its identifier and line: malformed
  headings, missing statements, keywords or reasons, duplicate identifiers, invalid
  `Applies when:` entries, and a criterion heading followed only by metadata.
- **Completeness.** Strict loading also refuses a visibly incomplete sentence: an EARS sentence
  without `shall`, or a sentence or statement that does not end in `.`, `!` or `?`.
- **Permissive loading is unchanged in kind.** `from(path, revision)` still leaves a malformed
  entry out and keeps an incomplete sentence, and logs each problem at WARN.
- **`Applies when:`** declares a requirement's optional applicability inside its entry.

## A linter for specification documents

`RequirementDocuments.lint(directory)` reads every Markdown file below a directory with both
strict readers and returns a `Report`: the documents read, the criteria and constraints loaded,
and every problem as `file:line ID: reason`. `RequirementDocuments.assertValid(directory)`
throws an `AssertionError` listing every problem, so one test keeps a specification valid.
`RequirementDocuments.main(dirs...)` prints the report and exits with status 1 on any problem.

## Generated audits refuse an oversized prompt before calling

Every generated EARS and RFC 2119 path (`EarsJury`, `Rfc2119Jury`, `EarsJudge`,
`Rfc2119Judge`) measures the whole request before calling the model. Above
`PromptLimits.DEFAULT_MAX_PROMPT_CHARACTERS` (100,000 characters) it makes no call and returns
`ERROR` with reason code `judge_failed`, never `FAIL`. The reasoning states the size, the limit,
and how much is requirement headers, the workspace slot, and template and criteria. Set another
limit with `.maxPromptCharacters(n)` right after `.runtime(...)`; it must be positive.

**Behaviour change.** A caller that pastes source or other large evidence into the workspace
slot now gets `ERROR` instead of a call. The workspace slot is for a path the judge reads with
its tools.

A roster refused this way is an ordinary result: it concludes `INCONCLUSIVE`, retains no
invocation, and stores and reads back exactly through the requirement codecs.

## Wire change: `generated-answer:v3`

A generated-answer invocation's `nativeFacts` now hold `messageDigests` (role, UTF-8 length and
SHA-256 of each exact request message) instead of the messages themselves. The protocol is
`generated-answer:v3` and the `messages` key is gone, so a reader of the old shape fails
visibly instead of finding no content. A verdict over large prepared evidence no longer carries
a second copy of it, which could exceed the codec's 1 MiB bound. A caller that must keep the
exact request bytes stores them itself and returns an `ArtifactRef` with the same digest.

## Agent Client integration

- `AgentClientEvalModel(Invocation, NativeCapture)` accepts a configured execution lifecycle:
  the consumer runs Agent Client itself, attaches correlation and waits for recording.
  Invocation facts and artifacts survive an invocation failure, as does any original response.
  The existing constructors keep their behaviour.
- `agent-judge-agent-client` builds against Agent Client `0.32.1-SNAPSHOT` and
  `agent-sandbox-core` `0.11.0-SNAPSHOT`.

## Breaking API changes

- **`EvalModel` no longer extends `EvalRuntime`.** It keeps the same `NativeExecution` shape as
  the typed `EvalRuntime<Q,A>` port, but is not one. Code that passed an `EvalModel` where an
  `EvalRuntime` is expected must adapt it.
- **`BuildSuccessJudge` extends `DeterministicJudge`, not `CommandJudge`.** It composes a
  command judge instead of inheriting from one, so `BuildSuccessJudge.builder(String, int, …)`
  and the inherited `CommandJudge` members are gone.
- **Binary-incompatible, source-compatible:** `EarsJudge`/`Rfc2119Judge`
  `GeneratedBuilder.requirement(...)` and `GeneratedRequirement.build()` now return through
  shared generic bases, so code compiled against 0.18.0 must be recompiled.

## Behaviour changes in `agent-judge-jev`

An `Error` or a cancellation in the Jev HTTP exchange now reaches the caller, instead of
becoming a detail-free `IOException` or leaving the future incomplete.

## Build

`main` sources are now also gated by SpotBugs, Error Prone (with NullAway), PMD and CPD, and
ArchUnit dependency-cycle rules in every module, beside the existing JaCoCo coverage floors. The fixes these gates
required are internal; none changes a public result.
