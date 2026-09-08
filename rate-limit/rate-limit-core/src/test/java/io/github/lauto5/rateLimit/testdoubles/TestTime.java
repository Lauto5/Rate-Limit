package io.github.lauto5.rateLimit.testdoubles;

import java.time.Instant;

/**
 * Shared clock/time constants for the core module tests.
 *
 * <p>Holds only the generic fixed timestamp, so algorithm tests do not depend on the
 * algorithm-specific {@link FixedWindowTestFixtures}.
 */
public final class TestTime {

	public static final Instant FIXED_NOW =
			Instant.parse("2026-01-01T10:00:00Z");

	private TestTime() {
	}
}