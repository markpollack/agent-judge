# Agent Judge staged-SBOM oracle result

This report records the 2026-08-22 empirical checkpoint for the Agent Judge-only
CycloneDX configuration change. The staged version `0.16.0-oracle` is a synthetic,
non-SNAPSHOT test coordinate built from commit
`fdf9406e53fa7d7481a7448864fef75dbf4fdc28`; it was not released, uploaded, tagged, or
pushed.

## Result

The required compile/runtime component set in each published library SBOM does **not**
exactly equal that module's isolated consumer closure. Eight of ten modules match. The
`agent-judge-llm` and `agent-judge-rag` modules each have two version substitutions:

| Direction | Coordinate |
|---|---|
| SBOM-only | `com.fasterxml:classmate:jar::1.7.3` |
| Consumer-only | `com.fasterxml:classmate:jar::1.7.2` |
| SBOM-only | `commons-logging:commons-logging:jar::1.3.6` |
| Consumer-only | `commons-logging:commons-logging:jar::1.3.5` |

The module-local `makeBom` goal still sees the reactor's root
`dependencyManagement`. The published flattened child POM does not export that management,
so Maven selects the older versions for a standalone consumer. Moving from an aggregate to
module SBOMs removes the unrelated reactor inventory, but it does not by itself make the
reactor-resolved versions consumer-real.

The accepted NetworkNT floor also still fails in both modules:

```text
agent-judge-llm -> spring-ai-client-chat:2.0.0
                -> com.networknt:json-schema-validator:3.0.1

agent-judge-rag -> spring-ai-client-chat:2.0.0
                -> com.networknt:json-schema-validator:3.0.1
```

This remains a version-floor failure, not a demonstrated vulnerable closure. No dependency
configuration was changed in this work.

## Per-module equality

Counts include each module's metadata/root component, matching the consumer tree's primary
module coordinate.

| Module | Consumer | Required SBOM | Exact |
|---|---:|---:|:---:|
| `agent-judge-agent-client` | 8 | 8 | yes |
| `agent-judge-ai-core` | 7 | 7 | yes |
| `agent-judge-core` | 6 | 6 | yes |
| `agent-judge-exec` | 9 | 9 | yes |
| `agent-judge-file` | 10 | 10 | yes |
| `agent-judge-koog` | 7 | 7 | yes |
| `agent-judge-langchain4j` | 7 | 7 | yes |
| `agent-judge-llm` | 39 | 39 | **no** |
| `agent-judge-rag` | 40 | 40 | **no** |
| `agent-judge-spring-ai` | 7 | 7 | yes |

The union measurements changed as follows:

| Inventory | v0.15.0 aggregate baseline | Module-SBOM result | Delta |
|---|---:|---:|---:|
| Isolated consumer union | 51 | 51 | 0 |
| SBOM required-component union | 190 | 51 | -139 |
| SBOM-only union | 141 | 2 | -139 |
| Consumer-only union | 2 | 2 | 0 |

## Objective isolation record

- Maven wrapper: Apache Maven 3.8.6; wrapper script SHA-256
  `5e53a64422a43077b28e7545942c20de550d6d885ed017a910d5f837a4041700`.
- JDK: GraalVM Community OpenJDK 21.0.2+13.1, JVMCI 23.1-b30.
- Build profile: `release`; build properties included `gpg.skip=true` and
  `skipTests=true`.
- Exact library GAVs: `io.github.markpollack:<ten published JAR artifactIds>:0.16.0-oracle`.
- Build dependency repository: fresh
  `/tmp/agent-judge-release-truth.sAw3zH/build-m2`.
- Build settings: `/tmp/agent-judge-release-truth.sAw3zH/settings.xml`, SHA-256
  `0688e145a1c7b230b2499c409ced72054e1b26ec914a214e5e40fc6241e1e106`;
  Maven Central was the only remote dependency source and the credentials were inert local
  test values.
- Central bundle: `agent-judge-0.16.0-oracle-central-bundle.zip`, SHA-256
  `7cdb7dd975ee44400ec3d1617215d4e3fa2f93a85ffb7afcdace478f5881b62d`.
- Bundle creation used Central's real staging/bundling path with its upload base URL forced
  to `http://127.0.0.1:9`. Bundling completed; the build then failed at the expected local
  connection refusal. No external upload was possible.
- Consumer profiles: none. Each generated consumer has no parent and no BOM and declares
  only its exact Agent Judge GAV plus the extracted staged repository.
- Consumer settings SHA-256:
  `f32888fa36ec31a8dee33dfc35d8a9243d5a090b4bf271fddab2b71932ee848f`;
  only the repository id `central` is mirrored to the canonical Maven Central URL, leaving
  the staged file repository reachable.
- The gate created a new temporary Maven local repository that could not reach `~/.m2`, and
  verified that the POM cached by every consumer was byte-for-byte equal to its staged POM.
- Gate matrix SHA-256:
  `75504692dcc335d6f9c90afcef0e780ed5d001ad6279236687cf9b766f3cd8fb`.
- Coordinates were canonicalized from CycloneDX purls as
  `groupId:artifactId:type:classifier:version`; optional SBOM components were excluded from
  required-closure equality.

## SBOM production behavior

The requested configuration change was applied without dependency changes:

- CycloneDX Maven plugin 2.9.2 to 2.9.3;
- inherited `makeBom` in place of root-only `makeAggregateBom`;
- provided and system scopes excluded;
- the explicit `skipNotDeployed=false` override removed.

`./mvnw clean verify` passed and produced all ten library SBOMs, with dependency component
counts 7, 6, 5, 8, 9, 6, 6, 38, 39, and 6. It also produced an eleventh, zero-dependency
SBOM for the root parent POM. The staged Central bundle likewise contains all ten library
SBOMs and the parent SBOM.

## Vulnerability-scan checkpoint

The two former SBOM-only coordinates
`org.apache.httpcomponents.core5:httpcore5:5.4.2` and
`org.apache.httpcomponents.core5:httpcore5-h2:5.4.2` are absent from both new 51-coordinate
unions. Therefore the old component-triggered findings `CVE-2026-54399` and
`CVE-2026-54428` cannot arise from these staged module inventories.

The frozen-database and current-database scan stages were not run. The work order says to
stop on any equality disagreement, and the authoritative oracle failed before the secondary
scanner stage. Consequently this checkpoint makes no new empirical vulnerability claim and
records no current-database delta.

## Falsified assumptions

1. Inherited `makeBom` does not limit development or release output to published JAR
   modules. Because the parent POM is deployed, CycloneDX 2.9.3 also gives it an attached,
   zero-dependency SBOM.
2. Sonatype Central's `skipPublishing=true` is not an offline "build the final bundle but do
   not upload" mode. It suppresses staging/bundling, and CycloneDX 2.9.3 treats every module
   as not deployed and skips all SBOMs. The no-upload oracle needed a loopback upload target
   to exercise the final bundler.
3. Per-module `makeBom` plus scope exclusions is insufficient to guarantee consumer truth
   when root dependency management changes transitive versions that the staged child POM
   does not export.

Per the work order, no configuration was adjusted after the equality failure. Optional
fixture qualification, frozen/current vulnerability scans, and later release-health stages
were not attempted.
