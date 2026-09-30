# Agent Eval

Evaluate evidence against requirements with typed Java judges, compose them into juries, and assert what their conclusions establish.

Start with an ordinary deterministic assertion:

```java
import static org.assertj.core.api.Assertions.assertThat;

assertThat(calculator.add(2, 2))
    .isEqualTo(4);
```

Do not use AI for a property ordinary deterministic code can establish.

Now change the subject to a response: **“Two pairs make a group of four.”** Arithmetic alone does not establish what that sentence communicates. State the requirement and give its judge the text:

```java
import static io.github.markpollack.judge.assertj.Assertions.assertThat;
import io.github.markpollack.judge.requirement.Requirement;

Requirement<String> TWO_PLUS_TWO_REQUIREMENT = Requirement.text(
    "two-plus-two", "1",
    "The response correctly communicates that 2 + 2 equals 4.");

String response = "Two pairs make a group of four.";

assertThat(TWO_PLUS_TWO_REQUIREMENT)
    .judgedBy(TWO_PLUS_TWO_RESPONSE_JUDGE)
    .withEvidence(response)
    .isSatisfied();
```

`TWO_PLUS_TWO_RESPONSE_JUDGE` is a `Judge<String>` configured to evaluate that requirement. A Judge evaluates evidence and returns a Judgment. The requirement says what must be true; the response is the evidence. Neither needs to carry an application acceptance policy.

With deterministic assertions, what counts as acceptable is usually built into the assertion itself. With AI evaluation, a judgment may carry uncertainty, abstention, or richer probabilistic information, so Agent Eval lets you make “what counts as acceptable” explicit when you need to, without forcing that complexity into the simple case.

```java
import io.github.markpollack.judge.acceptance.AcceptanceAction;
import io.github.markpollack.judge.acceptance.AcceptanceDecision;
import io.github.markpollack.judge.acceptance.AcceptancePolicy;

AcceptancePolicy explainedJudgment = judgment -> judgment.reasoning().isBlank()
    ? new AcceptanceDecision(AcceptanceAction.ABSTAIN, "An explanation is required")
    : new AcceptanceDecision(AcceptanceAction.RELY, "The judgment includes an explanation");

assertThat(TWO_PLUS_TWO_REQUIREMENT)
    .judgedBy(TWO_PLUS_TWO_RESPONSE_JUDGE)
    .withEvidence(response)
    .withAcceptancePolicy(explainedJudgment)
    .isSatisfied();
```

This illustrative policy checks for an explanation; it makes no confidence or calibration claim. `RELY` means rely on the Judgment as rendered, including a negative Judgment. Relying on a negative Judgment still fails `isSatisfied()`. `ABSTAIN` and `ESCALATE` withhold reliance; escalation records a request for the caller to act.

The default policy is `RELY`, with no confidence threshold. No policy identity, revision or digest is required. Applications that need durable policy provenance can attach it separately with `Policies.recorded(...)`.

## Configure a textual Judge

An ordinary Judge can be a lambda. A model-backed Judge adds explicit evidence rendering and response classification:

```java
import java.util.Map;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.ai.JudgmentClassifiers;
import io.github.markpollack.judge.ai.ModelBackedJudge;
import io.github.markpollack.judge.ai.prompt.JudgePromptTemplate;

// judgeModel is a configured JudgeModel backend supplied by your application.
Judge<String> TWO_PLUS_TWO_RESPONSE_JUDGE = ModelBackedJudge.<String>builder()
    .name("two-plus-two-response")
    .promptTemplate(JudgePromptTemplate.fromString("two-plus-two", """
        Requirement: {{requirement}}
        Response: {{response}}
        Does the response satisfy the requirement? Reply satisfied, violated, or unknown.
        """))
    .variables(text -> Map.of("requirement", TWO_PLUS_TWO_REQUIREMENT.text(), "response", text))
    .model(judgeModel)
    .judgmentClassifier(JudgmentClassifiers.passFail("satisfied", "violated"))
    .build();
```

The classifier returns an abstention for an unrecognized answer. Backend completion failure remains an instrument error. This example teaches the API, not the accuracy of a particular model or prompt. The [five executable API examples](agent-judge-assertj/src/test/java/io/github/markpollack/judge/assertj/AssertJApiExperienceTest.java) use a local judging stub and need no credentials.

## Compose a Jury

A Jury combines Judgments and produces a Verdict. The Verdict contains its collective `judgment()`, individual Judgments and composition history.

```java
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.jury.AllMustPassStrategy;
import io.github.markpollack.judge.jury.Jury;
import io.github.markpollack.judge.jury.SimpleJury;

Judge<String> nonemptyResponseJudge = text -> text.isBlank()
    ? Judgment.fail("The response is empty") : Judgment.pass("The response has text");

Jury<String> jury = SimpleJury.<String>builder()
    .judge(nonemptyResponseJudge)
    .judge(TWO_PLUS_TWO_RESPONSE_JUDGE)
    .votingStrategy(new AllMustPassStrategy())
    .build();

assertThat(TWO_PLUS_TWO_REQUIREMENT)
    .judgedBy(jury)
    .withEvidence(response)
    .isSatisfied();
```

Final acceptance runs after the Jury completes. It preserves the Jury's voting rules, internal policies, tier routing and original Verdict.

## Inspect a retained result

```java
import io.github.markpollack.judge.assertions.RequirementAssertions;

var result = RequirementAssertions.relyingOnJudgment()
    .evaluate(TWO_PLUS_TWO_REQUIREMENT, TWO_PLUS_TWO_RESPONSE_JUDGE, response, null);

System.out.println(result.verdict().judgment().reasoning());
System.out.println(result.interpretation().outcome());
RequirementAssertions.requireSatisfied(result);
```

Interpretation describes what the Verdict establishes about the Requirement: `SATISFIED`, `VIOLATED`, `UNRESOLVED`, `NOT_APPLICABLE` or `NOT_ASSESSED`. Reading support separately records whether the retained facts justify that interpretation. `isSatisfied()` asserts over these facts and final acceptance; it is not a stored boolean.

Asserting a retained result invokes no Judge, model or AcceptancePolicy. Calling the fluent `isSatisfied()` terminal again performs another evaluation.

## Structured detail and evidence

A Judgment is the Judge's conclusion. Findings are optional structured determinations supporting it. A deterministic PASS or FAIL needs no Finding. When available, `BooleanFinding`, `NumericFinding` and `CategoryFinding` retain producer detail. `Confidence` is metric-specific scalar support, not a general calibration guarantee. `ProbabilityDistribution` preserves probabilities over the declared domain.

`Provenance` records a Judgment's origins. `VerdictProvenance` records how the Jury's collective Judgment was produced, including ordinary reduction, one-seat identity and adoption from a tier. Internal policy executions remain distinct from final application acceptance.

Evidence is owned by the domain being evaluated:

| Judge family | Evidence |
|---|---|
| File existence/content, build/command, EARS/RFC2119 workspace audit | `Path` |
| Directory/file comparison | `DirectoryComparison` / `FileComparison` |
| Coverage comparison | `CoverageComparison` with a typed baseline |
| Request/response runtime bridges | `CompletionEvidence` |
| CLI AgentClient execution | `AgentExecutionEvidence` with workspace and completion |
| RAG | `RagEvidence(question, retrievedContext, answer)` |
| Jev | selected `JevEvidence` paired with the Requirement |

For example, `new FileExistsJudge("pom.xml").judge(workspace)` evaluates a `Path`; no execution context or metadata keys are needed. A Judge that needs the requirement itself uses `Judge<RequirementEvidence<S,E>>` and the explicit `judgedByRequirement(...)` stage. Ordinary `Judge<E>` implementations use `judgedBy(...)`.

## Build and dependencies

This source line is **0.18.0-SNAPSHOT** and requires Java 21. These APIs deliberately revise the earlier candidate; the examples describe this source line. Build it locally with `./mvnw install`, then use matching versions:

```xml
<dependency>
    <groupId>io.github.markpollack</groupId>
    <artifactId>agent-judge-assertj</artifactId>
    <version>0.18.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
<!-- Add for the model-backed example. -->
<dependency>
    <groupId>io.github.markpollack</groupId>
    <artifactId>agent-judge-ai-core</artifactId>
    <version>0.18.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

| Module | Responsibility |
|---|---|
| `agent-judge-core` | Requirements, typed Judge/Jury, Judgments, acceptance, provenance and composition |
| `agent-judge-ai-core` | Generic model-backed judges, prompt rendering and classification |
| `agent-judge-assertions` / `agent-judge-assertj` | Direct evaluation, retained assertions / staged AssertJ API |
| `agent-judge-file` / `agent-judge-exec` | Filesystem comparisons / build, command, class-version and coverage judges |
| `agent-judge-llm` / `agent-judge-rag` | Spring AI-backed judging / RAG judgments |
| `agent-judge-jev` | Lossless Jev System One adapter |
| `agent-judge-spring-ai` / `agent-judge-langchain4j` / `agent-judge-koog` | Runtime response bridges |
| `agent-judge-agent-client` | CLI execution evidence and AgentClient judging backend |

Core uses Jackson, SLF4J API and JSpecify, with no agent framework or model provider dependency. Concrete filesystem judges live in `agent-judge-file`.

Modern Judgment, Verdict and Interpretation records use **schemaVersion 3**. V2 is intentionally unsupported by the current typed reader. Historical tolerant interpretation remains available for unversioned records. See the [wire contract](portable-results-v3.md), [migration guide](MIGRATION_TYPED_EVIDENCE.md), [AssertJ guide](agent-judge-assertj/README.md) and [Jev guide](agent-judge-jev/README.md).

## License

The current source tree uses the Business Source License 1.1 with the project-specific terms in [LICENSE](LICENSE). Releases through 0.9.1 under the former `org.springaicommunity` coordinates retain their [Apache License 2.0](LICENSE-APACHE.txt) terms. That file records release history and is not a second license for current source.
