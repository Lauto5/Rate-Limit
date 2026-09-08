package io.github.lauto5.rateLimit.testutil;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Testcontainers Redis configuration for the module integration tests.
 *
 * <p>Each integration test class declares its own static {@code @Container} field created with
 * {@link #newRedisContainer()}, so every test class runs against its own container instance:
 * the tests mutate shared state (WATCH/MULTI, keys) and must not share a singleton container.
 */
@Testcontainers
public abstract class RedisContainerTestSupport {

	protected static GenericContainer<?> newRedisContainer() {
		return new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
				.withExposedPorts(6379);
	}

	protected static String redisUrlOf(GenericContainer<?> container) {
		return "redis://" + container.getHost() + ":" + container.getMappedPort(6379);
	}
}