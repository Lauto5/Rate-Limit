package io.github.lauto5.rateLimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.lauto5.rateLimit.api.Algorithm;
import io.github.lauto5.rateLimit.api.Persistence;
import io.github.lauto5.rateLimit.application.ports.in.RateLimitResult;
import io.github.lauto5.rateLimit.domain.policies.FixedWindowPolicy;

class RateLimitTest {

    private static final Instant FIXED_NOW =
            Instant.parse("2026-01-01T10:00:00Z");

    private static final Duration WINDOW =
            Duration.ofMinutes(1);

    private static final int LIMIT = 3;

    private Clock fixedClock;

    private RateLimit<FixedWindowPolicy> rateLimit;

    @BeforeEach
    void setUp() {

        fixedClock = Clock.fixed(
                FIXED_NOW,
                ZoneOffset.UTC
        );

        rateLimit = RateLimit.build(
                Algorithm.fixedWindow(),
                Persistence.inMemory(),
                fixedClock
        );
    }

    // ============================================================
    // Basic behavior
    // ============================================================

    @Test
    void firstRequestShouldBeAllowed() {

        // Arrange

        FixedWindowPolicy policy =
                new FixedWindowPolicy(
                        LIMIT,
                        WINDOW
                );

        // Act

        RateLimitResult result =
                rateLimit.use(
                        "user-1",
                        policy
                );

        // Assert

        assertTrue(result.isAllowed());

        assertEquals(
                2,
                result.getRemaining()
        );

        assertEquals(
                FIXED_NOW.plus(WINDOW),
                result.getResetAt()
        );
    }

    @Test
    void requestShouldBeDeniedAfterLimitIsReached() {

        // Arrange

        FixedWindowPolicy policy =
                new FixedWindowPolicy(
                        LIMIT,
                        WINDOW
                );

        // Act

        RateLimitResult first =
                rateLimit.use("user-1", policy);

        RateLimitResult second =
                rateLimit.use("user-1", policy);

        RateLimitResult third =
                rateLimit.use("user-1", policy);

        RateLimitResult fourth =
                rateLimit.use("user-1", policy);

        // Assert

        assertTrue(first.isAllowed());
        assertEquals(2, first.getRemaining());

        assertTrue(second.isAllowed());
        assertEquals(1, second.getRemaining());

        assertTrue(third.isAllowed());
        assertEquals(0, third.getRemaining());

        assertFalse(fourth.isAllowed());

        assertEquals(
                Duration.ofMinutes(1),
                fourth.getRetryAfter().get()
        );

        assertEquals(
                FIXED_NOW.plus(WINDOW),
                fourth.getResetAt()
        );
    }

    @Test
    void differentIdentifiersShouldHaveIndependentLimits() {

        // Arrange

        FixedWindowPolicy policy =
                new FixedWindowPolicy(
                        LIMIT,
                        WINDOW
                );

        // Act

        RateLimitResult user1 =
                rateLimit.use(
                        "user-1",
                        policy
                );

        RateLimitResult user2 =
                rateLimit.use(
                        "user-2",
                        policy
                );

        // Assert

        assertTrue(user1.isAllowed());
        assertEquals(2, user1.getRemaining());

        assertTrue(user2.isAllowed());
        assertEquals(2, user2.getRemaining());
    }

    // ============================================================
    // API contract
    // ============================================================

    @Test
    void shouldExposeOnlyPolicyTypeThroughPublicApi() {

        // Este test es principalmente de compilación.
        //
        // Si esto compila, la API pública está correctamente
        // encapsulada respecto de AlgorithmState.

        RateLimit<FixedWindowPolicy> rateLimit =
                RateLimit.build(
                        Algorithm.fixedWindow(),
                        Persistence.inMemory(),
                        fixedClock
                );

        FixedWindowPolicy policy =
                new FixedWindowPolicy(
                        LIMIT,
                        WINDOW
                );

        RateLimitResult result =
                rateLimit.use(
                        "user-1",
                        policy
                );

        assertTrue(result.isAllowed());
    }
}