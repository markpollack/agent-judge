# Agent Judge 0.15.0

Agent Judge 0.15.0 is a dependency-alignment and release-reproducibility update. It preserves the
0.14 normalized `Judgment` contract, the existing ten published modules, and the Java 21 baseline.

## Consumer dependency floors

- Standalone consumers continue to receive Jackson 2.21.6 where Jackson 2 applies.
- `agent-judge-llm` and `agent-judge-rag` now export the accepted Jackson 3.1.6 floor through their
  flattened POMs. This prevents a no-parent, no-AgentWorks-BOM consumer from falling back to the
  older Jackson 3 versions selected transitively by Spring AI 2.0.0.
- The provided Agent Client integration baseline advances from 0.25.0 to 0.26.0. Applications still
  select and provide their Agent Client runtime.

## Agent Sandbox alignment

`agent-judge-exec` now consumes `agent-sandbox-core` 0.10.0. That Core release avoids disclosing
environment values from `ExecSpec.toString()` and stops forwarding the entire parent process
environment when caller overrides are present.

Agent Judge's own three execution paths construct `ExecSpec` values with only a command and timeout;
they do not set environment variables, so those disclosure paths were not reachable from Agent Judge
itself. The update protects consumers that use the exported Sandbox API with their own environment-
carrying specs. Agent Judge does not add or provide the Agent Sandbox Docker or E2B modules.

## Reproducible release infrastructure

- Build and snapshot workflows are pinned to the reviewed immutable Build Tools commit
  `35297f1ade5f47c2925d6dab42a7e2d43bd734d0`.
- The Build workflow can be dispatched manually, allowing exact-SHA hosted verification before a
  release.
- Unused non-Central repositories and the ineffective scheduled security workflow were removed.
  Release security evidence uses the validated, immutable Trivy database snapshot and actual
  standalone-consumer runtime JAR closures.

## Compatibility and license

No Agent Judge public API is removed or changed in this release. All ten modules remain licensed
under the project-specific Business Source License terms in the repository root `LICENSE` file.
