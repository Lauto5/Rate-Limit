package io.github.lauto5.rateLimit.domain.algorithm;

import io.github.lauto5.rateLimit.domain.algorithmState.SlidingWindowLogState;
import io.github.lauto5.rateLimit.domain.policies.SlidingWindowLogPolicy;

public interface SlidingWindowLogAlgorithm extends RateLimitAlgorithm<SlidingWindowLogState, SlidingWindowLogPolicy> {

}
