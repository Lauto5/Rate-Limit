package io.github.lauto5.rateLimit.domain.algorithm;

import io.github.lauto5.rateLimit.domain.policies.SlidingWindowLogPolicy;
import io.github.lauto5.rateLimit.domain.states.SlidingWindowLogState;

public interface SlidingWindowLogAlgorithm extends RateLimitAlgorithm<SlidingWindowLogState, SlidingWindowLogPolicy> {

}
