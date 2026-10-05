package io.github.markpollack.judge.architecture;

import java.nio.file.Path;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

class CoreArchitectureTest {

	@Test
	void coreHasNoPackageCyclesOrEngineDependencies() {
		var classes = new ClassFileImporter().importPath(Path.of("target/classes"));
		assertThat(classes.size()).isGreaterThan(100);
		assertThat(classes.stream()).allMatch(c -> c.getPackageName().startsWith("io.github.markpollack.judge"));
		slices().assignedFrom(new com.tngtech.archunit.library.dependencies.SliceAssignment() {
			public com.tngtech.archunit.library.dependencies.SliceIdentifier getIdentifierOf(
					com.tngtech.archunit.core.domain.JavaClass type) {
				return com.tngtech.archunit.library.dependencies.SliceIdentifier.of(type.getPackageName());
			}

			public String getDescription() {
				return "every exact core package, including the root";
			}
		}).should().beFreeOfCycles().check(classes);
		noClasses().should()
			.dependOnClassesThat()
			.resideInAnyPackage("com.fasterxml.jackson.databind..", "com.fasterxml.jackson.core..",
					"org.springframework..", "org.slf4j..", "io.github.gudcks0305..")
			.check(classes);
	}

}
