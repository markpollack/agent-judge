package io.github.markpollack.judge.ai.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Token quantities a judge model backend reported for one invocation.
 *
 * <p>
 * Every component is optional, because no provider reports every category, and absence is
 * expressed by {@code null} here and by an omitted key in the
 * {@linkplain #toPortableMap() portable projection}. A present quantity is non-negative
 * and within the interoperable integer domain, so it survives a JSON boundary as the
 * value it is; anything else is refused at construction.
 * </p>
 *
 * <h2>The categories are observations, not an arithmetic identity</h2>
 * <p>
 * These are the numbers a source reported, under that source's own accounting. A provider
 * may count reasoning tokens inside output tokens, or cache activity inside input tokens.
 * Do not add every component, and do not require {@link #reportedTotalTokens()} to equal a
 * locally derived sum unless the originating provider contract says so.
 * {@code reportedTotalTokens} is only ever a total the source supplied — Agent Judge never
 * derives one.
 * </p>
 *
 * <h2>No price</h2>
 * <p>
 * Usage records quantities, not money. A price depends on model, provider, currency, time,
 * and a pricing table, none of which are execution evidence and all of which change after
 * the judgment was made. A cost system derives price downstream by combining this token
 * vector with an explicitly versioned pricing source; a monetary value stored here would
 * be a guess frozen into a result.
 * </p>
 *
 * @param inputTokens tokens the source reports in its input/prompt category
 * @param outputTokens tokens the source reports in its output/completion category
 * @param reasoningTokens separately reported reasoning/thinking tokens
 * @param cacheCreationTokens tokens reported for cache creation/write
 * @param cacheReadTokens tokens reported for cache reads/hits
 * @param reportedTotalTokens a total supplied by the source, never one Agent Judge derives
 * @author Mark Pollack
 * @since 0.10.0
 */
public record Usage(Long inputTokens, Long outputTokens, Long reasoningTokens, Long cacheCreationTokens,
		Long cacheReadTokens, Long reportedTotalTokens) {

	/** Metadata key for {@link #inputTokens()}. */
	public static final String INPUT_TOKENS_KEY = "inputTokens";

	/** Metadata key for {@link #outputTokens()}. */
	public static final String OUTPUT_TOKENS_KEY = "outputTokens";

	/** Metadata key for {@link #reasoningTokens()}. */
	public static final String REASONING_TOKENS_KEY = "reasoningTokens";

	/** Metadata key for {@link #cacheCreationTokens()}. */
	public static final String CACHE_CREATION_TOKENS_KEY = "cacheCreationTokens";

	/** Metadata key for {@link #cacheReadTokens()}. */
	public static final String CACHE_READ_TOKENS_KEY = "cacheReadTokens";

	/** Metadata key for {@link #reportedTotalTokens()}. */
	public static final String REPORTED_TOTAL_TOKENS_KEY = "reportedTotalTokens";

	/**
	 * The largest magnitude an IEEE-754 double represents exactly, which is the ceiling
	 * the result metadata value profile enforces. A larger count cannot cross a JSON
	 * boundary as the number it is, so it is refused where it is reported rather than
	 * where it is written.
	 */
	private static final long MAX_INTEROPERABLE_INTEGER = 9007199254740991L;

	/** Validate all reported token quantities. */
	public Usage {
		requireQuantity(inputTokens, INPUT_TOKENS_KEY);
		requireQuantity(outputTokens, OUTPUT_TOKENS_KEY);
		requireQuantity(reasoningTokens, REASONING_TOKENS_KEY);
		requireQuantity(cacheCreationTokens, CACHE_CREATION_TOKENS_KEY);
		requireQuantity(cacheReadTokens, CACHE_READ_TOKENS_KEY);
		requireQuantity(reportedTotalTokens, REPORTED_TOTAL_TOKENS_KEY);
	}

	/**
	 * Project the reported quantities into the portable value profile a
	 * {@code Judgment} result accepts.
	 * <p>
	 * A {@code Usage} record is a Java identity rather than a portable value, so a
	 * producer stores this projection: a string-keyed object of integers, in the order the
	 * components are declared, with unreported categories omitted. Every present quantity
	 * appears — the projection never narrows or drops a value it was given.
	 * </p>
	 * @return an immutable, ordered projection, empty when nothing was reported
	 */
	public Map<String, Object> toPortableMap() {
		Map<String, Object> portable = new LinkedHashMap<>();
		put(portable, INPUT_TOKENS_KEY, inputTokens);
		put(portable, OUTPUT_TOKENS_KEY, outputTokens);
		put(portable, REASONING_TOKENS_KEY, reasoningTokens);
		put(portable, CACHE_CREATION_TOKENS_KEY, cacheCreationTokens);
		put(portable, CACHE_READ_TOKENS_KEY, cacheReadTokens);
		put(portable, REPORTED_TOTAL_TOKENS_KEY, reportedTotalTokens);
		return Collections.unmodifiableMap(portable);
	}

	private static void put(Map<String, Object> portable, String key, Long quantity) {
		if (quantity != null) {
			portable.put(key, quantity);
		}
	}

	private static void requireQuantity(Long quantity, String key) {
		if (quantity == null) {
			return;
		}
		if (quantity < 0) {
			throw new IllegalArgumentException("usage." + key + ": " + quantity
					+ " is not a reported token quantity. A quantity is non-negative; "
					+ "an unreported category is null rather than a placeholder.");
		}
		if (quantity > MAX_INTEROPERABLE_INTEGER) {
			throw new IllegalArgumentException("usage." + key + ": " + quantity
					+ " is outside the interoperable integer range of +/-" + MAX_INTEROPERABLE_INTEGER
					+ ", so it cannot cross a JSON boundary as the value it is.");
		}
	}

	/**
	 * Start building a usage value by naming only the categories a source reported.
	 * <p>
	 * Six same-typed components make a positional constructor easy to get wrong, and a
	 * provider adapter typically reports two or three of them, so the builder is the
	 * intended construction path.
	 * </p>
	 * @return a new builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Collects reported token quantities. An unset category stays absent.
	 */
	public static final class Builder {

		private Long inputTokens;

		private Long outputTokens;

		private Long reasoningTokens;

		private Long cacheCreationTokens;

		private Long cacheReadTokens;

		private Long reportedTotalTokens;

		private Builder() {
		}

		/**
		 * Record tokens the source reports in its input/prompt category.
		 * @param inputTokens the reported quantity
		 * @return this builder
		 */
		public Builder inputTokens(long inputTokens) {
			this.inputTokens = inputTokens;
			return this;
		}

		/**
		 * Record tokens the source reports in its output/completion category.
		 * @param outputTokens the reported quantity
		 * @return this builder
		 */
		public Builder outputTokens(long outputTokens) {
			this.outputTokens = outputTokens;
			return this;
		}

		/**
		 * Record separately reported reasoning/thinking tokens. Set this only when the
		 * source reports reasoning apart from output; a provider that folds reasoning into
		 * output has not reported this category.
		 * @param reasoningTokens the reported quantity
		 * @return this builder
		 */
		public Builder reasoningTokens(long reasoningTokens) {
			this.reasoningTokens = reasoningTokens;
			return this;
		}

		/**
		 * Record tokens the source reports for cache creation/write.
		 * @param cacheCreationTokens the reported quantity
		 * @return this builder
		 */
		public Builder cacheCreationTokens(long cacheCreationTokens) {
			this.cacheCreationTokens = cacheCreationTokens;
			return this;
		}

		/**
		 * Record tokens the source reports for cache reads/hits.
		 * @param cacheReadTokens the reported quantity
		 * @return this builder
		 */
		public Builder cacheReadTokens(long cacheReadTokens) {
			this.cacheReadTokens = cacheReadTokens;
			return this;
		}

		/**
		 * Record a total the source supplied. Do not compute one: a category total that
		 * Agent Judge derived would claim provider accounting it does not have.
		 * @param reportedTotalTokens the total as reported
		 * @return this builder
		 */
		public Builder reportedTotalTokens(long reportedTotalTokens) {
			this.reportedTotalTokens = reportedTotalTokens;
			return this;
		}

		/**
		 * Build the usage value, validating every reported quantity.
		 * @return the usage value
		 */
		public Usage build() {
			return new Usage(inputTokens, outputTokens, reasoningTokens, cacheCreationTokens, cacheReadTokens,
					reportedTotalTokens);
		}

	}

}
