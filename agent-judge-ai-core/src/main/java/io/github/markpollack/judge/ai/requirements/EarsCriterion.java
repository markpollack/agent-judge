package io.github.markpollack.judge.ai.requirements;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One acceptance criterion, read from the specification the code was built from.
 *
 * <p>The judge does not invent these and does not get to choose them. Somebody wrote them down,
 * numbered, before any code existed, and this reads them verbatim.
 *
 * <p>The identifier is what makes an answer traceable back to the thing that was asked, and what
 * lets the roster notice a missing answer. A specification with 52 criteria has 52; a judge that
 * answers five of them has sampled it, not evaluated it.
 *
 * @param id the specification's own identifier, such as {@code UC6-AC41}
 * @param title the criterion's heading text
 * @param requirement the criterion sentence, verbatim from the specification
 * @author Mark Pollack
 * @since 0.16.0
 */
public record EarsCriterion(String id, String title, String requirement) {

	/** Flow logging at INFO: what was parsed, what was answered, what bound the verdict. */
	private static final Logger logger = LoggerFactory.getLogger(EarsCriterion.class);

	/** {@code ### UC6-AC5: Permit adjacent future appointments} */
	private static final Pattern HEADING = Pattern.compile("^### (UC\\d+-AC\\d+): (.+)$");

	/**
	 * Read every criterion out of a use case's {@code criteria.md}.
	 * @param criteriaFile the acceptance-criteria document to read
	 * @return every criterion in the document, in the document's order
	 * @throws java.io.UncheckedIOException if the document cannot be read
	 */
	public static List<EarsCriterion> from(Path criteriaFile) {
		List<EarsCriterion> criteria = new ArrayList<>();
		String id = null;
		String title = null;

		for (String line : read(criteriaFile).lines().toList()) {
			Matcher heading = HEADING.matcher(line.strip());
			if (heading.matches()) {
				id = heading.group(1);
				title = heading.group(2).strip();
				continue;
			}
			if (id == null) {
				continue;
			}
			String text = line.strip();
			if (text.isEmpty() || text.startsWith("**Covers:**") || text.startsWith("#")) {
				continue;
			}
			criteria.add(new EarsCriterion(id, title, text));
			id = null;
			title = null;
		}
		// The denominator comes from the parser, not from the model. Logged before any
		// answer exists, because that is what makes the roster guard meaningful.
		logger.info("{} criteria parsed from {}", criteria.size(), criteriaFile.getFileName());

		return List.copyOf(criteria);
	}

	/**
	 * Only the criteria whose identifiers were named, in the document's order.
	 * @param all the criteria read from the document
	 * @param ids the identifiers to select, such as {@code UC6-AC41}
	 * @return the named criteria, in the document's order rather than the caller's
	 * @throws IllegalArgumentException if any named identifier is not in the document, because
	 * silently returning fewer would evaluate a roster nobody wrote
	 */
	public static List<EarsCriterion> select(List<EarsCriterion> all, String... ids) {
		List<String> wanted = List.of(ids);
		List<EarsCriterion> selected = all.stream().filter(c -> wanted.contains(c.id())).toList();
		if (selected.size() != wanted.size()) {
			throw new IllegalArgumentException("asked for " + wanted.size()
				+ " criteria and found " + selected.size() + " in the document");
		}
		return selected;
	}

	/**
	 * The criterion as the judge is asked to assess it.
	 * @return the identifier and the requirement sentence
	 */
	public String asPrompt() {
		return id + ": " + requirement;
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
