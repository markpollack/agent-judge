/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge;

/**
 * Marker interface for judges that provide metadata.
 *
 * <p>
 * This interface follows Spring Framework's pattern of marker interfaces (like
 * {@code Ordered}, {@code SmartInitializingSingleton}) to enable semantic pattern
 * matching for judges with metadata. This allows infrastructure code, monitoring tools,
 * documentation generators, and dashboards to discover and access judge metadata in a
 * type-safe manner.
 * </p>
 *
 * <p>
 * <strong>Design Pattern:</strong> Marker interface for optional capability. Judges are
 * not required to have metadata - lambda judges work perfectly without it. However, when
 * metadata is needed (for logging, monitoring, UI display), implementing this interface
 * provides a standard way to expose it.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 * Executable examples are maintained in the Agent Judge Tutorial: https://github.com/markpollack/agent-judge-tutorial.
 *
 * @author Mark Pollack
 * @since 0.1.0
 * @see Judge
 * @see NamedJudge
 * @see JudgeMetadata
 */
public interface JudgeWithMetadata extends Judge {

	/**
	 * Get metadata about this judge.
	 * @return the judge metadata
	 */
	JudgeMetadata metadata();

}
