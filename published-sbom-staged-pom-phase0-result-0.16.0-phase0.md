# Agent Judge staged-POM SBOM Phase 0 result

This report records the 2026-08-23 falsification test for generating a module SBOM from
the exact staged published POM, outside the source reactor. The test used the previously
divergent `agent-judge-llm` module. The synthetic `0.16.0-phase0` coordinate was built from
commit `1ed053aa8612680f292ec6501a2d193aad4338da`; it was not released, uploaded, or pushed.

## Result

The test passes exactly. The clean-room consumer closure and the staged-POM `makeBom`
required-component inventory each contain 39 full
`groupId:artifactId:type:classifier:version` coordinates. The set differences are empty:

| Inventory | Coordinates |
|---|---:|
| Isolated consumer | 39 |
| Staged-POM required SBOM inventory | 39 |
| Consumer-only | 0 |
| SBOM-only | 0 |

This result was obtained after removing the certification-driven NetworkNT, classmate,
commons-logging, and Jackson YAML exports. The consumer and generated SBOM both resolve
NetworkNT JSON Schema Validator 3.0.1, classmate 1.7.2, commons-logging 1.3.5, and Jackson
3 core and databind 3.1.6. No dependency declaration was added or changed to make this
Phase 0 comparison pass.

The exact common coordinate set is:

```text
com.ethlo.time:itu:jar::1.14.0
com.fasterxml.jackson.core:jackson-annotations:jar::2.21
com.fasterxml.jackson.core:jackson-core:jar::2.21.6
com.fasterxml.jackson.core:jackson-databind:jar::2.21.6
com.fasterxml:classmate:jar::1.7.2
com.github.victools:jsonschema-generator:jar::5.0.0
com.github.victools:jsonschema-module-jackson:jar::5.0.0
com.github.victools:jsonschema-module-swagger-2:jar::5.0.0
com.knuddels:jtokkit:jar::1.1.0
com.networknt:json-schema-validator:jar::3.0.1
commons-logging:commons-logging:jar::1.3.5
io.github.markpollack:agent-judge-ai-core:jar::0.16.0-phase0
io.github.markpollack:agent-judge-core:jar::0.16.0-phase0
io.github.markpollack:agent-judge-llm:jar::0.16.0-phase0
io.micrometer:context-propagation:jar::1.2.1
io.micrometer:micrometer-commons:jar::1.17.0
io.micrometer:micrometer-core:jar::1.17.0
io.micrometer:micrometer-observation:jar::1.17.0
io.projectreactor:reactor-core:jar::3.8.6
io.swagger.core.v3:swagger-annotations-jakarta:jar::2.2.38
org.antlr:ST4:jar::4.3.4
org.antlr:antlr-runtime:jar::3.5.3
org.antlr:antlr4-runtime:jar::4.13.1
org.hdrhistogram:HdrHistogram:jar::2.2.2
org.jspecify:jspecify:jar::1.0.0
org.reactivestreams:reactive-streams:jar::1.0.4
org.slf4j:slf4j-api:jar::2.0.9
org.springframework.ai:spring-ai-client-chat:jar::2.0.0
org.springframework.ai:spring-ai-commons:jar::2.0.0
org.springframework.ai:spring-ai-model:jar::2.0.0
org.springframework.ai:spring-ai-template-st:jar::2.0.0
org.springframework:spring-aop:jar::7.0.8
org.springframework:spring-beans:jar::7.0.8
org.springframework:spring-context:jar::7.0.8
org.springframework:spring-core:jar::7.0.8
org.springframework:spring-expression:jar::7.0.8
org.springframework:spring-messaging:jar::7.0.8
tools.jackson.core:jackson-core:jar::3.1.6
tools.jackson.core:jackson-databind:jar::3.1.6
```

## Published-POM context

The exact staged POM is parentless and contains no `dependencyManagement`. Its four direct
compile dependencies are:

- `io.github.markpollack:agent-judge-core:0.16.0-phase0`;
- `io.github.markpollack:agent-judge-ai-core:0.16.0-phase0`;
- `org.springframework.ai:spring-ai-client-chat:2.0.0`; and
- `tools.jackson.core:jackson-databind:3.1.6`.

The captured effective POM also contains no `dependencyManagement` and preserves those same
four dependency declarations and versions. It is retained as
`published-sbom-staged-pom-phase0-effective-pom-0.16.0-phase0.xml`, SHA-256
`3bc5b6949b67391f506d716d0386fd1123d6666130566a8a4c56185c166d9a88`.

## SBOM identity and graph

CycloneDX generated schema version 1.6 with generator
`org.cyclonedx:cyclonedx-maven-plugin:2.9.3`. The metadata component is exactly:

- group: `io.github.markpollack`;
- name: `agent-judge-llm`;
- version: `0.16.0-phase0`;
- purl and bom-ref:
  `pkg:maven/io.github.markpollack/agent-judge-llm@0.16.0-phase0?type=jar`.

The dependency graph contains one entry for that root and roots the graph in the same four
direct dependencies as the staged POM. It has 39 component references and 39 dependency
entries, with no dangling component or dependency reference.

CycloneDX warned that it was unable to create a Maven project for the staged
`agent-judge-core` artifact. The goal nevertheless completed successfully, included that
component and its selected transitive closure, and produced exact inventory equality. The
warning is retained because it may matter when the approach is exercised across the full
portfolio; it did not falsify the Phase 0 inventory or identity assertions.

## Objective isolation

The Central bundle was created through the release profile from a clean `git archive` of
the source commit after changing only the synthetic version. Upload was directed to
`http://127.0.0.1:9`; bundle construction completed, and the expected connection refusal
prevented publication.

- Central bundle SHA-256:
  `9c4f30fc562ca012235452ec290a728b836e2844fd2e900aa67a546145d1ea0f`.
- Staged POM SHA-256:
  `e8beb4822a4eac639896a735e505f06c705593eab52eeac28e5620cba447387a`.
- Staged JAR SHA-256:
  `cee7810ed0ebac3a711530053a145b74f96b8ca44b30070befc55a202ecce313`.
- Consumer tree SHA-256:
  `0b3752081568dbca5cf00a9582b6e9bb6e9f89cac818eadbc729f407d503b474`.
- Generated SBOM SHA-256:
  `57f0af86927caea65ea92a84950ba3f0f793e205f0ee1d38c7d0e2ff9366333a`.
- Maven wrapper: Apache Maven 3.8.6; wrapper script SHA-256
  `5e53a64422a43077b28e7545942c20de550d6d885ed017a910d5f837a4041700`.
- JDK: GraalVM CE OpenJDK 21.0.2+13.1, JVMCI 23.1-b30.
- CycloneDX Maven plugin: 2.9.3, explicitly pinned on the command line.
- Clean-room settings SHA-256:
  `3fb5fdc71825af4126a16066bd21ccdeb581fd01e531ec8a093b725a82b82e70`.
- Maven global settings SHA-256:
  `b218a76f8a27fb17950c885f6d868f38e3afdb6df0f95b6f95d1b9582cb27536`.
- Consumer POM SHA-256:
  `40ff4553903faf4100178119e81df802c87b4bfbe43e47324c6957cc7d7d9eb9`.
- Fresh local repository:
  `target/staged-pom-phase0/phase0-m2`; every Phase 0 invocation used it explicitly and
  could not resolve artifacts from `~/.m2/repository`.
- Active profile: `phase0-repositories`, containing only the extracted staged repository
  and canonical Maven Central. The consumer POM had no parent, BOM, repositories, or
  profiles.

The consumer resolution, effective-POM capture, and SBOM generation all used this identical
prefix and the same working directory:

```text
JAVA_HOME=/home/mark/.sdkman/candidates/java/current
/home/mark/worktrees/agent-judge-release-truth/mvnw -B -ntp
-s /home/mark/worktrees/agent-judge-release-truth/target/staged-pom-phase0/phase0-settings.xml
-Dmaven.repo.local=/home/mark/worktrees/agent-judge-release-truth/target/staged-pom-phase0/phase0-m2
-Pphase0-repositories -Dscope=runtime
-DincludeCompileScope=true -DincludeRuntimeScope=true -DincludeTestScope=false
-DincludeProvidedScope=false -DincludeSystemScope=false
```

The three pinned goals were
`org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree`,
`org.apache.maven.plugins:maven-help-plugin:3.5.1:effective-pom`, and
`org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeBom`. The consumer goal used runtime scope
and JSON output. The CycloneDX goal used JSON output; compile and runtime scopes were true,
while test, provided, and system scopes were false.

## Preservation and verification

Before rollback, `git status --porcelain` showed no tracked working-tree changes. The exact
committed state that had produced the prior 10/10 result was
`cd54124a60e061fae2df403cd14757d45ce992cf`; local annotated tag `agen-20260823` preserves
it with the message `Old invariant passes only through certification-driven dependency
exports`. The tag was not pushed.

Commit `1ed053aa8612680f292ec6501a2d193aad4338da` removes only the certification-driven POM
exports while retaining the consumer-gate improvements. `./mvnw clean verify` passed after
that rollback. The only pre-existing untracked worktree file was left untouched.
