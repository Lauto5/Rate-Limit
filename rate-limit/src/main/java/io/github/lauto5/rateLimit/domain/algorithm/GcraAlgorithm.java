package io.github.lauto5.rateLimit.domain.algorithm;

import io.github.lauto5.rateLimit.domain.policies.GcraPolicy;
import io.github.lauto5.rateLimit.domain.states.GcraState;

public interface GcraAlgorithm extends RateLimitAlgorithm<GcraState, GcraPolicy> {

}
