package io.github.lauto5.rateLimit.application;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.lauto5.rateLimit.application.RateLimitAtomicOperation;
import io.github.lauto5.rateLimit.application.ports.out.AtomicOperationResult;
import io.github.lauto5.rateLimit.application.ports.out.StoreState;
import io.github.lauto5.rateLimit.domain.algorithm.RateLimitAlgorithm;
import io.github.lauto5.rateLimit.domain.algorithmState.FixedWindowState;
import io.github.lauto5.rateLimit.domain.context.AlgorithmContext;
import io.github.lauto5.rateLimit.domain.model.AlgorithmResult;
import io.github.lauto5.rateLimit.domain.policies.FixedWindowPolicy;
import io.github.lauto5.rateLimit.testdoubles.StubRateLimitAlgorithm;

class RateLimitAtomicOperationTest {

    private static final Instant FIXED_NOW =
            Instant.parse("2026-01-01T10:00:00Z");

    private static final Duration WINDOW =
            Duration.ofMinutes(1);

    private static final int DEFAULT_LIMIT = 10;

    private FixedWindowPolicy defaultPolicy;
    private AlgorithmContext defaultContext;

    // ==================== SETUP ====================

    @BeforeEach
    void setUp() {
        defaultPolicy = new FixedWindowPolicy(DEFAULT_LIMIT, WINDOW);
        defaultContext = new AlgorithmContext(FIXED_NOW);
    }

    // ==================== FACTORIES ====================

    private RateLimitAtomicOperation<FixedWindowState, FixedWindowPolicy> operation(
            RateLimitAlgorithm<FixedWindowState, FixedWindowPolicy> algorithm) {

        return new RateLimitAtomicOperation<>(
                algorithm,
                defaultPolicy,
                defaultContext
        );
    }

    private FixedWindowState state(int count) {
        return new FixedWindowState(count, FIXED_NOW);
    }

    private StoreState<FixedWindowState> storeState(
            FixedWindowState state,
            Instant expiresAt) {

        return new StoreState<>(state, expiresAt);
    }

    private AlgorithmResult<FixedWindowState> allowedResult(
            FixedWindowState state) {

        return AlgorithmResult.allowed(
                state,
                DEFAULT_LIMIT,
                FIXED_NOW.plus(WINDOW),
                WINDOW
        );
    }

    private AlgorithmResult<FixedWindowState> allowedResult(
            FixedWindowState state,
            Instant expiresAt) {

        return AlgorithmResult.allowed(
                state,
                DEFAULT_LIMIT,
                expiresAt,
                Duration.between(FIXED_NOW, expiresAt)
        );
    }

    // ==================== TESTS ====================

    @Test
    void shouldCreateInitialStateWhenStoreStateIsNull() {

        // Arrange

        FixedWindowState initialState = state(0);

        AlgorithmResult<FixedWindowState> algorithmResult =
                allowedResult(state(1));

        StubRateLimitAlgorithm<
                FixedWindowState,
                FixedWindowPolicy> stub =
                new StubRateLimitAlgorithm<>(
                        initialState,
                        algorithmResult);

        RateLimitAtomicOperation<
                FixedWindowState,
                FixedWindowPolicy> operation =
                operation(stub);

        // Act

        operation.apply(null);

        // Assert

        assertSame(initialState, stub.getReceivedState());
    }

    @Test
    void shouldUseExistingStateFromStore() {

        // Arrange

        FixedWindowState existingState = state(8);

        StoreState<FixedWindowState> currentStoreState =
                storeState(
                        existingState,
                        FIXED_NOW.plus(WINDOW));

        StubRateLimitAlgorithm<
                FixedWindowState,
                FixedWindowPolicy> stub =
                new StubRateLimitAlgorithm<>(
                        state(0),
                        allowedResult(existingState));

        RateLimitAtomicOperation<
                FixedWindowState,
                FixedWindowPolicy> operation =
                operation(stub);

        // Act

        operation.apply(currentStoreState);

        // Assert

        assertSame(existingState, stub.getReceivedState());
    }

    @Test
    void shouldReturnAlgorithmResultInsideAtomicOperationResult() {

        // Arrange

        FixedWindowState state = state(1);

        AlgorithmResult<FixedWindowState> expected =
                allowedResult(state);

        StubRateLimitAlgorithm<
                FixedWindowState,
                FixedWindowPolicy> stub =
                new StubRateLimitAlgorithm<>(
                        state,
                        expected);

        RateLimitAtomicOperation<
                FixedWindowState,
                FixedWindowPolicy> operation =
                operation(stub);

        // Act

        AtomicOperationResult<FixedWindowState> result =
                operation.apply(null);

        // Assert

        assertSame(expected, result.getAlgorithmResult());
    }

    @Test
    void shouldCreateStoreStateUsingAlgorithmResult() {

        // Arrange

        FixedWindowState state = state(3);

        Instant expiresAt =
                FIXED_NOW.plus(WINDOW);

        AlgorithmResult<FixedWindowState> algorithmResult =
                allowedResult(state, expiresAt);

        StubRateLimitAlgorithm<
                FixedWindowState,
                FixedWindowPolicy> stub =
                new StubRateLimitAlgorithm<>(
                        state,
                        algorithmResult);

        RateLimitAtomicOperation<
                FixedWindowState,
                FixedWindowPolicy> operation =
                operation(stub);

        // Act

        AtomicOperationResult<FixedWindowState> result =
                operation.apply(null);

        // Assert

        StoreState<FixedWindowState> stored =
                result.getStoreState();

        assertNotNull(stored);
        assertSame(state, stored.getState());
        assertEquals(expiresAt, stored.getExpiresAt());
    }
}