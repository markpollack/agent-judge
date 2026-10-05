/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.ai.requirements;

import java.util.Map;
import io.github.markpollack.judge.serialization.*;

/**
 * Explicit stable native Requirement reconstruction, with no execution or class loading.
 */
public final class NativeRequirementCodecs {

	private NativeRequirementCodecs() {
	}

	/**
	 * Registers text/AllOf plus the pure RFC2119/EARS implementations.
	 * @return strict V6 retained-result codec
	 */
	public static VerdictCodec codec() {
		return VerdictCodec.withSpecifications(specifications());
	}

	/**
	 * Supplies registrations for applications combining additional native types.
	 * @return immutable native registrations
	 */
	public static Map<String, SpecificationCodec<?>> specifications() {
		return Map.of("rfc2119:v1", new SpecificationCodec<>(Rfc2119Specification.class, Rfc2119Requirement::new),
				"ears:v1", new SpecificationCodec<>(EarsSpecification.class, EarsRequirement::new));
	}

}
