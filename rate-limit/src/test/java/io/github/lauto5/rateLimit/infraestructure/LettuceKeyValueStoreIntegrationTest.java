package io.github.lauto5.rateLimit.infraestructure;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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

}
