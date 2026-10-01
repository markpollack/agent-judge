/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.ai.requirements;

import io.github.markpollack.judge.requirement.*;

/**
 * An immutable native Rfc2119 requirement. All identity has one owner.
 *
 * @param id stable outer requirement identity
 * @param revision immutable semantic revision
 * @param text requirement text
 * @param specification pure native specification, without outer identity
 * @param source exact source snapshot and document-local identity
 */
public record Rfc2119Requirement(String id, String revision, String text, Rfc2119Specification specification,
		RequirementSource source) implements Requirement<Rfc2119Specification> {
	/** Validates all common components and the protocol identity. */
	public Rfc2119Requirement {
		Requirement.requireText(id);
		Requirement.requireText(revision);
		Requirement.requireText(text);
		java.util.Objects.requireNonNull(specification);
		java.util.Objects.requireNonNull(source);
		if (!id.equals(id.strip()) || id.contains(":") || id.contains("\n") || id.contains("\r"))
			throw new IllegalArgumentException("Invalid protocol identity");
	}

	/**
	 * Creates a crafted native requirement with a content-addressed source snapshot.
	 * @param id stable requirement identity
	 * @param revision semantic revision
	 * @param keyword normative RFC2119 keyword
	 * @param requirement exact native requirement text
	 * @param reason normative rationale
	 * @param applicability declared condition, or null
	 * @return immutable native requirement
	 */
	public static Rfc2119Requirement of(String id, String revision, String keyword, String requirement, String reason,
			String applicability) {
		String source = new Rfc2119Constraint(id, keyword, requirement, reason, applicability).asPrompt();
		return new Rfc2119Requirement(id, revision, source,
				new Rfc2119Specification(keyword, requirement, reason, applicability),
				new RequirementSource(io.github.markpollack.judge.provenance.ArtifactRef.ofBytes("crafted-rfc2119",
						source.getBytes(java.nio.charset.StandardCharsets.UTF_8), null), id));
	}

	/**
	 * Reads pure native requirements from an explicitly selected source document.
	 * @param path source document, never loaded during judge()/vote()
	 * @param revision caller-selected immutable revision
	 * @return immutable declared roster
	 */
	public static java.util.List<Rfc2119Requirement> from(java.nio.file.Path path, String revision) {
		Requirement.requireText(revision);
		try {
			byte[] bytes = java.nio.file.Files.readAllBytes(path);
			var artifact = io.github.markpollack.judge.provenance.ArtifactRef.ofBytes(path.toString(), bytes, null);
			return Rfc2119Constraint
				.parseSnapshot(new String(bytes, java.nio.charset.StandardCharsets.UTF_8),
						path.getFileName().toString())
				.stream()
				.map(item -> new Rfc2119Requirement(item.id(), revision, item.asPrompt(),
						new Rfc2119Specification(item.keyword(), item.title(), item.reason(), item.applicability()),
						new RequirementSource(artifact, item.id())))
				.toList();
		}
		catch (java.io.IOException failure) {
			throw new java.io.UncheckedIOException(failure);
		}
	}
}
