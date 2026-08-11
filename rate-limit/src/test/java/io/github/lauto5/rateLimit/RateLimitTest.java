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
import io.github.lauto5.rateLimit.domain.algorithmState.FixedWindowState;
import io.github.lauto5.rateLimit.domain.policies.FixedWindowPolicy;
import io.github.lauto5.rateLimit.domain.policies.RateLimitPolicy;

class RateLimitTest {

    private static final Instant FIXED_NOW =
            Instant.parse("2026-01-01T10:00:00Z");

    private static final Duration WINDOW =
            Duration.ofMinutes(1);

    private static final int LIMIT = 3;

    private Clock fixedClock;

    private RateLimit<FixedWindowState , FixedWindowPolicy> rateLimit;

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

    @Test
    void firstRequestShouldBeAllowed() {

        // Arrange
        FixedWindowPolicy policy =
                new FixedWindowPolicy(LIMIT, WINDOW);

        // Act
        RateLimitResult result =
                rateLimit.use("user-1", policy);

        // Assert
        assertTrue(result.isAllowed());
        assertEquals(2, result.getRemaining());
        assertEquals(
                FIXED_NOW.plus(WINDOW),
                result.getResetAt()
        );
    }

    @Test
    void requestAfterLimitShouldBeDenied() {

        // Arrange
    	FixedWindowPolicy policy =
                new FixedWindowPolicy(LIMIT, WINDOW);

        // Act
        rateLimit.use("user-1", policy);
        rateLimit.use("user-1", policy);

        RateLimitResult result =
                rateLimit.use("user-1", policy);

        // Assert
        assertTrue(result.isAllowed());
        assertEquals(0, result.getRemaining());

        RateLimitResult denied =
                rateLimit.use("user-1", policy);

        assertFalse(denied.isAllowed());
        assertEquals(
                Duration.ofMinutes(1),
                denied.getRetryAfter().get()
        );
    }

    @Test
    void differentIdentifiersShouldHaveIndependentLimits() {

        // Arrange
        FixedWindowPolicy policy =
                new FixedWindowPolicy(LIMIT, WINDOW);

        // Act
        RateLimitResult user1 =
                rateLimit.use("user-1", policy);

        RateLimitResult user2 =
                rateLimit.use("user-2", policy);

        // Assert
        assertTrue(user1.isAllowed());
        assertTrue(user2.isAllowed());

        assertEquals(2, user1.getRemaining());
        assertEquals(2, user2.getRemaining());
    }

    @Test
    void requestAfterWindowShouldBeAllowedAgain() {

        // Este test requiere que el Clock pueda avanzar.
        // Con Clock.fixed() no podemos modificar el tiempo.
    }
}