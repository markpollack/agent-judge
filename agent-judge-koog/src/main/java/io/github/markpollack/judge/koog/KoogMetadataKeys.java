package io.github.markpollack.judge.koog;

/** Public constants for Koog facts stored in {@code JudgmentContext.metadata()}. */
public final class KoogMetadataKeys {

	/** Unique Koog agent identifier. */
	public static final String AGENT_ID = "koog.agentId";

	/** Provider identifier from the configured Koog model. */
	public static final String PROVIDER = "koog.provider";

	/** Model identifier from the Koog agent configuration. */
	public static final String MODEL = "koog.model";

	private KoogMetadataKeys() {
	}

}
