/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.ai.requirements;

import io.github.markpollack.judge.requirement.*;

/**
 * An immutable native Ears requirement. All identity has one owner.
 *
 * @param id stable outer requirement identity
 * @param revision immutable semantic revision
 * @param text requirement text
 * @param specification pure native specification, without outer identity
 * @param source exact source snapshot and document-local identity
 */
public record EarsRequirement(String id, String revision, String text, EarsSpecification specification,
		RequirementSource source) implements Requirement<EarsSpecification> {
	/** Validates all common components and the protocol identity. */
	public EarsRequirement {
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
	 * @param title display title
	 * @param requirement exact native requirement text
	 * @param applicability declared condition, or null
	 * @return immutable native requirement
	 */
	public static EarsRequirement of(String id, String revision, String title, String requirement,
			String applicability) {
		String source = new EarsSpecification(title, requirement, applicability).asPrompt();
		return new EarsRequirement(id, revision, source, new EarsSpecification(title, requirement, applicability),
				new RequirementSource(io.github.markpollack.judge.provenance.ArtifactRef.ofBytes("crafted-ears",
						source.getBytes(java.nio.charset.StandardCharsets.UTF_8), null), id));
	}

	/**
	 * Reads pure native requirements from an explicitly selected source document.
	 * @param path source document, never loaded during judge()/vote()
	 * @param revision caller-selected immutable revision
	 * @return immutable declared roster
	 */
	public static java.util.List<EarsRequirement> from(java.nio.file.Path path, String revision) {
		Requirement.requireText(revision);
		try {
			byte[] bytes = java.nio.file.Files.readAllBytes(path);
			var artifact = io.github.markpollack.judge.provenance.ArtifactRef.ofBytes(path.toString(), bytes, null);
			return EarsCriterion
				.parseSnapshot(new String(bytes, java.nio.charset.StandardCharsets.UTF_8),
						path.getFileName().toString())
				.stream()
				.map(item -> new EarsRequirement(item.id(), revision, item.requirement(),
						new EarsSpecification(item.title(), item.requirement(), item.applicability()),
						new RequirementSource(artifact, item.id())))
				.toList();
		}
		catch (java.io.IOException failure) {
			throw new java.io.UncheckedIOException(failure);
		}
	}
}
