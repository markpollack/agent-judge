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

/**
 * Determines how a tier's verdict maps to cascade control flow.
 *
 * @author Mark Pollack
 * @since 0.9.0
 */
public enum TierPolicy {

	/**
	 * If ANY judge in the tier fails, stop the cascade and REJECT. If all pass, escalate
	 * to the next tier for further evaluation. Use for deterministic fail-fast gates
	 * (Tier 1).
	 */
	REJECT_ON_ANY_FAIL,

	/**
	 * If ALL judges in the tier pass, stop the cascade and ACCEPT. If any judge fails or
	 * is uncertain (low confidence), escalate. Use for structural analysis tiers (Tier
	 * 2).
	 */
	ACCEPT_ON_ALL_PASS,

	/**
	 * Always produce a verdict — no escalation possible. Must be the last tier in the
	 * cascade. Use for LLM semantic evaluation (Tier 3).
	 */
	FINAL_TIER

}
