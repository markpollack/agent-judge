/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.judge.assertions;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.*;
import java.util.*;

/**
 * Explicitly invoked two-request fixture runner, never a JUnit test. Normal builds cannot
 * call a provider. The caller must separately authorize exact evidence/configuration,
 * account, two calls, 30-second per-call deadline and spend before invoking this class.
 * It reads no environment variables or credential files and has no automatic live
 * fallback.
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
	public static List<AssertionResult> run(String apiKey, Path output) throws Exception {
		Objects.requireNonNull(apiKey);
		if (apiKey.isBlank())
			throw new IllegalArgumentException("Explicit nonblank credential required");
		var fixture = new ConferenceFixture();
		var contexts = List.of(fixture.context(0), fixture.context(1));
		Files.createDirectory(output);
		List<AssertionResult> results = new ArrayList<>();
		try (var http = HttpClient.newHttpClient()) {
			for (int i = 0; i < 2; i++) {
				Path caseOutput = output.resolve(i == 0 ? "rule-4" : "uc6-ac8");
				var assertions = fixture.facade(
						fixture.judge(apiKey, URI.create("https://api.typesafe.ai/v1/systemone"), http, caseOutput));
				var result = assertions.evaluate(contexts.get(i), fixture.requirement(i));
				ConferenceFixture.save(result, caseOutput, "LIVE explicitly invoked; inspect actual outcome");
				String outcome = "PASSED";
				try {
					SemanticAssertions.requireSatisfied(result);
				}
				catch (SemanticAssertionError error) {
					outcome = error.category().name();
				}
				Files.writeString(caseOutput.resolve("assertion-outcome.txt"), outcome + "\n");
				results.add(result);
			}
		}
		return List.copyOf(results);
	}

	/**
	 * Explicit interactive entry; the credential is entered through a console prompt.
	 * @param args exactly --live-two-calls and a new protected output directory
	 * @throws Exception when setup or the run fails
	 */
	public static void main(String[] args) throws Exception {
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
