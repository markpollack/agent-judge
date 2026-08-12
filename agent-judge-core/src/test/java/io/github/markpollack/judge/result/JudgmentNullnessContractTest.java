/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.result;

import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The declaration-level optionality contract for {@link Judgment}.
 *
 * <p>
 * {@code score} and {@code label} have always been <em>semantically</em> optional, but a
 * consumer reading the Java declaration — by eye, by reflection, or by a schema deriver —
 * could not tell them apart from the required members. This class pins the declaration so
 * reflection, schema derivation, and serialization cannot disagree about which members may
 * be absent.
 * </p>
 *
 * <p>
 * JSpecify supplies the vocabulary only; the build enforces it with NullAway. See
 * {@code io.github.markpollack.judge.result.package-info}.
 * </p>
 *
 * @author Mark Pollack
 * @since 0.14.0
 */
@DisplayName("Judgment nullness contract (M2)")
class JudgmentNullnessContractTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final List<String> OPTIONAL_MEMBERS = List.of("score", "label");

	private static final List<String> REQUIRED_MEMBERS = List.of("status", "reasoning", "checks", "metadata");

	@Nested
	@DisplayName("Declaration")
	class Declaration {

		@Test
		@DisplayName("the result package is null-marked, so unannotated reference types are non-null")
		void packageIsNullMarked() {
			assertThat(Judgment.class.getPackage().getAnnotation(NullMarked.class))
				.as("io.github.markpollack.judge.result must declare @NullMarked for @Nullable to mean anything")
				.isNotNull();
		}

		@Test
		@DisplayName("adoption stays bounded to the result package")
		void noOtherCorePackageIsNullMarked() {
			assertThat(List.of(io.github.markpollack.judge.Judge.class, io.github.markpollack.judge.jury.Verdict.class,
					io.github.markpollack.judge.context.JudgmentContext.class,
					io.github.markpollack.judge.config.JudgeSpec.class, io.github.markpollack.judge.fs.FileContentJudge.class))
				.allSatisfy(type -> assertThat(type.getPackage().getAnnotation(NullMarked.class))
					.as("%s must not be null-marked by this step", type.getPackageName())
					.isNull());
		}

		@Test
		@DisplayName("score and label are @Nullable on every declaration surface")
		void optionalMembersAreDeclaredNullable() {
			for (String member : OPTIONAL_MEMBERS) {
				assertThat(nullnessSurfaces(member)).allSatisfy(
						(surface, annotated) -> assertThat(annotated.getAnnotation(Nullable.class))
							.as("Judgment.%s must be @Nullable on its %s", member, surface)
							.isNotNull());
			}
		}

		@Test
		@DisplayName("status and the other required members carry no @Nullable")
		void requiredMembersAreNotDeclaredNullable() {
			for (String member : REQUIRED_MEMBERS) {
				assertThat(nullnessSurfaces(member)).allSatisfy(
						(surface, annotated) -> assertThat(annotated.getAnnotation(Nullable.class))
							.as("Judgment.%s must stay non-null on its %s", member, surface)
							.isNull());
			}
		}

	}

	@Nested
	@DisplayName("Runtime agreement")
	class RuntimeAgreement {

		@Test
		@DisplayName("only members declared @Nullable are ever absent at runtime")
		void absenceOnlyWhereDeclared() {
			examples().forEach((name, judgment) -> {
				for (RecordComponent component : Judgment.class.getRecordComponents()) {
					boolean absent = accessorValue(judgment, component) == null;
					boolean declaredNullable = component.getAnnotatedType().getAnnotation(Nullable.class) != null;
					assertThat(absent && !declaredNullable)
						.as("%s judgment left undeclared-optional member %s absent", name, component.getName())
						.isFalse();
				}
			});
		}

		@Test
		@DisplayName("status-only, scored, labelled and scored-plus-labelled judgments read as declared")
		void everyOptionalCombination() {
			Map<String, Judgment> examples = examples();

			assertThat(examples.get("status-only").score()).isNull();
			assertThat(examples.get("status-only").label()).isNull();
			assertThat(examples.get("scored").score()).isEqualTo(0.72);
			assertThat(examples.get("scored").label()).isNull();
			assertThat(examples.get("labelled").score()).isNull();
			assertThat(examples.get("labelled").label()).isEqualTo("relevant");
			assertThat(examples.get("scored-and-labelled").score()).isEqualTo(0.91);
			assertThat(examples.get("scored-and-labelled").label()).isEqualTo("excellent");

			assertThat(examples.values()).allSatisfy(judgment -> assertThat(judgment.status()).isNotNull());
		}

		@Test
		@DisplayName("an absent optional is omitted from the wire, never emitted as JSON null")
		void wireOmitsRatherThanNulls() {
			examples().forEach((name, judgment) -> {
				String json = writeJson(judgment);

				assertThat(json).as("%s judgment must not emit a JSON null", name).doesNotContain("null");
				for (String member : OPTIONAL_MEMBERS) {
					String key = "\"" + member + "\"";
					if (accessorValue(judgment, recordComponent(member)) == null) {
						assertThat(json).as("%s judgment must omit absent %s", name, member).doesNotContain(key);
					}
					else {
						assertThat(json).as("%s judgment must carry present %s", name, member).contains(key);
					}
				}
				for (String member : REQUIRED_MEMBERS) {
					assertThat(json).as("%s judgment must always carry %s", name, member)
						.contains("\"" + member + "\"");
				}
			});
		}

	}

	private static Map<String, Judgment> examples() {
		Map<String, Judgment> examples = new LinkedHashMap<>();
		examples.put("status-only", Judgment.pass("All checks passed"));
		examples.put("scored", Judgment.builder().pass().score(0.72).reasoning("above the bar").build());
		examples.put("labelled", Judgment.builder().pass().label("relevant").reasoning("on topic").build());
		examples.put("scored-and-labelled",
				Judgment.builder().pass().score(0.91).label("excellent").reasoning("both facts recorded").build());
		return examples;
	}

	/**
	 * The four places a consumer can read the nullness of one record component: the record
	 * component itself, its accessor return type, its canonical-constructor parameter type,
	 * and its backing field type. A JSpecify type-use annotation must reach all four, or a
	 * downstream reader would see a different contract depending on where it looked.
	 */
	private static Map<String, AnnotatedType> nullnessSurfaces(String member) {
		RecordComponent component = recordComponent(member);
		Map<String, AnnotatedType> surfaces = new LinkedHashMap<>();
		surfaces.put("record component", component.getAnnotatedType());
		surfaces.put("accessor return type", component.getAccessor().getAnnotatedReturnType());
		surfaces.put("canonical constructor parameter", canonicalConstructorParameter(member));
		surfaces.put("field type", fieldType(member));
		return surfaces;
	}

	private static RecordComponent recordComponent(String member) {
		return Arrays.stream(Judgment.class.getRecordComponents())
			.filter(component -> component.getName().equals(member))
			.findFirst()
			.orElseThrow(() -> new AssertionError("Judgment has no record component named " + member));
	}

	private static AnnotatedType canonicalConstructorParameter(String member) {
		RecordComponent[] components = Judgment.class.getRecordComponents();
		Class<?>[] parameterTypes = Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
		Constructor<Judgment> canonical;
		try {
			canonical = Judgment.class.getDeclaredConstructor(parameterTypes);
		}
		catch (NoSuchMethodException ex) {
			throw new AssertionError("Judgment has no canonical constructor", ex);
		}
		for (int i = 0; i < components.length; i++) {
			if (components[i].getName().equals(member)) {
				return canonical.getAnnotatedParameterTypes()[i];
			}
		}
		throw new AssertionError("Judgment has no record component named " + member);
	}

	private static AnnotatedType fieldType(String member) {
		try {
			return Judgment.class.getDeclaredField(member).getAnnotatedType();
		}
		catch (NoSuchFieldException ex) {
			throw new AssertionError("Judgment has no field named " + member, ex);
		}
	}

	private static Object accessorValue(Judgment judgment, RecordComponent component) {
		try {
			return component.getAccessor().invoke(judgment);
		}
		catch (ReflectiveOperationException ex) {
			throw new AssertionError("could not read Judgment." + component.getName(), ex);
		}
	}

	private static String writeJson(Judgment judgment) {
		try {
			return MAPPER.writeValueAsString(judgment);
		}
		catch (Exception ex) {
			throw new AssertionError("could not serialize judgment", ex);
		}
	}

}
