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
			        import static org.assertj.core.api.Assertions.assertThat;
			        import io.github.markpollack.judge.*;
			        import io.github.markpollack.judge.jury.*;
			        import io.github.markpollack.judge.judgment.*;
			import io.github.markpollack.judge.acceptance.AcceptanceAction;
			import io.github.markpollack.judge.acceptance.AcceptanceDecision;
			        import io.github.markpollack.judge.requirement.*;
			        class Probe {
			            Requirement<String> requirement = Requirement.text("r", "1", "ready");
			            Judge<RequirementEvidence<String, String>> judge = input -> Judgment.pass("ready");
			            Judge<String> general = input -> Judgment.pass(input);
			            Judge<Integer> number = input -> Judgment.pass(input.toString());
			            void probe() {
			        """;

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
	void validEvidenceAndOrdinaryAssertJCoexist() throws Exception {
		compile("assertThat(requirement).judgedByRequirement(judge).withEvidence(\"READY\").isSatisfied(); assertThat(42).isEqualTo(42);",
				true);
	}

	@Test
	void wrongEvidenceIsRejected() throws Exception {
		compile("assertThat(requirement).judgedByRequirement(judge).withEvidence(42).isSatisfied();", false);
	}

	@Test
	void ordinaryJudgeNeedsNoRequirementPlumbing() throws Exception {
		compile("assertThat(requirement).judgedBy(general).withEvidence(\"READY\").isSatisfied();", true);
	}

	@Test
	void prematureTerminalIsRejected() throws Exception {
		compile("assertThat(requirement).judgedByRequirement(judge).isSatisfied();", false);
	}

	@Test
	void juryCannotMixEvidenceTypes() throws Exception {
		compile("SimpleJury.<String>builder().judge(general).judge(number).build();", false);
	}

	@Test
	void juryFactoryCannotEraseEvidenceTypes() throws Exception {
		compile("Juries.fromJudges(new ConsensusStrategy(), general, number);", false);
	}

	@Test
	void forgottenTerminalIsLegalJava() throws Exception {
		compile("assertThat(requirement).judgedByRequirement(judge).withEvidence(\"READY\");", true);
	}

	@Test
	void typedJuryUsesSameFluentGrammar() throws Exception {
		compile("var jury = SimpleJury.<RequirementEvidence<String, String>>builder().judge(judge).build(); assertThat(requirement).judgedByRequirement(jury).withEvidence(\"READY\").isSatisfied();",
				true);
	}

	@Test
	void ordinaryWrongEvidenceIsRejected() throws Exception {
		compile("assertThat(requirement).judgedBy(general).withEvidence(42).isSatisfied();", false);
	}

	@Test
	void ordinaryJuryUsesSameGrammar() throws Exception {
		compile("var jury = SimpleJury.<String>builder().judge(general).build(); assertThat(requirement).judgedBy(jury).withEvidence(\"READY\").isSatisfied();",
				true);
	}

	@Test
	void policyCannotPrecedeEvidence() throws Exception {
		compile("assertThat(requirement).judgedBy(general).withAcceptancePolicy(j -> new AcceptanceDecision(AcceptanceAction.RELY, \"rely\"));",
				false);
	}

	@Test
	void policyLambdaAfterEvidenceNeedsNoIdentity() throws Exception {
		compile("assertThat(requirement).judgedBy(general).withEvidence(\"READY\").withAcceptancePolicy(j -> new AcceptanceDecision(AcceptanceAction.RELY, \"rely\")).isSatisfied();",
				true);
	}

	@Test
	void requirementAwareJudgeCannotLoseSpecificationType() throws Exception {
		compile("Requirement<Integer> numeric = new Requirement<>(\"n\", \"1\", \"number\", 4, null); assertThat(numeric).judgedByRequirement(judge);",
				false);
	}

	@Test
	void pureRequirementCannotAttachPolicy() throws Exception {
		compile("requirement.under(j -> new AcceptanceDecision(AcceptanceAction.RELY, \"rely\"));", false);
	}

    @Test
    void ordinaryAndPairedJudgeOverloadsHaveTheSameErasure() throws Exception {
        compile("class Alternatives { void judgedBy(Judge<String> j) {} void judgedBy(Judge<RequirementEvidence<String,String>> j) {} }", false);
    }

}
