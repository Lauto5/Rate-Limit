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
	 * Ejecta la misma secuencia contra un store dado y devuelve las decisiones tomadas.
	 */
	private List<Boolean> runFixedWindowSequence(RateLimitStore store) {

		FixedWindowAlgorithmImpl algorithm = new FixedWindowAlgorithmImpl();
		FixedWindowPolicy policy = new FixedWindowPolicy(3, Duration.ofMinutes(1));

		Instant base = Instant.now();
		String identifier = "contract-" + UUID.randomUUID();

		List<Boolean> decisions = new ArrayList<>();

		// Ventana 1 (t): 5 requests contra un limite de 3
		for (int i = 0; i < 5; i++) {
			RateLimitAtomicOperation<FixedWindowState, FixedWindowPolicy> op =
					new RateLimitAtomicOperation<>(algorithm, policy, new AlgorithmContext(base));
			decisions.add(store.executeAtomically(identifier, op).getAlgorithmResult().isAllowed());
		}

		// Ventana 2 (t + 2 min): la ventana reinicia, el contador vuelve a 1
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

		// Assert - ambos stores deben decidir igual sobre la misma secuencia,
		// con el TTL de Redis sustituyendo el chequeo de expiracion del InMemoryStore
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

		// Key compartida: todos operan sobre el mismo identifier y compiten por el mismo limite
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
						// Contestacion extrema por la key compartida: tras agotar los
						// reintentos el store propaga el fallo. No cuenta como permiso.
						errored.incrementAndGet();
					}

					return null;
				})
				.collect(Collectors.toList());

		// Act - sin gate de sincronizacion: solo 100 de las 1000 tareas pueden estar activas
		// a la vez (pool fijo), una barrera con 1000 parties nunca se tripularia. La rafaga de
		// submision y la retencion del lock del pool son suficientemente simultaneas para el
		// objetivo: que 1000 operaciones compitan por UNA sola key.
		List<Future<Void>> futures = tasks.stream()
				.map(executor::submit)
				.collect(Collectors.toList());

		for (Future<Void> future : futures) {
			future.get();
		}

		executor.shutdown();

		// Assert - la garantia principal: bajo alta concurrencia sobre UNA sola key,
		// jamas se permite mas de `limit` requests dentro de una ventana
		assertTrue(allowed.get() <= limit,
				"con limite=" + limit + " y 1000 operaciones concurrentes, allowed debe ser <= 100 (fue "
						+ allowed.get() + ")");

		assertTrue(allowed.get() + denied.get() + errored.get() == operators);

	}

}