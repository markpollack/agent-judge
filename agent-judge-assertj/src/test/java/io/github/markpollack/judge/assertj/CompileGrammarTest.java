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
    @TempDir Path directory;

    static final String PREFIX = """
        import static io.github.markpollack.judge.assertj.Assertions.assertThat;
        import static org.assertj.core.api.Assertions.assertThat;
        import io.github.markpollack.judge.*;
        import io.github.markpollack.judge.jury.*;
        import io.github.markpollack.judge.result.*;
        import io.github.markpollack.judge.requirement.*;
        class Probe {
            Requirement<String> requirement = Requirement.text("r", "1", "ready");
            Judge<RequirementEvidence<Requirement<String>, String>> judge = input -> Judgment.pass("ready");
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
            var options = List.of("-proc:none", "-classpath", System.getProperty("java.class.path"),
                "-d", directory.toString());
            boolean success = compiler.getTask(null, files, diagnostics, options, null,
                files.getJavaFileObjects(source.toFile())).call();
            assertThat(success).as("%s%n%s", body, diagnostics.getDiagnostics()).isEqualTo(expected);
        }
    }

    @Test void validEvidenceAndOrdinaryAssertJCoexist() throws Exception {
        compile("assertThat(requirement).judgedBy(judge).withEvidence(\"READY\").isSatisfied(); assertThat(42).isEqualTo(42);", true);
    }
    @Test void wrongEvidenceIsRejected() throws Exception {
        compile("assertThat(requirement).judgedBy(judge).withEvidence(42).isSatisfied();", false);
    }
    @Test void unrelatedJudgeIsRejected() throws Exception {
        compile("assertThat(requirement).judgedBy(general).withEvidence(\"READY\").isSatisfied();", false);
    }
    @Test void prematureTerminalIsRejected() throws Exception {
        compile("assertThat(requirement).judgedBy(judge).isSatisfied();", false);
    }
    @Test void juryCannotMixEvidenceTypes() throws Exception {
        compile("SimpleJury.<String>builder().judge(general).judge(number).build();", false);
    }
    @Test void juryFactoryCannotEraseEvidenceTypes() throws Exception {
        compile("Juries.fromJudges(new ConsensusStrategy(), general, number);", false);
    }
    @Test void forgottenTerminalIsLegalJava() throws Exception {
        compile("assertThat(requirement).judgedBy(judge).withEvidence(\"READY\");", true);
    }
    @Test void typedJuryUsesSameFluentGrammar() throws Exception {
        compile("var jury = SimpleJury.<RequirementEvidence<Requirement<String>, String>>builder().judge(judge).build(); assertThat(requirement).judgedBy(jury).withEvidence(\"READY\").isSatisfied();", true);
    }
}
