# Agent Judge 0.15.0 published-SBOM investigation

This report records the 2026-08-22 investigation of the released `v0.15.0` tag. It is a
historical baseline for the release-truth gate; it does not describe the dependency graph of an
unreleased development branch.

## Method

The release-profile artifacts from `v0.15.0` were installed into an isolated temporary Maven
repository. The reactor declared ten publishable JAR modules. For each module, the investigation
resolved one fresh Maven consumer with no parent and no imported BOM, then collected its complete
runtime dependency closure. The aggregate CycloneDX 1.6 JSON SBOM produced by the reactor was
canonicalized to Maven coordinates and compared with the union of those consumer closures.

The consumer union was also converted to CycloneDX and scanned at all severities with Trivy 0.70.0
and the locally installed Java vulnerability database dated 2026-08-21. The investigation did not
capture a database digest, so this scan is a measured baseline rather than independently replayable
frozen-database evidence.

## Measurements

| Inventory | Unique coordinates |
|---|---:|
| Isolated consumer runtime union | 51 |
| Aggregate reactor SBOM | 190 |
| Present only in the aggregate SBOM | 141 |
| Present only in the consumer union | 2 |

The two consumer-only coordinates were `com.fasterxml:classmate:1.7.2` and
`commons-logging:commons-logging:1.3.5`. The aggregate SBOM instead recorded versions 1.7.3 and
1.3.6 respectively. Most of the additional SBOM-only inventory came from provided dependency
graphs that a standalone runtime consumer did not inherit.

The released Jackson floor exports worked:

- all ten consumers selected Jackson 2 annotations 2.21 and Jackson 2 core/databind 2.21.6;
- `agent-judge-llm` and `agent-judge-rag` selected Jackson 3 core/databind 3.1.6;
- no consumer selected a second Jackson 2 or Jackson 3 version.

The published-consumer gate failed two modules because both selected
`com.networknt:json-schema-validator:3.0.1`, below the accepted 3.0.7 floor:

```text
agent-judge-llm:0.15.0
  -> spring-ai-client-chat:2.0.0
  -> json-schema-validator:3.0.1

agent-judge-rag:0.15.0
  -> spring-ai-client-chat:2.0.0
  -> json-schema-validator:3.0.1
```

This was a version-floor failure, not a demonstrated vulnerable consumer closure. Agent Judge's
direct Jackson 3 export mediated the validator's older transitive Jackson dependencies to 3.1.6.
The all-severity Trivy scan of the 51-coordinate consumer union reported zero known
vulnerabilities with the tool and database snapshot described above.

## SBOM-only vulnerability findings

Scanning the 190-coordinate aggregate SBOM reported two HIGH findings:

- `CVE-2026-54399` on `org.apache.httpcomponents.core5:httpcore5:5.4.2`;
- `CVE-2026-54428` on `org.apache.httpcomponents.core5:httpcore5-h2:5.4.2`.

Neither HttpComponents coordinate occurred in any of the ten isolated consumer runtime closures.
The vulnerabilities were real for the reported 5.4.2 components, but the components themselves
were SBOM-only. The aggregate SBOM therefore manufactured two HIGH findings relative to what these
standalone consumers resolved.

## Baseline conclusion

The v0.15.0 aggregate SBOM agreed with consumers on the named Jackson and NetworkNT versions but did
not represent any published module's required runtime dependency closure. The release-truth oracle
must compare each staged module POM with that same module's staged SBOM; the aggregate reactor BOM
cannot serve as the consumer-facing dependency inventory.
