/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.requirement;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import io.github.markpollack.judge.result.ArtifactRef;
import io.github.markpollack.judge.result.PolicyBinding;

/**
 * A versioned requirement retaining its native specification and exact source identity.
 * The caller must supply a stable specification snapshot for the entire evaluation and
 * retention lifetime. This envelope does not deep-copy arbitrary S or authenticate a
 * source/specification relationship. A display sentence is not its semantic identity.
 * An optional policy binding is an explicit application association, never a consequence
 * inferred from the native format or obligation keyword.
 * @param <S> native specification type
 * @param id stable application requirement identity
 * @param revision semantic revision
 * @param text display description; provider rendering belongs to the evaluator
 * @param specification complete stable native specification snapshot
 * @param source source artifact and native/document-local identity
 * @param acceptancePolicy application association, or null
 */
public record Requirement<S>(String id, String revision, String text, S specification,
        RequirementSource source, @Nullable PolicyBinding acceptancePolicy) {
    /** Validate required values without normalizing them. */
    public Requirement {
        requireText(id); requireText(revision); requireText(text);
        Objects.requireNonNull(specification, "specification");
        Objects.requireNonNull(source, "source");
    }
    /**
     * Create a native requirement without an application policy association.
     * @param id stable identity
     * @param revision semantic revision
     * @param text display description
     * @param specification stable native snapshot
     * @param source exact source provenance
     */
    public Requirement(String id, String revision, String text, S specification, RequirementSource source) {
        this(id, revision, text, specification, source, null);
    }
    /**
     * Create a plain-text specification with content-addressed source provenance.
     * @param id stable identity
     * @param revision semantic revision
     * @param text exact specification
     * @return text requirement
     */
    public static Requirement<String> text(String id, String revision, String text) {
        requireText(text);
        return new Requirement<>(id, revision, text, text,
            new RequirementSource(ArtifactRef.ofBytes("requirement", text.getBytes(StandardCharsets.UTF_8), null), null));
    }
    /**
     * Associate application consequence without changing native/source facts.
     * @param policy application-owned association
     * @return new envelope with ASSOCIATED policy provenance
     */
    public Requirement<S> under(PolicyBinding policy) {
        return new Requirement<>(id, revision, text, specification, source, Objects.requireNonNull(policy));
    }
    /**
     * Validate a nonblank Unicode scalar string without normalization.
     * @param text value to validate
     */
    public static void requireText(String text) {
        if (Objects.requireNonNull(text).isBlank()) throw new IllegalArgumentException("Nonblank requirement value required");
        for (int i = 0; i < text.length(); i++) {
            char value = text.charAt(i);
            if (Character.isHighSurrogate(value)) {
                if (++i == text.length() || !Character.isLowSurrogate(text.charAt(i)))
                    throw new IllegalArgumentException("Unpaired surrogate in requirement");
            } else if (Character.isLowSurrogate(value)) throw new IllegalArgumentException("Unpaired surrogate in requirement");
        }
    }
}
