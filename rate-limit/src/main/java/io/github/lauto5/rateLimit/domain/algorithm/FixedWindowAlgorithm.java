package io.github.lauto5.rateLimit.domain.algorithm;

import io.github.lauto5.rateLimit.domain.algorithmState.FixedWindowState;
import io.github.lauto5.rateLimit.domain.policies.FixedWindowPolicy;

/**
 * Rate-limiting algorithm that enforces a fixed number of requests within discrete, non
 * overlapping time windows.
 *
 * <p>A <em>Fixed Window</em> algorithm allows up to {@code limit} requests per window of size
 * {@code windowSize}. When the current window expires, a new window starts and the counter
 * resets. This is a simple and memory-efficient strategy, though it does not smooth load across
 * window boundaries.
 *
 * @see FixedWindowPolicy
 * @see FixedWindowState
 */
public interface FixedWindowAlgorithm extends RateLimitAlgorithm<FixedWindowState, FixedWindowPolicy> {

}
