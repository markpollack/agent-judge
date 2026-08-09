# Agent Judge Agent Instructions

This public repository owns code, tests, Maven builds, releases, shipped contracts, and public
documentation. Private planning and control state are authoritative in
`/home/mark/projects/agent-judge-steward`; read its `BINDING.md` before planning or executing work.

Use `./mvnw`, never `mvn`. The normal gate is `./mvnw clean verify`. Release-health checks are
`./mvnw -o javadoc:aggregate` and
`./mvnw -o -Prelease -Dgpg.skip=true clean package`; Javadoc errors remain build-breaking.

Follow `/home/mark/projects/agento-forge/guides/java-library-quality.md`. The project uses a
customized source license; see `LICENSE`. Commit messages contain no AI attribution.

Do not copy private planning, current-action, candidate, checkpoint, or dirty-tree state into public
files. The ignored `plans/` tree in this checkout is transition material, not public authority.
