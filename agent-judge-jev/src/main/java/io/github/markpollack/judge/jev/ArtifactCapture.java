/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jev;

import io.github.markpollack.judge.result.ArtifactRef;

/**
 * Caller-owned protected artifact storage. Implementations must be thread safe, bounded
 * in retention, and must not log input bytes. A call supplies bounded immutable copies;
 * storage failure makes evaluation ERROR. No credentials or request headers are passed
 * here.
 */
@FunctionalInterface
public interface ArtifactCapture {

	/**
	 * Retain exact bytes, returning their durable reference.
	 * @param kind artifact role, such as request, response or trace
	 * @param bytes exact bytes owned by the recipient
	 * @return reference whose digest matches these bytes
	 */
	ArtifactRef retain(String kind, byte[] bytes);

}
