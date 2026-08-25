# Agent Judge Agent Instructions

This public repository owns code, tests, Maven builds, releases, shipped contracts, and public
documentation. Private planning and control state are authoritative in
`/home/mark/projects/agent-judge-steward`; read its `BINDING.md` before planning or executing work.

Use `./mvnw`, never `mvn`. The normal gate is `./mvnw clean verify`. Release-health checks are
`./mvnw -o javadoc:aggregate` and
`./mvnw -o -Prelease -Dgpg.skip=true clean package`; Javadoc errors remain build-breaking.
The aggregate CycloneDX goal requires online mode and runs at `verify`; the offline release package
checks the source/Javadoc artifacts but intentionally does not regenerate the SBOM.

Follow `/home/mark/projects/agento-forge/guides/java-library-quality.md`. The project uses a
customized source license; see `LICENSE`. Commit messages contain no AI attribution.

Library code must not resolve resources through the thread context classloader, and must not let a
judge's failure escape the jury that configured it. Juries run judges on pool threads whose context
classloader is not the application's, and an escaping exception discards every other judge in the
jury and collapses the enclosing cascade tier. Resolve external inputs eagerly, on the caller's
thread; convert a judge failure into an ERROR judgment so `ErrorPolicy` governs it.

Do not copy private planning, current-action, candidate, checkpoint, or dirty-tree state into public
files. The ignored `plans/` tree in this checkout is transition material, not public authority.
