package io.github.lauto5.rateLimit.infrastructure;

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
import org.testcontainers.junit.jupiter.Container;

import io.github.lauto5.rateLimit.application.RateLimitAtomicOperation;
import io.github.lauto5.rateLimit.application.VersionedStateCodec;
import io.github.lauto5.rateLimit.application.ports.out.AtomicOperationResult;
import io.github.lauto5.rateLimit.domain.algorithm.StateCodec;
import io.github.lauto5.rateLimit.domain.algorithm.FixedWindowAlgorithmImpl;
import io.github.lauto5.rateLimit.domain.algorithmState.FixedWindowState;
import io.github.lauto5.rateLimit.domain.context.AlgorithmContext;
import io.github.lauto5.rateLimit.domain.policies.FixedWindowPolicy;
import io.github.lauto5.rateLimit.testutil.RedisContainerTestSupport;

public class RedisStoreIntegrationTest extends RedisContainerTestSupport {

	@SuppressWarnings("resource")
	@Container
	static final GenericContainer<?> REDIS = RedisContainerTestSupport.newRedisContainer();

	private static String redisUrl;
	private static LettuceTransactionPort keyValueStore;
	private static RedisStore redisStore;

	// key = namespace + ":" + identifier (RedisStore.buildKey)
	private static final String NS = RedisStore.DEFAULT_NAMESPACE + ":";

	@BeforeAll
	static void startRedis() {

		redisUrl = RedisContainerTestSupport.redisUrlOf(REDIS);

		keyValueStore = new LettuceTransactionPort(redisUrl);
		redisStore = new RedisStore(keyValueStore);

	}

	@AfterAll
	static void stopRedis() throws Exception {
		redisStore.close();
	}

	// ==================== HELPER ====================

	private String uniqueIdentifier() {
		return "user-" + UUID.randomUUID();
	}

	private RateLimitAtomicOperation<FixedWindowState, FixedWindowPolicy> operationWith(
			FixedWindowAlgorithmImpl algorithm, FixedWindowPolicy policy, Instant now) {
		return new RateLimitAtomicOperation<>(algorithm, policy, new AlgorithmContext(now));
	}

	private StateCodec<FixedWindowState> wireCodec(io.github.lauto5.rateLimit.domain.algorithm.StateCodec<FixedWindowState> codec) {
		return new VersionedStateCodec<>(codec);
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

		// Verify it really stayed in Redis, not only in the in-memory result
		byte[] rawStored = keyValueStore.get(NS + identifier);
		FixedWindowState decoded = wireCodec(algorithm.getCodec()).decode(rawStored);

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
		// Verifies the state lives in Redis, not in the process memory:
		// a completely new adapter/RedisStore instance pointing to the
		// same Redis must see the state left by the first one.
		FixedWindowAlgorithmImpl algorithm = new FixedWindowAlgorithmImpl();
		FixedWindowPolicy policy = new FixedWindowPolicy(5, Duration.ofMinutes(1));
		String identifier = uniqueIdentifier();
		Instant now = Instant.now();

		redisStore.executeAtomically(identifier, operationWith(algorithm, policy, now));
		redisStore.executeAtomically(identifier, operationWith(algorithm, policy, now));

		try (LettuceTransactionPort secondKeyValueStore = new LettuceTransactionPort(redisUrl)) {

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
		// Limit of 1: if WATCH/MULTI/EXEC did not work (plain GET/SET without
		// atomicity), several threads could read count=0 at the same time and
		// all be allowed. With the transaction, only ONE must win.

		int threadCount = 20;

		FixedWindowAlgorithmImpl algorithm = new FixedWindowAlgorithmImpl();
		FixedWindowPolicy policy = new FixedWindowPolicy(1, Duration.ofMinutes(1));
		String identifier = uniqueIdentifier();

		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		CyclicBarrier barrier = new CyclicBarrier(threadCount);

		Callable<Boolean> task = () -> {

			barrier.await(); // all threads fire as close to each other as possible

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
						+ "(si da mas de 1, la transaccion no esta previniendo la condicion de carrera)");

	}

	@Test
	void shouldHandleDifferentIdentifiersConcurrently() throws Exception {

		// Arrange
		// Different identifiers must not interfere with each other: each one has its
		// own key in Redis, therefore they should not compete for the same
		// transaction nor block each other.

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
		// Since each identifier has limit=1 and they are independent of each other,
		// ALL should be allowed (unlike the previous test,
		// where they share the identifier and compete for the same limit).
		assertEquals(identifierCount, allowedCount,
				"Identifiers distintos no deberian competir por el mismo limite");

	}

	@Test
	void shouldAllowRequestsAgainAfterStateExpires() throws InterruptedException {

		// Arrange
		FixedWindowAlgorithmImpl algorithm = new FixedWindowAlgorithmImpl();
		FixedWindowPolicy policy = new FixedWindowPolicy(1, Duration.ofSeconds(1)); // minimum allowed window
		String identifier = uniqueIdentifier();

		AtomicOperationResult<FixedWindowState> firstResult =
				redisStore.executeAtomically(identifier, operationWith(algorithm, policy, Instant.now()));

		assertTrue(firstResult.getAlgorithmResult().isAllowed());

		AtomicOperationResult<FixedWindowState> deniedResult =
				redisStore.executeAtomically(identifier, operationWith(algorithm, policy, Instant.now()));

		assertTrue(!deniedResult.getAlgorithmResult().isAllowed(), "La segunda request dentro de la misma ventana debe ser denegada");

		// Act - wait for the window (and the TTL in Redis) to expire
		AtomicOperationResult<FixedWindowState> afterExpiryResult = deniedResult;
		Instant deadline = Instant.now().plusSeconds(5);
		while (!afterExpiryResult.getAlgorithmResult().isAllowed() && Instant.now().isBefore(deadline)) {
			Thread.sleep(100L);
			afterExpiryResult =
					redisStore.executeAtomically(identifier, operationWith(algorithm, policy, Instant.now()));
		}

		// Assert
		assertTrue(afterExpiryResult.getAlgorithmResult().isAllowed(),
				"Tras expirar la ventana, una nueva request deberia ser permitida");

	}

	@Test
	void corruptedStoredStateShouldBeResetInRedis() {

		// Arrange - pre-load a value that does not follow the versioned format
		String identifier = uniqueIdentifier();
		byte[] garbage = "legacy-raw-state".getBytes(java.nio.charset.StandardCharsets.UTF_8);
		keyValueStore.executeTransaction(NS + identifier,
				current -> new TransactionWrite<>(garbage, 60_000L, null));

		FixedWindowAlgorithmImpl algorithm = new FixedWindowAlgorithmImpl();
		FixedWindowPolicy policy = new FixedWindowPolicy(5, Duration.ofMinutes(1));

		// Act - an update must treat the corrupted state as missing
		AtomicOperationResult<FixedWindowState> result =
				redisStore.executeAtomically(identifier, operationWith(algorithm, policy, Instant.now()));

		// Assert - the state was rewritten in valid versioned format
		assertTrue(result.getAlgorithmResult().isAllowed());
		FixedWindowState decoded = wireCodec(algorithm.getCodec()).decode(keyValueStore.get(NS + identifier));
		assertEquals(1, decoded.getCount());

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