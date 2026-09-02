package io.github.lauto5.rateLimit.infraestructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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

import io.github.lauto5.rateLimit.application.RateLimitAtomicOperation;
import io.github.lauto5.rateLimit.application.ports.out.AtomicOperationResult;
import io.github.lauto5.rateLimit.domain.algorithm.FixedWindowAlgorithmImpl;
import io.github.lauto5.rateLimit.domain.algorithmState.FixedWindowState;
import io.github.lauto5.rateLimit.domain.context.AlgorithmContext;
import io.github.lauto5.rateLimit.domain.policies.FixedWindowPolicy;

@Testcontainers
public class RedisStoreIntegrationTest {

	private static GenericContainer<?> redisContainer;
	private static String redisUrl;
	private static LettuceKeyValueStore keyValueStore;
	private static RedisStore redisStore;

	@SuppressWarnings("resource")
	@BeforeAll
	static void startRedis() {

		redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
				.withExposedPorts(6379);

		redisContainer.start();

		redisUrl = "redis://" + redisContainer.getHost() + ":" + redisContainer.getMappedPort(6379);

		keyValueStore = new LettuceKeyValueStore(redisUrl);
		redisStore = new RedisStore(keyValueStore);

	}

	@AfterAll
	static void stopRedis() throws Exception {
		redisStore.close();
		redisContainer.stop();
	}

	// ==================== HELPER ====================

	private String uniqueIdentifier() {
		return "user-" + UUID.randomUUID();
	}

	private RateLimitAtomicOperation<FixedWindowState, FixedWindowPolicy> operationWith(
			FixedWindowAlgorithmImpl algorithm, FixedWindowPolicy policy, Instant now) {
		return new RateLimitAtomicOperation<>(algorithm, policy, new AlgorithmContext(now));
	}

	// ==================== HELPER ====================

	// ==================== TESTS ====================

	@Test
	void shouldPersistStateInRedis() {

		// Arrange
		FixedWindowAlgorithmImpl algorithm = new FixedWindowAlgorithmImpl();
		FixedWindowPolicy policy = new FixedWindowPolicy(5, Duration.ofMinutes(1));
		String identifier = uniqueIdentifier();

		// Act
		AtomicOperationResult<FixedWindowState> result =
				redisStore.executeAtomically(identifier, operationWith(algorithm, policy, Instant.now()));

		// Assert
		assertTrue(result.getAlgorithmResult().isAllowed());

		// Verificamos que realmente quedo en Redis, no solo en el resultado en memoria
		byte[] rawStored = keyValueStore.get(identifier);
		FixedWindowState decoded = algorithm.getCodec().decode(rawStored);

		assertEquals(1, decoded.getCount());

	}

	@Test
	void shouldMaintainStateAcrossMultipleRequests() {

		// Arrange
		FixedWindowAlgorithmImpl algorithm = new FixedWindowAlgorithmImpl();
		FixedWindowPolicy policy = new FixedWindowPolicy(5, Duration.ofMinutes(1));
		String identifier = uniqueIdentifier();
		Instant now = Instant.now();

		// Act
		redisStore.executeAtomically(identifier, operationWith(algorithm, policy, now));
		redisStore.executeAtomically(identifier, operationWith(algorithm, policy, now));
		AtomicOperationResult<FixedWindowState> thirdResult =
				redisStore.executeAtomically(identifier, operationWith(algorithm, policy, now));

		// Assert
		assertEquals(3, thirdResult.getState().getCount());

	}

	@Test
	void shouldPersistStateAcrossStoreInstances() throws Exception {

		// Arrange
		// Verifica que el estado vive en Redis, no en memoria del proceso:
		// una instancia totalmente nueva de LettuceKeyValueStore/RedisStore,
		// apuntando al mismo Redis, debe ver el estado dejado por la primera.
		FixedWindowAlgorithmImpl algorithm = new FixedWindowAlgorithmImpl();
		FixedWindowPolicy policy = new FixedWindowPolicy(5, Duration.ofMinutes(1));
		String identifier = uniqueIdentifier();
		Instant now = Instant.now();

		redisStore.executeAtomically(identifier, operationWith(algorithm, policy, now));
		redisStore.executeAtomically(identifier, operationWith(algorithm, policy, now));

		try (LettuceKeyValueStore secondKeyValueStore = new LettuceKeyValueStore(redisUrl)) {

			RedisStore secondRedisStore = new RedisStore(secondKeyValueStore);

			// Act
			AtomicOperationResult<FixedWindowState> result =
					secondRedisStore.executeAtomically(identifier, operationWith(algorithm, policy, now));

			// Assert
			assertEquals(3, result.getState().getCount());

		}

	}

	@Test
	void shouldRespectLimitUnderConcurrency() throws Exception {

		// Arrange
		// Limite de 1: si el CAS no funcionara (GET/SET plano sin
		// atomicidad), varios threads podrian leer count=0 al mismo
		// tiempo y todos ser permitidos. Con el CAS, solo UNO debe ganar.

		int threadCount = 20;

		FixedWindowAlgorithmImpl algorithm = new FixedWindowAlgorithmImpl();
		FixedWindowPolicy policy = new FixedWindowPolicy(1, Duration.ofMinutes(1));
		String identifier = uniqueIdentifier();

		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		CyclicBarrier barrier = new CyclicBarrier(threadCount);

		Callable<Boolean> task = () -> {

			barrier.await(); // todos los threads disparan lo mas cerca posible entre si

			AtomicOperationResult<FixedWindowState> result =
					redisStore.executeAtomically(identifier, operationWith(algorithm, policy, Instant.now()));

			return result.getAlgorithmResult().isAllowed();
		};

		List<Callable<Boolean>> tasks = IntStream.range(0, threadCount)
				.mapToObj(i -> task)
				.collect(Collectors.toList());

		// Act
		List<Future<Boolean>> futures = executor.invokeAll(tasks, 30, TimeUnit.SECONDS);

		executor.shutdown();

		long allowedCount = countAllowed(futures);

		// Assert
		assertEquals(1, allowedCount,
				"Con un limite de 1, exactamente 1 request concurrente debe ser ALLOWED "
						+ "(si da mas de 1, el CAS no esta previniendo la condicion de carrera)");

	}

	@Test
	void shouldHandleDifferentIdentifiersConcurrently() throws Exception {

		// Arrange
		// Distintos identifiers no deben interferir entre si: cada uno
		// tiene su propia key en Redis, por lo tanto no deberian competir
		// por el mismo CAS ni bloquearse entre si.

		int identifierCount = 10;

		FixedWindowAlgorithmImpl algorithm = new FixedWindowAlgorithmImpl();
		FixedWindowPolicy policy = new FixedWindowPolicy(1, Duration.ofMinutes(1));

		List<String> identifiers = IntStream.range(0, identifierCount)
				.mapToObj(i -> uniqueIdentifier())
				.collect(Collectors.toList());

		ExecutorService executor = Executors.newFixedThreadPool(identifierCount);
		CyclicBarrier barrier = new CyclicBarrier(identifierCount);

		List<Callable<Boolean>> tasks = identifiers.stream()
				.map(identifier -> (Callable<Boolean>) () -> {

					barrier.await();

					AtomicOperationResult<FixedWindowState> result =
							redisStore.executeAtomically(identifier, operationWith(algorithm, policy, Instant.now()));

					return result.getAlgorithmResult().isAllowed();

				})
				.collect(Collectors.toList());

		// Act
		List<Future<Boolean>> futures = executor.invokeAll(tasks, 30, TimeUnit.SECONDS);

		executor.shutdown();

		long allowedCount = countAllowed(futures);

		// Assert
		// Como cada identifier tiene limite=1 y son independientes entre si,
		// TODOS deberian ser permitidos (a diferencia del test anterior,
		// donde comparten identifier y compiten por el mismo limite).
		assertEquals(identifierCount, allowedCount,
				"Identifiers distintos no deberian competir por el mismo limite");

	}

	@Test
	void shouldAllowRequestsAgainAfterStateExpires() throws InterruptedException {

		// Arrange
		FixedWindowAlgorithmImpl algorithm = new FixedWindowAlgorithmImpl();
		FixedWindowPolicy policy = new FixedWindowPolicy(1, Duration.ofSeconds(1)); // ventana minima permitida
		String identifier = uniqueIdentifier();

		AtomicOperationResult<FixedWindowState> firstResult =
				redisStore.executeAtomically(identifier, operationWith(algorithm, policy, Instant.now()));

		assertTrue(firstResult.getAlgorithmResult().isAllowed());

		AtomicOperationResult<FixedWindowState> deniedResult =
				redisStore.executeAtomically(identifier, operationWith(algorithm, policy, Instant.now()));

		assertTrue(!deniedResult.getAlgorithmResult().isAllowed(), "La segunda request dentro de la misma ventana debe ser denegada");

		// Act - esperamos a que la ventana (y el TTL en Redis) expiren
		Thread.sleep(1_200L);

		AtomicOperationResult<FixedWindowState> afterExpiryResult =
				redisStore.executeAtomically(identifier, operationWith(algorithm, policy, Instant.now()));

		// Assert
		assertTrue(afterExpiryResult.getAlgorithmResult().isAllowed(),
				"Tras expirar la ventana, una nueva request deberia ser permitida");

	}

	// ==================== HELPER (post-test) ====================

	private long countAllowed(List<Future<Boolean>> futures) throws Exception {

		AtomicInteger allowedCount = new AtomicInteger(0);

		for (Future<Boolean> future : futures) {
			if (future.get()) {
				allowedCount.incrementAndGet();
			}
		}

		return allowedCount.get();

	}

}
