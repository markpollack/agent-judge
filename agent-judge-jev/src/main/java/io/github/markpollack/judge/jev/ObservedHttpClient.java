/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.jev;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import java.util.function.Consumer;
import javax.net.ssl.*;
import org.jspecify.annotations.Nullable;

/**
 * One evaluation's observer. SDK owns deadlines, JSON transport and cancellation policy.
 */
final class ObservedHttpClient extends HttpClient {

	private final HttpClient delegate;

	private final int limit;

	private final Consumer<byte[]> validate;

	private final Object lock = new Object();

	private final ByteArrayOutputStream requestBytes = new ByteArrayOutputStream();

	private byte @Nullable [] responseBytes;

	private @Nullable String requestId;

	private int attempts;

	private int status;

	private long started;

	private long elapsed;

	private boolean ended;

	private boolean cancelled;

	private @Nullable CompletableFuture<?> pending;

	private final CompletableFuture<Void> observed = new CompletableFuture<>();

	ObservedHttpClient(HttpClient delegate, int limit, Consumer<byte[]> validate) {
		this.delegate = delegate;
		this.limit = limit;
		this.validate = validate;
	}

	/**
	 * What one observed exchange recorded. A class, not a record: it holds byte arrays,
	 * which a record would compare by reference. Each accessor returns a copy.
	 */
	static final class Snapshot {

		private final byte[] request;

		private final byte @Nullable [] response;

		private final @Nullable String requestId;

		private final int attempts;

		private final int status;

		private final long elapsedNanos;

		private final boolean cancelled;

		/** Copies the client's state; the caller holds the client's lock. */
		Snapshot(ObservedHttpClient client) {
			this.request = client.requestBytes.toByteArray();
			this.response = client.responseBytes == null ? null : client.responseBytes.clone();
			this.requestId = client.requestId;
			this.attempts = client.attempts;
			this.status = client.status;
			this.elapsedNanos = client.elapsed;
			this.cancelled = client.cancelled;
		}

		byte[] request() {
			return request.clone();
		}

		byte @Nullable [] response() {
			return response == null ? null : response.clone();
		}

		@Nullable String requestId() {
			return requestId;
		}

		int attempts() {
			return attempts;
		}

		int status() {
			return status;
		}

		long elapsedNanos() {
			return elapsedNanos;
		}

		boolean cancelled() {
			return cancelled;
		}

	}

	Snapshot snapshot() {
		synchronized (lock) {
			return new Snapshot(this);
		}
	}

	void cancelPending() {
		CompletableFuture<?> source;
		synchronized (lock) {
			source = pending;
		}
		if (source != null) {
			if (!source.isDone()) {
				finish(true);
				source.cancel(true);
			}
			// If the HTTP future already completed, its observer may still be publishing
			// the original bytes. Wait for that bounded callback before exposing a trace.
			if (source.isDone())
				observed.join();
		}
	}

	private void finish(boolean cancellation) {
		synchronized (lock) {
			if (!ended) {
				ended = true;
				cancelled = cancellation;
				elapsed = Math.max(0, System.nanoTime() - started);
			}
		}
	}

	@Override
	public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
		HttpRequest.BodyPublisher original = request.bodyPublisher().orElseThrow();
		if (original.contentLength() < 0 || original.contentLength() > limit)
			throw new IllegalArgumentException("Encoded request exceeds capture bound");
		synchronized (lock) {
			if (attempts != 0)
				throw new IllegalStateException("Only one attempt permitted");
			attempts++;
			started = System.nanoTime();
		}
		byte[] encoded = requestBody(original);
		synchronized (lock) {
			requestBytes.writeBytes(encoded);
		}
		HttpRequest forwarded = HttpRequest.newBuilder(request, (name, value) -> true)
			.method(request.method(), HttpRequest.BodyPublishers.ofByteArray(encoded))
			.build();
		final CompletableFuture<HttpResponse<T>> source;
		try {
			source = delegate.sendAsync(forwarded, info -> new LimitedSubscriber<>(handler.apply(info), limit));
		}
		catch (RuntimeException failure) {
			io.github.markpollack.judge.portable.PreservationLimitException.propagate(failure);
			finish(false);
			throw new IllegalArgumentException("HTTP send failed");
		}
		synchronized (lock) {
			pending = source;
		}
		CompletableFuture<HttpResponse<T>> result = new CompletableFuture<>() {
			@Override
			public boolean cancel(boolean mayInterrupt) {
				finish(true);
				source.cancel(mayInterrupt);
				return super.cancel(mayInterrupt);
			}
		};
		// complete(...) settles the exposed future on every outcome, fatal errors included,
		// so the callback's own future carries nothing a caller needs.
		var unused = source.whenComplete((response, failure) -> complete(result, response, failure));
		return result;
	}

	private <T> void complete(CompletableFuture<HttpResponse<T>> result, @Nullable HttpResponse<T> response,
			@Nullable Throwable failure) {
		try {
			if (failure != null) {
				finish(false);
				result.completeExceptionally(transportFailure(failure));
				return;
			}
			accept(result, java.util.Objects.requireNonNull(response));
		}
		catch (Error fatal) {
			// The callback's own future is not exposed, so a fatal error thrown here
			// reaches the caller only through the exposed future.
			finish(false);
			result.completeExceptionally(fatal);
		}
		finally {
			observed.complete(null);
		}
	}

	private <T> void accept(CompletableFuture<HttpResponse<T>> result, HttpResponse<T> response) {
		try {
			synchronized (lock) {
				if (ended)
					return;
				if (!(response.body() instanceof byte[] body) || body.length > limit)
					throw new IllegalArgumentException("Bounded byte response required");
				responseBytes = body.clone();
				status = response.statusCode();
				requestId = response.headers().firstValue("x-typesafe-request-id").orElse(null);
				// Publish validated facts atomically before completion or cancellation can
				// expose a snapshot. Never apply SDK defaults to an unchecked original.
				if (response.statusCode() >= 200 && response.statusCode() < 300)
					validate.accept(body);
				finish(false);
			}
			result.complete(response);
		}
		catch (RuntimeException e) {
			finish(false);
			result.completeExceptionally(refusal(e, "Invalid provider response"));
		}
	}

	/**
	 * What the caller sees when the HTTP future fails. Control flow keeps its original
	 * through asynchronous wrappers: a fatal error and a cancellation are delivered as
	 * they are, and an interruption becomes a cancellation caused by it. An ordinary
	 * transport failure is reported without detail.
	 */
	private static Throwable transportFailure(Throwable failure) {
		Throwable original = failure;
		while ((original instanceof java.util.concurrent.CompletionException
				|| original instanceof java.util.concurrent.ExecutionException) && original.getCause() != null)
			original = original.getCause();
		if (original instanceof Error || original instanceof java.util.concurrent.CancellationException)
			return original;
		if (original instanceof InterruptedException) {
			var cancelled = new java.util.concurrent.CancellationException("HTTP exchange interrupted");
			cancelled.initCause(original);
			return cancelled;
		}
		return refusal(failure, "HTTP transport failed");
	}

	/** A preservation refusal keeps its original; any other failure is reported without detail. */
	private static Throwable refusal(Throwable failure, String message) {
		try {
			io.github.markpollack.judge.portable.PreservationLimitException.propagate(failure);
		}
		catch (io.github.markpollack.judge.portable.PreservationLimitException limitFailure) {
			return limitFailure;
		}
		return new IOException(message);
	}

	private byte[] requestBody(HttpRequest.BodyPublisher publisher) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		CompletableFuture<byte[]> body = new CompletableFuture<>();
		java.util.concurrent.atomic.AtomicReference<Flow.Subscription> subscribed = new java.util.concurrent.atomic.AtomicReference<>();
		// The released SDK uses the synchronous JDK byte-array publisher. Capture its
		// exact prepared payload once, including on a later connection failure. Refuse
		// an unexpected streaming publisher rather than blocking outside the deadline.
		publisher.subscribe(new Flow.Subscriber<ByteBuffer>() {
			@Override
			public void onSubscribe(Flow.Subscription subscription) {
				subscribed.set(subscription);
				subscription.request(Long.MAX_VALUE);
			}

			@Override
			public void onNext(ByteBuffer item) {
				if (item.remaining() > limit - bytes.size()) {
					Objects.requireNonNull(subscribed.get()).cancel();
					body.completeExceptionally(new IllegalArgumentException("Request exceeds bound"));
					return;
				}
				byte[] chunk = new byte[item.remaining()];
				item.duplicate().get(chunk);
				bytes.writeBytes(chunk);
			}

			@Override
			public void onError(Throwable failure) {
				body.completeExceptionally(new IllegalArgumentException("Request body unavailable"));
			}

			@Override
			public void onComplete() {
				body.complete(bytes.toByteArray());
			}
		});
		if (!body.isDone()) {
			Flow.Subscription subscription = subscribed.get();
			if (subscription != null)
				subscription.cancel();
			throw new IllegalArgumentException("SDK byte-array publisher required");
		}
		return body.join();
	}

	private static final class LimitedSubscriber<T> implements HttpResponse.BodySubscriber<T> {

		private final HttpResponse.BodySubscriber<T> delegate;

		private final int limit;

		private int count;

		private boolean failed;

		private Flow.@Nullable Subscription subscription;

		LimitedSubscriber(HttpResponse.BodySubscriber<T> delegate, int limit) {
			this.delegate = delegate;
			this.limit = limit;
		}

		@Override
		public CompletionStage<T> getBody() {
			return delegate.getBody();
		}

		@Override
		public void onSubscribe(Flow.Subscription s) {
			subscription = s;
			delegate.onSubscribe(s);
		}

		@Override
		public void onNext(List<ByteBuffer> buffers) {
			long size = buffers.stream().mapToLong(ByteBuffer::remaining).sum();
			if (size > limit - count) {
				failed = true;
				Objects.requireNonNull(subscription).cancel();
				delegate.onError(new IOException("Response exceeds capture bound"));
			}
			else if (!failed) {
				count += (int) size;
				delegate.onNext(buffers);
			}
		}

		@Override
		public void onError(Throwable failure) {
			if (!failed)
				delegate.onError(failure);
		}

		@Override
		public void onComplete() {
			if (!failed)
				delegate.onComplete();
		}

	}

	@Override
	public <T> HttpResponse<T> send(HttpRequest r, HttpResponse.BodyHandler<T> h)
			throws IOException, InterruptedException {
		throw new UnsupportedOperationException("SDK uses asynchronous transport");
	}

	@Override
	public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r, HttpResponse.BodyHandler<T> h,
			HttpResponse.PushPromiseHandler<T> p) {
		throw new UnsupportedOperationException("Push is not supported");
	}

	@Override
	public Optional<CookieHandler> cookieHandler() {
		return delegate.cookieHandler();
	}

	@Override
	public Optional<Duration> connectTimeout() {
		return delegate.connectTimeout();
	}

	@Override
	public Redirect followRedirects() {
		return delegate.followRedirects();
	}

	@Override
	public Optional<ProxySelector> proxy() {
		return delegate.proxy();
	}

	@Override
	public SSLContext sslContext() {
		return delegate.sslContext();
	}

	@Override
	public SSLParameters sslParameters() {
		return delegate.sslParameters();
	}

	@Override
	public Optional<Authenticator> authenticator() {
		return delegate.authenticator();
	}

	@Override
	public Version version() {
		return delegate.version();
	}

	@Override
	public Optional<Executor> executor() {
		return delegate.executor();
	}

}
