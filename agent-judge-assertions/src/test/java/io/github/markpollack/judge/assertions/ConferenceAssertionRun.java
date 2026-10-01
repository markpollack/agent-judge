/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertions;

import java.net.URI;
import io.github.markpollack.judge.evaluation.*;
import java.net.http.HttpClient;
import java.nio.file.*;
import java.util.*;

/**
 * Explicitly invoked two-request fixture runner, never a JUnit test. Normal builds cannot
 * call a provider. The caller must separately authorize exact evidence/configuration,
 * account, two calls, 30-second per-call deadline and spend before invoking this class.
 * Only its explicit Vercel entry reads AI_GATEWAY_API_KEY; it never reads credential
 * files and has no automatic live fallback.
 */
public final class ConferenceAssertionRun {

	private ConferenceAssertionRun() {
	}

	/**
	 * Run the two exact reviewed requests against the official endpoint, each at most
	 * once. Both outcomes are recorded even when an assertion is unsuccessful. No
	 * assertion outcome is preordained by source review. A new output directory is
	 * required.
	 * @param apiKey explicitly supplied provider credential, never retained
	 * @param output new protected output directory
	 * @return actual results in RULE-4, AC8 order
	 * @throws Exception if setup or artifact retention fails
	 */
	public static List<EvaluationResult> run(String apiKey, Path output) throws Exception {
		return run(apiKey, output, false);
	}

	private static List<EvaluationResult> run(String apiKey, Path output, boolean vercel) throws Exception {
		Objects.requireNonNull(apiKey);
		if (apiKey.isBlank())
			throw new IllegalArgumentException("Explicit nonblank credential required");
		var fixture = new ConferenceFixture(vercel);
		var evidence = List.of(fixture.evidence(0), fixture.evidence(1));
		Files.createDirectory(output);
		fixture.saveRouting(output);
		URI endpoint = URI
			.create(vercel ? fixture.routing.path("endpoint").asText() : "https://api.typesafe.ai/v1/systemone");
		List<EvaluationResult> results = new ArrayList<>();
		try (var http = HttpClient.newHttpClient()) {
			for (int i = 0; i < 2; i++) {
				Path caseOutput = output.resolve(i == 0 ? "rule-4" : "uc6-ac8");

				var judge = fixture.bind(fixture.judge(apiKey, endpoint, http, caseOutput));
				var result = ConfiguredRules.evaluate(fixture.requirement(i), judge, evidence.get(i), fixture.binding);
				ConferenceFixture.save(result, caseOutput, "LIVE explicitly invoked; inspect actual outcome");
				String outcome = "PASSED";
				try {
					RequirementAssertions.requireSatisfied(result);
				}
				catch (RequirementAssertionError error) {
					outcome = error.result().verdict().conclusion().name();
				}
				Files.writeString(caseOutput.resolve("assertion-outcome.txt"), outcome + "\n");
				results.add(result);
			}
		}
		return List.copyOf(results);
	}

	/**
	 * Explicit entry. Direct calls prompt for a credential; the opted-in Vercel command
	 * reads AI_GATEWAY_API_KEY from the protected process environment.
	 * @param args --live-two-calls or --live-two-calls-vercel-env and a new protected
	 * output directory
	 * @throws Exception when setup or the run fails
	 */
	public static void main(String[] args) throws Exception {
		if (args.length == 2 && args[0].equals("--live-two-calls-vercel-env")) {
			String key = System.getenv("AI_GATEWAY_API_KEY");
			if (key == null || key.isBlank())
				throw new IllegalStateException("AI_GATEWAY_API_KEY must be supplied explicitly");
			run(key, Path.of(args[1]), true);
			return;
		}
		if (args.length != 2 || !args[0].equals("--live-two-calls"))
			throw new IllegalArgumentException("Explicit --live-two-calls OUTPUT required; no normal-build execution");
		var console = System.console();
		if (console == null)
			throw new IllegalStateException(
					"Interactive console required; alternatively call run with an explicit credential");
		char[] key = console.readPassword("Explicitly authorized Jev credential (not retained): ");
		if (key == null)
			throw new IllegalArgumentException("No credential supplied");
		try {
			run(new String(key), Path.of(args[1]));
		}
		finally {
			Arrays.fill(key, '\0');
		}
	}

}
