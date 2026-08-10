package io.github.lauto5.rateLimit.infraestructure;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.lauto5.rateLimit.application.ports.out.AtomicOperationResult;
import io.github.lauto5.rateLimit.application.ports.out.StoreState;
import io.github.lauto5.rateLimit.domain.algorithmState.FixedWindowState;
import io.github.lauto5.rateLimit.domain.context.AlgorithmContext;
import io.github.lauto5.rateLimit.domain.model.AlgorithmResult;
import io.github.lauto5.rateLimit.testdoubles.FakeAtomicOperation;

class InMemoryStoreTest {

	private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

	private InMemoryStore store;

	@BeforeEach
	void setUp() {
		store = new InMemoryStore();
	}

	// ============================================================
	// Helpers
	// ============================================================

	private FixedWindowState stateWith(int count) {
		return new FixedWindowState(count, NOW);
	}

	private StoreState<FixedWindowState> storeStateWith(FixedWindowState state, Instant expiresAt) {

		return new StoreState<>(state, expiresAt);
	}

	private AtomicOperationResult<FixedWindowState> operationResult(StoreState<FixedWindowState> state) {

		return new AtomicOperationResult<>(state.getExpiresAt(),
				AlgorithmResult.allowed(state.getState(), 0, state.getExpiresAt(), java.time.Duration.ZERO));
	}

	private FakeAtomicOperation<FixedWindowState> operationReturning(StoreState<FixedWindowState> state) {

		return new FakeAtomicOperation<>(operationResult(state), new AlgorithmContext(NOW));
	}

	// ============================================================
	// Tests
	// ============================================================

	@Nested
	class BasicCases {

		@Test
		void shouldPassNullWhenIdentifierDoesNotExist() {

			// Arrange

			StoreState<FixedWindowState> newState = storeStateWith(stateWith(1), NOW.plusSeconds(60));

			FakeAtomicOperation<FixedWindowState> operation = operationReturning(newState);

			// Act

			AtomicOperationResult<FixedWindowState> result = store.executeAtomically("user-1", operation);

			// Assert

			assertNull(operation.getReceivedState());

			assertNotNull(result);
			assertSame(newState.getState(), result.getAlgorithmResult().getState());
			assertEquals(newState.getExpiresAt(), result.getStoreState().getExpiresAt());

		}

		@Test
		void shouldPersistReturnedState() {

			// Arrange

			StoreState<FixedWindowState> stored = storeStateWith(stateWith(7), NOW.plusSeconds(60));

			FakeAtomicOperation<FixedWindowState> firstOperation = operationReturning(stored);

			FakeAtomicOperation<FixedWindowState> secondOperation = operationReturning(stored);

			// Act

			AtomicOperationResult<FixedWindowState> firstResult = store.executeAtomically("user-1", firstOperation);

			AtomicOperationResult<FixedWindowState> secondResult = store.executeAtomically("user-1", secondOperation);

			// Assert

			assertNotNull(secondOperation.getReceivedState());

			assertSame(stored.getState(), secondOperation.getReceivedState().getState());

			assertEquals(stored.getExpiresAt(), secondOperation.getReceivedState().getExpiresAt());

			assertNotNull(secondResult);

			assertSame(secondResult.getStoreState().getState(), secondResult.getAlgorithmResult().getState());

			assertEquals(stored.getExpiresAt(), secondResult.getStoreState().getExpiresAt());

		}

		@Test
		void shouldReplaceExistingState() {

			// Arrange

			StoreState<FixedWindowState> firstState = storeStateWith(stateWith(1), NOW.plusSeconds(60));

			StoreState<FixedWindowState> secondState = storeStateWith(stateWith(9), NOW.plusSeconds(120));

			FakeAtomicOperation<FixedWindowState> firstOperation = operationReturning(firstState);

			FakeAtomicOperation<FixedWindowState> secondOperation = operationReturning(secondState);

			FakeAtomicOperation<FixedWindowState> readOperation = operationReturning(secondState);

			// Act

			store.executeAtomically("user-1", firstOperation);

			store.executeAtomically("user-1", secondOperation);

			AtomicOperationResult<FixedWindowState> result = store.executeAtomically("user-1", readOperation);

			// Assert

			assertNotNull(readOperation.getReceivedState());

			assertEquals(9, readOperation.getReceivedState().getState().getCount());

			assertEquals(NOW.plusSeconds(120), readOperation.getReceivedState().getExpiresAt());

			assertEquals(9, result.getAlgorithmResult().getState().getCount());

		}
	}

	@Nested
	class ExpirationCases {

		@Test
		void shouldIgnoreExpiredState() {
			// Arrange

			StoreState<FixedWindowState> expiredState = storeStateWith(stateWith(5), NOW.minusSeconds(1));

			StoreState<FixedWindowState> newState = storeStateWith(stateWith(1), NOW.plusSeconds(60));

			FakeAtomicOperation<FixedWindowState> insertOperation = operationReturning(expiredState);

			store.executeAtomically("user-1", insertOperation);

			FakeAtomicOperation<FixedWindowState> operation = operationReturning(newState);

			// Act

			AtomicOperationResult<FixedWindowState> result = store.executeAtomically("user-1", operation);

			// Assert

			assertNull(operation.getReceivedState());

			assertNotNull(result);

			assertSame(newState.getState(), result.getAlgorithmResult().getState());

			assertEquals(newState.getExpiresAt(), result.getStoreState().getExpiresAt());
		}
	}
}