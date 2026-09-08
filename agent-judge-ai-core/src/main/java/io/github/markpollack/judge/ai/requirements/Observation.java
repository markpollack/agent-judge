package io.github.markpollack.judge.ai.requirements;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.markpollack.judge.result.Judgment;

/**
 * Something useful learned while establishing a judgment, which is not itself a judgment.
 *
 * <p>The distinction is the point:
 *
 * <pre>
 *   Judgment     did the implementation satisfy the requirement?
 *   Observation  what did we learn while establishing that?
 * </pre>
 *
 * <p>A requirement can be fully PASS and still produce one of these. When the judge established
 * that owner cancellation is correctly rejected at the exact start instant, it also noticed that no
 * existing test exercises that boundary. The requirement says the implementation must behave
 * correctly there; it does not say a test must exist. So the criterion passes, and the gap is worth
 * keeping.
 *
 * <p><b>An observation is non-binding.</b> It never enters the roster, never changes PASS to FAIL or
 * ABSTAIN, is not a new criterion, is not a warning status, and is not a score. It rides in
 * {@code Judgment.metadata()}, which participates in no rollup.
 *
 * <p>We must not fit the rubric after seeing the implementation. The roster comes from the
 * specification, and a missing test is not silently promoted into a requirement nobody wrote.
 *
 * @param requirementId the criterion this was noticed while establishing
 * @param message one externally verifiable sentence
 * @param locations file:line references extracted from the message, possibly empty
 *
 * @author Mark Pollack
 * @since 0.16.0
 */
public record Observation(String requirementId, String message, List<String> locations) {

	/** The metadata key observations ride under. */
	public static final String METADATA_KEY = "observations";

	/** Copies the locations, so an observation cannot be changed after it is made. */
	public Observation {
		locations = List.copyOf(locations);
	}

	/**
	 * Portable form for {@code Judgment.metadata()}.
	 *
	 * <p>The library requires metadata to be JSON-portable — strings, numbers, booleans, arrays and
	 * string-keyed objects — and rejects live Java objects at runtime. That is the right
	 * constraint: a result that cannot be serialised cannot be recorded, replayed or shipped
	 * anywhere. So the typed record is the working view and this is the stored one.
	 * @return the observation as ordinary JSON-shaped data
	 */
	public Map<String, Object> toMetadata() {
		Map<String, Object> portable = new LinkedHashMap<>();
		portable.put("requirementId", requirementId);
		portable.put("message", message);
		portable.put("locations", locations);
		return Map.copyOf(portable);
	}

	/**
	 * Read observations back off a judgment, typed. Absent or unexpected shapes yield none.
	 * @param judgment the judgment to read observations from
	 * @return the observations it carries, or none
	 */
	@SuppressWarnings("unchecked")
	public static List<Observation> of(Judgment judgment) {
		Object stored = judgment.metadata().get(METADATA_KEY);
		if (!(stored instanceof List<?> entries)) {
			return List.of();
		}
		List<Observation> found = new ArrayList<>();
		for (Object entry : entries) {
			if (entry instanceof Map<?, ?> map
					&& map.get("requirementId") instanceof String id
					&& map.get("message") instanceof String message) {
				Object locations = map.get("locations");
				found.add(new Observation(id, message,
					locations instanceof List<?> list ? (List<String>) list : List.of()));
			}
		}
		return List.copyOf(found);
	}
}
