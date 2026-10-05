/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.evaluation;

import io.github.markpollack.judge.verdict.AttemptDisposition;
import io.github.markpollack.judge.verdict.CompositeAttempt;
import io.github.markpollack.judge.verdict.CompositeRelation;
import io.github.markpollack.judge.verdict.DispositionReason;
import io.github.markpollack.judge.verdict.Verdict;
import io.github.markpollack.judge.verdict.VerdictProvenance;
import io.github.markpollack.judge.verdict.VerdictProvenanceKind;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import io.github.markpollack.judge.judgment.*;
import io.github.markpollack.judge.jury.*;
import io.github.markpollack.judge.verdict.*;
import io.github.markpollack.judge.voting.*;
import io.github.markpollack.judge.requirement.*;
import io.github.markpollack.judge.reporting.VerdictReport;
import io.github.markpollack.judge.serialization.VerdictCodec;
import static org.assertj.core.api.Assertions.*;

class AllOfContainmentTest {

	@ParameterizedTest
	@CsvSource({ "true,false", "false,false", "true,true", "false,true" })
	void returnedInvalidChildIsRetainedWithoutErasingSibling(boolean failingSibling, boolean wrongAssociation) {
		var a = Requirement.text("A", "1", "A");
		var b = Requirement.text("B", "1", "B");
		var alien = Requirement.text("ALIEN", "1", "alien");
		var parent = new GeneralRequirement<>("parent", "1", "A and B", new AllOf(List.of(a, b)), a.source());
		var answer = Judgment.fail("Rejected apparent violation").forRequirement(wrongAssociation ? alien : b);
		var leaf = Verdict.single("original", answer);
		var rejected = new Verdict(leaf.judgment(), leaf.individual(), leaf.individualByName(), leaf.seats(),
				leaf.provenance(), leaf.compositeAttempts(), wrongAssociation ? 1 : 2, wrongAssociation ? alien : b);
		var calls = new AtomicInteger();
		var accepted = Verdict
			.single("A", failingSibling ? Judgment.fail("Established violation") : Judgment.pass("A holds"))
			.forRequirement(a);
		var jury = Assignments.<String>forRequirement(parent).jury(a, TestRecipes.jury((r, e) -> {
			calls.incrementAndGet();
			return accepted;
		})).jury(b, TestRecipes.jury((r, e) -> {
			calls.incrementAndGet();
			return rejected;
		})).validate().evidence("captured").build();
		var result = jury.vote();
		var expected = failingSibling ? Verdict.Conclusion.FAIL : Verdict.Conclusion.INCONCLUSIVE;
		assertThat(result.conclusion()).isEqualTo(expected);
		assertThat(result.compositeAttempts().getFirst().verdict()).isEqualTo(accepted);
		var attempt = result.compositeAttempts().getLast();
		assertThat(attempt.disposition()).isEqualTo(AttemptDisposition.STAGE_FAILED);
		assertThat(attempt.dispositionReason()).isEqualTo(DispositionReason.INVALID_TIER_RESULT);
		assertThat(attempt.verdict()).isSameAs(rejected);
		assertThat(attempt.failure()).isNull();
		var codec = new VerdictCodec();
		var json = codec.write(result);
		var read = codec.read(json);
		assertThat(read).isEqualTo(result);
		assertThat(read.conclusion()).isEqualTo(expected);
		assertThat(read.compositeAttempts().getLast().verdict()).isEqualTo(rejected);
		assertThat(VerdictReport.of(read).summary()).contains("B: STAGE_FAILED", "INVALID_TIER_RESULT");
		assertThat(calls).hasValue(2);
		if (!wrongAssociation)
			assertThatThrownBy(rejected::conclusion).isInstanceOf(IllegalArgumentException.class);
		else
			assertThat(rejected.conclusion()).isEqualTo(Verdict.Conclusion.FAIL);
		assertThatThrownBy(() -> codec
			.read(json.replace("stage_failed", "used").replace(",\"dispositionReason\":\"invalid_tier_result\"", "")))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void falseInvalidResultRefusalIsRejectedByRetainedValidation() {
		var b = Requirement.text("B", "1", "B");
		var parent = new GeneralRequirement<>("parent", "1", "B required", new AllOf(List.of(b)), b.source());
		var valid = Verdict.single("B", Judgment.pass("B holds")).forRequirement(b);
		var forged = Verdict.advancedBuilder()
			.requirement(parent)
			.judgment(Judgment.abstain("fabricated refusal"))
			.declaredCardinality(1)
			.provenance(new VerdictProvenance(VerdictProvenanceKind.CONSTITUENTS, null, null))
			.compositeAttempts(List.of(CompositeAttempt.stageFailed("B", CompositeRelation.CONSTITUENT, null,
					DispositionReason.INVALID_TIER_RESULT, valid)))
			.build();
		assertThatThrownBy(forged::conclusion).hasMessageContaining("Invalid-result refusal requires");
		assertThatThrownBy(() -> new VerdictCodec().write(forged))
			.hasMessageContaining("Invalid-result refusal requires");
	}

	@Test
	void cancellationEscapesConstituentContainment() {
		var a = Requirement.text("A", "1", "A");
		var b = Requirement.text("B", "1", "B");
		var parent = new GeneralRequirement<>("parent", "1", "A and B", new AllOf(List.of(a, b)), a.source());
		var cancelled = new CancellationException("caller cancelled");
		var calls = new AtomicInteger();
		var jury = Assignments.<String>forRequirement(parent).jury(a, TestRecipes.jury((r, e) -> {
			calls.incrementAndGet();
			return Verdict.single("A", Judgment.fail("A violated"));
		})).jury(b, TestRecipes.jury((r, e) -> {
			calls.incrementAndGet();
			throw cancelled;
		})).validate().evidence("fixture").build();
		assertThatThrownBy(jury::vote).isSameAs(cancelled);
		assertThat(calls).hasValue(2);
	}

}
