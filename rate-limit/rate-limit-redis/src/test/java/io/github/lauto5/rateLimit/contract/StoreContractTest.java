package io.github.lauto5.rateLimit.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import io.github.lauto5.rateLimit.application.ports.out.RateLimitStore;
import io.github.lauto5.rateLimit.domain.algorithm.FixedWindowAlgorithmImpl;
import io.github.lauto5.rateLimit.domain.algorithmState.FixedWindowState;
import io.github.lauto5.rateLimit.domain.context.AlgorithmContext;
import io.github.lauto5.rateLimit.domain.policies.FixedWindowPolicy;
import io.github.lauto5.rateLimit.infrastructure.InMemoryStore;
import io.github.lauto5.rateLimit.infrastructure.LettuceTransactionPort;
import io.github.lauto5.rateLimit.infrastructure.RedisStore;

@Testcontainers
public class StoreContractTest {

	private static GenericContainer<?> redisContainer;
	private static LettuceTransactionPort redisPort;
	private static RedisStore redisStore;

	@SuppressWarnings("resource")
	@BeforeAll
	static void startRedis() {

		redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
				.withExposedPorts(6379);

		redisContainer.start();

		String redisUrl =
				"redis://" + redisContainer.getHost() + ":" + redisContainer.getMappedPort(6379);

		redisPort = new LettuceTransactionPort(redisUrl);
		redisStore = new RedisStore(redisPort);

	}

	@AfterAll
	static void stopRedis() throws Exception {
		redisStore.close();
		redisContainer.stop();
	}

	/**
	 * Runs the same sequence against a given store and returns the decisions taken.
	 */
	private List<Boolean> runFixedWindowSequence(RateLimitStore store) {

		FixedWindowAlgorithmImpl algorithm = new FixedWindowAlgorithmImpl();
		FixedWindowPolicy policy = new FixedWindowPolicy(3, Duration.ofMinutes(1));

		Instant base = Instant.now();
		String identifier = "contract-" + UUID.randomUUID();

		List<Boolean> decisions = new ArrayList<>();

		// Window 1 (t): 5 requests against a limit of 3
		for (int i = 0; i < 5; i++) {
			RateLimitAtomicOperation<FixedWindowState, FixedWindowPolicy> op =
					new RateLimitAtomicOperation<>(algorithm, policy, new AlgorithmContext(base));
			decisions.add(store.executeAtomically(identifier, op).getAlgorithmResult().isAllowed());
		}

		// Window 2 (t + 2 min): the window resets, the counter returns to 1
		RateLimitAtomicOperation<FixedWindowState, FixedWindowPolicy> next =
				new RateLimitAtomicOperation<>(algorithm, policy,
						new AlgorithmContext(base.plus(Duration.ofMinutes(2))));
		decisions.add(store.executeAtomically(identifier, next).getAlgorithmResult().isAllowed());

		return decisions;
	}

	@Test
	void fixedWindowDecisionsShouldMatchInMemoryStore() {

		// Arrange
		List<Boolean> inMemory = runFixedWindowSequence(new InMemoryStore());
		List<Boolean> redis = runFixedWindowSequence(redisStore);

		// Assert - both stores must decide the same on the same sequence,
		// with the Redis TTL replacing the InMemoryStore expiry check
		assertEquals(inMemory, redis,
				"el store Redis debe emitir exactamente las mismas decisiones que el InMemoryStore");

		assertEquals(
				Arrays.asList(true, true, true, false, false, true), inMemory,
				"secuencia esperada para un fixed window de limite 3");

	}

	@Test
	void highConcurrencyOnSingleIdentifierShouldNeverExceedLimit() throws Exception {

		// Arrange
		int operators = 1000;
		int limit = 100;

		FixedWindowAlgorithmImpl algorithm = new FixedWindowAlgorithmImpl();
		FixedWindowPolicy policy = new FixedWindowPolicy(limit, Duration.ofMinutes(1));

		// Shared key: all operators act on the same identifier and compete for the same limit
		String identifier = "scale-" + UUID.randomUUID();

		ExecutorService executor = Executors.newFixedThreadPool(100);

		AtomicInteger allowed = new AtomicInteger();
		AtomicInteger denied = new AtomicInteger();
		AtomicInteger errored = new AtomicInteger();

		List<Callable<Void>> tasks = IntStream.range(0, operators)
				.mapToObj(i -> (Callable<Void>) () -> {

					try {
						AtomicOperationResult<FixedWindowState> result = redisStore
								.executeAtomically(identifier,
										new RateLimitAtomicOperation<>(algorithm, policy,
												new AlgorithmContext(Instant.now())));

						if (result.getAlgorithmResult().isAllowed()) {
							allowed.incrementAndGet();
						} else {
							denied.incrementAndGet();
						}
					} catch (IllegalStateException e) {
						// Extreme contention on the shared key: after exhausting the
						// retries the store propagates the failure. It does not count as a permit.
						errored.incrementAndGet();
					}

					return null;
				})
				.collect(Collectors.toList());

		// Act - no synchronization gate: only 100 of the 1000 tasks can be active
		// at a time (fixed pool), a barrier with 1000 parties would never be released.
		// The submission burst and the pool lock retention are simultaneous enough for
		// the goal: 1000 operations contending for a SINGLE key.
		List<Future<Void>> futures = tasks.stream()
				.map(executor::submit)
				.collect(Collectors.toList());

		for (Future<Void> future : futures) {
			future.get();
		}

		executor.shutdown();

		// Assert - the main guarantee: under high concurrency on a SINGLE key,
		// more than `limit` requests are never allowed within a window
		assertTrue(allowed.get() <= limit,
				"con limite=" + limit + " y 1000 operaciones concurrentes, allowed debe ser <= 100 (fue "
						+ allowed.get() + ")");

		assertTrue(allowed.get() + denied.get() + errored.get() == operators);

	}

}