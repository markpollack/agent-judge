/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertj;
import io.github.markpollack.judge.construction.NonEmptyJudge;
import io.github.markpollack.judge.jury.JuryEvidenceStep;
import io.github.markpollack.judge.jury.JuryRecipe;
import io.github.markpollack.judge.jury.ReadyJury;
import io.github.markpollack.judge.verdict.Verdict;
import io.github.markpollack.judge.voting.ConsensusStrategy;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompileGrammarTest {

	@TempDir
	Path directory;

	static final String PREFIX = """
			import static io.github.markpollack.judge.assertj.Assertions.assertThat;
			import static org.assertj.core.api.Assertions.assertThat;
			import io.github.markpollack.judge.*;
			import io.github.markpollack.judge.jury.*;
import io.github.markpollack.judge.verdict.*;
import io.github.markpollack.judge.voting.*;
			import io.github.markpollack.judge.judgment.*;
			import io.github.markpollack.judge.policy.*;
			import io.github.markpollack.judge.requirement.*;
			import io.github.markpollack.judge.construction.*;
			import io.github.markpollack.judge.execution.*;
			import io.github.markpollack.judge.evaluation.*;
			import io.github.markpollack.judge.provenance.*;
			import io.github.markpollack.judge.ai.*;
			import io.github.markpollack.judge.ai.model.*;
			import io.github.markpollack.judge.ai.prompt.JudgePromptTemplate;
			import io.github.markpollack.judge.ai.requirements.*;
			import java.util.*;
			class Probe {
			  record Evidence(int count, String text) {}
			  Requirement<String> requirement=Requirement.text("r","1","ready");
			  Rfc2119Requirement nativeRequirement=Rfc2119Requirement.of("n","1","MUST","be ready","readiness",null);
			  Requirement<AllOf> parent=new GeneralRequirement<>("p","1","both",new AllOf(List.of(requirement,nativeRequirement)),requirement.source());
			  EvalModel model=request->new EvalModelResponse("satisfied", "fixture", null, Map.of());
			  JudgeRecipe<String,String> judge=ModelBackedJudge.<String>builder().name("ready")
			    .promptTemplate(JudgePromptTemplate.fromString("ready","{{requirement}} {{evidence}}"))
			    .variables(value->Map.of("evidence",value))
			    .judgmentClassifier(response->Judgment.pass("ready")).runtime(model);
			  EvalRuntime<RequirementRequest<Rfc2119Specification,Integer>,Judgment> nativeRuntime=request->new NativeExecution<>(
			    Judgment.pass("native"),new Invocation("native", "test:v1", true, null, 0, Map.of(), List.of()));
			  JudgeRecipe<Rfc2119Specification,Integer> nativeJudge=Rfc2119Judge.builder().runtime(nativeRuntime);
			  JuryRecipe<String,String> jury=actual->new JuryEvidenceStep<>() {
			    public ReadyJury evidence(String evidence){return evidenceSupplier(()->evidence);}
			    public ReadyJury evidenceSupplier(java.util.function.Supplier<? extends String> source){
			      return ()->SimpleJury.builder().judge(judge.requirement(actual).evidenceSupplier(source).build()).build();
			    }
			  };
			  Judge general=NonEmptyJudge.builder().evidence("ready").build();
			  Judge number=()->Judgment.pass("42");
			  Verdict retained=Verdict.single("one",Judgment.pass("retained"));
			  EvaluationResult evaluated=Evaluations.of(retained);
			  void probe() {
			""";

	@Test
	void exactRootReadmeJavaBlocksCompileTogether() throws Exception {
		Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		while (!Files.exists(root.resolve("agent-judge-core")))
			root = root.getParent();
		String readme = Files.readString(root.resolve("README.md"));
		var blocks = java.util.regex.Pattern.compile("(?s)```java\\s*\\n(.*?)```").matcher(readme);
		var imports = new StringBuilder();
		var body = new StringBuilder();
		while (blocks.find())
			for (String line : blocks.group(1).split("\\n")) {
				if (line.startsWith("import "))
					imports.append(line).append('\n');
				else
					body.append(line).append('\n');
			}
		String stub = "io.github.markpollack.judge.ai.model.EvalModel judgeModel = request -> new io.github.markpollack.judge.ai.model.EvalModelResponse(\"satisfied\",\"local\",null,java.util.Map.of());\n";
		var source = directory.resolve("Readme.java");
		Files.writeString(source, imports + "class Readme { void run() {\n" + stub + body + "\n}}\n");
		var diagnostics = new DiagnosticCollector<JavaFileObject>();
		var compiler = ToolProvider.getSystemJavaCompiler();
		try (var files = compiler.getStandardFileManager(diagnostics, null, null)) {
			boolean success = compiler.getTask(null, files, diagnostics, List.of("-proc:none", "-classpath",
					System.getProperty("java.class.path"), "-d", directory.toString()), null,
					files.getJavaFileObjects(source.toFile()))
				.call();
			assertThat(success).as("README Java examples: %s", diagnostics.getDiagnostics()).isTrue();
		}
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = { "agent-judge-assertions", "agent-judge-assertj" })
	void exactModuleReadmeJavaBlocksCompile(String module) throws Exception {
		Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		while (!Files.exists(root.resolve("agent-judge-core")))
			root = root.getParent();
		var blocks = java.util.regex.Pattern.compile("(?s)```java\\s*\\n(.*?)```")
			.matcher(Files.readString(root.resolve(module).resolve("README.md")));
		var imports = new StringBuilder(
				"import io.github.markpollack.judge.*;\n" + "import io.github.markpollack.judge.judgment.*;\n"
						+ "import io.github.markpollack.judge.requirement.*;\n"
						+ "import io.github.markpollack.judge.evaluation.*;\n"
						+ "import io.github.markpollack.judge.assertions.*;\n");
		var body = new StringBuilder();
		int count = 0;
		while (blocks.find()) {
			count++;
			for (String line : blocks.group(1).split("\\n")) {
				if (line.startsWith("import "))
					imports.append(line).append('\n');
				else
					body.append(line).append('\n');
			}
		}
		assertThat(count).isPositive();
		var source = directory.resolve("ModuleReadme.java");
		Files.writeString(source, imports + "class ModuleReadme { void run() {\n" + body + "\n}}\n");
		var diagnostics = new DiagnosticCollector<JavaFileObject>();
		var compiler = ToolProvider.getSystemJavaCompiler();
		try (var files = compiler.getStandardFileManager(diagnostics, null, null)) {
			boolean success = compiler.getTask(null, files, diagnostics, List.of("-proc:none", "-classpath",
					System.getProperty("java.class.path"), "-d", directory.toString()), null,
					files.getJavaFileObjects(source.toFile()))
				.call();
			assertThat(success).as("%s README Java examples: %s", module, diagnostics.getDiagnostics()).isTrue();
		}
	}

	void compile(String body, boolean expected) throws Exception {
		var source = directory.resolve("Probe.java");
		Files.writeString(source, PREFIX + body + "\n}}\n");
		var compiler = ToolProvider.getSystemJavaCompiler();
		var diagnostics = new DiagnosticCollector<JavaFileObject>();
		try (var files = compiler.getStandardFileManager(diagnostics, null, null)) {
			var options = List.of("-proc:none", "-classpath", System.getProperty("java.class.path"), "-d",
					directory.toString());
			boolean success = compiler
				.getTask(null, files, diagnostics, options, null, files.getJavaFileObjects(source.toFile()))
				.call();
			assertThat(success).as("%s%n%s", body, diagnostics.getDiagnostics()).isEqualTo(expected);
		}
	}

	@Test
	void validRequirementAndOrdinaryAssertJCoexist() throws Exception {
		compile("assertThat(requirement).judgedBy(judge).withEvidence(\"READY\").isSatisfied(); assertThat(42).isEqualTo(42);",
				true);
	}

	@Test
	void ordinaryCheckNeedsNoRequirement() throws Exception {
		compile("assertThat(general).isPassed();", true);
	}

	@Test
	void wrongEvidence() throws Exception {
		compile("assertThat(requirement).judgedBy(judge).withEvidence(42).isSatisfied();", false);
	}

	@Test
	void ordinaryJudgeCannotDiscardRequirement() throws Exception {
		compile("assertThat(requirement).judgedBy(general).withEvidence(\"READY\").isSatisfied();", false);
	}

	@Test
	void prematureTerminal() throws Exception {
		compile("assertThat(requirement).judgedBy(judge).isSatisfied();", false);
	}

	@Test
	void readyJuryMayMixIndependentEvidence() throws Exception {
		compile("SimpleJury.builder().judge(general).judge(number).build();", true);
	}

	@Test
	void readyFactoryAcceptsIndependentJudges() throws Exception {
		compile("Juries.fromJudges(new ConsensusStrategy(),general,number);", true);
	}

	@Test
	void omittedTerminalIsLegalJava() throws Exception {
		compile("assertThat(requirement).judgedBy(judge).withEvidence(\"READY\");", true);
	}

	@Test
	void requirementJuryGrammar() throws Exception {
		compile("assertThat(requirement).judgedBy(jury).withEvidence(\"READY\").isSatisfied();", true);
	}

	@Test
	void ordinaryJuryCannotDiscardRequirement() throws Exception {
		compile("assertThat(requirement).judgedBy(Juries.fromJudges(new ConsensusStrategy(),general)).withEvidence(\"READY\").isSatisfied();",
				false);
	}

	@Test
	void policyCannotPrecedeEvidence() throws Exception {
		compile("assertThat(requirement).judgedBy(judge).withPolicy(v->new PolicyDecision(PolicyAction.RELY,\"yes\"));",
				false);
	}

	@Test
	void policyNeedsNoIdentity() throws Exception {
		compile("assertThat(requirement).judgedBy(judge).withEvidence(\"READY\").withPolicy(v->new PolicyDecision(PolicyAction.RELY,\"yes\")).isSatisfied();",
				true);
	}

	@Test
	void specificationMismatch() throws Exception {
		compile("assertThat(nativeRequirement).judgedBy(judge);", false);
	}

	@Test
	void pureRequirementCannotAttachPolicy() throws Exception {
		compile("requirement.under(v->new PolicyDecision(PolicyAction.RELY,\"yes\"));", false);
	}

	@Test
	void excludedCannotCarryFinding() throws Exception {
		compile("Judgment.builder().notApplicable().reasoning(\"outside\").label(\"no_java\").build();", false);
	}

	@Test
	void completeMixedComposition() throws Exception {
		compile("Assignments.<Evidence>forRequirement(parent).jury(requirement,Evidence::text,jury).judge(nativeRequirement,Evidence::count,nativeJudge).validate().evidence(new Evidence(0,\"ready\")).build().vote();",
				true);
	}

	@Test
	void incompleteMappingCompilesButRequiresRuntimeValidation() throws Exception {
		compile("Assignments.<Evidence>forRequirement(parent).judge(nativeRequirement,Evidence::count,nativeJudge).validate();",
				true);
	}

	@Test
	void assignmentSpecificationMismatch() throws Exception {
		compile("Assignments.<String>forRequirement(parent).judge(nativeRequirement,judge);", false);
	}

	@Test
	void assignmentEvidenceMismatch() throws Exception {
		compile("Assignments.<Integer>forRequirement(parent).judge(requirement,judge);", false);
	}

	@Test
	void assignmentOrdinaryJudgeMisuse() throws Exception {
		compile("Assignments.<String>forRequirement(parent).judge(requirement,general);", false);
	}

	@Test
	void assignmentJuryAsJudge() throws Exception {
		compile("Assignments.<String>forRequirement(parent).judge(requirement,jury);", false);
	}

	@Test
	void judgeSelectorOutputMismatch() throws Exception {
		compile("Assignments.<Evidence>forRequirement(parent).judge(requirement,Evidence::count,judge);", false);
	}

	@Test
	void jurySelectorOutputMismatch() throws Exception {
		compile("Assignments.<Evidence>forRequirement(parent).jury(requirement,Evidence::count,jury);", false);
	}

	@Test
	void jurySpecMismatch() throws Exception {
		compile("Assignments.<String>forRequirement(parent).jury(nativeRequirement,jury);", false);
	}

	@Test
	void retainedVerdictCannotExecutePolicy() {
		try {
			compile("assertThat(retained).withPolicy(v->new PolicyDecision(PolicyAction.RELY,\"yes\"));", false);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	void retainedEvaluationCannotClaimNewSatisfaction() throws Exception {
		compile("assertThat(evaluated).isSatisfied();", false);
	}

	@Test
	void retainedEvaluationCannotExecutePolicy() throws Exception {
		compile("assertThat(evaluated).withPolicy(v->new PolicyDecision(PolicyAction.RELY,\"yes\"));", false);
	}

	@Test
	void configuredJudgeHasNoInputOperation() throws Exception {
		compile("general.judge(\"replacement\");", false);
	}

	@Test
	void configuredJuryHasNoInputOperation() throws Exception {
		compile("SimpleJury.builder().judge(general).build().vote(\"replacement\");", false);
	}

	@Test
	void evidenceCanOnlyBeChosenOnce() throws Exception {
		compile("Rfc2119Judge.builder().runtime(model).requirement(nativeRequirement).evidence(\"first\").evidence(\"second\");",
				false);
	}

	@Test
	void investigativeBuildNeedsNoFakeEvidence() throws Exception {
		compile("Rfc2119Judge.builder().runtime(model).requirement(nativeRequirement).build().judge();", true);
	}

	@Test
	void structuredRuntimeRequiresItsEvidence() throws Exception {
		compile("Rfc2119Judge.builder().runtime(nativeRuntime).requirement(nativeRequirement).build();", false);
	}

	@Test
	void wrongStructuredEvidence() throws Exception {
		compile("Rfc2119Judge.builder().runtime(nativeRuntime).requirement(nativeRequirement).evidence(\"wrong\");",
				false);
	}

}
