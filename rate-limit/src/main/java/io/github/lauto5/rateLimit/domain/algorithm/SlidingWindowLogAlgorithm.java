package io.github.lauto5.rateLimit.domain.algorithm;

import io.github.lauto5.rateLimit.domain.algorithmState.SlidingWindowLogState;
import io.github.lauto5.rateLimit.domain.policies.SlidingWindowLogPolicy;

/**
 * Rate-limiting algorithm based on a sliding-window request log.
 *
 * <p>Each request's timestamp is recorded. A request is only allowed if the number of
 * timestamps falling within the current sliding window is below the configured limit. This
 * provides precise sliding-window semantics at the cost of storing every request timestamp
 * within the window.
 *
 * @see SlidingWindowLogPolicy
 * @see SlidingWindowLogState
 */
public interface SlidingWindowLogAlgorithm extends RateLimitAlgorithm<SlidingWindowLogState, SlidingWindowLogPolicy> {

}
