package io.github.lauto5.rateLimit.domain.algorithm;

import io.github.lauto5.rateLimit.domain.policies.LeakyBucketPolicy;
import io.github.lauto5.rateLimit.domain.states.LeakyBucketState;

public interface LeakyBucketAlgorithm extends RateLimitAlgorithm<LeakyBucketState, LeakyBucketPolicy> {

}
