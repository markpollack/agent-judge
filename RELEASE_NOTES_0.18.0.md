# Agent Eval 0.18.0

Configure requirements and evidence before running a Judge or Jury, retain the complete
assessment, and let application policy decide whether to rely on it.

- **Configured execution:** typed builders produce ready `Judge.judge()` and `Jury.vote()` operations.
- **Executable requirements:** native RFC2119/EARS requirements, one generated investigation per roster, and typed Jev evaluation.
- **Complete results:** original judgments, child verdicts and native execution facts survive composition; policy decides RELY, ABSTAIN or ESCALATE independently. AssertJ stages cache execution.
- **Durable storage:** explicit Jackson 2 codecs retain Verdict/EvaluationResult data in V6; retained reads and assertions execute no evaluator.

**Breaking changes:** recompile 0.17 consumers and migrate to configured builders and the
new result packages. V6 readers refuse older typed results; preserve historical records.
See [migration details](MIGRATION_0.18.md).

Java 21. Maven coordinates remain `io.github.markpollack:agent-judge-*`.

Try the [PetClinic walkthrough](https://github.com/markpollack/agent-judge-tutorial/tree/main/case-studies/spec-driven-petclinic),
which demonstrates the APIs using archived responses and offline replay.
