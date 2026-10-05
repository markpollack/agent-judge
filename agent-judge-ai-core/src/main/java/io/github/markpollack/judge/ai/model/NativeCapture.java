/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.ai.model;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * Captures provider data independently of answer decoding. Caller implementations can
 * retain protected artifacts. The default is a bounded JSON snapshot of SDK fields, not a
 * claim to possess original HTTP bytes.
 *
 * @param <T> native response type
 */
@FunctionalInterface
public interface NativeCapture<T> {

	/**
	 * Captures the response, including unsuccessful executions.
	 * @param response original native result
	 * @return portable snapshot
	 */
	NativeSnapshot capture(T response);

	/**
	 * Bounded SDK JSON capture, retaining nulls/numbers inside the exact JSON string.
	 * @param <T> response type
	 * @param maximumBytes positive UTF-8 bound
	 * @return native capture
	 */
	static <T> NativeCapture<T> json(int maximumBytes) {
		if (maximumBytes < 1)
			throw new IllegalArgumentException("Positive native capture bound required");
		var module = new SimpleModule();
		module.addSerializer(java.time.Duration.class, ToStringSerializer.instance);
		module.addSerializer(java.time.Instant.class, ToStringSerializer.instance);
		var mapper = JsonMapper.builder().addModule(module).build();
		return response -> {
			try {
				byte[] json = mapper.writeValueAsBytes(response);
				if (json.length > maximumBytes)
					throw new io.github.markpollack.judge.portable.PreservationLimitException(
							"Native SDK snapshot exceeds capture bound", response);
				return new NativeSnapshot(
						Map.of("nativeResponseJson", new String(json, java.nio.charset.StandardCharsets.UTF_8)),
						List.of());
			}
			catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
				io.github.markpollack.judge.portable.PreservationLimitException.propagate(failure);
				throw new IllegalArgumentException("Native SDK response cannot be captured", failure);
			}
		};
	}

}
