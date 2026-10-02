/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.reporting;

import java.util.*;
import io.github.markpollack.judge.requirement.Requirement;
import org.jspecify.annotations.Nullable;
import io.github.markpollack.judge.jury.*;
import io.github.markpollack.judge.judgment.Judgment;

/** Readable traversal over retained facts. Never invokes a producer or policy. */
public final class VerdictReport {

	private final Verdict verdict;

	private VerdictReport(Verdict verdict) {
		this.verdict = Objects.requireNonNull(verdict);
		verdict.conclusion();
	}

	/**
	 * Inspect a usable verdict.
	 * @param verdict complete retained record
	 * @return read-only report
	 */
	public static VerdictReport of(Verdict verdict) {
		return new VerdictReport(verdict);
	}

	/**
	 * The authoritative derived conclusion.
	 * @return domain conclusion
	 */
	public Verdict.Conclusion conclusion() {
		return verdict.conclusion();
	}

	/**
	 * Complete underlying verdict.
	 * @return original record
	 */
	public Verdict verdict() {
		return verdict;
	}

	/**
	 * Root individual judgments, in seat order; child opinions remain on child verdicts.
	 * @return root opinions
	 */
	public List<Judgment> judgments() {
		return verdict.individual();
	}

	/**
	 * All entered composite attempts in depth-first order.
	 * @return named attempt paths and complete child records
	 */
	public List<CompositePathEntry> attempts() {
		return CompositePaths.flatten(verdict);
	}

	/**
	 * Follow adopted tiers to the deciding path.
	 * @return local names from the root
	 */
	public List<String> decidingPath() {
		var path = new ArrayList<String>();
		Verdict node = verdict;
		while (node.provenance().kind() == VerdictProvenanceKind.TIER) {
			String name = Objects.requireNonNull(node.provenance().tier());
			path.add(name);
			if (node.provenance().basis() == VerdictProvenanceBasis.INDIVIDUAL_REJECTION)
				break;
			node = Objects.requireNonNull(node.compositeAttempts()
				.stream()
				.filter(a -> a.name().equals(name))
				.findFirst()
				.orElseThrow()
				.verdict());
		}
		return List.copyOf(path);
	}

	/**
	 * Counts native invocations once by their stable identities, including shared roster
	 * owners and semantic copies through identity or selected tiers.
	 * @return complete unique native invocation records in traversal order
	 */
	public List<io.github.markpollack.judge.provenance.Invocation> invocations() {
		return io.github.markpollack.judge.jury.InvocationRecords.of(verdict);
	}

	private static String requirement(@Nullable Requirement<?> value) {
		return value == null ? "" : "; requirement=" + value.id() + "@" + value.revision() + "; source="
				+ value.source().artifact().id() + "#" + value.source().artifact().sha256();
	}

	/**
	 * Summarize conclusion, deciding path, opinions and retained failures.
	 * @return deterministic readable text
	 */
	public String summary() {
		var lines = new ArrayList<String>();
		lines.add((verdict.requirement() == null ? "Check" : "Requirement " + verdict.requirement().id()) + ": "
				+ conclusion());
		if (!verdict.roster().isEmpty())
			lines.add("Declared roster: " + verdict.roster().stream().map(q -> q.id() + "@" + q.revision()).toList());
		if (!invocations().isEmpty())
			lines.add("Native invocations: " + invocations().size());
		lines.add("Collective judgment: " + verdict.judgment().status() + "; "
				+ bounded(verdict.judgment().reasoning(), 640));
		if (verdict.judgment().finding() != null)
			lines.add("Finding: " + bounded(verdict.judgment().finding().toString(), 640));
		if (verdict.judgment().confidence() != null)
			lines.add("Confidence: " + bounded(verdict.judgment().confidence().toString(), 320));
		if (verdict.judgment().probabilityDistribution() != null)
			lines.add("Probabilities: " + bounded(verdict.judgment().probabilityDistribution().toString(), 640));
		if (verdict.judgment().provenance() != null)
			lines.add("Provenance: " + bounded(verdict.judgment().provenance().toString(), 640));
		if (!decidingPath().isEmpty())
			lines.add("Deciding path: " + String.join(" / ", decidingPath()));
		for (int i = 0; i < verdict.individual().size(); i++)
			lines.add("Seat " + verdict.seats().get(i).verdictKey() + ": " + verdict.individual().get(i).status() + "; "
					+ verdict.seats().get(i).participation() + requirement(verdict.individual().get(i).requirement())
					+ (verdict.seats().get(i).rejection() == null ? ""
							: "; rejected=" + Objects.requireNonNull(verdict.seats().get(i).rejection()).reasonCode()));
		for (var entry : attempts())
			lines.add(entry.path() + ": " + entry.attempt().disposition() + "; "
					+ (entry.attempt().dispositionReason() == null ? ""
							: "reason=" + entry.attempt().dispositionReason() + "; ")
					+ (entry.attempt().verdict() == null ? entry.attempt().failure()
							: entry.attempt().verdict().judgment().status())
					+ (entry.attempt().routingDecision() == null ? ""
							: "; routing=" + entry.attempt().routingDecision().reason())
					+ (entry.attempt().verdict() == null ? "" : requirement(entry.attempt().verdict().requirement())));
		return String.join("\n", lines);
	}

	private static String bounded(String value, int limit) {
		return value.length() <= limit ? value : value.substring(0, limit) + "… [full value retained]";
	}

}
