/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury.interpretation;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.JudgeMetadata;
import io.github.markpollack.judge.JudgeType;
import io.github.markpollack.judge.JudgeWithMetadata;
import io.github.markpollack.judge.Judges;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.jury.ConsensusStrategy;
import io.github.markpollack.judge.jury.Jury;
import io.github.markpollack.judge.jury.SimpleJury;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.jury.VotingStrategy;
import io.github.markpollack.judge.result.Judgment;

import static org.assertj.core.api.Assertions.assertThat;

/** Stored fixtures, live juries and small helpers shared by the interpretation tests. */
final class Fixtures {

	static final ObjectMapper MAPPER = new ObjectMapper();

	static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
	};

	static final JudgmentContext CONTEXT = JudgmentContext.builder().goal("interpret").build();

	/** The condition a capable judge declares. */
	static final String EXCLUSION = "the change set contains no Java sources";

	static final String EXAMPLE_ONE = "example-0-17-cascade";

	static final String EXAMPLE_TWO = "bud-ddd-9b68576e-review-derived-brief";

	static final String A068E50A = "bud-ddd-a068e50a-merged-spring-batch";

	static final String A36A7598C = "bud-ddd-36a7598c-merged-spring-batch";

	static final String BUD_EVAL_7E423DE9 = "bud-eval-7e423de9-rest-service-fewshot";

	static final String ACP_13_RUN = "pr-review-polyglot-2ea96a55-ACP-13";

	static final String ACP_13_SESSION = "pr-review-polyglot-20260820-210100-grok-ACP-13";

	static final List<String> ALL_STORED = List.of(EXAMPLE_ONE, EXAMPLE_TWO, A068E50A, A36A7598C, BUD_EVAL_7E423DE9,
			ACP_13_RUN, ACP_13_SESSION);

	static final String COMPOSITE_GOLDEN = "/conformance/composite-verdict-0.17.json";

	static final String BOUNDARY_GOLDEN = "/conformance/boundary-rejection-0.17.json";

	private Fixtures() {
	}

	/** A stored verdict object copied from the archive, as parsed JSON in encounter order. */
	static Map<String, Object> stored(String name) {
		return readMap("/interpretation/stored/" + name + ".verdict.json");
	}

	static Map<String, Object> readMap(String resource) {
		return MAPPER.convertValue(readTree(resource), MAP);
	}

	static JsonNode readTree(String resource) {
		try (InputStream in = Fixtures.class.getResourceAsStream(resource)) {
			assertThat(in).as("missing test resource %s", resource).isNotNull();
			return MAPPER.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
		}
		catch (Exception ex) {
			throw new AssertionError("could not read " + resource, ex);
		}
	}

	static Verdict golden(String resource) {
		try {
			return MAPPER.treeToValue(readTree(resource), Verdict.class);
		}
		catch (Exception ex) {
			throw new AssertionError("could not read " + resource + " as a Verdict", ex);
		}
	}

	/** The stored projection of a live verdict: the same map a reader parses from the wire. */
	static Map<String, Object> asMap(Verdict verdict) {
		return MAPPER.convertValue(verdict, MAP);
	}

	/** A deep, mutable copy so a test can damage a fixture without touching the shared one. */
	@SuppressWarnings("unchecked")
	static Map<String, Object> mutableCopy(Map<String, Object> map) {
		return (Map<String, Object>) copy(map);
	}

	private static Object copy(Object value) {
		if (value instanceof Map<?, ?> map) {
			Map<String, Object> out = new LinkedHashMap<>();
			map.forEach((key, entry) -> out.put(String.valueOf(key), copy(entry)));
			return out;
		}
		if (value instanceof List<?> list) {
			return new java.util.ArrayList<>(list.stream().map(Fixtures::copy).toList());
		}
		return value;
	}

	@SuppressWarnings("unchecked")
	static Map<String, Object> at(Map<String, Object> map, String... keys) {
		Map<String, Object> current = map;
		for (String key : keys) {
			current = (Map<String, Object>) current.get(key);
		}
		return current;
	}

	@SuppressWarnings("unchecked")
	static List<Object> listAt(Map<String, Object> map, String key) {
		return (List<Object>) map.get(key);
	}

	static Optional<Defect> defect(List<Defect> defects, String path, String field) {
		return defects.stream().filter(d -> d.path().equals(path) && d.field().equals(field)).findFirst();
	}

	static void assertDefect(List<Defect> defects, String path, String field, DefectKind kind) {
		assertThat(defect(defects, path, field)).as("a defect at %s / %s", path, field)
			.isPresent()
			.get()
			.extracting(Defect::kind)
			.isEqualTo(kind);
	}

	// ==================== Live juries ====================

	static Jury passingTier(String name, String reasoning) {
		return SimpleJury.builder()
			.judge(Judges.named(context -> Judgment.pass(reasoning), name))
			.votingStrategy(new ConsensusStrategy())
			.build();
	}

	/** An opaque jury that returns a fixed verdict and declares no capability. */
	static Jury returning(Verdict verdict) {
		return new Jury() {
			@Override
			public List<Judge> getJudges() {
				return List.of();
			}

			@Override
			public VotingStrategy getVotingStrategy() {
				return new ConsensusStrategy();
			}

			@Override
			public Verdict vote(JudgmentContext context) {
				return verdict;
			}
		};
	}

	static Jury throwing(RuntimeException failure) {
		return new Jury() {
			@Override
			public List<Judge> getJudges() {
				return List.of();
			}

			@Override
			public VotingStrategy getVotingStrategy() {
				return new ConsensusStrategy();
			}

			@Override
			public Verdict vote(JudgmentContext context) {
				throw failure;
			}
		};
	}

	/** A leaf jury whose reduction throws, so the tier returns an undecided verdict. */
	static Jury undecidedTier(Judgment... judgments) {
		SimpleJury.Builder builder = SimpleJury.builder().votingStrategy(new VotingStrategy() {
			@Override
			public Judgment aggregate(List<Judgment> input, Map<String, Double> weights) {
				throw new IllegalStateException("the reduction broke");
			}

			@Override
			public String getName() {
				return "broken";
			}
		});
		for (int index = 0; index < judgments.length; index++) {
			Judgment judgment = judgments[index];
			builder.judge(Judges.named(context -> judgment, "judge-" + (index + 1)));
		}
		return builder.build();
	}

	/** An opaque tier that excludes over real individuals while declaring no capability. */
	static Jury opaqueExcludingTier(Judgment... individuals) {
		Map<String, Judgment> byName = new LinkedHashMap<>();
		for (int index = 0; index < individuals.length; index++) {
			byName.put("judge-" + (index + 1), individuals[index]);
		}
		return returning(Verdict.of(Judgment.notApplicable(EXCLUSION), byName));
	}

	/** A judge that declares it may exclude, so a jury built on it is capable. */
	record Conditional(String name, Judgment result) implements JudgeWithMetadata {

		@Override
		public Judgment judge(JudgmentContext context) {
			return this.result;
		}

		@Override
		public JudgeMetadata metadata() {
			return new JudgeMetadata(this.name, "conditional", JudgeType.DETERMINISTIC, EXCLUSION);
		}

	}

}
