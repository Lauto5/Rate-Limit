package io.github.lauto5.rateLimit.domain.algorithm;

import io.github.lauto5.rateLimit.domain.algorithmState.SlidingWindowCounterState;
import io.github.lauto5.rateLimit.domain.policies.SlidingWindowCounterPolicy;

/**
 * Rate-limiting algorithm that approximates a sliding window using weighted sub-window
 * counters.
 *
 * <p>The window is divided into a fixed number of {@code subWindows}, each holding a request
 * count. The effective count for the current sliding window is computed by weighting buckets
 * that only partially overlap the window. This offers a memory-efficient trade-off between the
 * accuracy of a sliding log and the simplicity of a fixed window.
 *
 * @see SlidingWindowCounterPolicy
 * @see SlidingWindowCounterState
 */
public interface SlidingWindowCounterAlgorithm extends RateLimitAlgorithm<SlidingWindowCounterState, SlidingWindowCounterPolicy> {

}
