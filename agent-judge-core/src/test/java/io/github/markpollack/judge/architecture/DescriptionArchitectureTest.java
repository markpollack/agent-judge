/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.architecture;

import java.util.Set;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.markpollack.judge.description.JuryDescription;
import io.github.markpollack.judge.description.archfixture.ReflectiveFixture;
import io.github.markpollack.judge.description.archfixture.ReflectiveMemberListing;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The description package describes by self-report, never by reflection.
 *
 * <p>
 * A description read reflectively from private fields would change whenever a field was
 * renamed, and the portable form is a published contract that consumers hash. Two rules are
 * needed: a dependency on {@code java.lang.reflect} catches reflective access, and a call to
 * {@code java.lang.Class}'s member lookups catches reflection that never names a
 * {@code java.lang.reflect} type in bytecode. Each rule is proved able to fail.
 * </p>
 */
@DisplayName("The description package uses no reflection")
class DescriptionArchitectureTest {

	private static final String DESCRIPTION_PACKAGE = "io.github.markpollack.judge.description";

	private static final Set<String> MEMBER_LOOKUPS = Set.of("getFields", "getField", "getDeclaredFields",
			"getDeclaredField", "getMethods", "getMethod", "getDeclaredMethods", "getDeclaredMethod", "getConstructors",
			"getConstructor", "getDeclaredConstructors", "getDeclaredConstructor", "getRecordComponents",
			"getEnclosingMethod", "getEnclosingConstructor");

	static final ArchRule NO_REFLECT_PACKAGE = noClasses().that()
		.resideInAPackage(DESCRIPTION_PACKAGE + "..")
		.should()
		.dependOnClassesThat()
		.resideInAPackage("java.lang.reflect..")
		.because("each type describes itself; reading private state reflectively would let a field rename "
				+ "change a published, hashed description");

	static final ArchRule NO_REFLECTIVE_MEMBER_LOOKUP = noClasses().that()
		.resideInAPackage(DESCRIPTION_PACKAGE + "..")
		.should()
		.callMethodWhere(DescribedPredicate.describe("a reflective member lookup on java.lang.Class",
				(JavaMethodCall call) -> call.getTargetOwner().isEquivalentTo(Class.class)
						&& MEMBER_LOOKUPS.contains(call.getName())))
		.because("listing a class's members is reflection even when no java.lang.reflect type is named");

	@Test
	void theDescriptionPackageUsesNoReflection() {
		JavaClasses main = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
			.importPackages(DESCRIPTION_PACKAGE);

		assertThat(main.contain(JuryDescription.class)).as("the rules see the main classes").isTrue();
		assertThat(main.contain(ReflectiveFixture.class)).as("the rules do not see the test fixtures").isFalse();
		NO_REFLECT_PACKAGE.check(main);
		NO_REFLECTIVE_MEMBER_LOOKUP.check(main);
	}

	@Test
	void theReflectPackageRuleFailsOnReflectiveAccess() {
		JavaClasses fixture = new ClassFileImporter().importClasses(ReflectiveFixture.class);

		assertThat(NO_REFLECT_PACKAGE.evaluate(fixture).hasViolation()).isTrue();
	}

	@Test
	void theMemberLookupRuleFailsOnReflectionThePackageRuleCannotSee() {
		JavaClasses fixture = new ClassFileImporter().importClasses(ReflectiveMemberListing.class);

		assertThat(NO_REFLECT_PACKAGE.evaluate(fixture).hasViolation()).as("the gap").isFalse();
		assertThat(NO_REFLECTIVE_MEMBER_LOOKUP.evaluate(fixture).hasViolation()).as("the rule that closes it")
			.isTrue();
	}

}
