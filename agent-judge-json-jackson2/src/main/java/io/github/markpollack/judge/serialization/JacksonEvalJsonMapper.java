package io.github.markpollack.judge.serialization;
import java.util.Objects;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.markpollack.judge.json.EvalJsonMapper;
/** Jackson 2 mechanics over an explicitly configured mapper. */
public final class JacksonEvalJsonMapper implements EvalJsonMapper {
 private final ObjectMapper mapper;
 /** Snapshot configuration.
  * @param mapper explicitly configured engine
  */
 public JacksonEvalJsonMapper(ObjectMapper mapper) { this.mapper=Objects.requireNonNull(mapper).copy(); }
 @Override public String write(Object value) { try { return mapper.writeValueAsString(value); } catch(JsonProcessingException e) {throw new IllegalArgumentException("Cannot write JSON: "+e.getOriginalMessage(),e);} }
 @Override public <T> T read(String json,Class<T> type) {try {return Objects.requireNonNull(mapper.readValue(json,type),"JSON value is null");} catch(JsonProcessingException e) {throw new IllegalArgumentException("Cannot read JSON: "+e.getOriginalMessage(),e);} }
}
