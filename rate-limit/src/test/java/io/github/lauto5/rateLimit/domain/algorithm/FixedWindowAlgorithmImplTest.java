package io.github.lauto5.rateLimit.domain.algorithm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.github.lauto5.rateLimit.domain.algorithmState.FixedWindowState;
import io.github.lauto5.rateLimit.domain.context.AlgorithmContext;
import io.github.lauto5.rateLimit.domain.model.AlgorithmResult;
import io.github.lauto5.rateLimit.domain.model.AllowedDecision;
import io.github.lauto5.rateLimit.domain.model.DeniedDecision;
import io.github.lauto5.rateLimit.domain.policies.FixedWindowPolicy;

public class FixedWindowAlgorithmImplTest {

	@Test
	void firstRequestShouldBeAllowed() {

		// Arrange

		FixedWindowAlgorithm algorithm = new FixedWindowAlgorithmImpl();

		FixedWindowPolicy policy = new FixedWindowPolicy(5, Duration.ofMinutes(1));

		Instant now = Instant.parse("2026-01-01T10:00:00Z");

		AlgorithmContext context = new AlgorithmContext(now);

		FixedWindowState state = new FixedWindowState(0, now);

		// Act

		AlgorithmResult<FixedWindowState> result = algorithm.execute(state, policy, context);

		// Assert

		assertTrue(result.getDecision().isAllowed());

		assertEquals(1, result.getState().getCount());

		AllowedDecision decision = (AllowedDecision) result.getDecision();

		assertEquals(4, decision.getRemaining());

		assertEquals(now, result.getState().getWindowStart());

	}

	@Test
	void requestAfterLimitShouldBeDenied() {

		// Arrange

		FixedWindowAlgorithm algorithm = new FixedWindowAlgorithmImpl();

		Duration expireIn = Duration.ofMinutes(1);

		FixedWindowPolicy policy = new FixedWindowPolicy(1, expireIn);

		Instant now = Instant.parse("2026-01-01T10:00:00Z");

		AlgorithmContext context = new AlgorithmContext(now);

		FixedWindowState state = new FixedWindowState(1, now);

		// Act

		AlgorithmResult<FixedWindowState> result = algorithm.execute(state, policy, context);

		// Assert

		assertTrue(!result.getDecision().isAllowed());

		assertEquals(1, result.getState().getCount());

		DeniedDecision decision = (DeniedDecision) result.getDecision();

		assertEquals(now, result.getState().getWindowStart());

		assertEquals(expireIn, decision.getRetryAfter());

		assertEquals(expireIn , result.getExpireIn());

		assertEquals(now.plus(expireIn), result.getResetAt());

		assertSame(state, result.getState());

	}

	@Test
	void expiredWindowShouldCreateNewState() {
	}

}
