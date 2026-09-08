package io.github.lauto5.rateLimit.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
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
import io.github.lauto5.rateLimit.domain.algorithmState.FixedWindowState;
import io.github.lauto5.rateLimit.domain.context.AlgorithmContext;
import io.github.lauto5.rateLimit.domain.policies.FixedWindowPolicy;
import io.github.lauto5.rateLimit.infrastructure.TransactionBody;
import io.github.lauto5.rateLimit.testdoubles.FakeKeyValueStore;
import io.github.lauto5.rateLimit.testdoubles.StubFixedWindowOperation;

public class RedisStoreUnitTest {

	private FakeKeyValueStore fakeKeyValueStore;
	private RedisStore redisStore;

	// Relies on the agreement key = namespace + ":" + identifier (RedisStore.buildKey)
	private static final String NS = RedisStore.DEFAULT_NAMESPACE + ":";

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
		assertTrue(fakeKeyValueStore.exists(NS + "test-user-1"));

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

	@Test
	void corruptedStoredStateShouldBeResetAndRewritten() {

		// Arrange - pre-load garbage that does not follow the versioned format
		String identifier = "corrupted-user-" + System.nanoTime();
		byte[] garbage = "not-a-valid-versioned-state".getBytes(StandardCharsets.UTF_8);
		fakeKeyValueStore.putRaw(NS + identifier, garbage);

		FixedWindowAlgorithmImpl algorithm = new FixedWindowAlgorithmImpl();
		FixedWindowPolicy policy = new FixedWindowPolicy(5, Duration.ofMinutes(1));
		AlgorithmContext context = new AlgorithmContext(Instant.now());

		RateLimitAtomicOperation<?, ?> operation =
				new RateLimitAtomicOperation<>(algorithm, policy, context);

		// Act
		AtomicOperationResult<?> result = redisStore.executeAtomically(identifier, operation);

		// Assert - corrupted state is treated as missing and rewritten clean
		assertTrue(result.getAlgorithmResult().isAllowed());
		assertTrue(fakeKeyValueStore.exists(NS + identifier));

	}

	@Test
	void corruptedPayloadWithValidHeaderShouldBeResetAndRewritten() {

		// Arrange - the versioned header is valid but the concrete codec payload is
		// unreadable: it passes the header validation and breaks the FixedWindow decode
		String identifier = "corrupted-payload-" + System.nanoTime();
		byte[] header = new byte[] { 'R', 'L', 0x01 };
		byte[] garbagePayload = "not-a-number|also-not-a-number".getBytes(StandardCharsets.UTF_8);
		byte[] faked = new byte[header.length + garbagePayload.length];
		System.arraycopy(header, 0, faked, 0, header.length);
		System.arraycopy(garbagePayload, 0, faked, header.length, garbagePayload.length);
		fakeKeyValueStore.putRaw(NS + identifier, faked);

		FixedWindowAlgorithmImpl algorithm = new FixedWindowAlgorithmImpl();
		FixedWindowPolicy policy = new FixedWindowPolicy(5, Duration.ofMinutes(1));
		AlgorithmContext context = new AlgorithmContext(Instant.now());

		RateLimitAtomicOperation<?, ?> operation =
				new RateLimitAtomicOperation<>(algorithm, policy, context);

		// Act - must not throw NumberFormatException: treated as missing state
		AtomicOperationResult<?> result = redisStore.executeAtomically(identifier, operation);

		// Assert - fail-open policy: rewritten from scratch instead of failing the request
		assertTrue(result.getAlgorithmResult().isAllowed());
		assertTrue(fakeKeyValueStore.exists(NS + identifier));

	}

	// ==================== TTL calculation ====================

	@Test
	void ttlShouldEqualExpiryDistanceFromReferenceClock() {

		// Arrange
		Instant now = Instant.parse("2026-01-01T10:00:00Z");
		Instant expiresAt = now.plus(Duration.ofMinutes(2));
		String identifier = "ttl-positive-" + System.nanoTime();

		StubFixedWindowOperation operation = new StubFixedWindowOperation(
				now, expiresAt, true, new FixedWindowState(1, now));

		// Act
		redisStore.executeAtomically(identifier, operation);

		// Assert - the persisted TTL is exactly expiresAt - now
		assertEquals(120_000L, (long) fakeKeyValueStore.getRemainingTtl(NS + identifier));

	}

	@Test
	void ttlShouldBeFlooredToOneMilliWhenExpireAtEqualsNow() {

		// Arrange
		Instant now = Instant.parse("2026-01-01T10:00:00Z");
		String identifier = "ttl-zero-" + System.nanoTime();

		StubFixedWindowOperation operation = new StubFixedWindowOperation(
				now, now, true, new FixedWindowState(1, now));

		// Act
		redisStore.executeAtomically(identifier, operation);

		// Assert
		assertEquals(1L, (long) fakeKeyValueStore.getRemainingTtl(NS + identifier),
				"Un expireIn de exactamente 0 ms no debe traducirse a ausencia de TTL");

	}

	@Test
	void ttlShouldBeFlooredToOneMilliWhenExpireAtIsBeforeNow() {

		// Arrange
		Instant now = Instant.parse("2026-01-01T10:00:00Z");
		Instant expiresAt = now.minus(Duration.ofSeconds(5));
		String identifier = "ttl-negative-" + System.nanoTime();

		StubFixedWindowOperation operation = new StubFixedWindowOperation(
				now, expiresAt, true, new FixedWindowState(1, now));

		// Act
		redisStore.executeAtomically(identifier, operation);

		// Assert - an expiresAt in the past is treated as immediate expiration, never as invalid TTL
		assertEquals(1L, (long) fakeKeyValueStore.getRemainingTtl(NS + identifier));

	}

@Test
	void ttlShouldFloorSubMillisecondExpiryToOneMilli() {

		// Arrange
		Instant now = Instant.parse("2026-01-01T10:00:00Z");
		Instant subMillisecondExpiry = now.plusNanos(500_000L); // 0.5 ms
		String identifier = "ttl-subms-" + System.nanoTime();

		StubFixedWindowOperation operation = new StubFixedWindowOperation(
				now, subMillisecondExpiry, true, new FixedWindowState(1, now));

		// Act
		redisStore.executeAtomically(identifier, operation);

		// Assert - the millisecond conversion truncates to 0; the floor brings it to 1ms
		assertEquals(1L, (long) fakeKeyValueStore.getRemainingTtl(NS + identifier));

	}

	// ==================== Namespace and validation ====================

	@Test
	void keysShouldBeNamespacedUnderCustomNamespace() {

		// Arrange
		FakeKeyValueStore fake = new FakeKeyValueStore();
		RedisStore store = new RedisStore(fake, "test-app");

		FixedWindowAlgorithmImpl algorithm = new FixedWindowAlgorithmImpl();
		FixedWindowPolicy policy = new FixedWindowPolicy(5, Duration.ofMinutes(1));
		AlgorithmContext context = new AlgorithmContext(Instant.now());
		RateLimitAtomicOperation<?, ?> operation =
				new RateLimitAtomicOperation<>(algorithm, policy, context);

		// Act
		store.executeAtomically("alice", operation);

		// Assert - the key is written under the chosen namespace, not the default one
		assertTrue(fake.exists("test-app:alice"));
		assertTrue(!fake.exists(NS + "alice"));

	}

	@Test
	void defaultNamespaceShouldBeUsedWhenNotSpecified() {

		// Arrange
		FakeKeyValueStore fake = new FakeKeyValueStore();
		RedisStore store = new RedisStore(fake);

		FixedWindowAlgorithmImpl algorithm = new FixedWindowAlgorithmImpl();
		FixedWindowPolicy policy = new FixedWindowPolicy(5, Duration.ofMinutes(1));
		AlgorithmContext context = new AlgorithmContext(Instant.now());
		RateLimitAtomicOperation<?, ?> operation =
				new RateLimitAtomicOperation<>(algorithm, policy, context);

		// Act
		store.executeAtomically("alice", operation);

		// Assert
		assertTrue(fake.exists(NS + "alice"));

	}

	@Test
	void differentNamespacesShouldNeitherCollideNorShareState() {

		// Arrange - two stores share the same physical keyValueStore but different namespaces
		FakeKeyValueStore shared = new FakeKeyValueStore();
		RedisStore storeA = new RedisStore(shared, "app-a");
		RedisStore storeB = new RedisStore(shared, "app-b");

		FixedWindowPolicy policy = new FixedWindowPolicy(1, Duration.ofMinutes(1));

		RateLimitAtomicOperation<?, ?> firstA = new RateLimitAtomicOperation<>(
				new FixedWindowAlgorithmImpl(), policy, new AlgorithmContext(Instant.now()));
		RateLimitAtomicOperation<?, ?> firstB = new RateLimitAtomicOperation<>(
				new FixedWindowAlgorithmImpl(), policy, new AlgorithmContext(Instant.now()));

		// Act - each store consumes (and exhausts) its own key
		assertTrue(storeA.executeAtomically("server-1", firstA).getAlgorithmResult().isAllowed());
		assertTrue(storeB.executeAtomically("server-1", firstB).getAlgorithmResult().isAllowed());

		// Assert - the second operation of each store sees its own exhausted counter
		RateLimitAtomicOperation<?, ?> secondA = new RateLimitAtomicOperation<>(
				new FixedWindowAlgorithmImpl(), policy, new AlgorithmContext(Instant.now()));
		RateLimitAtomicOperation<?, ?> secondB = new RateLimitAtomicOperation<>(
				new FixedWindowAlgorithmImpl(), policy, new AlgorithmContext(Instant.now()));

		assertTrue(!storeA.executeAtomically("server-1", secondA).getAlgorithmResult().isAllowed());
		assertTrue(!storeB.executeAtomically("server-1", secondB).getAlgorithmResult().isAllowed());

	}

	@Test
	void nullOrEmptyIdentifierShouldBeRejected() {

		// Arrange
		FixedWindowPolicy policy = new FixedWindowPolicy(5, Duration.ofMinutes(1));
		RateLimitAtomicOperation<?, ?> operation = new RateLimitAtomicOperation<>(
				new FixedWindowAlgorithmImpl(), policy, new AlgorithmContext(Instant.now()));

		// Act & Assert
		assertThrows(IllegalArgumentException.class,
				() -> redisStore.executeAtomically(null, operation));
		assertThrows(IllegalArgumentException.class,
				() -> redisStore.executeAtomically("", operation));

	}

	@Test
	void oversizedIdentifierShouldBeRejected() {

		// Arrange
		FixedWindowPolicy policy = new FixedWindowPolicy(5, Duration.ofMinutes(1));
		RateLimitAtomicOperation<?, ?> operation = new RateLimitAtomicOperation<>(
				new FixedWindowAlgorithmImpl(), policy, new AlgorithmContext(Instant.now()));
		String oversized = new String(new char[513]).replace('\0', 'x');

		// Act & Assert
		assertThrows(IllegalArgumentException.class,
				() -> redisStore.executeAtomically(oversized, operation));

	}

	// ==================== Infra error vs conflict ====================

	@Test
	void infraErrorShouldPropagateWithoutRetrying() {

		// Arrange
		ThrowingPort port = new ThrowingPort();
		RedisStore store = new RedisStore(port);

		Instant now = Instant.parse("2026-01-01T10:00:00Z");
		StubFixedWindowOperation operation = new StubFixedWindowOperation(
				now, now.plus(Duration.ofMinutes(1)), true, new FixedWindowState(1, now));

		// Act
		RuntimeException thrown = assertThrows(RuntimeException.class,
				() -> store.executeAtomically("infra-error-user", operation));

		// Assert - the infrastructure error is NOT converted into a WATCH conflict:
		// it propagates to the caller and does not trigger any retry
		assertSame(port.failure, thrown);
		assertEquals(1, port.calls.get());
		port.close();

	}

	@Test
	void maxRetriesShouldBeExhaustedOnPersistentConflict() {

		// Arrange
		AlwaysConflictingPort port = new AlwaysConflictingPort();
		RedisStore store = new RedisStore(port);

		Instant now = Instant.parse("2026-01-01T10:00:00Z");
		StubFixedWindowOperation operation = new StubFixedWindowOperation(
				now, now.plus(Duration.ofMinutes(1)), true, new FixedWindowState(1, now));

		// Act
		IllegalStateException thrown = assertThrows(IllegalStateException.class,
				() -> store.executeAtomically("contended-user", operation));

		// Assert - the retry loop is bounded: the attempt budget is exhausted
		// instead of degrading indefinitely
		assertTrue(thrown.getMessage().contains("after 25 attempts"));
		assertEquals(25, port.calls.get());
		port.close();

	}

	@Test
	void interruptedBackoffShouldRestoreFlagAndFail() {

		// Arrange
		AlwaysConflictingPort port = new AlwaysConflictingPort();
		RedisStore store = new RedisStore(port);

		Instant now = Instant.parse("2026-01-01T10:00:00Z");
		StubFixedWindowOperation operation = new StubFixedWindowOperation(
				now, now.plus(Duration.ofMinutes(1)), true, new FixedWindowState(1, now));

		// Act - the thread arrives interrupted: the first non-zero sleep throws
		// InterruptedException, which must become a failure without swallowing the interruption
		Thread.currentThread().interrupt();
		try {
			assertThrows(IllegalStateException.class,
					() -> store.executeAtomically("interrupted-user", operation));
		} finally {
			// Thread.interrupted() returns the previous value and clears it:
			// it only passes if the flag was restored by backoffBeforeRetry
			assertTrue(Thread.interrupted(), "El flag de interrupcion debe conservarse");
		}
		port.close();

	}

	// ==================== TEST DOUBLES ====================

	/**
	 * Port that always simulates a WATCH conflict (returns null).
	 */
	private static final class AlwaysConflictingPort implements RedisTransactionPort {

		private final AtomicInteger calls = new AtomicInteger();

		@Override
		public <T> T executeTransaction(String key, TransactionBody<T> body) {
			calls.incrementAndGet();
			return null;
		}

		@Override
		public byte[] get(String key) {
			return null;
		}

		@Override
		public void close() {
			// no-op
		}
	}

	/**
	 * Port that always throws an infrastructure error (like a network outage).
	 */
	private static final class ThrowingPort implements RedisTransactionPort {

		private final RuntimeException failure = new RuntimeException("redis-down");
		private final AtomicInteger calls = new AtomicInteger();

		@Override
		public <T> T executeTransaction(String key, TransactionBody<T> body) {
			calls.incrementAndGet();
			throw failure;
		}

		@Override
		public byte[] get(String key) {
			return null;
		}

		@Override
		public void close() {
			// no-op
		}
	}

}
