/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jev;

import io.github.gudcks0305.jev.NoulQuestion;
import io.github.gudcks0305.jev.typesafe.TypeSafeJevClient;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentStatus;
import io.github.markpollack.judge.provenance.ArtifactRef;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.net.ssl.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TransportTest {

	static final URI ENDPOINT = URI.create("http://127.0.0.1:12345/v1/systemone");

	static ArtifactRef capture(String kind, byte[] bytes) {
		return ArtifactRef.ofBytes("protected:" + kind, bytes, null);
	}

	static JevRuntime judge(HttpClient http, Duration timeout) {
		return new JevRuntime("fake-key", "jev-1.13.0", ENDPOINT, timeout, 16000, 32000, JevJudgeTest.NOUL, http,
				TransportTest::capture);
	}

	static class PendingHttp extends HttpClient {

		final HttpClient defaults = HttpClient.newHttpClient();

		final CountDownLatch sent = new CountDownLatch(1);

		final CompletableFuture<HttpResponse<byte[]>> actual = new CompletableFuture<>();

		final AtomicInteger calls = new AtomicInteger();

		@Override
		@SuppressWarnings("unchecked")
		public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r, HttpResponse.BodyHandler<T> h) {
			calls.incrementAndGet();
			sent.countDown();
			return (CompletableFuture<HttpResponse<T>>) (CompletableFuture<?>) actual;
		}

		@Override
		public <T> HttpResponse<T> send(HttpRequest r, HttpResponse.BodyHandler<T> h)
				throws IOException, InterruptedException {
			throw new UnsupportedOperationException();
		}

		@Override
		public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r, HttpResponse.BodyHandler<T> h,
				HttpResponse.PushPromiseHandler<T> p) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Optional<CookieHandler> cookieHandler() {
			return defaults.cookieHandler();
		}

		@Override
		public Optional<Duration> connectTimeout() {
			return defaults.connectTimeout();
		}

		@Override
		public Redirect followRedirects() {
			return defaults.followRedirects();
		}

		@Override
		public Optional<ProxySelector> proxy() {
			return defaults.proxy();
		}

		@Override
		public SSLContext sslContext() {
			return defaults.sslContext();
		}

		@Override
		public SSLParameters sslParameters() {
			return defaults.sslParameters();
		}

		@Override
		public Optional<Authenticator> authenticator() {
			return defaults.authenticator();
		}

		@Override
		public Version version() {
			return defaults.version();
		}

		@Override
		public Optional<Executor> executor() {
			return defaults.executor();
		}

		@Override
		public void close() {
			defaults.close();
		}

	}

	@Test
	void sdkDeadlineCancelsActualHttpFuture() {
		try (var http = new PendingHttp()) {
			long start = System.nanoTime();
			Judgment j = JevJudge.builder()
				.runtime(judge(http, Duration.ofMillis(100)))
				.requirement(JevJudgeTest.input().requirement())
				.evidence(JevJudgeTest.input().evidence())
				.build()
				.judge();
			assertThat(j.status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(http.calls).hasValue(1);
			assertThat(http.actual).isCancelled();
			assertThat(System.nanoTime() - start).isLessThan(Duration.ofSeconds(2).toNanos());
		}
	}

	@Test
	void sdkCancellationReachesActualFuture() throws Exception {
		try (var http = new PendingHttp()) {
			var observer = new ObservedHttpClient(http, 32000, b -> {
			});
			try (var sdk = TypeSafeJevClient.builder()
				.apiKey("fake-key")
				.model("jev-1.13.0")
				.endpoint(ENDPOINT)
				.timeout(Duration.ofSeconds(5))
				.maxRetries(0)
				.httpClient(observer)
				.build()) {
				var future = sdk.evaluateAsync(Map.of("evidence", "selected"),
						NoulQuestion.of("q", "Explicit instructions"));
				assertThat(http.sent.await(1, TimeUnit.SECONDS)).isTrue();
				assertThat(future.cancel(true)).isTrue();
				assertThat(http.actual).isCancelled();
				assertThat(observer.snapshot().cancelled()).isTrue();
				assertThat(Checks.parse(observer.snapshot().request()).path("state").path("evidence").asText())
					.isEqualTo("selected");
			}
		}
	}

	@Test
	void interruptRestoresFlagAndCancelsActualFuture() throws Exception {
		try (var http = new PendingHttp()) {
			AtomicReference<java.util.concurrent.CancellationException> result = new AtomicReference<>();
			AtomicBoolean interrupted = new AtomicBoolean();
			Thread thread = Thread.ofVirtual().start(() -> {
				try {
					JevJudge.builder()
						.runtime(judge(http, Duration.ofSeconds(5)))
						.requirement(JevJudgeTest.input().requirement())
						.evidence(JevJudgeTest.input().evidence())
						.build()
						.judge();
				}
				catch (java.util.concurrent.CancellationException cancellation) {
					result.set(cancellation);
				}
				finally {
					interrupted.set(Thread.currentThread().isInterrupted());
				}
			});
			assertThat(http.sent.await(2, TimeUnit.SECONDS)).isTrue();
			thread.interrupt();
			thread.join(2000);
			assertThat(thread.isAlive()).isFalse();
			assertThat(interrupted).isTrue();
			assertThat(result.get()).isNotNull();
			assertThat(http.actual).isCancelled();
		}
	}

	@Test
	void preInterruptedThreadMakesNoRequest() {
		try (var http = new PendingHttp()) {
			Thread.currentThread().interrupt();
			try {
				org.assertj.core.api.Assertions
					.assertThatThrownBy(() -> JevJudge.builder()
						.runtime(judge(http, Duration.ofSeconds(1)))
						.requirement(JevJudgeTest.input().requirement())
						.evidence(JevJudgeTest.input().evidence())
						.build()
						.judge())
					.isInstanceOf(java.util.concurrent.CancellationException.class);
				assertThat(Thread.currentThread().isInterrupted()).isTrue();
				assertThat(http.calls).hasValue(0);
			}
			finally {
				Thread.interrupted();
			}
		}
	}

	@Test
	void oversizedEncodedRequestIsRejectedBeforeUnderlyingSend() {
		try (var http = new PendingHttp()) {
			var judge = new JevRuntime("fake-key", "jev-1.13.0", ENDPOINT, Duration.ofMillis(100), 6000, 8000,
					JevJudgeTest.NOUL, http, TransportTest::capture);
			assertThat(JevJudge.builder()
				.runtime(judge)
				.requirement(JevJudgeTest.input().requirement())
				.evidence(JevJudgeTest.input(JevJudgeTest.GOAL, "\n".repeat(5000), true).evidence())
				.build()
				.judge()
				.status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(http.calls).hasValue(0);
		}
	}

	@Test
	void connectionFailureRetainsExactIntendedRequestWithoutTransmissionClaim() {
		try (var http = new PendingHttp()) {
			http.actual.completeExceptionally(new IOException("echoed private transport details"));
			Map<String, byte[]> bytes = new ConcurrentHashMap<>();
			ArtifactCapture sink = (kind, data) -> {
				bytes.put(kind, data);
				return ArtifactRef.ofBytes("protected:" + kind, data, null);
			};
			JevRuntime evaluator = new JevRuntime("fake-key", "jev-1.13.0", ENDPOINT, Duration.ofSeconds(1), 16000,
					32000, JevJudgeTest.NOUL, http, sink);
			Judgment result = JevJudge.builder()
				.runtime(evaluator)
				.requirement(JevJudgeTest.input().requirement())
				.evidence(JevJudgeTest.input().evidence())
				.build()
				.judge();
			assertThat(result.status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(result.reasoning()).doesNotContain("private transport");
			assertThat(Checks.parse(bytes.get("request")).path("state").path("requirement").asText())
				.isEqualTo(JevJudgeTest.GOAL);
			assertThat(bytes).doesNotContainKey("response");
			assertThat(http.calls).hasValue(1);
			assertThat(Checks.parse(bytes.get("trace")).path("attempts").asInt()).isEqualTo(1);
		}
	}

	@Test
	void exactOfficialGatewayRouteReachesOnlyTheInjectedTransport() {
		try (var http = new PendingHttp()) {
			http.actual.completeExceptionally(new IOException("fake failure; no network"));
			var evaluator = new JevRuntime("fake-key", "typesafe-ai/jev",
					URI.create("https://ai-gateway.vercel.sh/typesafe/v1/systemone"), Duration.ofSeconds(1), 16000,
					32000, JevJudgeTest.choice(), http, TransportTest::capture);
			assertThat(JevJudge.builder()
				.runtime(evaluator)
				.requirement(JevJudgeTest.input().requirement())
				.evidence(JevJudgeTest.input().evidence())
				.build()
				.judge()
				.status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(http.calls).hasValue(1);
		}
	}

	@Test
	void gatewayRedirectsAreRejectedBeforeInjectedTransport() {
		try (var http = new PendingHttp() {
			@Override
			public Redirect followRedirects() {
				return Redirect.ALWAYS;
			}
		}) {
			var evaluator = new JevRuntime("fake-key", "typesafe-ai/jev",
					URI.create("https://ai-gateway.vercel.sh/typesafe/v1/systemone"), Duration.ofSeconds(1), 16000,
					32000, JevJudgeTest.choice(), http, TransportTest::capture);
			assertThat(JevJudge.builder()
				.runtime(evaluator)
				.requirement(JevJudgeTest.input().requirement())
				.evidence(JevJudgeTest.input().evidence())
				.build()
				.judge()
				.status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(http.calls).hasValue(0);
		}
	}

}
