package io.github.lauto5.rateLimit.domain.algorithm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.lauto5.rateLimit.domain.algorithmState.FixedWindowState;
import io.github.lauto5.rateLimit.domain.context.AlgorithmContext;
import io.github.lauto5.rateLimit.domain.model.AlgorithmResult;
import io.github.lauto5.rateLimit.domain.model.AllowedDecision;
import io.github.lauto5.rateLimit.domain.model.DeniedDecision;
import io.github.lauto5.rateLimit.domain.policies.FixedWindowPolicy;

public class FixedWindowAlgorithmImplTest {
	
    private FixedWindowAlgorithm algorithm;
    private FixedWindowPolicy defaultPolicy;
    private Instant fixedNow;

    @BeforeEach
    void setUp() {
        algorithm = new FixedWindowAlgorithmImpl();
        defaultPolicy = new FixedWindowPolicy(5, Duration.ofMinutes(1));
        fixedNow = Instant.parse("2026-01-01T10:00:00Z");
    }
    
    // HELPER
    
    private AlgorithmContext contextAt(Instant instant) {
        return new AlgorithmContext(instant);
    }

    private FixedWindowState stateWith(int count, Instant windowStart) {
        return new FixedWindowState(count, windowStart);
    }

    private FixedWindowPolicy policyWith(int limit, Duration duration) {
        return new FixedWindowPolicy(limit, duration);
    }

    private AllowedDecision extractAllowed(AlgorithmResult<FixedWindowState> result) {
        assertTrue(result.isAllowed(), "Expected decision to be ALLOWED");
        return assertInstanceOf(AllowedDecision.class, result.getDecision());
    }

    private DeniedDecision extractDenied(AlgorithmResult<FixedWindowState> result) {
        assertFalse(result.isAllowed(), "Expected decision to be DENIED");
        return assertInstanceOf(DeniedDecision.class, result.getDecision());
    }
    
    // =========================================================================
    
    
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

		assertTrue(result.isAllowed());

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

		FixedWindowPolicy policy = new FixedWindowPolicy(5, expireIn);

		Instant now = Instant.parse("2026-01-01T10:00:00Z");

		AlgorithmContext context = new AlgorithmContext(now);

		FixedWindowState state = new FixedWindowState(5, now);

		// Act

		AlgorithmResult<FixedWindowState> result = algorithm.execute(state, policy, context);

		// Assert

		assertFalse(result.isAllowed());

		assertEquals(5, result.getState().getCount());

		DeniedDecision decision = (DeniedDecision) result.getDecision();

		assertEquals(now, result.getState().getWindowStart());

		assertEquals(expireIn, decision.getRetryAfter());

		assertEquals(expireIn , result.getExpireIn());

		assertEquals(now.plus(expireIn), result.getResetAt());

		assertSame(state, result.getState());

	}

	@Test
	void expiredWindowShouldCreateNewState() {
		
		// Arrange

		FixedWindowAlgorithm algorithm = new FixedWindowAlgorithmImpl();

		FixedWindowPolicy policy = new FixedWindowPolicy(5, Duration.ofMinutes(1));

		Instant now = Instant.parse("2026-01-01T10:00:00Z");

		AlgorithmContext context = new AlgorithmContext(now);

		FixedWindowState state = new FixedWindowState(0, now);
		
		// Act

		AlgorithmResult<FixedWindowState> result = algorithm.execute(state, policy, context);

		AllowedDecision decision = (AllowedDecision) result.getDecision();
		
		// Assert

		assertTrue(result.isAllowed());

		assertEquals(1, result.getState().getCount());

		assertEquals(now, result.getState().getWindowStart());
		
		assertEquals(policy.getLimit() - 1 , decision.getRemaining());

		assertNotSame(state, result.getState());

		
	}
	
	@Test
	void windowShouldExpireExactlyAtBoundary() {
		
		// Arrange

		FixedWindowAlgorithm algorithm = new FixedWindowAlgorithmImpl();

		FixedWindowPolicy policy = new FixedWindowPolicy(5, Duration.ofMinutes(1));

		Instant now = Instant.parse("2026-01-01T10:00:00Z");

		AlgorithmContext context = new AlgorithmContext(now.plus(Duration.ofMinutes(1)));

		FixedWindowState state = new FixedWindowState(0, now);
		
		// Act

		AlgorithmResult<FixedWindowState> result = algorithm.execute(state, policy, context);

		AllowedDecision decision = (AllowedDecision) result.getDecision();

		// Assert

		assertTrue(result.isAllowed());

		assertEquals(1, result.getState().getCount());

		assertEquals(now.plus(Duration.ofMinutes(1)), result.getState().getWindowStart());
		
		assertEquals(policy.getLimit() - 1 , decision.getRemaining());

		assertNotSame(state, result.getState());
		
	}
	
	@Test
	void windowShouldNotExpireBeforeBoundary() {
		
		// Arrange

		FixedWindowAlgorithm algorithm = new FixedWindowAlgorithmImpl();

		FixedWindowPolicy policy = new FixedWindowPolicy(5, Duration.ofMinutes(1));

		Instant now = Instant.parse("2026-01-01T10:00:00Z");

		AlgorithmContext context = new AlgorithmContext(now.plus(Duration.ofSeconds(59)));

		FixedWindowState state = new FixedWindowState(0, now);
		
		// Act

		AlgorithmResult<FixedWindowState> result = algorithm.execute(state, policy, context);

		AllowedDecision decision = (AllowedDecision) result.getDecision();

		// Assert

		assertTrue(result.isAllowed());

		assertEquals(1, result.getState().getCount());

		assertEquals(now, result.getState().getWindowStart());
		
		assertEquals(policy.getLimit() - 1 , decision.getRemaining());

		assertNotSame(state, result.getState());
		
	}

}
