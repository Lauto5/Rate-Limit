package io.github.lauto5.rateLimit.domain.algorithm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.lauto5.rateLimit.application.ports.out.StateCodec;
import io.github.lauto5.rateLimit.domain.algorithmState.SlidingWindowCounterState;
import io.github.lauto5.rateLimit.domain.context.AlgorithmContext;
import io.github.lauto5.rateLimit.domain.model.AlgorithmResult;
import io.github.lauto5.rateLimit.domain.model.AllowedDecision;
import io.github.lauto5.rateLimit.domain.model.DeniedDecision;
import io.github.lauto5.rateLimit.domain.policies.SlidingWindowCounterPolicy;

public class SlidingWindowCounterAlgorithmImplTest {

    private SlidingWindowCounterAlgorithm algorithm;
    private SlidingWindowCounterPolicy standardPolicy;
    private Instant fixedNow;

    @BeforeEach
    void setUp() {
        algorithm = new SlidingWindowCounterAlgorithmImpl();

        standardPolicy =
                new SlidingWindowCounterPolicy(
                        5,
                        Duration.ofMinutes(1),
                        6
                );

        fixedNow =
                Instant.parse("2026-01-01T10:00:00Z");
    }

    // ============================================================
    // Helpers
    // ============================================================

    private AlgorithmContext contextAt(Instant instant) {
        return new AlgorithmContext(instant);
    }

    private SlidingWindowCounterState stateWith(
            Map<Long, Integer> windows) {

        return new SlidingWindowCounterState(windows);
    }

    private SlidingWindowCounterPolicy policyWith(
            int limit,
            Duration windowSize,
            int subWindows) {

        return new SlidingWindowCounterPolicy(
                limit,
                windowSize,
                subWindows
        );
    }

    private AlgorithmResult<SlidingWindowCounterState> executeAlgorithm(
            SlidingWindowCounterState state,
            AlgorithmContext context) {

        return algorithm.execute(
                state,
                standardPolicy,
                context
        );
    }

    private AlgorithmResult<SlidingWindowCounterState> executeAlgorithmWithPolicy(
            SlidingWindowCounterState state,
            SlidingWindowCounterPolicy policy,
            AlgorithmContext context) {

        return algorithm.execute(
                state,
                policy,
                context
        );
    }

    private AllowedDecision extractAllowed(
            AlgorithmResult<SlidingWindowCounterState> result) {

        assertTrue(
                result.isAllowed(),
                "Expected decision to be ALLOWED"
        );

        return assertInstanceOf(
                AllowedDecision.class,
                result.getDecision()
        );
    }

    private DeniedDecision extractDenied(
            AlgorithmResult<SlidingWindowCounterState> result) {

        assertFalse(
                result.isAllowed(),
                "Expected decision to be DENIED"
        );

        return assertInstanceOf(
                DeniedDecision.class,
                result.getDecision()
        );
    }

    private long subWindowMillis(
            SlidingWindowCounterPolicy policy) {

        return policy.getWindowSize().toMillis()
                / policy.getSubWindows();
    }

    private long bucketFor(
            Instant instant,
            long subWindowMillis) {

        return instant.toEpochMilli()
                / subWindowMillis;
    }

    private long standardBucketFor(
            Instant instant) {

        return bucketFor(
                instant,
                subWindowMillis(standardPolicy)
        );
    }

    // ============================================================
    // Basic cases
    // ============================================================

    @Nested
    class BasicCases {

        @Test
        void firstRequestShouldBeAllowed() {

            // Arrange

            SlidingWindowCounterState initialState =
                    stateWith(new HashMap<>());

            AlgorithmContext context =
                    contextAt(fixedNow);

            // Act

            AlgorithmResult<SlidingWindowCounterState> result =
                    executeAlgorithm(
                            initialState,
                            context
                    );

            // Assert

            AllowedDecision decision =
                    extractAllowed(result);

            Map<Long, Integer> windows =
                    result.getState().getWindows();

            assertEquals(
                    4,
                    decision.getRemaining()
            );

            assertEquals(
                    1,
                    windows.size()
            );

            assertEquals(
                    Integer.valueOf(1),
                    windows.get(
                            standardBucketFor(fixedNow)
                    )
            );
        }

        @Test
        void requestAfterLimitShouldBeDenied() {

            // Arrange

            Map<Long, Integer> windows =
                    new HashMap<>();

            windows.put(
                    standardBucketFor(fixedNow),
                    5
            );

            SlidingWindowCounterState initialState =
                    stateWith(windows);

            AlgorithmContext context =
                    contextAt(fixedNow);

            // Act

            AlgorithmResult<SlidingWindowCounterState> result =
                    executeAlgorithm(
                            initialState,
                            context
                    );

            // Assert

            extractDenied(result);

            assertEquals(
                    windows,
                    result.getState().getWindows()
            );
        }
    }

    // ============================================================
    // Remaining cases
    // ============================================================

    @Nested
    class RemainingCases {

        @Test
        void remainingShouldAccountForExistingCounts() {

            // Arrange

            Map<Long, Integer> windows =
                    new HashMap<>();

            windows.put(
                    standardBucketFor(fixedNow),
                    2
            );

            SlidingWindowCounterState initialState =
                    stateWith(windows);

            // Act

            AlgorithmResult<SlidingWindowCounterState> result =
                    executeAlgorithm(
                            initialState,
                            contextAt(fixedNow)
                    );

            // Assert

            AllowedDecision decision =
                    extractAllowed(result);

            assertEquals(
                    2,
                    decision.getRemaining()
            );
        }

        @Test
        void remainingShouldBeZeroAtLimitBoundary() {

            // Arrange

            Map<Long, Integer> windows =
                    new HashMap<>();

            windows.put(
                    standardBucketFor(fixedNow),
                    4
            );

            SlidingWindowCounterState initialState =
                    stateWith(windows);

            // Act

            AlgorithmResult<SlidingWindowCounterState> result =
                    executeAlgorithm(
                            initialState,
                            contextAt(fixedNow)
                    );

            // Assert

            AllowedDecision decision =
                    extractAllowed(result);

            assertEquals(
                    0,
                    decision.getRemaining()
            );
        }

        @Test
        void countsFromMultipleFullyOverlappingBucketsShouldBeSummed() {

            // Arrange

            Map<Long, Integer> windows =
                    new HashMap<>();

            windows.put(
                    standardBucketFor(fixedNow),
                    2
            );

            windows.put(
                    standardBucketFor(
                            fixedNow.minusSeconds(20)
                    ),
                    2
            );

            SlidingWindowCounterState initialState =
                    stateWith(windows);

            // Act

            AlgorithmResult<SlidingWindowCounterState> result =
                    executeAlgorithm(
                            initialState,
                            contextAt(fixedNow)
                    );

            // Assert

            AllowedDecision decision =
                    extractAllowed(result);

            assertEquals(
                    0,
                    decision.getRemaining()
            );
        }
    }

    // ============================================================
    // Weighted overlap cases
    // ============================================================

    @Nested
    class WeightedOverlapCases {

        @Test
        void partialOverlapBucketShouldContributePartialWeight() {

            // Arrange

            AlgorithmContext context =
                    contextAt(
                            fixedNow.plusSeconds(5)
                    );

            long oldestBucket =
                    standardBucketFor(
                            fixedNow.minusSeconds(55)
                    );

            Map<Long, Integer> windows =
                    new HashMap<>();

            windows.put(
                    oldestBucket,
                    4
            );

            SlidingWindowCounterState initialState =
                    stateWith(windows);

            // Act

            AlgorithmResult<SlidingWindowCounterState> result =
                    executeAlgorithm(
                            initialState,
                            context
                    );

            // Assert

            AllowedDecision decision =
                    extractAllowed(result);

            assertEquals(
                    2,
                    decision.getRemaining()
            );
        }

        @Test
        void deniedWhenPartialOverlapWeightAloneReachesLimit() {

            // Arrange

            AlgorithmContext context =
                    contextAt(
                            fixedNow.plusSeconds(5)
                    );

            long oldestBucket =
                    standardBucketFor(
                            fixedNow.minusSeconds(55)
                    );

            Map<Long, Integer> windows =
                    new HashMap<>();

            windows.put(
                    oldestBucket,
                    10
            );

            SlidingWindowCounterState initialState =
                    stateWith(windows);

            // Act

            AlgorithmResult<SlidingWindowCounterState> result =
                    executeAlgorithm(
                            initialState,
                            context
                    );

            // Assert

            extractDenied(result);
        }

        @Test
        void bucketFullyOutsideWindowShouldContributeZeroWeight() {

            // Arrange

            Map<Long, Integer> windows =
                    new HashMap<>();

            windows.put(
                    standardBucketFor(
                            fixedNow.minusSeconds(300)
                    ),
                    5
            );

            SlidingWindowCounterState initialState =
                    stateWith(windows);

            // Act

            AlgorithmResult<SlidingWindowCounterState> result =
                    executeAlgorithm(
                            initialState,
                            contextAt(fixedNow)
                    );

            // Assert

            AllowedDecision decision =
                    extractAllowed(result);

            assertEquals(
                    4,
                    decision.getRemaining()
            );
        }
    }

    // ============================================================
    // Counter cases
    // ============================================================

    @Nested
    class CounterCases {

        @Test
        void allowedRequestShouldIncrementExistingBucket() {

            // Arrange

            Map<Long, Integer> windows =
                    new HashMap<>();

            windows.put(
                    standardBucketFor(fixedNow),
                    2
            );

            SlidingWindowCounterState initialState =
                    stateWith(windows);

            // Act

            AlgorithmResult<SlidingWindowCounterState> result =
                    executeAlgorithm(
                            initialState,
                            contextAt(fixedNow)
                    );

            // Assert

            extractAllowed(result);

            Map<Long, Integer> newWindows =
                    result.getState().getWindows();

            assertEquals(
                    1,
                    newWindows.size()
            );

            assertEquals(
                    Integer.valueOf(3),
                    newWindows.get(
                            standardBucketFor(fixedNow)
                    )
            );
        }

        @Test
        void deniedRequestShouldNotModifyWindows() {

            // Arrange

            Map<Long, Integer> windows =
                    new HashMap<>();

            windows.put(
                    standardBucketFor(fixedNow),
                    5
            );

            SlidingWindowCounterState initialState =
                    stateWith(windows);

            // Act

            AlgorithmResult<SlidingWindowCounterState> result =
                    executeAlgorithm(
                            initialState,
                            contextAt(fixedNow)
                    );

            // Assert

            extractDenied(result);

            Map<Long, Integer> returnedWindows =
                    result.getState().getWindows();

            assertEquals(
                    1,
                    returnedWindows.size()
            );

            assertEquals(
                    Integer.valueOf(5),
                    returnedWindows.get(
                            standardBucketFor(fixedNow)
                    )
            );
        }

        @Test
        void allowedRequestShouldCreateNewBucketWhenTimeMovesToAnotherSubWindow() {

            // Arrange

            Map<Long, Integer> windows =
                    new HashMap<>();

            windows.put(
                    standardBucketFor(fixedNow),
                    2
            );

            SlidingWindowCounterState initialState =
                    stateWith(windows);

            Instant later =
                    fixedNow.plusSeconds(10);

            // Act

            AlgorithmResult<SlidingWindowCounterState> result =
                    executeAlgorithm(
                            initialState,
                            contextAt(later)
                    );

            // Assert

            extractAllowed(result);

            Map<Long, Integer> resultWindows =
                    result.getState().getWindows();

            assertEquals(
                    2,
                    resultWindows.size()
            );

            assertEquals(
                    Integer.valueOf(2),
                    resultWindows.get(
                            standardBucketFor(fixedNow)
                    )
            );

            assertEquals(
                    Integer.valueOf(1),
                    resultWindows.get(
                            standardBucketFor(later)
                    )
            );
        }
    }

    // ============================================================
    // Pruning cases
    // ============================================================

    @Nested
    class PruningCases {

        @Test
        void bucketsOutsideWindowShouldBeRemovedFromResultingState() {

            // Arrange

            long expiredBucket =
                    standardBucketFor(
                            fixedNow.minusSeconds(120)
                    );

            long validBucket =
                    standardBucketFor(
                            fixedNow.minusSeconds(20)
                    );

            Map<Long, Integer> windows =
                    new HashMap<>();

            windows.put(
                    expiredBucket,
                    3
            );

            windows.put(
                    validBucket,
                    1
            );

            SlidingWindowCounterState initialState =
                    stateWith(windows);

            // Act

            AlgorithmResult<SlidingWindowCounterState> result =
                    executeAlgorithm(
                            initialState,
                            contextAt(fixedNow)
                    );

            // Assert

            Map<Long, Integer> resultWindows =
                    result.getState().getWindows();

            assertFalse(
                    resultWindows.containsKey(expiredBucket)
            );

            assertTrue(
                    resultWindows.containsKey(validBucket)
            );
        }

        @Test
        void bucketEndingExactlyAtWindowStartShouldBeRemoved() {

            // Arrange

            /*
             * Window:
             *
             * fixedNow - 60 seconds
             *       ↓
             *
             * El bucket termina exactamente
             * en el inicio de la ventana.
             */

            long expiredBucket =
                    standardBucketFor(
                            fixedNow.minusSeconds(70)
                    );

            Map<Long, Integer> windows =
                    new HashMap<>();

            windows.put(
                    expiredBucket,
                    3
            );

            SlidingWindowCounterState initialState =
                    stateWith(windows);

            // Act

            AlgorithmResult<SlidingWindowCounterState> result =
                    executeAlgorithm(
                            initialState,
                            contextAt(fixedNow)
                    );

            // Assert

            Map<Long, Integer> resultWindows =
                    result.getState().getWindows();

            assertFalse(
                    resultWindows.containsKey(expiredBucket)
            );
        }

        @Test
        void partiallyOverlappingBucketShouldNotBePruned() {

            // Arrange

            Instant now =
                    fixedNow.plusSeconds(5);

            long partialBucket =
                    standardBucketFor(
                            fixedNow.minusSeconds(55)
                    );

            Map<Long, Integer> windows =
                    new HashMap<>();

            windows.put(
                    partialBucket,
                    3
            );

            SlidingWindowCounterState initialState =
                    stateWith(windows);

            // Act

            AlgorithmResult<SlidingWindowCounterState> result =
                    executeAlgorithm(
                            initialState,
                            contextAt(now)
                    );

            // Assert

            Map<Long, Integer> resultWindows =
                    result.getState().getWindows();

            assertTrue(
                    resultWindows.containsKey(partialBucket)
            );
        }
    }

    // ============================================================
    // Retry after cases
    // ============================================================

    @Nested
    class RetryAfterCases {

        @Test
        void retryAfterShouldBeCalculatedFromPartialOldestBucketDecay() {

            // Arrange

            /*
             * Window = 60 seconds
             * Buckets = 6
             * Bucket size = 10 seconds
             * Limit = 5
             *
             * At fixedNow + 5s:
             *
             * Oldest bucket has 10 requests.
             *
             * Its current weight is 0.5.
             *
             * Weighted count:
             *
             * 10 * 0.5 = 5
             *
             * The request is denied.
             *
             * The bucket contribution decreases at:
             *
             * 10 / 10000 = 0.001 request/ms
             *
             * To allow another request:
             *
             * weightedCount <= 4
             *
             * Need to decrease:
             *
             * 5 -> 4
             *
             * Required:
             *
             * 1000ms
             */

            Instant now =
                    fixedNow.plusSeconds(5);

            long oldestBucket =
                    standardBucketFor(
                            fixedNow.minusSeconds(55)
                    );

            Map<Long, Integer> windows =
                    new HashMap<>();

            windows.put(
                    oldestBucket,
                    10
            );

            SlidingWindowCounterState initialState =
                    stateWith(windows);

            // Act

            AlgorithmResult<SlidingWindowCounterState> result =
                    executeAlgorithm(
                            initialState,
                            contextAt(now)
                    );

            // Assert

            DeniedDecision decision =
                    extractDenied(result);

            assertEquals(
                    Duration.ofSeconds(1),
                    decision.getRetryAfter()
            );
        }

        @Test
        void retryAfterShouldCrossMultipleBucketsWhenOldestBucketIsNotEnough() {

            // Arrange

            /*
             * Limit = 5
             *
             * Oldest bucket contributes 1.
             * Next bucket contributes 5.
             *
             * Total = 6.
             *
             * Removing the oldest bucket is not enough
             * to allow a new request.
             *
             * The algorithm must continue evaluating
             * the next bucket.
             */

            SlidingWindowCounterPolicy policy =
                    policyWith(
                            5,
                            Duration.ofMinutes(1),
                            6
                    );

            Instant now =
                    fixedNow.plusSeconds(5);

            long oldestBucket =
                    standardBucketFor(
                            fixedNow.minusSeconds(55)
                    );

            long nextBucket =
                    standardBucketFor(
                            fixedNow.minusSeconds(45)
                    );

            Map<Long, Integer> windows =
                    new HashMap<>();

            windows.put(
                    oldestBucket,
                    2
            );

            windows.put(
                    nextBucket,
                    5
            );

            SlidingWindowCounterState initialState =
                    stateWith(windows);

            // Act

            AlgorithmResult<SlidingWindowCounterState> result =
                    executeAlgorithmWithPolicy(
                            initialState,
                            policy,
                            contextAt(now)
                    );

            // Assert

            DeniedDecision decision =
                    extractDenied(result);

            assertTrue(
                    decision.getRetryAfter()
                            .compareTo(Duration.ofSeconds(5)) > 0
            );
        }

        @Test
        void retryAfterShouldBePositiveWhenLimitIsReached() {

            // Arrange

            Map<Long, Integer> windows =
                    new HashMap<>();

            windows.put(
                    standardBucketFor(fixedNow),
                    5
            );

            SlidingWindowCounterState initialState =
                    stateWith(windows);

            // Act

            AlgorithmResult<SlidingWindowCounterState> result =
                    executeAlgorithm(
                            initialState,
                            contextAt(fixedNow)
                    );

            // Assert

            DeniedDecision decision =
                    extractDenied(result);

            assertTrue(
                    decision.getRetryAfter()
                            .compareTo(Duration.ZERO) > 0
            );
        }
    }

    // ============================================================
    // Expiration cases
    // ============================================================

    @Nested
    class ExpirationCases {

        @Test
        void allowedResultShouldExpireAfterEntireWindow() {

            // Arrange

            SlidingWindowCounterState initialState =
                    stateWith(new HashMap<>());

            // Act

            AlgorithmResult<SlidingWindowCounterState> result =
                    executeAlgorithm(
                            initialState,
                            contextAt(fixedNow)
                    );

            // Assert

            assertEquals(
                    standardPolicy.getWindowSize(),
                    result.getExpireIn()
            );
        }

        @Test
        void deniedResultShouldKeepStateForEntireWindow() {

            // Arrange

            Map<Long, Integer> windows =
                    new HashMap<>();

            windows.put(
                    standardBucketFor(fixedNow),
                    5
            );

            SlidingWindowCounterState initialState =
                    stateWith(windows);

            // Act

            AlgorithmResult<SlidingWindowCounterState> result =
                    executeAlgorithm(
                            initialState,
                            contextAt(fixedNow)
                    );

            // Assert

            extractDenied(result);

            assertEquals(
                    standardPolicy.getWindowSize(),
                    result.getExpireIn()
            );
        }
    }

    // ============================================================
    // State cases
    // ============================================================

    @Nested
    class StateCases {

        @Test
        void allowedRequestShouldReturnNewStateInstance() {

            // Arrange

            Map<Long, Integer> windows =
                    new HashMap<>();

            SlidingWindowCounterState initialState =
                    stateWith(windows);

            // Act

            AlgorithmResult<SlidingWindowCounterState> result =
                    executeAlgorithm(
                            initialState,
                            contextAt(fixedNow)
                    );

            // Assert

            assertNotSame(
                    initialState,
                    result.getState()
            );
        }
    }
    
    @Nested
    class CodecCases {

    	@Test
    	void encodeThenDecodeShouldReturnEquivalentState() {

    		// Arrange
    		StateCodec<SlidingWindowCounterState> codec = algorithm.getCodec();

    		Map<Long, Integer> windows = new HashMap<>();
    		windows.put(standardBucketFor(fixedNow), 3);
    		windows.put(standardBucketFor(fixedNow.minusSeconds(20)), 2);

    		SlidingWindowCounterState original = stateWith(windows);

    		// Act
    		SlidingWindowCounterState decoded = codec.decode(codec.encode(original));

    		// Assert
    		assertEquals(original.getWindows(), decoded.getWindows());

    	}

    	@Test
    	void encodeThenDecodeShouldWorkWithEmptyMap() {

    		// Arrange
    		// Mismo caso critico que en Log: mapa recien creado (vacio)
    		// no debe romper el parsing.
    		StateCodec<SlidingWindowCounterState> codec = algorithm.getCodec();
    		SlidingWindowCounterState original = stateWith(new HashMap<>());

    		// Act
    		SlidingWindowCounterState decoded = codec.decode(codec.encode(original));

    		// Assert
    		assertTrue(decoded.getWindows().isEmpty());

    	}

    	@Test
    	void encodeThenDecodeShouldWorkWithSingleBucket() {

    		// Arrange
    		StateCodec<SlidingWindowCounterState> codec = algorithm.getCodec();

    		Map<Long, Integer> windows = new HashMap<>();
    		windows.put(standardBucketFor(fixedNow), 1);

    		SlidingWindowCounterState original = stateWith(windows);

    		// Act
    		SlidingWindowCounterState decoded = codec.decode(codec.encode(original));

    		// Assert
    		assertEquals(Integer.valueOf(1), decoded.getWindows().get(standardBucketFor(fixedNow)));

    	}

    }
    
}