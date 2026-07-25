package io.github.lauto5.rateLimit.domain.algorithm;

import io.github.lauto5.rateLimit.domain.policies.FixedWindowPolicy;
import io.github.lauto5.rateLimit.domain.states.FixedWindowState;

public interface FixedWindowAlgorithm extends RateLimitAlgorithm<FixedWindowState, FixedWindowPolicy> {

}
