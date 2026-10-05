/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.policy;

import java.util.Map;
import io.github.markpollack.judge.portable.*;

/**
 * Optional caller-owned policy identity and complete portable configuration. The codec
 * carries inline values. References/digests inside configuration do not retain external
 * bytes; the caller owns custody/resolution of those referenced bytes.
 *
 * @param id caller policy identity
 * @param version explicit caller revision
 * @param configuration complete portable configuration
 */
public record PolicyAttribution(String id, String version, Map<String, Object> configuration) {
	/** Freeze the caller's actual attribution without execution. */
	public PolicyAttribution {
		ValueRequirements.text(id, "policy id");
		ValueRequirements.text(version, "policy version");
		configuration = PortableForm.ordered(configuration, "policy.configuration");
	}
}
