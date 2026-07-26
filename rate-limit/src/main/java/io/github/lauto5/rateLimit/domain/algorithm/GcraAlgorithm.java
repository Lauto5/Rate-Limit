package io.github.lauto5.rateLimit.domain.algorithm;

import io.github.lauto5.rateLimit.domain.algorithmState.GcraState;
import io.github.lauto5.rateLimit.domain.policies.GcraPolicy;

public interface GcraAlgorithm extends RateLimitAlgorithm<GcraState, GcraPolicy> {

}
