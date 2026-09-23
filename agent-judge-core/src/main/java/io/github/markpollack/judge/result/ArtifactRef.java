/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.result;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.jspecify.annotations.Nullable;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Reference to retained exact bytes; selectors identify a part without changing the hash.
 *
 * @param id artifact identity
 * @param sha256 lowercase SHA-256 of exact retained bytes
 * @param selector optional selector within the artifact
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ArtifactRef(String id, String sha256, @Nullable String selector) {
	/** Validate and freeze this value. */
	public ArtifactRef {
		ValueRequirements.text(id, "id");
		ValueRequirements.digest(sha256);
		if (selector != null) {
			ValueRequirements.text(selector, "selector");
		}
	}

	/**
	 * Hash bytes without text decoding or JSON canonicalization.
	 * @param id artifact identity
	 * @param bytes exact retained bytes
	 * @param selector optional selector
	 * @return reference with exact-byte SHA-256
	 */
	public static ArtifactRef ofBytes(String id, byte[] bytes, @Nullable String selector) {
		try {
			return new ArtifactRef(id, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
					selector);
		}
		catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("Java requires SHA-256", impossible);
		}
	}
}
