# Agent Judge Steward Binding

Agent Judge uses a one-project/one-steward-repository arrangement.

| Responsibility | Authority |
|---|---|
| Code, tests, Maven build, releases, public documentation, shipped contracts | this public `agent-judge` repository |
| VISION, project DESIGN, ROADMAP, journals, learnings, review evidence, private research | private `markpollack/agent-judge-steward` repository |

Local steward checkout: `/home/mark/projects/agent-judge-steward`.

The authoritative active planning trio is:

- `/home/mark/projects/agent-judge-steward/plans/VISION.md`
- `/home/mark/projects/agent-judge-steward/plans/DESIGN.md`
- `/home/mark/projects/agent-judge-steward/plans/ROADMAP.md`

`STEWARD.md` is the sole tracked planning bridge in this public repository. The ignored `plans/` tree
is retained temporarily through the current closure round and is not authority; it will be migrated
and removed at the recorded stage-close cleanup. Historical handoffs and reviews are evidence until
adjudicated; they cannot override the active trio.

## Applicable Engineering Standard

Java development follows
`/home/mark/projects/agento-forge/guides/java-library-quality.md`. JSpecify annotations are declarations
rather than enforcement: any `@NullMarked` adoption must be enforced by a build-breaking nullness
checker and proved with a watched-failure case.
