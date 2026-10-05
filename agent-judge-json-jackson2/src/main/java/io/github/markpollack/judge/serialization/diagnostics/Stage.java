/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.serialization.diagnostics;

import java.util.List;
import java.util.Objects;
import io.github.markpollack.judge.verdict.VerdictProvenance;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jspecify.annotations.Nullable;

/**
 * One stage of a verdict: the root, or one attempt a composite jury entered, with its own
 * aggregate, its own evidence and its own judges.
 *
 * <p>
 * The first eight components are the attempt facts a parent recorded about the stage;
 * they are all null on the root, which no parent entered, and on a legacy
 * {@code subVerdicts} entry, which recorded none of them. The rest are the stage's own
 * verdict. {@code status}, {@code reasoning}, {@code evidence} and {@code judges} are
 * absent together, exactly when the attempt entered and never produced a verdict;
 * {@code failure} then carries the failure code. <b>An absent status is never read as a
 * failure.</b>
 *
 * @param stage the stage's configured name, or null for the root and for a legacy entry
 * that recorded none
 * @param path the stage's own full path from the root — {@code ["structure"]} for a
 * direct tier, {@code ["structure", "inner"]} for one nested beneath it; empty for the
 * root, and for a nameless legacy entry the parent's path
 * @param relation how the stage relates to its parent, as recorded
 * @param policy the cascade policy, as recorded
 * @param disposition whether the parent could use the stage, as recorded
 * @param reason why the parent could not use it, as recorded
 * @param failure the failure code, when the stage produced no verdict
 * @param usedByParent true for {@code used}, false for {@code stage_failed}, null when
 * the disposition is absent or unrecognised — unknown is never read as used
 * @param status the stage's aggregate status, as the wire token 0.17 writes, or null when
 * the stage produced no verdict or recorded no readable status
 * @param reasonCode the aggregate's reason code, or null when none was recorded
 * @param reasoning the aggregate's reasoning, or null when the stage produced no verdict
 * @param evidence the aggregate's aggregation evidence, or null when it carries none
 * @param judges the judges seated in this stage, in seat order; empty when the stage
 * produced no verdict
 * @param judgment complete semantic view, absent when unavailable
 * @param declaredCardinality recorded declaredCardinality, absent in historical records
 * @param provenance recorded provenance, absent in historical records
 * @author Mark Pollack
 * @since 0.17.0
 */
@JsonPropertyOrder({ "stage", "path", "relation", "policy", "disposition", "reason", "failure", "usedByParent",
		"status", "reasonCode", "reasoning", "evidence", "judges" })
public record Stage(@Nullable String stage, List<String> path, @Nullable String relation, @Nullable String policy,
		@Nullable String disposition, @Nullable String reason, @Nullable String failure, @Nullable Boolean usedByParent,
		@Nullable String status, @Nullable String reasonCode, @Nullable String reasoning, @Nullable Evidence evidence,
		List<JudgeSeat> judges, @Nullable JudgmentView judgment, @Nullable Integer declaredCardinality,
		@Nullable VerdictProvenance provenance) {

	/**
	 * Construct a historical semantic view with absent execution structure.
	 * @param stage recorded stage
	 * @param path recorded path
	 * @param relation recorded relation
	 * @param policy recorded policy
	 * @param disposition recorded disposition
	 * @param reason recorded reason
	 * @param failure recorded failure
	 * @param usedByParent recorded usedByParent
	 * @param status recorded status
	 * @param reasonCode recorded reasonCode
	 * @param reasoning recorded reasoning
	 * @param evidence recorded evidence
	 * @param judges recorded judges
	 * @param judgment recorded judgment
	 */
	public Stage(@Nullable String stage, List<String> path, @Nullable String relation, @Nullable String policy,
			@Nullable String disposition, @Nullable String reason, @Nullable String failure,
			@Nullable Boolean usedByParent, @Nullable String status, @Nullable String reasonCode,
			@Nullable String reasoning, @Nullable Evidence evidence, List<JudgeSeat> judges,
			@Nullable JudgmentView judgment) {
		this(stage, path, relation, policy, disposition, reason, failure, usedByParent, status, reasonCode, reasoning,
				evidence, judges, judgment, null, null);
	}

	/**
	 * Construct a historical view with no modern judgment.
	 * @param stage recorded stage
	 * @param path recorded path
	 * @param relation recorded relation
	 * @param policy recorded policy
	 * @param disposition recorded disposition
	 * @param reason recorded reason
	 * @param failure recorded failure
	 * @param usedByParent recorded usedByParent
	 * @param status recorded status
	 * @param reasonCode recorded reasonCode
	 * @param reasoning recorded reasoning
	 * @param evidence recorded evidence
	 * @param judges recorded judges
	 */
	public Stage(@Nullable String stage, List<String> path, @Nullable String relation, @Nullable String policy,
			@Nullable String disposition, @Nullable String reason, @Nullable String failure,
			@Nullable Boolean usedByParent, @Nullable String status, @Nullable String reasonCode,
			@Nullable String reasoning, @Nullable Evidence evidence, List<JudgeSeat> judges) {
		this(stage, path, relation, policy, disposition, reason, failure, usedByParent, status, reasonCode, reasoning,
				evidence, judges, null);
	}

	/** Validate and copy. */
	public Stage {
		Objects.requireNonNull(path, "path must not be null");
		path = List.copyOf(path);
		Objects.requireNonNull(judges, "judges must not be null");
		judges = List.copyOf(judges);
	}

}
