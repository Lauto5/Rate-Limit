package io.github.lauto5.rateLimit.infrastructure;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
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

public class LettuceTransactionPortIntegrationTest {

	private static GenericContainer<?> redisContainer;
	private static LettuceTransactionPort transactionPort;
	private static LettuceTransactionPort secondPort;

	@SuppressWarnings("resource")
	@BeforeAll
	static void startRedis() {

		redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
				.withExposedPorts(6379);

		redisContainer.start();

		String redisUrl = "redis://" + redisContainer.getHost() + ":" + redisContainer.getMappedPort(6379);

		transactionPort = new LettuceTransactionPort(redisUrl);
		secondPort = new LettuceTransactionPort(redisUrl);

	}

	@AfterAll
	static void stopRedis() throws Exception {
		transactionPort.close();
		secondPort.close();
		redisContainer.stop();
	}

	// ==================== HELPER ====================

	private String uniqueKey() {
		return "test-key-" + UUID.randomUUID();
	}

	private byte[] bytesOf(String text) {
		return text.getBytes(StandardCharsets.UTF_8);
	}

	private TransactionWrite<String> write(String value, long ttlMillis) {
		return new TransactionWrite<>(bytesOf(value), ttlMillis, value);
	}

	// ==================== HELPER ====================

	// ==================== TESTS ====================

	@Test
	void shouldReturnNullWhenKeyDoesNotExist() {

		// Act
		byte[] result = transactionPort.get(uniqueKey());

		// Assert
		assertNull(result);

	}

	@Test
	void shouldGetStoredValue() {

		// Arrange
		String key = uniqueKey();
		String value = "hello-redis";

		assertEquals(value, transactionPort.executeTransaction(key, current -> write(value, 60_000L)));

		// Act
		byte[] result = transactionPort.get(key);

		// Assert
		assertArrayEquals(bytesOf(value), result);

	}

	@Test
	void shouldCommitWhenKeyDoesNotExist() {

		// Arrange
		String key = uniqueKey();
		String value = "initial-value";

		// Act
		String result = transactionPort.executeTransaction(key, current -> {
			assertNull(current);
			return write(value, 60_000L);
		});

		// Assert
		assertEquals(value, result);
		assertArrayEquals(bytesOf(value), transactionPort.get(key));

	}

	@Test
	void shouldCommitWhenKeyIsUnchangedSinceWatch() {

		// Arrange
		String key = uniqueKey();
		transactionPort.executeTransaction(key, current -> write("initial-value", 60_000L));

		// Act - transaccion directa: watch -> body -> exec
		String result = transactionPort.executeTransaction(key, current -> {
			assertArrayEquals(bytesOf("initial-value"), current);
			return write("updated-value", 60_000L);
		});

		// Assert
		assertEquals("updated-value", result);
		assertArrayEquals(bytesOf("updated-value"), transactionPort.get(key));

	}

	@Test
	void shouldAbortWhenKeyChangesAfterWatch() throws Exception {

		// Arrange
		String key = uniqueKey();

		// A observa la key (ausente) y queda bloqueada dentro del body mientras B escribe
		CountDownLatch bodyEntered = new CountDownLatch(1);
		CountDownLatch releaseBody = new CountDownLatch(1);

		ExecutorService executor = Executors.newSingleThreadExecutor();

		Future<String> abortedTransaction = executor.submit(() ->
				transactionPort.executeTransaction(key, current -> {
					bodyEntered.countDown();
					try {
						releaseBody.await(10, TimeUnit.SECONDS);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						throw new RuntimeException("Interrumpido esperando la liberacion de la transaccion", e);
					}
					return write("should-not-be-written", 60_000L);
				})
		);

		assertTrue(bodyEntered.await(10, TimeUnit.SECONDS), "A debio entrar al body");
		assertNull(transactionPort.get(key));

		// B (otra conexion) escribe el valor mientras A sigue en transaccion
		secondPort.executeTransaction(key, current -> write("winner-value", 60_000L));

		// Liberamos a A: su EXEC debe abortar porque la key cambio
		releaseBody.countDown();

		// Assert
		String committedByA = abortedTransaction.get(10, TimeUnit.SECONDS);
		executor.shutdown();

		assertNull(committedByA, "La transaccion A debe abortar por el WATCH");
		assertArrayEquals(bytesOf("winner-value"), transactionPort.get(key),
				"El valor escrito por B debe quedar intacto");

	}

	@Test
	void shouldSetExpirationOnSuccessfulCommit() throws InterruptedException {

		// Arrange
		String key = uniqueKey();
		String value = "short-lived-value";
		long shortTtlMillis = 200L;

		// Act
		transactionPort.executeTransaction(key, current -> new TransactionWrite<>(bytesOf(value), shortTtlMillis, value));

		// Assert - antes de que expire, el valor esta presente
		assertArrayEquals(bytesOf(value), transactionPort.get(key));

		// Act - esperamos a que venza el TTL
		Thread.sleep(400L);

		// Assert - Redis elimino la key por su cuenta
		assertNull(transactionPort.get(key));

	}

	@Nested
	class ConcurrencyCases {

		@Test
		void onlyOneThreadShouldWinTheInitialWriteRace() throws Exception {

			// Arrange
			// Varios threads compiten por CREAR la key (estado observado: ausente).
			// El primero que hace EXEC crea la key; el resto aborta porque la key
			// ya no coincide con lo que observaron antes de su EXEC.

			int threadCount = 10;
			String key = uniqueKey();

			ExecutorService executor = Executors.newFixedThreadPool(threadCount);
			CyclicBarrier barrier = new CyclicBarrier(threadCount);

			Callable<Boolean> task = () -> {
				barrier.await();
				String candidate = "writer-" + Thread.currentThread().getId();
				String committed = transactionPort.executeTransaction(
						key, current -> current == null ? write(candidate, 60_000L) : null);
				return committed != null;
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
					"Solo un thread deberia lograr crear la key; el resto debe abortar su transaccion");
			assertNotNull(transactionPort.get(key), "La key debe haber quedado escrita por el ganador");

		}

		@Test
		void onlyOneThreadShouldWinWhenReplacingAnExistingValue() throws Exception {

			// Arrange
			// La key YA existe con un valor conocido y TODOS los threads parten de haber
			// observado ese mismo valor (como en un CAS anclado): el primero en ejecutar
			// EXEC reemplaza el valor; los demas deben abortar porque la key ya no
			// coincide con lo que observaron.

			int threadCount = 10;
			String key = uniqueKey();
			byte[] sharedExpectedValue = bytesOf("shared-initial-value");

			transactionPort.executeTransaction(key, current -> new TransactionWrite<>(sharedExpectedValue, 60_000L, null));

			CyclicBarrier barrier = new CyclicBarrier(threadCount);
			ExecutorService executor = Executors.newFixedThreadPool(threadCount);

			Callable<Boolean> task = () -> {
				barrier.await();
				String candidate = "candidate-" + Thread.currentThread().getId();
				String committed = transactionPort.executeTransaction(key, current -> {
					if (!Arrays.equals(current, sharedExpectedValue)) {
						return null; // la key cambio; esta iteracion aborta sin escribir
					}
					return write(candidate, 60_000L);
				});
				return committed != null;
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
					"Solo un thread deberia lograr reemplazar el valor compartido observado; "
							+ "el resto debe abortar su transaccion");

		}

		@Test
		void concurrentIncrementsWithRetryLoopShouldNotLoseUpdates() throws Exception {

			// Arrange
			// Alta concurrencia: N threads incrementan un contador compartido usando el
			// patron WATCH -> GET -> retry si aborta (el mismo que usa RedisStore
			// internamente). Si hubiera perdida de updates, el conteo final seria MENOR.

			int threadCount = 50;
			String key = uniqueKey();

			transactionPort.executeTransaction(key, current -> write("0", 60_000L));

			ExecutorService executor = Executors.newFixedThreadPool(threadCount);
			CyclicBarrier barrier = new CyclicBarrier(threadCount);

			Callable<Void> incrementTask = () -> {

				barrier.await();

				while (true) {

					Integer committed = transactionPort.executeTransaction(key, current -> {
						int currentValue = Integer.parseInt(new String(current, StandardCharsets.UTF_8));
						return new TransactionWrite<>(
								bytesOf(String.valueOf(currentValue + 1)), 60_000L, currentValue + 1);
					});

					if (committed != null) {
						return null;
					}
				}
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
			byte[] finalBytes = transactionPort.get(key);
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