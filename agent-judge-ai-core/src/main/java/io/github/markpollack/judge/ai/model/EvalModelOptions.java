package io.github.markpollack.judge.ai.model;

import java.time.Duration;

/**
 * Options for a judge model invocation.
 *
 * <p>All fields are nullable — {@code null} means "use the model's default".
 * Adapter implementations map non-null values to framework-specific options.
 *
 * @param model the model identifier (e.g., "gpt-4o", "claude-sonnet-4-20250514")
 * @param temperature sampling temperature
 * @param maxTokens maximum tokens in the response
 * @param timeout request timeout
 * @param responseFormat response format hint (e.g., "json")
 * @author Mark Pollack
 * @since 0.10.0
 */
public record EvalModelOptions(String model, Double temperature, Integer maxTokens, Duration timeout,
		String responseFormat) {

	/**
	 * Create options that defer every setting to the backend.
	 * @return empty/default options
	 */
	public static EvalModelOptions defaults() {
		return new EvalModelOptions(null, null, null, null, null);
	}

 /** Snapshot the explicitly supplied options; unknown fields remain absent.
  * @return immutable portable options */
 public java.util.Map<String,Object> toPortable() {
  var values=new java.util.LinkedHashMap<String,Object>();
  if(model!=null) values.put("model",model);
  if(temperature!=null) values.put("temperature",temperature);
  if(maxTokens!=null) values.put("maxTokens",maxTokens);
  if(timeout!=null) values.put("timeout",timeout.toString());
  if(responseFormat!=null) values.put("responseFormat",responseFormat);
  return io.github.markpollack.judge.portable.PortableValues.copy(values,"request.options");
 }
 /** Refuse malformed options before backend execution. */
 public EvalModelOptions {
  if(temperature!=null && !Double.isFinite(temperature)) throw new IllegalArgumentException("Temperature must be finite");
  if(maxTokens!=null && maxTokens<=0) throw new IllegalArgumentException("maxTokens must be positive");
  if(timeout!=null && (timeout.isNegative() || timeout.isZero())) throw new IllegalArgumentException("Timeout must be positive");
 }

}
