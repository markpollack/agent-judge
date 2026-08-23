# Agent Judge staged-SBOM release-truth result

This report records the successful 2026-08-23 checkpoint after restoring Jackson 3.1.6 and
exporting Jackson YAML directly from the two Spring AI-backed modules. The staged version
`0.16.0-oracle4` is a synthetic, non-SNAPSHOT coordinate built from commit
`9f05ff945c4a26a27b29232f683c45d0336b4b58`. It was not released, uploaded, tagged, or
pushed.

## Result

The required compile/runtime component set of every published library SBOM exactly equals
that module's isolated clean-room consumer closure on the full
`groupId:artifactId:type:classifier:version` coordinate.

| Module | Consumer | Required SBOM | Exact |
|---|---:|---:|:---:|
| `agent-judge-agent-client` | 8 | 8 | yes |
| `agent-judge-ai-core` | 7 | 7 | yes |
| `agent-judge-core` | 6 | 6 | yes |
| `agent-judge-exec` | 9 | 9 | yes |
| `agent-judge-file` | 10 | 10 | yes |
| `agent-judge-koog` | 7 | 7 | yes |
| `agent-judge-langchain4j` | 7 | 7 | yes |
| `agent-judge-llm` | 41 | 41 | yes |
| `agent-judge-rag` | 42 | 42 | yes |
| `agent-judge-spring-ai` | 7 | 7 | yes |

The union measurements are:

| Inventory | v0.15.0 aggregate baseline | Final staged result | Delta |
|---|---:|---:|---:|
| Isolated consumer union | 51 | 53 | +2 |
| SBOM required-component union | 190 | 53 | -137 |
| SBOM-only union | 141 | 0 | -141 |
| Consumer-only union | 2 | 0 | -2 |

The additional consumer coordinates relative to the original baseline are part of the
NetworkNT 3.0.7 closure. No new coordinate appeared in this round: the prior 53-coordinate
consumer and SBOM unions became equal by exporting the already reactor-selected YAML 3.1.6
coordinate.

For both `agent-judge-llm` and `agent-judge-rag`, the validated consumer closure resolves:

- Jackson 2 annotations 2.21, core 2.21.6, and databind 2.21.6;
- Jackson 3 core, databind, and YAML 3.1.6;
- NetworkNT JSON Schema Validator 3.0.7;
- classmate 1.7.3; and
- commons-logging 1.3.6.

## Staged artifact gates

`./mvnw clean verify` and `./mvnw -o javadoc:aggregate` passed. The release-profile build then created the
Central bundle through the real Sonatype staging/bundling path. Its upload URL was forced to
`http://127.0.0.1:9`; bundling completed and the only build failure was the expected local
connection refusal. No external upload was possible.

All ten staged library artifacts passed:

- exact consumer/SBOM coordinate equality;
- Java 21 class-major validation;
- thin-JAR inspection: no nested JARs, shade markers, or class files outside the project's
  package namespace;
- CycloneDX 1.6 structural and metadata validation;
- exact metadata component GAV and purl;
- CycloneDX Maven plugin 2.9.3 generator identity and version;
- serial-number presence;
- Maven purl presence and well-formedness;
- canonical non-duplicate component identities; and
- complete, internally valid dependency references, including root relationships.

CycloneDX also performed its schema validation while generating each staged SBOM. The
previously documented zero-dependency parent SBOM remains an ancillary attachment for the
deployed parent POM and is outside the ten-library equality policy.

## Vulnerability scans

Trivy 0.70.0 scanned every validated module SBOM at all severities.

The hash-verified frozen snapshot used:

- vulnerability DB SHA-256
  `2755fd01b5ea63660d993ceb6af50f7045c75cc5d78bb4401599931c3bffc1f3`, updated
  `2026-08-21T13:05:54.74660547Z`;
- Java DB SHA-256
  `f499c3d5fcae196a61828c723167f5df45a553600d7dd5357970aa0418bdc43c`, updated
  `2026-08-17T01:05:44.801663581Z`.

It reported zero UNKNOWN, LOW, MEDIUM, HIGH, or CRITICAL findings for every module. In
particular, the former SBOM-only `httpcore5:5.4.2` findings `CVE-2026-54399` and
`CVE-2026-54428` are gone under the frozen database.

The current scan used a new empty cache and downloaded vulnerability DB SHA-256
`4a4a799e564839e1bff7baebba1cdad79aee5d7e6fb00d95c2200676ccf6d3c8`, updated
`2026-08-23T12:55:49.676159552Z`. Trivy's CycloneDX SBOM path did not download a separate
current Java DB because the package identities were already present in the input SBOMs.
The current database also reported zero findings at every severity for every module. The
frozen-to-current finding delta is zero.

These are tool-and-database-bounded results, not a claim that the libraries have no possible
vulnerabilities.

## Objective isolation and evidence

- Maven wrapper: Apache Maven 3.8.6; wrapper script SHA-256
  `5e53a64422a43077b28e7545942c20de550d6d885ed017a910d5f837a4041700`.
- JDK: GraalVM Community OpenJDK 21.0.2+13.1, JVMCI 23.1-b30.
- Build profile: `release`; properties included `gpg.skip=true` and `skipTests=true`.
- Build settings SHA-256:
  `195bc35a545fd4677fb75e3b0699fe434f4036115755d1ac91971b06e7e375a8`; Maven Central
  was the only remote dependency source and credentials were inert local test values.
- Fresh build repository: worktree-local `target/release-truth-oracle/build-m2`; it could
  not reach `~/.m2`.
- Exact staged GAVs: `io.github.markpollack:<ten published JAR artifactIds>:0.16.0-oracle4`.
- Central bundle SHA-256:
  `3c01026200e3b7775ec114dc41d7be46f27554ac7178769298af0cf6f0f777c0`.
- Consumer profiles: none; each generated consumer had no parent and no BOM.
- Consumer settings SHA-256:
  `f32888fa36ec31a8dee33dfc35d8a9243d5a090b4bf271fddab2b71932ee848f`.
- Gate matrix SHA-256:
  `3f6a35df75b3c5d63ad60eec0bc041d39719f1ceb63c62435729facd6c30f0c7`.
- Evidence manifest SHA-256:
  `b2ab222747a61987985313396d226dada606a27384f15637721f2128b6fb053f`.

The evidence manifest binds, per module, the exact GAV, JAR/POM/SBOM hashes, canonical
consumer-closure hash, scan-result hashes and counts, toolchain/database identity, and gate
assertions.

The manifest is explicitly marked `UNSIGNED`. This was a synthetic local coordinate, and
the task prohibited release, publication, and push; the normal CI/repository provenance
signing mechanism was therefore unavailable. No local signature was substituted for that
release provenance.
