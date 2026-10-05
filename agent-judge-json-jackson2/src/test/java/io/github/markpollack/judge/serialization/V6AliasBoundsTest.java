/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.serialization;

import java.util.*;
import io.github.markpollack.judge.judgment.*;
import io.github.markpollack.judge.jury.*;
import io.github.markpollack.judge.verdict.*;
import io.github.markpollack.judge.voting.*;
import io.github.markpollack.judge.requirement.Requirement;
import io.github.markpollack.judge.provenance.Invocation;
import io.github.markpollack.judge.portable.PreservationLimitException;
import io.github.markpollack.judge.evaluation.Evaluations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class V6AliasBoundsTest {

	Judgment checked(int count) {
		var b = Judgment.pass("supported").toBuilder();
		for (int i = 0; i < count; i++) b.check(Check.pass("c" + i));
		return b.build();
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, 100, 150, 255 })
	void singleIdentityReopensAtTheSameNodeBound(int count) {
		var verdict = Verdict.single("single", checked(count)).requireUsable();
		var codec = new VerdictCodec();
		var reopened = codec.read(codec.write(verdict));
		assertThat(reopened).isEqualTo(verdict);
		assertThat(reopened.judgment()).isSameAs(reopened.individual().getFirst());
		assertThat(reopened.individual().getFirst().checks()).hasSize(count);
		if (count > 1) assertThat(reopened.judgment().checks().get(0).judgment())
				.isNotSameAs(reopened.judgment().checks().get(1).judgment());
	}

	@Test
	void nestedSelectedAndMetaRelationshipsRestoreOnlyTheirDeclaredAliases() {
		var child = Verdict.single("checked", checked(150));
		var meta = Juries.meta(new AllEligiblePassStrategy(), new NamedJury("member", () -> child));
		var cascade = CascadedJury.builder().tier("nested", meta, RoutingRule.FINAL_TIER).build().vote().requireUsable();
		var codec = new VerdictCodec();
		assertThat(codec.read(codec.write(cascade))).isEqualTo(cascade);
	}

	@Test
	void refusedOriginalWithOwnChecksReopensWhenRetainedInSeveralRelatedLocations() {
		var original = checked(150).forRequirement(Requirement.text("actual", "1", "actual"));
		var refused = Judgment.refuse(original, Requirement.text("expected", "1", "expected"),
				new Invocation("item", "fixture", true, null, 1, Map.of(), List.of()));
		var verdict = Verdict.builder().panel(new AllEligiblePassStrategy()).opinion("refused", refused).build().requireUsable();
		var codec = new VerdictCodec();
		var reopened = codec.read(codec.write(verdict));
		assertThat(reopened).isEqualTo(verdict);
		assertThat(reopened.seats().getFirst().rejection().refusedReturn().original())
				.isSameAs(reopened.individual().getFirst());
		assertThat(codec.readEvaluation(codec.write(Evaluations.of(verdict))).verdict()).isEqualTo(verdict);
	}

	@Test
	void nestedCompleteRefusalsKeepBothOriginalCarriersAndTheirOwnChecks() {
		var nativeReturn = checked(150).forRequirement(Requirement.text("native", "1", "native"));
		var inner = Judgment.refuse(nativeReturn, Requirement.text("inner", "1", "inner"),
				new Invocation("inner", "fixture", true, null, 1, Map.of(), List.of()));
		var outer = Judgment.refuse(inner, Requirement.text("outer", "1", "outer"),
				new Invocation("outer", "fixture", true, null, 1, Map.of(), List.of()));
		var verdict = Verdict.builder().panel(new AllEligiblePassStrategy()).opinion("refused", outer).build().requireUsable();
		var codec = new VerdictCodec();
		assertThat(codec.read(codec.write(verdict))).isEqualTo(verdict);
	}

	@Test
	void ninthCompleteRefusalIsStillRefusedWithItsCompleteOriginal() {
		Judgment value = Judgment.pass("native").forRequirement(Requirement.text("native", "1", "native"));
		for (int i = 0; i < JudgmentBounds.MAX_REFUSAL_DEPTH; i++)
			value = Judgment.refuse(value, Requirement.text("r" + i, "1", "r" + i),
					new Invocation("i" + i, "fixture", true, null, 1, Map.of(), List.of()));
		var original = value;
		assertThatThrownBy(() -> Judgment.refuse(original, Requirement.text("ninth", "1", "ninth"),
				new Invocation("ninth", "fixture", true, null, 1, Map.of(), List.of())))
				.isInstanceOfSatisfying(PreservationLimitException.class, e -> assertThat(e.original()).isSameAs(original));
	}

	@Test
	void genuineOversizePreservesTheWholeLiveOrStoredOriginal() {
		var verdict = Verdict.single("too-many", checked(256));
		var codec = new VerdictCodec();
		assertThatThrownBy(() -> codec.write(verdict)).isInstanceOfSatisfying(PreservationLimitException.class,
				e -> assertThat(e.original()).isSameAs(verdict));
		var mapper = new JacksonEvalJsonMapper(com.fasterxml.jackson.databind.json.JsonMapper.builder().addModule(ResultJson.module()).build());
		String wire = mapper.write(verdict);
		assertThatThrownBy(() -> codec.read(wire)).isInstanceOfSatisfying(PreservationLimitException.class,
				e -> assertThat(e.original()).isSameAs(wire));
		String evaluationWire = "{\"schemaVersion\":6,\"verdict\":" + wire
				+ ",\"policyResult\":{\"kind\":\"notRequested\"}}";
		assertThatThrownBy(() -> codec.readEvaluation(evaluationWire)).isInstanceOfSatisfying(PreservationLimitException.class,
				e -> assertThat(e.original()).isSameAs(evaluationWire));
	}

	@Test
	void unrepresentedCheckAliasesAreRefusedBeforePublishingUnreadableBytes() {
		var shared = Judgment.pass("shared");
		var builder = Judgment.pass("many aliases").toBuilder();
		for (int i = 0; i < 256; i++) builder.check(new Check("c" + i, shared));
		var verdict = Verdict.single("alias", builder.build()).requireUsable();
		var evaluation = Evaluations.of(verdict);
		assertThatThrownBy(() -> new VerdictCodec().write(evaluation)).isInstanceOfSatisfying(PreservationLimitException.class,
				e -> assertThat(e.original()).isSameAs(evaluation));
		assertThatThrownBy(() -> new VerdictCodec().write(verdict)).isInstanceOfSatisfying(PreservationLimitException.class,
				e -> assertThat(e.original()).isSameAs(verdict));
	}
}
