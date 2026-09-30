/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertions;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.*;

class AssertionBoundariesTest {

	@Test
	void productionDependsOnCoreAndOpenTest4jWithoutProviderOrJunitRuntime() {
		var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
			.importPackages("io.github.markpollack.judge.assertions");
		noClasses().should()
			.dependOnClassesThat()
			.resideInAnyPackage("io.github.markpollack.judge.jev..", "io.github.gudcks0305..", "org.junit..",
					"org.springframework..", "io.github.markpollack.ai..", "java.net..", "java.net.http..")
			.check(classes);
		noClasses().should().callMethod(Thread.class, "getContextClassLoader").check(classes);
		noClasses().should().dependOnClassesThat().areAssignableTo(ThreadLocal.class).check(classes);
		var core = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
			.importPackages("io.github.markpollack.judge.judgment", "io.github.markpollack.judge.acceptance",
                    "io.github.markpollack.judge.provenance", "io.github.markpollack.judge.requirement",
                    "io.github.markpollack.judge.serialization", "io.github.markpollack.judge.jury");
		noClasses().should()
			.dependOnClassesThat()
			.resideInAnyPackage("io.github.markpollack.judge.assertions..", "io.github.markpollack.judge.jev..",
                    "io.github.gudcks0305..", "org.springframework..")
			.check(core);
	}

	@Test
	void reviewedAliasAndSelectedByteReferencesRemainExact() throws Exception {
		var f = new ConferenceFixture();
		assertEquals("27756e9f1d4248bb0229c182f23d403a8bc5d2ec", f.bindings.path("subjectRevision").asText());
		verifyReference(f.bindings.path("evidenceManifest"));
		verifyReference(f.bindings.path("sufficiencyReviews"));
		for (var b : f.bindings.path("bindings")) {
			for (String key : List.of("sourceRequirement", "recipe", "bundle"))
				verifyReference(b.path(key));
			if (b.has("selectedClause"))
				verifyReference(b.path("selectedClause"));
			assertEquals(b.path("textSha256").asText(),
					ConferenceFixture.sha(b.path("text").asText().getBytes(StandardCharsets.UTF_8)));
			var recipe = ConferenceFixture.JSON
				.readTree(ConferenceFixture.resource(b.path("recipe").path("path").asText()));
			assertEquals(b.path("recipeId").asText(), recipe.path("extractionRecipe").path("id").asText());
			assertEquals(b.path("recipeVersion").asText(), recipe.path("extractionRecipe").path("version").asText());
			assertEquals(b.path("scope").asText(), recipe.path("scope").asText());
		}
		var b = f.bindings.path("bindings").get(0);
		assertEquals(ConferenceFixture.LOCK, b.path("text").asText());
		var bundle = ConferenceFixture.JSON
			.readTree(ConferenceFixture.resource(b.path("bundle").path("path").asText()));
		assertEquals(new String(ConferenceFixture.resource(b.path("selectedClause").path("path").asText()),
				StandardCharsets.UTF_8), bundle.path("requiredLockOrder").asText());
		assertEquals(b.path("scope").asText(), bundle.path("scope").asText());
		assertEquals(20425, ConferenceFixture.resource(b.path("bundle").path("path").asText()).length);
		assertEquals(3318, ConferenceFixture
			.resource(f.bindings.path("bindings").get(1).path("bundle").path("path").asText()).length);
		var reviews = ConferenceFixture.JSON
			.readTree(ConferenceFixture.resource("evidence-compilation/v1/reviews.json"));
		for (var binding : f.bindings.path("bindings")) {
			var review = java.util.stream.StreamSupport.stream(reviews.path("reviews").spliterator(), false)
				.filter(r -> r.path("recipe").asText().equals(binding.path("reviewKey").asText()))
				.findFirst()
				.orElseThrow();
			assertEquals("SUFFICIENT_FOR_SCOPED_SOURCE_CLAIM", review.path("sufficiency").asText());
			assertEquals(binding.path("bundle").path("sha256"), review.path("bundle").path("sha256"));
		}
	}

	private void verifyReference(com.fasterxml.jackson.databind.JsonNode ref) throws Exception {
		byte[] bytes = ConferenceFixture.resource(ref.path("path").asText());
		assertEquals(ref.path("bytes").asInt(), bytes.length);
		assertEquals(ref.path("sha256").asText(), ConferenceFixture.sha(bytes));
	}

	@Test
	void liveRunnerRequiresExplicitInvocationAndCredentialBeforeAnySetup() {
		assertThrows(IllegalArgumentException.class, () -> ConferenceAssertionRun.main(new String[0]));
		assertThrows(IllegalArgumentException.class,
				() -> ConferenceAssertionRun.run("", java.nio.file.Path.of("unused")));
	}

}
