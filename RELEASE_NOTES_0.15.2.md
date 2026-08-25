# Agent Judge 0.15.2

Correctness release. **If you have collected scores with 0.15.0 or 0.15.1, read this.**

## A jury could silently score with fewer judges than it listed

In 0.15.0 and 0.15.1 a jury could drop a judge from the vote and still return a verdict. The
verdict did not fail, did not warn, and still named every configured judge — but the aggregate was
computed from the judges that survived. **Any score produced by a parallel jury on 0.15.0 or 0.15.1
may have been computed from fewer judges than it reports.** Treat those scores as suspect and
re-run anything you are relying on.

Two independent defects combined to produce it.

**Prompt templates were resolved on the rendering thread.** `TextSources.classpath(...)` returned a
source that read the resource lazily, through the calling thread's context classloader, at render
time. A parallel `SimpleJury` renders on a `ForkJoinPool.commonPool()` worker, whose context
classloader is the system classloader rather than the application's. A classpath template that
loaded perfectly well from application code could therefore fail to load inside the jury — under
`mvn exec:java`, in a container, or anywhere the application's resources are not visible to the
system classloader.

**A judge that threw took its jury down with it.** `SimpleJury` let the exception escape, so one
failing judge discarded every other judge's result in the same jury, and inside a `CascadedJury`
collapsed the whole tier. Combined with the first defect, whether a judge counted depended on which
thread it landed on.

This was reported twice independently, once from inside the project and once downstream, where two
of six judges turned out never to have run.

## What changed

- `TextSources.classpath(...)` and `TextSources.file(...)` now read their text **eagerly**, on the
  thread that calls the factory, and the returned `TextSource` replays it. Rendering no longer
  depends on any thread's context classloader. Classpath resolution tries this library's own
  classloader first, then the context and system classloaders, covering container and child-first
  arrangements.
- A missing classpath resource or unreadable file now fails at construction, on the caller's
  stack, next to the configuration that named it — instead of inside a judge on a pool thread.
- Template text is frozen at construction, so a file edited mid-run can no longer have two
  judgments in the same run made against different prompts.
- `SimpleJury` converts a judge that throws, or returns no judgment, into an `ERROR` judgment
  naming the judge and the cause, in both parallel and sequential modes. Every configured judge is
  represented in the returned `Verdict`, and the voting strategy's `ErrorPolicy` decides what an
  error means. `Error` is deliberately still not caught.
- The count that actually voted is readable from the `AggregationEvidence` block on the aggregate
  judgment: submitted, eligible, errored, and errors treated as abstain.

## Upgrading

Drop-in for 0.15.1. No API changed and no signature moved.

Two behaviour changes are worth knowing about, both of them the fix:

- A template naming a resource that does not exist now throws from `fromClasspath(...)` /
  `fromFile(...)` rather than from `render(...)`. If you were building templates eagerly and
  rendering them lazily against resources you expected to be absent, you will see the failure
  sooner.
- A throwing judge no longer propagates out of `Jury.vote(...)`. It becomes an `ERROR` judgment.
  Under the default `ErrorPolicy.PROPAGATE` the aggregate is an `ERROR` verdict you can read,
  with the working judges' results attached, rather than an exception.
