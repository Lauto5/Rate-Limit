package io.github.lauto5.rateLimit.infraestructure;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public class LettuceKeyValueStoreIntegrationTest {

	private static GenericContainer<?> redisContainer;
	private static LettuceKeyValueStore keyValueStore;

	@SuppressWarnings("resource")
	@BeforeAll
	static void startRedis() {

		redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
				.withExposedPorts(6379);

		redisContainer.start();

		String redisUrl = "redis://" + redisContainer.getHost() + ":" + redisContainer.getMappedPort(6379);

		keyValueStore = new LettuceKeyValueStore(redisUrl);

	}

	@AfterAll
	static void stopRedis() throws Exception {
		keyValueStore.close();
		redisContainer.stop();
	}

	// ==================== HELPER ====================

	private String uniqueKey() {
		return "test-key-" + UUID.randomUUID();
	}

	private byte[] bytesOf(String text) {
		return text.getBytes(StandardCharsets.UTF_8);
	}

	// ==================== HELPER ====================

	// ==================== TESTS ====================

	@Test
	void shouldReturnNullWhenKeyDoesNotExist() {

		// Arrange
		String key = uniqueKey();

		// Act
		byte[] result = keyValueStore.get(key);

		// Assert
		assertNull(result);

	}

	@Test
	void shouldGetStoredValue() {

		// Arrange
		String key = uniqueKey();
		byte[] value = bytesOf("hello-redis");

		keyValueStore.compareAndSwap(key, null, value, 60_000L);

		// Act
		byte[] result = keyValueStore.get(key);

		// Assert
		assertArrayEquals(value, result);

	}

	@Test
	void shouldCompareAndSwapWhenKeyDoesNotExist() {

		// Arrange
		String key = uniqueKey();
		byte[] value = bytesOf("initial-value");

		// Act
		boolean applied = keyValueStore.compareAndSwap(key, null, value, 60_000L);

		// Assert
		assertTrue(applied);
		assertArrayEquals(value, keyValueStore.get(key));

	}

	@Test
	void shouldCompareAndSwapWhenExpectedValueMatches() {

		// Arrange
		String key = uniqueKey();
		byte[] initial = bytesOf("initial-value");
		byte[] updated = bytesOf("updated-value");

		keyValueStore.compareAndSwap(key, null, initial, 60_000L);

		// Act
		boolean applied = keyValueStore.compareAndSwap(key, initial, updated, 60_000L);

		// Assert
		assertTrue(applied);
		assertArrayEquals(updated, keyValueStore.get(key));

	}

	@Test
	void shouldRejectCompareAndSwapWhenExpectedValueDoesNotMatch() {

		// Arrange
		String key = uniqueKey();
		byte[] initial = bytesOf("initial-value");
		byte[] wrongExpected = bytesOf("wrong-expected-value");
		byte[] attemptedUpdate = bytesOf("should-not-be-written");

		keyValueStore.compareAndSwap(key, null, initial, 60_000L);

		// Act
		boolean applied = keyValueStore.compareAndSwap(key, wrongExpected, attemptedUpdate, 60_000L);

		// Assert
		assertFalse(applied);
		assertArrayEquals(initial, keyValueStore.get(key)); // el valor original no cambio

	}

	@Test
	void shouldRejectCompareAndSwapWhenExpectedValueIsNullAndKeyExists() {

		// Arrange
		String key = uniqueKey();
		byte[] initial = bytesOf("already-exists");
		byte[] attemptedUpdate = bytesOf("should-not-overwrite");

		keyValueStore.compareAndSwap(key, null, initial, 60_000L);

		// Act - esperabamos que la key NO existiera, pero ya existe
		boolean applied = keyValueStore.compareAndSwap(key, null, attemptedUpdate, 60_000L);

		// Assert
		assertFalse(applied);
		assertArrayEquals(initial, keyValueStore.get(key));

	}

	@Test
	void shouldSetExpirationOnSuccessfulCompareAndSwap() throws InterruptedException {

		// Arrange
		String key = uniqueKey();
		byte[] value = bytesOf("short-lived-value");
		long shortTtlMillis = 200L;

		// Act
		keyValueStore.compareAndSwap(key, null, value, shortTtlMillis);

		// Assert - antes de que expire, el valor esta presente
		assertArrayEquals(value, keyValueStore.get(key));

		// Act - esperamos a que venza el TTL
		Thread.sleep(400L);

		// Assert - Redis elimino la key por su cuenta
		assertNull(keyValueStore.get(key));

	}

	@Nested
	class ConcurrencyCases {

		@Test
		void onlyOneThreadShouldWinTheInitialWriteRace() throws Exception {

			// Arrange
			// Concurrencia media: varios threads compiten por CREAR la key
			// al mismo tiempo (expectedValue = null). Solo uno debe ganar.

			int threadCount = 10;
			String key = uniqueKey();

			ExecutorService executor = Executors.newFixedThreadPool(threadCount);
			CyclicBarrier barrier = new CyclicBarrier(threadCount);

			Callable<Boolean> task = () -> {
				barrier.await();
				byte[] value = bytesOf("writer-" + Thread.currentThread().getId());
				return keyValueStore.compareAndSwap(key, null, value, 60_000L);
			};

			List<Callable<Boolean>> tasks = IntStream.range(0, threadCount)
					.mapToObj(i -> task)
					.collect(Collectors.toList());

			// Act
			List<Future<Boolean>> futures = executor.invokeAll(tasks, 10, TimeUnit.SECONDS);
			executor.shutdown();

			long successCount = countSuccesses(futures);

			// Assert
			assertEquals(1, successCount,
					"Solo un thread deberia lograr crear la key; el resto debe fallar el CAS");
			assertNotNull(keyValueStore.get(key), "La key debe haber quedado escrita por el ganador");

		}

		@Test
		void onlyOneThreadShouldWinWhenReplacingAnExistingValue() throws Exception {

			// Arrange
			// Concurrencia media: la key YA existe con un valor conocido.
			// Varios threads leen ese mismo valor y compiten por reemplazarlo.
			// Solo el primero en llegar a Redis debe tener exito; el resto
			// debe fallar porque el valor ya cambio para cuando intentan.

			int threadCount = 10;
			String key = uniqueKey();
			byte[] sharedExpectedValue = bytesOf("shared-initial-value");

			keyValueStore.compareAndSwap(key, null, sharedExpectedValue, 60_000L);

			ExecutorService executor = Executors.newFixedThreadPool(threadCount);
			CyclicBarrier barrier = new CyclicBarrier(threadCount);

			Callable<Boolean> task = () -> {
				barrier.await();
				byte[] candidateValue = bytesOf("candidate-" + Thread.currentThread().getId());
				return keyValueStore.compareAndSwap(key, sharedExpectedValue, candidateValue, 60_000L);
			};

			List<Callable<Boolean>> tasks = IntStream.range(0, threadCount)
					.mapToObj(i -> task)
					.collect(Collectors.toList());

			// Act
			List<Future<Boolean>> futures = executor.invokeAll(tasks, 10, TimeUnit.SECONDS);
			executor.shutdown();

			long successCount = countSuccesses(futures);

			// Assert
			assertEquals(1, successCount,
					"Solo un thread deberia lograr reemplazar el valor compartido; "
							+ "el resto debe encontrar que el valor ya no coincide");

		}

		@Test
		void concurrentIncrementsWithRetryLoopShouldNotLoseUpdates() throws Exception {

			// Arrange
			// Alta concurrencia: N threads incrementan un contador compartido
			// usando GET -> CAS -> retry si falla (el mismo patron que usa
			// RedisStore internamente). Si el CAS tuviera un agujero, el
			// conteo final seria MENOR a threadCount (updates perdidos).

			int threadCount = 50;
			String key = uniqueKey();

			keyValueStore.compareAndSwap(key, null, bytesOf("0"), 60_000L);

			ExecutorService executor = Executors.newFixedThreadPool(threadCount);
			CyclicBarrier barrier = new CyclicBarrier(threadCount);

			Callable<Void> incrementTask = () -> {

				barrier.await();

				boolean applied = false;

				while (!applied) {

					byte[] currentBytes = keyValueStore.get(key);
					int currentValue = Integer.parseInt(new String(currentBytes, StandardCharsets.UTF_8));

					byte[] newBytes = bytesOf(String.valueOf(currentValue + 1));

					applied = keyValueStore.compareAndSwap(key, currentBytes, newBytes, 60_000L);

				}

				return null;
			};

			List<Callable<Void>> tasks = IntStream.range(0, threadCount)
					.mapToObj(i -> incrementTask)
					.collect(Collectors.toList());

			// Act
			List<Future<Void>> futures = executor.invokeAll(tasks, 30, TimeUnit.SECONDS);
			executor.shutdown();

			for (Future<Void> future : futures) {
				future.get(); // propaga cualquier excepcion ocurrida en los threads
			}

			// Assert
			byte[] finalBytes = keyValueStore.get(key);
			int finalValue = Integer.parseInt(new String(finalBytes, StandardCharsets.UTF_8));

			assertEquals(threadCount, finalValue,
					"Con " + threadCount + " incrementos concurrentes via retry loop, "
							+ "el valor final debe ser exactamente " + threadCount
							+ " (cualquier valor menor indica updates perdidos)");

		}

		// ==================== HELPER (concurrencia) ====================

		private long countSuccesses(List<Future<Boolean>> futures) throws Exception {

			AtomicInteger successCount = new AtomicInteger(0);

			for (Future<Boolean> future : futures) {
				if (future.get()) {
					successCount.incrementAndGet();
				}
			}

			return successCount.get();

		}
	}

	
}