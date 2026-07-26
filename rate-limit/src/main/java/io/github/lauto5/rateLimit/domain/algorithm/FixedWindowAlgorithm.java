package io.github.lauto5.rateLimit.domain.algorithm;

import io.github.lauto5.rateLimit.domain.algorithmState.FixedWindowState;
import io.github.lauto5.rateLimit.domain.policies.FixedWindowPolicy;

public interface FixedWindowAlgorithm extends RateLimitAlgorithm<FixedWindowState, FixedWindowPolicy> {

}
