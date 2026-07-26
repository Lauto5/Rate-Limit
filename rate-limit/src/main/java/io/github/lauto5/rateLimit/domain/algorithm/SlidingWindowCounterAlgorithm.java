package io.github.lauto5.rateLimit.domain.algorithm;

import io.github.lauto5.rateLimit.domain.algorithmState.SlidingWindowCounterState;
import io.github.lauto5.rateLimit.domain.policies.SlidingWindowCounterPolicy;

public interface SlidingWindowCounterAlgorithm extends RateLimitAlgorithm<SlidingWindowCounterState, SlidingWindowCounterPolicy> {

}
