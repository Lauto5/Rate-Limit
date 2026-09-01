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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import io.lettuce.core.RedisClient;

import io.github.lauto5.rateLimit.application.RateLimitAtomicOperation;
import io.github.lauto5.rateLimit.application.ports.out.AtomicOperationResult;
import io.github.lauto5.rateLimit.domain.algorithm.FixedWindowAlgorithmImpl;
import io.github.lauto5.rateLimit.domain.context.AlgorithmContext;
import io.github.lauto5.rateLimit.domain.policies.FixedWindowPolicy;

@Testcontainers
public class RedisStoreIntegrationTest {

	private static GenericContainer<?> redisContainer;
	private static RedisClient redisClient;
	private static RedisStore redisStore;

	@SuppressWarnings("resource")
	@BeforeAll
	static void startRedis() {

		redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
				.withExposedPorts(6379);

		redisContainer.start();

		String redisUrl = "redis://" + redisContainer.getHost() + ":" + redisContainer.getMappedPort(6379);

		redisClient = RedisClient.create(redisUrl);
		redisStore = new RedisStore(redisClient);

	}

	@AfterAll
	static void stopRedis() {
		redisStore.close();
		redisClient.shutdown();
		redisContainer.stop();
	}

	@Test
	void firstRequestShouldBeAllowedAndPersistedInRedis() {

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

	}

	@Test
	void concurrentRequestsShouldNotExceedLimitDueToRaceCondition() throws Exception {

		// Arrange
		// Limite de 1: si el CAS no funcionara (GET/SET plano sin
		// atomicidad), varios threads podrian leer count=0 al mismo
		// tiempo y todos ser permitidos. Con el CAS, solo UNO debe ganar.

		int threadCount = 20;

		FixedWindowAlgorithmImpl algorithm = new FixedWindowAlgorithmImpl();
		FixedWindowPolicy policy = new FixedWindowPolicy(1, Duration.ofMinutes(1));
		String identifier = "concurrent-user-" + System.nanoTime();

		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		CyclicBarrier barrier = new CyclicBarrier(threadCount);

		Callable<Boolean> task = () -> {

			barrier.await(); // todos los threads disparan lo mas cerca posible entre si

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
		// Con limite=1, exactamente 1 de los N threads concurrentes
		// debe haber sido permitido; el resto debe haber sido denegado
		// por el algoritmo (no por errores del store).
		assertEquals(1, allowedCount.get(),
				"Con un limite de 1, exactamente 1 request concurrente debe ser ALLOWED "
						+ "(si da mas de 1, el CAS no esta previniendo la condicion de carrera)");

	}

}