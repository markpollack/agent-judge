/*
 * Copyright 2024 Spring AI Community
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.markpollack.judge.jury;

import java.util.Objects;

/**
 * Configuration for a single tier within a {@link CascadedJury}.
 *
 * @param name human-readable tier name for diagnostics (e.g., "deterministic")
 * @param jury the jury implementation for this tier
 * @param policy cascade control flow policy
 * @author Mark Pollack
 * @since 0.9.0
 */
public record TierConfig(String name, Jury jury, TierPolicy policy) {

	public TierConfig {
		Objects.requireNonNull(name, "name must not be null");
		Objects.requireNonNull(jury, "jury must not be null");
		Objects.requireNonNull(policy, "policy must not be null");
	}

}
