/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.description;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.Judges;
import io.github.markpollack.judge.jury.Jury;
import io.github.markpollack.judge.jury.VotingStrategy;

/**
 * A jury that does not describe its own structure: the default of
 * {@link Jury#describe()}.
 *
 * <p>
 * What it knows comes from the jury's public view: the implementing class,
 * {@link Jury#getVotingStrategy()} and the flattened {@link Jury#getJudges()}. It does not
 * know how the jury seats, keys, weights or orders those judges, and it does not claim to.
 * A jury author who wants a structural description overrides {@code describe()}, and may
 * return one of the other {@link JuryDescription} variants.
 * </p>
 *
 * <h2>Portable form</h2>
 * <pre>
 * {
 *   "descriptionVersion": 2,
 *   "kind": "OPAQUE",
 *   "aggregateMayBeNotApplicable": false,
 *   "implementation": {...},
 *   "strategy": {"declared": true, "value": {...}},
 *   "judges": [{...}, ...]
 * }
 * </pre>
 * <p>
 * {@code "strategy": {"declared": false}} means the jury reported no voting strategy.
 * </p>
 *
 * @param implementation the class that implements the jury
 * @param aggregateMayBeNotApplicable what the jury itself declared, since an opaque jury's
 * structure gives no way to derive it
 * @param strategy the jury's strategy, or null when it reports none
 * @param judges the jury's flattened judges, in the order it reports them
 * @author Mark Pollack
 * @since 0.17.0
 */
public record OpaqueJuryDescription(ImplementationIdentity implementation, boolean aggregateMayBeNotApplicable,
		@Nullable StrategyDescription strategy, List<JudgeDescription> judges) implements JuryDescription {

	/** Validate the implementation and copy the judges. */
	public OpaqueJuryDescription {
		Objects.requireNonNull(implementation, "implementation must not be null");
		judges = List.copyOf(Objects.requireNonNull(judges, "judges must not be null"));
	}

	static OpaqueJuryDescription of(Jury jury) {
		Objects.requireNonNull(jury, "jury must not be null");
		VotingStrategy votingStrategy = jury.getVotingStrategy();
		StrategyDescription strategy = votingStrategy == null ? null : votingStrategy.describe();
		List<Judge> reported = Objects.requireNonNull(jury.getJudges(), "getJudges() must not return null");
		List<JudgeDescription> judges = new ArrayList<>(reported.size());
		for (int index = 0; index < reported.size(); index++) {
			try {
				judges.add(Judges.describe(reported.get(index)));
			}
			catch (IllegalArgumentException ex) {
				throw new IllegalArgumentException("judges[" + index + "]: " + ex.getMessage(), ex);
			}
		}
		return new OpaqueJuryDescription(ImplementationIdentity.of(jury.getClass()),
				jury.aggregateMayBeNotApplicable(), strategy, judges);
	}

	@Override
	public Map<String, Object> toPortable() {
		return PortableForm.freezeRoot(portableTree(), "jury");
	}

	Map<String, Object> portableTree() {
		List<Object> judgeTrees = new ArrayList<>(judges.size());
		for (JudgeDescription judge : judges) {
			judgeTrees.add(judge.portableTree());
		}
		Map<String, Object> tree = new LinkedHashMap<>();
		tree.put("kind", "OPAQUE");
		tree.put("aggregateMayBeNotApplicable", aggregateMayBeNotApplicable);
		tree.put("implementation", implementation.portableTree());
		StrategyDescription reported = strategy;
		tree.put("strategy",
				reported == null ? PortableForm.undeclared() : PortableForm.declaredValue(reported.portableTree()));
		tree.put("judges", judgeTrees);
		return tree;
	}

}
