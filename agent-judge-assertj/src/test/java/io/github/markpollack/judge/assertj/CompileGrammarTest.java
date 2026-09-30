/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertj;

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
			import static io.github.markpollack.judge.assertj.Assertions.assertThatEvidence;
			import static org.assertj.core.api.Assertions.assertThat;
			import io.github.markpollack.judge.*;
			import io.github.markpollack.judge.jury.*;
			import io.github.markpollack.judge.judgment.*;
			import io.github.markpollack.judge.policy.*;
			import io.github.markpollack.judge.requirement.*;
			import java.util.List;
			class Probe {
			  record Native(int limit) {}
			  record Evidence(int count, String text) {}
			  Requirement<String> requirement=Requirement.text("r","1","ready");
			  Requirement<Native> nativeRequirement=new Requirement<>("n","1","native",new Native(0),requirement.source());
			  Requirement<AllOf> parent=new Requirement<>("p","1","both",new AllOf(List.of(requirement,nativeRequirement)),requirement.source());
			  RequirementJudge<String,String> judge=(r,e)->Judgment.pass("ready");
			  RequirementJudge<Native,Integer> nativeJudge=(r,e)->Judgment.pass("native");
			  RequirementJury<String,String> jury=RequirementJuries.voting(new ConsensusStrategy(),List.of(judge));
			  Judge<String> general=input->Judgment.pass(input);
			  Judge<Integer> number=input->Judgment.pass(input.toString());
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
		String stub = "io.github.markpollack.judge.ai.model.JudgeModel judgeModel = request -> new io.github.markpollack.judge.ai.model.JudgeModelResponse(\"satisfied\",\"local\",null,java.util.Map.of());\n";
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
		compile("assertThatEvidence(\"READY\").judgedBy(general).isPassed();", true);
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
	void juryCannotMixEvidence() throws Exception {
		compile("SimpleJury.<String>builder().judge(general).judge(number).build();", false);
	}

	@Test
	void factoryCannotEraseEvidence() throws Exception {
		compile("Juries.fromJudges(new ConsensusStrategy(),general,number);", false);
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
		compile("Assignments.<Evidence>forRequirement(parent).jury(requirement,Evidence::text,jury).judge(nativeRequirement,Evidence::count,nativeJudge).validate().vote(new Evidence(0,\"ready\"));",
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

}
