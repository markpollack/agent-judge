/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.ai.requirements;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * A linter for specification documents: every EARS criterion and RFC 2119 constraint in a
 * directory tree, checked with the same strict rules the loaders apply, so a problem is
 * found while the specification is written rather than when a judge runs.
 *
 * <p>
 * Every Markdown file below the directory is read by both readers. Each reader reports
 * only what it recognises (criteria headings such as {@code ### UC1-AC3: Title}, rule
 * headings such as {@code ### RULE-2}), so a README or design note contributes nothing.
 * Each problem names its file by its path relative to the directory, its requirement
 * identifier when there is one, and its line.
 * </p>
 *
 * <p>
 * In a specification repository, one test keeps the whole tree valid:
 * </p>
 *
 * <pre>{@code
 * @Test
 * void specificationIsValid() {
 *     RequirementDocuments.assertValid(Path.of("spec"));
 * }
 * }</pre>
 *
 * <p>
 * From the command line, {@code java -cp <agent-judge-ai-core and its dependencies>
 * io.github.markpollack.judge.ai.requirements.RequirementDocuments spec} prints the report
 * and exits with status 1 when there is any problem.
 * </p>
 *
 * @since 0.19.0
 */
public final class RequirementDocuments {

	private RequirementDocuments() {
	}

	/**
	 * What one lint of a directory found.
	 *
	 * @param directory the directory that was linted
	 * @param documents every Markdown file read, relative to the directory, in path order
	 * @param criteria how many EARS criteria loaded
	 * @param constraints how many RFC 2119 constraints loaded
	 * @param problems every problem, in file then document order
	 */
	public record Report(Path directory, List<Path> documents, int criteria, int constraints,
			List<RequirementDocumentProblem> problems) {

		/** Freezes the report. */
		public Report {
			documents = List.copyOf(documents);
			problems = List.copyOf(problems);
		}

		/**
		 * Whether a strict load of every document would succeed.
		 * @return true when there is no problem
		 */
		public boolean valid() {
			return problems.isEmpty();
		}

		/**
		 * The report as text: a summary line, then one line per problem.
		 * @return the readable report
		 */
		public String describe() {
			StringBuilder text = new StringBuilder("%d requirement document(s) in %s: %d EARS criteria, %d RFC 2119 constraints, %d problem(s)"
				.formatted(documents.size(), directory, criteria, constraints, problems.size()));
			for (RequirementDocumentProblem problem : problems) {
				text.append('\n').append(problem.describe());
			}
			return text.toString();
		}

	}

	/**
	 * Lint every Markdown document below a directory.
	 * @param directory the specification directory
	 * @return the report; {@link Report#valid()} is true when nothing is wrong
	 * @throws UncheckedIOException if the directory or a document cannot be read
	 */
	public static Report lint(Path directory) {
		List<Path> documents = markdown(directory);
		List<RequirementDocumentProblem> problems = new ArrayList<>();
		int criteria = 0;
		int constraints = 0;
		for (Path relative : documents) {
			String snapshot = read(directory.resolve(relative));
			String source = relative.toString().replace('\\', '/');
			var ears = EarsCriterion.parse(snapshot, source);
			var rules = Rfc2119Constraint.parse(snapshot, source);
			criteria += ears.items().size();
			constraints += rules.items().size();
			problems.addAll(ears.problems());
			problems.addAll(rules.problems());
		}
		return new Report(directory, documents, criteria, constraints, problems);
	}

	/**
	 * Fail with every problem when any document below the directory would be refused by a
	 * strict load. Meant for one test in a specification repository.
	 * @param directory the specification directory
	 * @throws AssertionError listing every problem, when there is any
	 */
	public static void assertValid(Path directory) {
		Report report = lint(directory);
		if (!report.valid()) {
			throw new AssertionError(report.describe());
		}
	}

	/**
	 * Lint each directory named on the command line, print each report, and exit with
	 * status 1 when any has a problem.
	 * @param args the specification directories; the current directory when none
	 */
	// A command-line tool's report is its output.
	@SuppressWarnings("SystemOut")
	public static void main(String[] args) {
		List<String> directories = args.length == 0 ? List.of(".") : List.of(args);
		boolean valid = true;
		for (String directory : directories) {
			Report report = lint(Path.of(directory));
			System.out.println(report.describe());
			valid &= report.valid();
		}
		if (!valid) {
			System.exit(1);
		}
	}

	private static List<Path> markdown(Path directory) {
		try (Stream<Path> files = Files.walk(directory)) {
			return files.filter(Files::isRegularFile)
				.filter(file -> file.getFileName().toString().endsWith(".md"))
				.map(directory::relativize)
				.sorted()
				.toList();
		}
		catch (IOException e) {
			throw new UncheckedIOException("Could not list " + directory, e);
		}
	}

	private static String read(Path path) {
		try {
			return Files.readString(path);
		}
		catch (IOException e) {
			throw new UncheckedIOException("Could not read " + path, e);
		}
	}

}
