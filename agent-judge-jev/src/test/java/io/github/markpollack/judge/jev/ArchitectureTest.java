/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jev;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureTest {

	@Test
	void coreRemainsProviderNeutralAndAdapterHasNoFrameworkRuntime() {
		var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
			.importPackages("io.github.markpollack.judge");
		noClasses().that()
			.resideOutsideOfPackage("io.github.markpollack.judge.jev..")
			.should()
			.dependOnClassesThat()
			.resideInAnyPackage("io.github.gudcks0305.jev..", "io.github.markpollack.judge.jev..")
			.check(classes);
		noClasses().that()
			.resideInAPackage("io.github.markpollack.judge.jev..")
			.should()
			.dependOnClassesThat()
			.resideInAnyPackage("org.springframework..", "io.github.markpollack.judge.ai..",
					"io.github.markpollack.judge.llm..")
			.check(classes);
		assertThat(JevJudge.class.getInterfaces()).containsExactly(io.github.markpollack.judge.RequirementJudge.class);
	}

}
