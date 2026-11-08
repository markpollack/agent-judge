# java-guardrails decisions

Owner decisions for the kit's gates on this project (java-guardrails main `ab75812`, 0.4.0 unreleased, control
mode). One line per decision; the scripts read the `KEY: value` lines and do not re-ask. Changing a decision is
an edit here.

GATE_ORDER: spotbugs, errorprone, pmd (2026-11-07, the order assess recommended; JaCoCo and ArchUnit already gate)
TEST_SUPPORT_MODULES: gate (2026-11-07; agent-judge-assertions and agent-judge-assertj are published coordinates whose retained-assertion and AssertJ API consumers code against, each with its own tests and coverage report)
PMD_MECHANISM: ratchet (2026-11-07; per module at its measured count, lowered as fixes land, never raised; CPD stays at zero)
THRESHOLD_FIT_CYCLO: accept (2026-11-07; the failing methods are the hotspots cognitive complexity also flags)
THRESHOLD_FIT_LAMBDA: accept (2026-11-07; the two failures are the twin EARS/RFC2119 classifier lambdas)
GUARDRAILS_MODE: control (2026-11-07)
ERRORPRONE_DISABLED: none (2026-11-07, the kit default: every finding fixed in the code)
ARCHUNIT_CYCLES: scope agent-judge-agent-client, agent-judge-ai-core, agent-judge-assertions, agent-judge-assertj, agent-judge-exec, agent-judge-file, agent-judge-jev, agent-judge-koog, agent-judge-langchain4j, agent-judge-llm, agent-judge-rag, agent-judge-spring-ai (2026-11-07, Owner: "No need to pursue this: Breaking those would change public value types for no real gain." The class cycles in agent-judge-core and agent-judge-json-jackson2 are the recursive value model and stay; package-level no-cycles is enforced in all 14 modules)
