package io.github.markpollack.judge.json;

/** Library-owned JSON mechanics. Engines must refuse duplicate keys, coercions and trailing input.
 * Domain storage uses a separately configured, versioned codec, never unchecked POJO binding. */
public interface EvalJsonMapper {
 /** Encode a value.
  * @param value value
  * @return JSON
  */
 String write(Object value);
 /** Decode an explicitly selected type.
  * @param json JSON
  * @param type target
  * @param <T> target type
  * @return value
  */
 <T> T read(String json, Class<T> type);
}
