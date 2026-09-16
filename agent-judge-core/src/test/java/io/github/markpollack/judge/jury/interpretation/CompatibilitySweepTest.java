/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jury.interpretation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A16: the compatibility sweep over every stored result in the fleet.
 *
 * <p>Enabled by {@code -Daj26.sweep=true}. It enumerates every {@code *.json} under the roots
 * ({@code -Daj26.sweep.roots}, comma-separated; default {@code ~/tuvium/projects,~/projects}),
 * skipping {@code .git}, {@code target}, {@code node_modules}, {@code .m2} and {@code build},
 * parses each, and classifies by shape rather than by path: an item is any object with a
 * {@code verdict} member that is not itself inside a verdict tree; a verdict node is any object
 * with {@code aggregated}. Every item whose verdict is an object goes through
 * {@link Verdicts#interpret(Map)}; an exception is a failure of the sweep. The report is written
 * to {@code -Daj26.sweep.report} (default {@code target/aj26-sweep-report.md}).
 */
@DisplayName("The compatibility sweep")
@EnabledIfSystemProperty(named = "aj26.sweep", matches = "true")
class CompatibilitySweepTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final Set<String> SKIPPED = Set.of(".git", "target", "node_modules", ".m2", "build");

	/** Everything the sweep counts. */
	private static final class Tally {

		int files;

		int parsed;

		final List<String> unparseable = new ArrayList<>();

		int resultFiles;

		int filesWithVerdicts;

		int items;

		int rootVerdicts;

		int verdictNodes;

		final Map<String, Integer> rootShapes = new TreeMap<>();

		final Map<String, Integer> nodeShapes = new TreeMap<>();

		final Map<String, Integer> readings = new TreeMap<>();

		final Map<String, Integer> support = new TreeMap<>();

		final Map<String, Integer> defectKinds = new TreeMap<>();

		final Map<String, Integer> sourceVersions = new TreeMap<>();

		final Map<String, Integer> errorRootedSupport = new TreeMap<>();

		final Map<String, int[]> perProject = new TreeMap<>();

		final List<String> exceptions = new ArrayList<>();

		final List<String> flatRootExamples = new ArrayList<>();

		final List<String> contradicted = new ArrayList<>();

		/** Items whose verdict is an object that carries {@code aggregated}: the verdicts proper. */
		int rootsWithAggregated;

		final List<String> rootsWithoutAggregated = new ArrayList<>();

		/** Verdict nodes reached outside any item, such as a bare verdict fixture file. */
		int nodesOutsideItems;

		/** Files under an {@code experiments/runs} directory that hold at least one verdict proper. */
		int runsFilesWithVerdicts;

		/** Files under an {@code experiments/runs} directory, whatever they hold. */
		int runsFiles;

		/** Items under a result file's {@code items} array whose verdict is null. */
		int itemsWithNullVerdict;

	}

	@Test
	@DisplayName("every stored verdict in the fleet yields an interpretation without an exception")
	void everyStoredVerdictInterprets() throws IOException {
		String home = System.getProperty("user.home");
		String roots = System.getProperty("aj26.sweep.roots", home + "/tuvium/projects," + home + "/projects");
		Tally tally = new Tally();
		for (String root : roots.split(",")) {
			Path start = Path.of(root.trim());
			if (!Files.isDirectory(start)) {
				continue;
			}
			Files.walkFileTree(start, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
					return SKIPPED.contains(dir.getFileName().toString()) ? FileVisitResult.SKIP_SUBTREE
							: FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
					if (attrs.isRegularFile() && file.getFileName().toString().endsWith(".json")) {
						sweepFile(file, start, tally);
					}
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(Path file, IOException exc) {
					return FileVisitResult.CONTINUE;
				}
			});
		}
		String report = report(roots, tally);
		Path out = Path.of(System.getProperty("aj26.sweep.report", "target/aj26-sweep-report.md"));
		Files.createDirectories(out.toAbsolutePath().getParent());
		Files.writeString(out, report, StandardCharsets.UTF_8);
		System.out.println(report);

		assertThat(tally.exceptions).as("interpret(Map) threw on a stored verdict").isEmpty();
		assertThat(tally.rootVerdicts).as("the sweep found stored verdicts").isPositive();
	}

	private static void sweepFile(Path file, Path root, Tally tally) {
		tally.files++;
		JsonNode tree;
		try {
			tree = MAPPER.readTree(file.toFile());
		}
		catch (Exception ex) {
			tally.unparseable.add(file + " (" + ex.getClass().getSimpleName() + ")");
			return;
		}
		if (tree == null) {
			tally.unparseable.add(file + " (empty)");
			return;
		}
		tally.parsed++;
		String project = root.relativize(file).getName(0).toString();
		int[] counts = tally.perProject.computeIfAbsent(project, key -> new int[4]);
		counts[0]++;
		boolean underRuns = file.toString().contains("/experiments/runs/");
		if (underRuns) {
			tally.runsFiles++;
		}
		if (tree.isObject() && tree.has("items") && tree.get("items").isArray()) {
			tally.resultFiles++;
			counts[1]++;
		}
		int before = tally.rootVerdicts;
		int beforeProper = tally.rootsWithAggregated;
		visit(tree, file, tally, counts, "$");
		if (tally.rootVerdicts > before) {
			tally.filesWithVerdicts++;
		}
		if (underRuns && tally.rootsWithAggregated > beforeProper) {
			tally.runsFilesWithVerdicts++;
		}
	}

	/** Walk the tree outside any verdict; inside a verdict, count nodes but do not look for items. */
	private static void visit(JsonNode node, Path file, Tally tally, int[] counts, String where) {
		if (node.isObject()) {
			if (node.has("verdict")) {
				tally.items++;
				counts[2]++;
				JsonNode verdict = node.get("verdict");
				if (verdict.isNull()) {
					tally.itemsWithNullVerdict++;
				}
				if (verdict.isObject()) {
					tally.rootVerdicts++;
					counts[3]++;
					if (verdict.has("aggregated")) {
						tally.rootsWithAggregated++;
					}
					else if (tally.rootsWithoutAggregated.size() < 5) {
						tally.rootsWithoutAggregated.add(file + " :: " + where + " :: keys " + keysOf(verdict));
					}
					countNodes(verdict, tally);
					interpret(verdict, node, file, tally);
				}
				node.fields().forEachRemaining(entry -> {
					if (!entry.getKey().equals("verdict")) {
						visit(entry.getValue(), file, tally, counts, where + "." + entry.getKey());
					}
				});
				return;
			}
			if (node.has("aggregated")) {
				// A verdict node reached without an item around it: count its tree once and stop.
				int before = tally.verdictNodes;
				countNodes(node, tally);
				tally.nodesOutsideItems += tally.verdictNodes - before;
				return;
			}
			node.fields().forEachRemaining(entry -> visit(entry.getValue(), file, tally, counts,
					where + "." + entry.getKey()));
		}
		else if (node.isArray()) {
			for (int index = 0; index < node.size(); index++) {
				visit(node.get(index), file, tally, counts, where + "[" + index + "]");
			}
		}
	}

	private static void countNodes(JsonNode verdict, Set<JsonNode> seen, Tally tally) {
		if (verdict.isObject() && verdict.has("aggregated") && seen.add(verdict)) {
			tally.verdictNodes++;
			bump(tally.nodeShapes, shape(verdict));
		}
		if (verdict.isObject()) {
			verdict.fields().forEachRemaining(entry -> countNodes(entry.getValue(), seen, tally));
		}
		else if (verdict.isArray()) {
			verdict.forEach(child -> countNodes(child, seen, tally));
		}
	}

	private static void countNodes(JsonNode verdict, Tally tally) {
		countNodes(verdict, new java.util.HashSet<>(), tally);
	}

	private static String shape(JsonNode verdict) {
		if (verdict.has("compositeAttempts")) {
			return "compositeAttempts";
		}
		if (verdict.has("subVerdicts")) {
			return "subVerdicts";
		}
		return "flat";
	}

	private static void interpret(JsonNode verdict, JsonNode item, Path file, Tally tally) {
		String shape = shape(verdict);
		bump(tally.rootShapes, shape);
		String itemName = item.has("itemSlug") ? item.get("itemSlug").asText()
				: item.has("itemId") ? item.get("itemId").asText() : "<unnamed item>";
		if (shape.equals("flat") && verdict.has("aggregated") && tally.flatRootExamples.size() < 5) {
			tally.flatRootExamples.add(file + " :: " + itemName);
		}
		Map<String, Object> stored = MAPPER.convertValue(verdict, Fixtures.MAP);
		Interpretation interpretation;
		try {
			interpretation = Verdicts.interpret(stored);
			MAPPER.writeValueAsString(interpretation);
			if (!Summaries.of(interpretation).equals(interpretation.summary())) {
				throw new IllegalStateException("the summary is not deterministic");
			}
		}
		catch (Throwable ex) {
			tally.exceptions.add(file + " :: " + itemName + " :: " + ex);
			return;
		}
		bump(tally.readings, String.valueOf(interpretation.reading()));
		bump(tally.support, interpretation.readingSupport().name());
		bump(tally.sourceVersions, "sourceVersion " + interpretation.sourceVersion());
		for (Defect defect : interpretation.defects()) {
			bump(tally.defectKinds, defect.kind().name());
		}
		if ("error".equals(interpretation.root().status())) {
			bump(tally.errorRootedSupport, interpretation.readingSupport().name());
		}
		if (interpretation.readingSupport() == ReadingSupport.CONTRADICTED && tally.contradicted.size() < 20) {
			tally.contradicted.add(file + " :: " + itemName + " :: " + interpretation.defects()
				.stream()
				.filter(defect -> defect.kind() == DefectKind.INCONSISTENT)
				.map(Defect::note)
				.toList());
		}
	}

	private static void bump(Map<String, Integer> counts, String key) {
		counts.merge(key, 1, Integer::sum);
	}

	private static List<String> keysOf(JsonNode node) {
		List<String> keys = new ArrayList<>();
		node.fieldNames().forEachRemaining(keys::add);
		return keys;
	}

	private static String report(String roots, Tally tally) {
		StringBuilder out = new StringBuilder();
		out.append("# AJ-26 compatibility sweep\n\n");
		out.append("Roots: `").append(roots).append("`; skipped directories: ").append(SKIPPED).append("\n\n");
		out.append("| Count | Value |\n|---|---|\n");
		row(out, "JSON files found", tally.files);
		row(out, "JSON files parsed", tally.parsed);
		row(out, "JSON files not parseable", tally.unparseable.size());
		row(out, "result files (top-level object with an `items` array)", tally.resultFiles);
		row(out, "files holding at least one verdict object", tally.filesWithVerdicts);
		row(out, "items (objects with a `verdict` member, outside any verdict tree)", tally.items);
		row(out, "items whose verdict is an object (root verdicts interpreted)", tally.rootVerdicts);
		row(out, "of those, verdicts proper (the object carries `aggregated`)", tally.rootsWithAggregated);
		row(out, "items whose verdict is null", tally.itemsWithNullVerdict);
		row(out, "verdict nodes (objects with `aggregated`, roots included)", tally.verdictNodes);
		row(out, "of those, reached outside any item (bare verdict files)", tally.nodesOutsideItems);
		row(out, "files under an experiments/runs directory", tally.runsFiles);
		row(out, "of those, holding at least one verdict proper", tally.runsFilesWithVerdicts);
		row(out, "interpret(Map) exceptions", tally.exceptions.size());
		out.append("\n## Root verdicts by shape\n\n");
		table(out, tally.rootShapes);
		out.append("\n## Verdict nodes by shape\n\n");
		table(out, tally.nodeShapes);
		out.append("\n## Readings\n\n");
		table(out, tally.readings);
		out.append("\n## Reading support\n\n");
		table(out, tally.support);
		out.append("\n## Reading support over error-rooted items\n\n");
		table(out, tally.errorRootedSupport);
		out.append("\n## Source versions\n\n");
		table(out, tally.sourceVersions);
		out.append("\n## Defects by kind\n\n");
		table(out, tally.defectKinds);
		out.append("\n## Per project (files parsed / result files / items / root verdicts)\n\n");
		out.append("| Project | Files | Result files | Items | Root verdicts |\n|---|---|---|---|---|\n");
		tally.perProject.forEach((project, counts) -> out.append("| ")
			.append(project)
			.append(" | ")
			.append(counts[0])
			.append(" | ")
			.append(counts[1])
			.append(" | ")
			.append(counts[2])
			.append(" | ")
			.append(counts[3])
			.append(" |\n"));
		out.append("\n## Flat roots that are verdicts proper, first five\n\n");
		if (tally.flatRootExamples.isEmpty()) {
			out.append("none\n");
		}
		tally.flatRootExamples.forEach(example -> out.append("- ").append(example).append('\n'));
		out.append("\n## Objects with a `verdict` member that carries no `aggregated`, first five\n\n");
		if (tally.rootsWithoutAggregated.isEmpty()) {
			out.append("none\n");
		}
		tally.rootsWithoutAggregated.forEach(example -> out.append("- ").append(example).append('\n'));
		out.append("\n## Contradicted readings, first twenty\n\n");
		if (tally.contradicted.isEmpty()) {
			out.append("none\n");
		}
		tally.contradicted.forEach(example -> out.append("- ").append(example).append('\n'));
		out.append("\n## Exceptions\n\n");
		if (tally.exceptions.isEmpty()) {
			out.append("none\n");
		}
		tally.exceptions.forEach(example -> out.append("- ").append(example).append('\n'));
		out.append("\n## Files not parseable as JSON, first twenty\n\n");
		if (tally.unparseable.isEmpty()) {
			out.append("none\n");
		}
		tally.unparseable.stream().limit(20).forEach(example -> out.append("- ").append(example).append('\n'));
		return out.toString();
	}

	private static void row(StringBuilder out, String label, int value) {
		out.append("| ").append(label).append(" | ").append(value).append(" |\n");
	}

	private static void table(StringBuilder out, Map<String, Integer> counts) {
		out.append("| Value | Count |\n|---|---|\n");
		new LinkedHashMap<>(counts).forEach((key, value) -> out.append("| ").append(key).append(" | ").append(value)
			.append(" |\n"));
	}

}
