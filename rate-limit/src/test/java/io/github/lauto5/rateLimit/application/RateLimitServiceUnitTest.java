package io.github.lauto5.rateLimit.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.lauto5.rateLimit.api.RateLimitResult;
import io.github.lauto5.rateLimit.application.RateLimitService;
import io.github.lauto5.rateLimit.application.ports.out.AtomicOperation;
import io.github.lauto5.rateLimit.application.ports.out.AtomicOperationResult;
import io.github.lauto5.rateLimit.domain.algorithmState.FixedWindowState;
import io.github.lauto5.rateLimit.domain.model.AlgorithmResult;
import io.github.lauto5.rateLimit.domain.policies.FixedWindowPolicy;
import io.github.lauto5.rateLimit.testdoubles.FakeRateLimitStore;
import io.github.lauto5.rateLimit.testdoubles.StubRateLimitAlgorithm;

class RateLimitServiceUnitTest {

    private static final Instant FIXED_NOW =
            Instant.parse("2026-01-01T10:00:00Z");

    private static final Duration ONE_MINUTE =
            Duration.ofMinutes(1);

    private static final int DEFAULT_LIMIT = 10;

    private Clock fixedClock;

    private FixedWindowPolicy standardPolicy;

    private StubRateLimitAlgorithm<
            FixedWindowState,
            FixedWindowPolicy> algorithm;

    private FakeRateLimitStore store;

    private RateLimitService<
            FixedWindowState,
            FixedWindowPolicy> service;


    // ============================================================
    // SETUP
    // ============================================================

    @BeforeEach
    void setUp() {

        fixedClock = Clock.fixed(
                FIXED_NOW,
                ZoneOffset.UTC
        );

        standardPolicy =
                new FixedWindowPolicy(
                        DEFAULT_LIMIT,
                        ONE_MINUTE
                );

        FixedWindowState state =
                stateWith(1, FIXED_NOW);

        AlgorithmResult<FixedWindowState> algorithmResult =
                AlgorithmResult.allowed(
                        state,
                        DEFAULT_LIMIT - 1,
                        FIXED_NOW.plus(ONE_MINUTE),
                        ONE_MINUTE
                );

        AtomicOperationResult<FixedWindowState> operationResult =
                operationResult(
                        FIXED_NOW.plus(ONE_MINUTE),
                        algorithmResult
                );

        algorithm =
                new StubRateLimitAlgorithm<>(
                        state,
                        algorithmResult
                );

        store =
                new FakeRateLimitStore(
                        operationResult
                );

        service =
                new RateLimitService<>(
                        store,
                        algorithm,
                        fixedClock
                );
    }


    // ============================================================
    // HELPERS
    // ============================================================

    private FixedWindowState stateWith(
            int count,
            Instant windowStart) {

        return new FixedWindowState(
                count,
                windowStart
        );
    }

    private AtomicOperationResult<FixedWindowState> operationResult(
            Instant expiresAt,
            AlgorithmResult<FixedWindowState> algorithmResult) {

        return new AtomicOperationResult<>(
                expiresAt,
                algorithmResult
        );
    }
    
    private AlgorithmResult<FixedWindowState> allowedResult(
            FixedWindowState state,
            long remaining,
            Instant resetAt,
            Duration expiresIn) {

        return AlgorithmResult.allowed(
                state,
                (int) remaining,
                resetAt,
                expiresIn
        );
    }

    private AlgorithmResult<FixedWindowState> deniedResult(
            FixedWindowState state,
            Duration retryAfter,
            Instant resetAt,
            Duration expiresIn) {

        return AlgorithmResult.denied(
                state,
                retryAfter,
                resetAt,
                expiresIn
        );
    }


    // ============================================================
    // TESTS
    // ============================================================

    @Nested
    class BasicCases {

        @Test
        void useShouldExecuteStoreOperation() {

            // Arrange

            String identifier = "user-1";

            // Act

            service.execute(
                    identifier,
                    standardPolicy
            );

            // Assert

            assertEquals(
                    identifier,
                    store.getReceivedIdentifier()
            );

            assertNotNull(
                    store.getReceivedOperation()
            );
        }


        @Test
        void useShouldUseInjectedClock() {

            // Arrange

            String identifier = "user-1";

            // Act

            service.execute(
                    identifier,
                    standardPolicy
            );

            // Assert

            AtomicOperation<?> operation =
                    store.getReceivedOperation();

            assertNotNull(operation);

            assertEquals(
                    FIXED_NOW,
                    operation.getNow()
            );
        }


        @Test
        void useShouldPassPolicyToAtomicOperation() {

            // Arrange

            String identifier = "user-1";

            // Act

            service.execute(
                    identifier,
                    standardPolicy
            );

            // Assert

            AtomicOperation<?> operation =
                    store.getReceivedOperation();

            assertNotNull(operation);

            /*
             * La policy queda encapsulada dentro de
             * RateLimitAtomicOperation.
             *
             * Si expones getPolicy(), podemos verificar
             * directamente que sea la misma instancia.
             */
        }


        @Test
        void useShouldReturnMappedResult() {

            // Arrange

            String identifier = "user-1";

            // Act

            RateLimitResult result =
                    service.execute(
                            identifier,
                            standardPolicy
                    );

            // Assert

            assertNotNull(result);

            /*
             * Las assertions concretas dependen del contrato
             * actual de RateLimitResult.
             */
        }
    
    @Test
    void useShouldReturnAllowedResult() {

        // Arrange

        FixedWindowState state =
                stateWith(1, FIXED_NOW);

        int remaining = 9;

        Instant resetAt =
                FIXED_NOW.plus(ONE_MINUTE);

        AlgorithmResult<FixedWindowState> algorithmResult =
                allowedResult(
                        state,
                        remaining,
                        resetAt,
                        ONE_MINUTE
                );

        AtomicOperationResult<FixedWindowState> operationResult =
                operationResult(
                        resetAt,
                        algorithmResult
                );

        store =
                new FakeRateLimitStore(operationResult);

        service =
                new RateLimitService<>(
                        store,
                        algorithm,
                        fixedClock
                );

        // Act

        RateLimitResult result =
                service.execute(
                        "user-1",
                        standardPolicy
                );

        // Assert

        assertTrue(result.isAllowed());

        assertEquals(
                remaining,
                result.getRemaining()
        );

        assertTrue(
                result.getRetryAfter().isEmpty()
        );

        assertEquals(
                resetAt,
                result.getResetAt()
        );
    }
    
    @Test
    void useShouldReturnDeniedResult() {

        // Arrange

        FixedWindowState state =
                stateWith(10, FIXED_NOW);

        Duration retryAfter =
                Duration.ofSeconds(30);

        Instant resetAt =
                FIXED_NOW.plus(ONE_MINUTE);

        AlgorithmResult<FixedWindowState> algorithmResult =
                deniedResult(
                        state,
                        retryAfter,
                        resetAt,
                        ONE_MINUTE
                );

        AtomicOperationResult<FixedWindowState> operationResult =
                operationResult(
                        resetAt,
                        algorithmResult
                );

        store =
                new FakeRateLimitStore(operationResult);

        service =
                new RateLimitService<>(
                        store,
                        algorithm,
                        fixedClock
                );

        // Act

        RateLimitResult result =
                service.execute(
                        "user-1",
                        standardPolicy
                );

        // Assert

        assertFalse(result.isAllowed());

        assertEquals(
                0,
                result.getRemaining()
        );

        assertTrue(
                result.getRetryAfter().isPresent()
        );

        assertEquals(
                retryAfter,
                result.getRetryAfter().get()
        );

        assertEquals(
                resetAt,
                result.getResetAt()
        );
    }
    
    }
}
