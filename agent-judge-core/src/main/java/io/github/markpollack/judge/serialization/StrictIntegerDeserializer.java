/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.serialization;

import java.io.IOException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

/** Exact required JSON integers for protocol versions, populations and positions. */
public final class StrictIntegerDeserializer extends JsonDeserializer<Integer> {

	/** Default constructor for Jackson. */
	public StrictIntegerDeserializer() {
	}

	@Override
	public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
		if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT))
			throw new IOException("Required exact JSON integer");
		return parser.getIntValue();
	}

	@Override
	public Integer getNullValue(DeserializationContext context)
			throws com.fasterxml.jackson.databind.JsonMappingException {
		throw com.fasterxml.jackson.databind.JsonMappingException.from(context, "Required integer is absent or null");
	}

}
