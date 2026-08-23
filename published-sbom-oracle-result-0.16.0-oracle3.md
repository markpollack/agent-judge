# Agent Judge staged-SBOM oracle result after Jackson 3.2.1 alignment

This report records the 2026-08-23 empirical checkpoint after aligning Agent Judge's
Jackson 3 BOM with AgentWorks BOM 1.17 and NetworkNT 3.0.7. The staged version
`0.16.0-oracle3` is a synthetic, non-SNAPSHOT coordinate built from commit
`8b2b12f3e10c3399c9f0b30673eb5e6c5407079a`. It was not released, uploaded, tagged, or
pushed.

## Result

The required compile/runtime component set in each published library SBOM still does
**not** exactly equal that module's isolated clean-room consumer closure. Eight of ten
modules match. `agent-judge-llm` and `agent-judge-rag` each have this substitution:

| Direction | Coordinate |
|---|---|
| SBOM-only for the module | `com.fasterxml.jackson.core:jackson-annotations:jar::2.21` |
| Consumer-only for the module | `com.fasterxml.jackson.core:jackson-annotations:jar::2.22` |

Both consumers also fail the explicitly preserved Jackson 2 annotations-line policy because
they resolve annotations 2.22 rather than 2.21. Jackson 2 core and databind remain at
2.21.6.

Jackson 3.2.1 itself is internally consistent in the consumer: core, databind, and YAML all
resolve at 3.2.1. NetworkNT remains at 3.0.7, classmate at 1.7.3, and commons-logging at
1.3.6.

The cause is Jackson 3's continued use of the Jackson 2 annotations artifact. The Jackson
3.2.1 BOM and `tools.jackson.core:jackson-databind:3.2.1` declare
`com.fasterxml.jackson.core:jackson-annotations:2.22`. In the reactor, the Jackson 2 BOM is
imported first and manages annotations to 2.21, so CycloneDX records 2.21. In the flattened
standalone child POM, the root BOMs are absent and the direct Jackson 3 databind path brings
annotations 2.22 nearer than the Jackson 2 path. Maven therefore selects 2.22.

This falsifies the assumption that aligning only the Jackson 3 BOM to 3.2.1 can also leave
the standalone Spring AI module consumers on the Jackson 2 annotations 2.21 line. Per the
work order, no dependency or configuration adjustment was made after this equality failure.

## Per-module equality

Counts include each module's metadata/root coordinate, matching the clean-room consumer's
primary module coordinate.

| Module | Consumer | Required SBOM | Exact |
|---|---:|---:|:---:|
| `agent-judge-agent-client` | 8 | 8 | yes |
| `agent-judge-ai-core` | 7 | 7 | yes |
| `agent-judge-core` | 6 | 6 | yes |
| `agent-judge-exec` | 9 | 9 | yes |
| `agent-judge-file` | 10 | 10 | yes |
| `agent-judge-koog` | 7 | 7 | yes |
| `agent-judge-langchain4j` | 7 | 7 | yes |
| `agent-judge-llm` | 41 | 41 | **no** |
| `agent-judge-rag` | 42 | 42 | **no** |
| `agent-judge-spring-ai` | 7 | 7 | yes |

The union measurements are:

| Inventory | v0.15.0 aggregate baseline | `0.16.0-oracle2` | `0.16.0-oracle3` | Baseline delta |
|---|---:|---:|---:|---:|
| Isolated consumer union | 51 | 53 | 54 | +3 |
| SBOM required-component union | 190 | 53 | 53 | -137 |
| Global SBOM-only union | 141 | 1 | 0 | -141 |
| Global consumer-only union | 2 | 1 | 1 | -1 |

The global SBOM-only count is zero because annotations 2.21 remains present in both unions
through the other eight modules. Equality is nevertheless false for LLM and RAG individually,
where 2.21 is SBOM-only and 2.22 is consumer-only.

## Objective isolation record

- `./mvnw clean verify` passed before staging.
- Maven wrapper: Apache Maven 3.8.6; wrapper script SHA-256
  `5e53a64422a43077b28e7545942c20de550d6d885ed017a910d5f837a4041700`.
- JDK: GraalVM Community OpenJDK 21.0.2+13.1, JVMCI 23.1-b30.
- Source commit: `8b2b12f3e10c3399c9f0b30673eb5e6c5407079a`.
- Build profile: `release`; build properties included `gpg.skip=true` and
  `skipTests=true`.
- Exact staged GAVs: `io.github.markpollack:<ten published JAR artifactIds>:0.16.0-oracle3`.
- Fresh build repository: `target/release-truth-oracle/build-m2`; it did not use
  `~/.m2`.
- Build settings: `target/release-truth-oracle/settings.xml`, SHA-256
  `195bc35a545fd4677fb75e3b0699fe434f4036115755d1ac91971b06e7e375a8`; Maven Central
  was the only remote dependency source and credentials were inert local test values.
- Central bundle: `agent-judge-0.16.0-oracle3-central-bundle.zip`, SHA-256
  `3cd99bc4b35ec17421fbd0b8312892166d43d354b9f46673f4004c99d329fc72`.
- Bundle creation used Central's real staging/bundling path with its upload base URL forced
  to `http://127.0.0.1:9`. Bundling completed and the build then failed at the expected
  loopback connection refusal. No external upload was possible.
- Consumer profiles: none. Each generated consumer had no parent and no BOM and declared
  only its exact Agent Judge GAV plus the extracted staged repository.
- Consumer settings SHA-256:
  `f32888fa36ec31a8dee33dfc35d8a9243d5a090b4bf271fddab2b71932ee848f`.
- The gate used a fresh temporary Maven local repository that could not reach `~/.m2` and
  verified that each consumer-cached POM was byte-for-byte equal to its staged POM.
- Gate matrix SHA-256:
  `1867a5da37f57145889b9afd6a80834959547f084d19c85d13b13462916d7e99`.
- Coordinates were canonicalized from CycloneDX purls as
  `groupId:artifactId:type:classifier:version`; optional SBOM components were excluded from
  required-closure equality.

## Scanner and evidence-manifest checkpoint

The two former SBOM-only coordinates
`org.apache.httpcomponents.core5:httpcore5:5.4.2` and
`org.apache.httpcomponents.core5:httpcore5-h2:5.4.2` remain absent from both staged unions.
Their former component-triggered findings were `CVE-2026-54399` and `CVE-2026-54428`.

Frozen-database and current-database scans were not run, so this checkpoint makes no new
empirical vulnerability claim and records no database delta. The per-module success
evidence manifest was not emitted. The user and work order explicitly require stopping at
an equality disagreement before those stages.
