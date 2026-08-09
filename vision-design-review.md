# Fresh Vision and Design Review — 2026-08-03

## Verdict

The end goal is coherent: Agent Judge is a portable JVM verification layer whose evaluation policy
survives changes in agent runtime or framework.

The strongest design idea remains the horizontal boundary:

- adapters translate native execution evidence;
- JudgmentContext carries evaluation inputs;
- judges make findings;
- juries aggregate policy;
- verdicts expose the result and evidence.

The older vision and design obscured that clarity because they described a May 2026 product state and
promoted several implementation details as permanent differentiators. The revised root VISION.md and
DESIGN.md retain the durable thesis and remove claims the current repository disproves.

## Findings Incorporated

### 1. Product state was several releases stale

The old documents described 0.9.2 as current and 0.10.0 as a target. The repository has released
0.10.0, 0.11.0, 0.12.0, and 0.13.0 and is developing 0.14.0.

The revised documents describe ten modules, including AI Core, and distinguish released 0.13 from the
in-progress normalized-Judgment migration.

### 2. The Score hierarchy was presented as a distinctive stable feature

The old vision celebrated sealed Score types. The old stability section promised Score as a stable
core abstraction from 0.10 onward. The DDD review and implementation showed that the hierarchy was a
type code, duplicated JudgmentStatus, and manufactured negative votes for abstention and errors.

The revised documents make status, optional normalized score, and optional label the core contract.
They also correct the pre-1.0 compatibility policy: a minor version may make an evidenced domain
correction, but it owes users characterization, a migration guide, and release notes.

### 3. “Zero-dependency core” was false

agent-judge-core currently declares Jackson Databind, SLF4J API, and optional Reactor. The product is
framework-neutral, not dependency-free.

The revised vision uses the defensible claim: core has no dependency on an agent framework, model
provider, dependency-injection container, or hosted evaluation service.

A future dependency-reduction project may still be worthwhile, but it should be measured and designed
rather than treated as existing architecture.

### 4. Competitive commentary displaced the product contract

The old vision spent substantial space comparing Dokimos and Maxim AI. Those comparisons age quickly
and require recurring external verification. They are useful research and positioning material, but
not the load-bearing definition of the product.

The revised vision centers the durable buyer problem, promise, boundary, current state, and outcome
scoreboard. Competitive research remains in plans/learnings and can be refreshed separately.

### 5. The roadmap confused unchecked boxes with unfinished software

The historical `plans/archive/ROADMAP-product-development-through-0.13.md` shows Steps 3.6 and 3.7
as unfinished, but commits 10f115d and d5e438f
delivered the AgentClient and Spring AI bridges. Step 3.8's principal product outputs also shipped:

- README rewrite: 97896b6;
- release 0.10.0: 8f4cdb2;
- later releases through 0.13.0;
- documentation now covers all four adapters, RAG judges, REFUSED, the metadata convention, and the
  Judge-versus-Journal boundary.

What was genuinely not completed from that roadmap was mostly process consolidation:

- step-3.6, step-3.7, and step-3.8 learning files were not created;
- plans/learnings/LEARNINGS.md was not created;
- the Stage 3 consolidation summary was not created;
- plans/inbox was not triaged;
- roadmap checkboxes and status snapshots were not reconciled;
- CLAUDE.md remained on version 0.9.2 and described the deleted Score hierarchy.

Backfilling every missing per-step learning file now would create narrative without contemporaneous
evidence. The better course is to preserve the historical roadmap, record this reconciliation, update
the repository guide, and use the new root roadmap for current work.

### 6. License wording was too absolute

The old files called the project canonical BSL 1.1. The repository uses deliberately customized
license text. The revised documents refer to the customized source license in LICENSE and leave
machine-readable licensing to the separate REUSE/SBOM workstream.

## Historical Roadmap Reconciliation

| Historical item | Actual state | Treatment |
|---|---|---|
| Step 3.6 AgentClient bridge | Delivered in 10f115d | Do not rebuild; document as completed |
| Step 3.7 Spring AI bridge | Delivered in d5e438f | Do not rebuild; document as completed |
| Step 3.8 README | Delivered in 97896b6 and subsequently updated | Revisit only for 0.14 migration |
| Step 3.8 release 0.10 | Delivered in 8f4cdb2 | Historical completion |
| Step 3.8 documentation | Substantially delivered and current through 0.13 | Update for 0.14 API |
| Step 3.K learnings consolidation | Not completed | Superseded by this review and current roadmap |
| Step 3.K inbox triage | Not completed | Optional housekeeping; do not block 0.14 |
| Stage 4 AI Core | Delivered in 1246a08 and released in 0.11 | Completed despite “commits pending” text |
| Future candidate features | Mostly not implemented | Remain candidates, not backlog commitments |

## Remaining Design Risks

### Core dependency posture

The revised design tells the truth about dependencies. A later decision is still possible: keep the
current pragmatic dependencies, split configuration/serialization out of core, or make some
dependencies optional. That is not required to complete 0.14.

### Unused reactive surface

ReactiveJudge is the only reason agent-judge-core declares optional Reactor. It has no implementation,
test, production caller, or sample in this repository. Optional Maven scope prevents transitive
propagation but does not remove the published Mono-shaped API coupling.

Recommendation: remove ReactiveJudge and reactor-core during the 0.14 pre-1.0 break. A real reactive
consumer can wrap Judge directly, or a separate adapter can be introduced when demanded. AsyncJudge
uses only JDK CompletableFuture and can be evaluated separately.

### Metadata portability

Judgment's top-level value is serializable, but arbitrary metadata can still contain non-portable Java
objects. The normalized design correctly limits the guarantee. Future cross-language use may justify
a stricter metadata value algebra.

### Consumer score semantics

Downstream code must distinguish an actually measured score from effectiveScore(), which may project
PASS/FAIL to 1.0/0.0. The consumer handoff must make this explicit, especially for threshold gates.

### Pre-1.0 stability

0.14 deliberately breaks the Score API. The change is justified, but it must be accompanied by a
clear migration document and release notes. The project should avoid another foundational result-model
change without new consumer evidence.

## Recommended Plan

1. Finish the normalized-Judgment full-reactor and coverage gates.
2. Produce and review the consumer migration handoff.
3. Update public documentation and examples for 0.14.
4. Complete the separate license/SBOM workstream.
5. Release 0.14 only when the outcome scoreboard and roadmap stopping conditions pass.

No additional vision workshop is required before continuing. The goal is clear enough to build
against; the immediate risk is verification and migration completeness, not lack of product identity.
