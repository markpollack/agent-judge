# Agent Judge staged-SBOM oracle result after floor export

This report records the 2026-08-23 empirical checkpoint after exporting the authorized
consumer floors. The staged version `0.16.0-oracle2` is a synthetic, non-SNAPSHOT test
coordinate built from commit `ae56c5b9034bd19f542f26342a57533bf29b1b0d`. It was not
released, uploaded, tagged, or pushed.

## Result

The required compile/runtime component set in each published library SBOM still does
**not** exactly equal that module's isolated clean-room consumer closure. Eight of ten
modules match. `agent-judge-llm` and `agent-judge-rag` each have the same single version
substitution:

| Direction | Coordinate |
|---|---|
| SBOM-only | `tools.jackson.dataformat:jackson-dataformat-yaml:jar::3.1.6` |
| Consumer-only | `tools.jackson.dataformat:jackson-dataformat-yaml:jar::3.2.1` |

The new direct dependencies did export the intended NetworkNT, classmate, and
commons-logging versions. Both isolated consumers now resolve
`com.networknt:json-schema-validator:3.0.7`, `com.fasterxml:classmate:1.7.3`, and
`commons-logging:commons-logging:1.3.6`.

The remaining mismatch is new information. NetworkNT 3.0.7 directly declares both
`tools.jackson.core:jackson-databind:3.2.1` and
`tools.jackson.dataformat:jackson-dataformat-yaml:3.2.1`. The published Agent Judge child
POM directly declares databind 3.1.6, so Maven's nearest-dependency rule selects databind
3.1.6 while leaving NetworkNT's YAML dependency at 3.2.1. In the reactor, the root Jackson
3 BOM manages YAML to 3.1.6, and CycloneDX records that reactor result. The flattened child
POM does not export the BOM, so its standalone consumer resolves YAML 3.2.1.

Per the frozen work order, no configuration or dependency adjustment was made after this
equality failure.

## Per-module equality

Counts include each module's metadata/root coordinate, matching the isolated consumer's
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

| Inventory | v0.15.0 aggregate baseline | `0.16.0-oracle` | `0.16.0-oracle2` | Baseline delta |
|---|---:|---:|---:|---:|
| Isolated consumer union | 51 | 51 | 53 | +2 |
| SBOM required-component union | 190 | 51 | 53 | -137 |
| SBOM-only union | 141 | 2 | 1 | -140 |
| Consumer-only union | 2 | 2 | 1 | -1 |

The equal cardinalities of the two 53-coordinate unions do not imply equality: YAML 3.1.6
is SBOM-only and YAML 3.2.1 is consumer-only.

## Resolved security-floor coordinates

For both failing modules, the clean-room consumer resolves:

- Jackson 2 annotations 2.21, core 2.21.6, and databind 2.21.6;
- Jackson 3 core 3.1.6 and databind 3.1.6;
- Jackson 3 YAML 3.2.1;
- NetworkNT JSON Schema Validator 3.0.7;
- classmate 1.7.3; and
- commons-logging 1.3.6.

Thus the authorized NetworkNT 3.0.7 floor failure is closed. The result does not establish
the release-truth invariant because the SBOM reports a different Jackson 3 YAML version.

## Objective isolation record

- Maven wrapper: Apache Maven 3.8.6; wrapper script SHA-256
  `5e53a64422a43077b28e7545942c20de550d6d885ed017a910d5f837a4041700`.
- JDK: GraalVM Community OpenJDK 21.0.2+13.1, JVMCI 23.1-b30.
- Source commit: `ae56c5b9034bd19f542f26342a57533bf29b1b0d`.
- Build profile: `release`; build properties included `gpg.skip=true` and
  `skipTests=true`.
- Exact staged GAVs: `io.github.markpollack:<ten published JAR artifactIds>:0.16.0-oracle2`.
- Fresh build repository:
  `target/release-truth-oracle/build-m2`; it did not use `~/.m2`.
- Build settings: `target/release-truth-oracle/settings.xml`, SHA-256
  `0688e145a1c7b230b2499c409ced72054e1b26ec914a214e5e40fc6241e1e106`;
  Maven Central was the only remote dependency source and credentials were inert local
  test values.
- Central bundle: `agent-judge-0.16.0-oracle2-central-bundle.zip`, SHA-256
  `aac087d4038cc4dd011aaf5abc3146378900f4c55c330ee9ac7db43c95f7723d`.
- Bundle creation used Central's real staging and bundling path with its upload base URL
  forced to `http://127.0.0.1:9`. Bundling completed, then the build failed at the expected
  loopback connection refusal. No external upload was possible.
- Consumer profiles: none. Each generated consumer had no parent and no BOM and declared
  only its exact Agent Judge GAV plus the extracted staged repository.
- Consumer settings SHA-256:
  `f32888fa36ec31a8dee33dfc35d8a9243d5a090b4bf271fddab2b71932ee848f`.
- The gate used a fresh temporary Maven local repository that could not reach `~/.m2` and
  verified that each consumer-cached POM was byte-for-byte equal to its staged POM.
- Gate matrix SHA-256:
  `0939c5bd9fde4aca9e446beb01bc743f9a8737447041bc02f8cda339efbb006d`.
- Coordinates were canonicalized from CycloneDX purls as
  `groupId:artifactId:type:classifier:version`; optional SBOM components were excluded from
  required-closure equality.

## Scanner and evidence-manifest checkpoint

The two former SBOM-only coordinates
`org.apache.httpcomponents.core5:httpcore5:5.4.2` and
`org.apache.httpcomponents.core5:httpcore5-h2:5.4.2` are absent from both 53-coordinate
unions. Their former component-triggered findings were `CVE-2026-54399` and
`CVE-2026-54428`.

Frozen-database and current-database scans were not run, so this checkpoint makes no new
empirical vulnerability claim and records no database delta. The per-module success
evidence manifest was not emitted. Both stages depend on a validated, consumer-real SBOM,
and the work order explicitly requires stopping at an equality disagreement rather than
continuing or tuning configuration.

## Work-order corrections carried forward

The earlier corrections remain valid:

1. Inherited `makeBom` produces ten library SBOMs plus a zero-dependency parent SBOM. The
   parent is intentionally documented as an ancillary SBOM outside the library equality
   policy because its parent POM is deployed.
2. Sonatype Central's `skipPublishing=true` suppresses staging/bundling and makes CycloneDX
   deployment detection skip the SBOMs. A loopback upload target is required to exercise
   the final bundler without publication.
3. Module-local `makeBom` can still record a root-BOM-managed version that the flattened
   child POM does not export. The new Jackson 3 YAML substitution is the concrete second
   demonstration of this condition.

The authoritative work order is outside the user-mandated worktree boundary, so these
corrections are recorded here for its owner to fold in; that external file was not edited.
