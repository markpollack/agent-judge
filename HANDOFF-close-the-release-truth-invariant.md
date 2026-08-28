# Handoff — close the release-truth invariant on Agent Judge

> Raised 2026-08-22. Continues work already committed on `release-truth-sbom`.

## Start the session here

```bash
cd /home/mark/worktrees/agent-judge-release-truth
codex --dangerously-bypass-approvals-and-sandbox
```

**Launch from this worktree, not `~/projects/agent-judge`.** That checkout is on
`fix-silent-judge-loss` and belongs to a separate feature stream. Switching its branch has
already stranded a script once today. This worktree is clean, on `release-truth-sbom`, four
commits ahead and unpushed.

The governing work order is
`/home/mark/projects/agent-release-manager/plans/inbox/2026-08-22-release-truth-oracle-for-published-sboms.md`

## Then paste everything in the block below

```text
You are continuing work already committed on this branch. Read the work order at
/home/mark/projects/agent-release-manager/plans/inbox/2026-08-22-release-truth-oracle-for-published-sboms.md
and the empirical report at ./published-sbom-oracle-result-0.16.0-oracle.md before acting.
The design is frozen. Do not redesign it — the checkpoint is an empirical result.

WHERE THIS STANDS

The per-module SBOM change is implemented and the oracle has run once. It worked, mostly:

  Consumer union   51 -> 51
  SBOM union      190 -> 51
  SBOM-only       141 -> 2
  Consumer-only     2 -> 2
  Exact equality        8 of 10 modules

The two httpcore5:5.4.2 coordinates and their two HIGH findings are gone from the
inventories. That was the headline false-positive problem and it is resolved.

WHAT STILL FAILS

agent-judge-llm and agent-judge-rag disagree identically:

  SBOM classmate:1.7.3        vs consumer 1.7.2
  SBOM commons-logging:1.3.6  vs consumer 1.3.5

Their module SBOMs still inherit root dependencyManagement that their published flattened
POMs do not export. This is exactly the gap the work order predicted makeBom would not close,
and it is why the isolated clean-room consumer is permanent infrastructure rather than a
one-off check. The oracle earned its place on its first run — do not weaken it.

Separately, both consumers still resolve com.networknt:json-schema-validator:3.0.1 through
spring-ai-client-chat:2.0.0, below the 3.0.7 floor. That remains a floor failure, not a
demonstrated vulnerable closure, and it should keep that characterisation unless evidence
changes it.

YOUR OWN PROPOSED FIX, WHICH I AM AUTHORIZING

Export the affected floors as versioned direct dependencies in the staged child POMs —
NetworkNT 3.0.7 plus the classmate and commons-logging versions where needed — then rerun the
oracle. You noted correctly that a consumer BOM alone cannot satisfy the no-parent/no-BOM
case. This is the same remedy that took agent-workflow from 33 HIGH to 0 earlier today, so
the pattern is known to work.

TWO ASSUMPTIONS YOU ALREADY FALSIFIED — carry them forward

1. Inherited makeBom produces eleven SBOMs: ten library SBOMs plus a zero-component parent
   SBOM. The policy is "one SBOM per published library module", so the parent SBOM needs an
   explicit decision — suppress it or document why it exists.
2. skipPublishing=true suppresses Central staging and causes CycloneDX's deployment detection
   to skip SBOMs. The evidence bundle needed a loopback upload target.

Fold both into the work order as corrections when you are done, so the pattern that gets
replicated to agent-client, agent-workflow and agent-journal is the corrected one.

WHAT DONE LOOKS LIKE

  - Exact equality on all ten modules, on the full coordinate
    groupId:artifactId:type:classifier:version, against the staged bundle
  - Frozen-database scan of the validated SBOMs, and a current-database scan, with any delta
    recorded
  - The evidence manifest emitted per the work order
  - Before/after coordinate counts recorded

If equality still does not hold, stop and tell me why rather than adjusting configuration
until it does. A disagreement is new information and is worth more than a green result.

CONSTRAINTS

  - This worktree only. Do not enter ~/projects/agent-judge, do not switch its branch, do not
    touch fix-silent-judge-loss.
  - agent-client, agent-workflow and agent-journal are out of scope. The pattern gets
    replicated only after it is proven here.
  - No release, no tag, no publication, no push. Four commits are already local and unpushed;
    keep it that way.

Stop and report.
```

## Why this is the last thing blocking the pattern

Agent Judge is the proving ground. Once all ten modules reach exact equality, the
configuration change and the release gate replicate to agent-client, agent-workflow and
agent-journal without further design work. Until then, replicating would just copy a
half-verified pattern into three more repositories.
