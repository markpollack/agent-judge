package io.github.markpollack.judge.ai.prompt;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.context.ExecutionStatus;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.jury.ErrorPolicy;
import io.github.markpollack.judge.jury.MajorityVotingStrategy;
import io.github.markpollack.judge.jury.SimpleJury;
import io.github.markpollack.judge.jury.TiePolicy;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guards for the silent judge loss defect, exercised on the executor that
 * actually produced it: {@link ForkJoinPool#commonPool()}, which is what a parallel
 * {@link SimpleJury} uses when no executor is configured.
 *
 * <p>
 * A common-pool worker's context classloader is the system classloader, not the
 * application's. Under {@code exec:java} or in a container that could not see the
 * application's resources, so a lazily-resolved classpath template failed to load there,
 * the judge threw, and it dropped out of the vote while the jury still returned a
 * confident verdict computed from fewer judges than it listed.
 * </p>
 *
 * <p>
 * Every test here blinds the worker's context classloader for the duration of the task
 * and asserts the resource really is invisible through it first, so none of them can pass
 * vacuously by happening to run where the system classloader can see the test classpath.
 * The blinded loader is always restored: common-pool workers are shared and outlive the
 * task.
 * </p>
 */
class CommonPoolTemplateRenderingTests {

	private static final String TEMPLATE = "judges/test-relevance.md";

	@Test
	void classpathTemplateRendersOnACommonPoolWorker() {
		var template = JudgePromptTemplate.fromClasspath(TEMPLATE);

		Rendered rendered = onCommonPoolWorkerWithoutApplicationContextClassLoader(
				() -> new Rendered(Thread.currentThread(), template.render(renderContext())));

		assertThat(rendered.thread()).isInstanceOf(ForkJoinWorkerThread.class);
		assertThat(((ForkJoinWorkerThread) rendered.thread()).getPool()).isSameAs(ForkJoinPool.commonPool());
		assertThat(rendered.text()).contains("test goal").contains("test output").doesNotContain("{{");
	}

	@Test
	void parallelJuryKeepsEveryJudgeThatRendersAClasspathTemplate() {
		// The template is built once on the calling thread, the way a judge holds one as
		// a field. Before the fix its text was not read until render(), on a common-pool
		// worker, and the two template-backed judges below vanished from the vote.
		var template = JudgePromptTemplate.fromClasspath(TEMPLATE);

		Judge renders = context -> Judgment.pass(blindContextClassLoader(() -> template.render(context)));

		SimpleJury jury = SimpleJury.builder()
			.judge(renders)
			.judge(renders)
			.judge(context -> Judgment.pass("no template"))
			.votingStrategy(new MajorityVotingStrategy(TiePolicy.FAIL, ErrorPolicy.TREAT_AS_ABSTAIN))
			.parallel(true)
			.build();

		Verdict verdict = jury.vote(renderContext());

		assertThat(verdict.individual()).hasSize(3);
		assertThat(verdict.individual()).noneMatch(judgment -> judgment.status() == JudgmentStatus.ERROR);
		assertThat(verdict.individual().get(0).reasoning()).contains("test goal").contains("test output");
		assertThat(verdict.aggregated().status()).isEqualTo(JudgmentStatus.PASS);
	}

	private record Rendered(Thread thread, String text) {
	}

	private static JudgmentContext renderContext() {
		return JudgmentContext.builder()
			.goal("test goal")
			.agentOutput("test output")
			.status(ExecutionStatus.SUCCESS)
			.build();
	}

	/**
	 * Run {@code action} on a {@link ForkJoinPool#commonPool()} worker whose context
	 * classloader cannot see the test classpath, and hand back whatever it produced.
	 */
	private static <T> T onCommonPoolWorkerWithoutApplicationContextClassLoader(Callable<T> action) {
		return CompletableFuture.supplyAsync(() -> blindContextClassLoader(action), ForkJoinPool.commonPool()).join();
	}

	/**
	 * Blind the current thread's context classloader, prove the template is unreachable
	 * through it, run {@code action}, and always put the original loader back.
	 */
	private static <T> T blindContextClassLoader(Callable<T> action) {
		Thread current = Thread.currentThread();
		ClassLoader original = current.getContextClassLoader();
		try (URLClassLoader blind = new URLClassLoader(new URL[0], null)) {
			current.setContextClassLoader(blind);
			// Guard against the test going vacuous if this loader ever gains sight of the
			// resource.
			assertThat(blind.getResourceAsStream(TEMPLATE)).isNull();
			return action.call();
		}
		catch (RuntimeException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
		finally {
			current.setContextClassLoader(original);
		}
	}

}
