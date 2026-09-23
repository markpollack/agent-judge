/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.conformance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Test-only expected migration of the frozen status-only 0.17 composite fixtures. */
public final class ModernFixtureExpectations {

	private ModernFixtureExpectations() {
	}

	public static JsonNode statusOnly(JsonNode source) {
		JsonNode copy = source.deepCopy();
		rewrite(copy);
		return copy;
	}

	private static void rewrite(JsonNode node) {
		if (node.isObject() && node.has("status") && node.has("checks") && node.has("metadata")) {
			ObjectNode object = (ObjectNode) node;
			if (object.has("score")) {
				throw new IllegalArgumentException("fixture is not status-only");
			}
			// The frozen N/A vocabulary had a label. It remains historical evidence in
			// the
			// original fixture, but the modern live N/A contract forbids any assessment.
			if (object.has("label")) {
				if (!object.path("status").asText().equals("not_applicable")) {
					throw new IllegalArgumentException("fixture contains a classification");
				}
				object.remove("label");
			}
			object.set("producerStatus", object.remove("status"));
		}
		node.elements().forEachRemaining(ModernFixtureExpectations::rewrite);
	}

}
