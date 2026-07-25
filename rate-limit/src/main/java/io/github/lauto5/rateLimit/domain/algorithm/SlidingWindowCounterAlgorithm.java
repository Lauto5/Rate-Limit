package io.github.lauto5.rateLimit.domain.algorithm;

import io.github.lauto5.rateLimit.domain.policies.SlidingWindowCounterPolicy;
import io.github.lauto5.rateLimit.domain.states.SlidingWindowCounterState;

public interface SlidingWindowCounterAlgorithm extends RateLimitAlgorithm<SlidingWindowCounterState, SlidingWindowCounterPolicy> {

}
