/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * No dependency cycles between the packages of this module, at any depth. Main sources only.
 */
class NoCyclesTest {

	/** This module's compiled main classes: Maven's target/classes, or Gradle's build/classes/java/main. */
	private static final JavaClasses CLASSES = new ClassFileImporter()
		.importPath(java.nio.file.Files.isDirectory(java.nio.file.Path.of("target", "classes")) ? java.nio.file.Path.of("target", "classes")
				: java.nio.file.Path.of("build", "classes", "java", "main"));

	/** All imported packages participate, including the root itself and the default package. */
	private static final SliceAssignment PACKAGE = new SliceAssignment() {
		@Override
		public SliceIdentifier getIdentifierOf(JavaClass javaClass) {
			String name = javaClass.getPackageName();
			return SliceIdentifier.of(name.isEmpty() ? "<default package>" : name);
		}

		@Override
		public String getDescription() {
			return "packages";
		}
	};

	@Test
	void importsSomething() {
		// an empty import would let the rule pass vacuously
		assertFalse(CLASSES.isEmpty(), "no classes imported for io.github.markpollack.judge.architecture: is the test running from the module directory with main classes compiled?");
	}

	/** Every imported package is a slice; a cycle between any two fails. */
	@Test
	void noPackageCycles() {
		slices().assignedFrom(PACKAGE).should().beFreeOfCycles().check(CLASSES);
	}

	// No class-cycle rule here, by the Owner's ARCHUNIT_CYCLES decision (2026-11-07): this
	// module's class cycles are its recursive value model, and breaking them would change
	// public value types for no structural gain. Package cycles stay forbidden above.

}
