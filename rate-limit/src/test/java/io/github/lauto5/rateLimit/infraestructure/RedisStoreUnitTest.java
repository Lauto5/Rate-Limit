package io.github.lauto5.rateLimit.infraestructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.lauto5.rateLimit.application.RateLimitAtomicOperation;
import io.github.lauto5.rateLimit.application.ports.out.AtomicOperationResult;
import io.github.lauto5.rateLimit.domain.algorithm.FixedWindowAlgorithmImpl;
import io.github.lauto5.rateLimit.domain.context.AlgorithmContext;
import io.github.lauto5.rateLimit.domain.policies.FixedWindowPolicy;
import io.github.lauto5.rateLimit.testdoubles.FakeKeyValueStore;

public class RedisStoreUnitTest {

	private FakeKeyValueStore fakeKeyValueStore;
	private RedisStore redisStore;

	@BeforeEach
	void setUp() {
		fakeKeyValueStore = new FakeKeyValueStore();
		redisStore = new RedisStore(fakeKeyValueStore);
	}

	@AfterEach
	void tearDown() throws Exception {
		redisStore.close();
	}

	@Test
	void firstRequestShouldBeAllowedAndPersisted() {

		// Arrange
		FixedWindowAlgorithmImpl algorithm = new FixedWindowAlgorithmImpl();
		FixedWindowPolicy policy = new FixedWindowPolicy(5, Duration.ofMinutes(1));
		AlgorithmContext context = new AlgorithmContext(Instant.now());

		RateLimitAtomicOperation<?, ?> operation =
				new RateLimitAtomicOperation<>(algorithm, policy, context);

		// Act
		AtomicOperationResult<?> result = redisStore.executeAtomically("test-user-1", operation);

		// Assert
		assertTrue(result.getAlgorithmResult().isAllowed());
		assertTrue(fakeKeyValueStore.exists("test-user-1"));

	}

	@Test
	void concurrentRequestsShouldNotExceedLimitDueToRaceCondition() throws Exception {

		// Arrange
		int threadCount = 20;

		FixedWindowAlgorithmImpl algorithm = new FixedWindowAlgorithmImpl();
		FixedWindowPolicy policy = new FixedWindowPolicy(1, Duration.ofMinutes(1));
		String identifier = "concurrent-user-" + System.nanoTime();

		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		CyclicBarrier barrier = new CyclicBarrier(threadCount);

		Callable<Boolean> task = () -> {

			barrier.await();

			AlgorithmContext context = new AlgorithmContext(Instant.now());

			RateLimitAtomicOperation<?, ?> operation =
					new RateLimitAtomicOperation<>(algorithm, policy, context);

			AtomicOperationResult<?> result = redisStore.executeAtomically(identifier, operation);

			return result.getAlgorithmResult().isAllowed();
		};

		List<Callable<Boolean>> tasks = IntStream.range(0, threadCount)
				.mapToObj(i -> task)
				.collect(Collectors.toList());

		// Act
		List<Future<Boolean>> futures = executor.invokeAll(tasks, 30, TimeUnit.SECONDS);

		executor.shutdown();

		AtomicInteger allowedCount = new AtomicInteger(0);

		for (Future<Boolean> future : futures) {
			if (future.get()) {
				allowedCount.incrementAndGet();
			}
		}

		// Assert
		assertEquals(1, allowedCount.get(),
				"Con un limite de 1, exactamente 1 request concurrente debe ser ALLOWED");

	}

}
