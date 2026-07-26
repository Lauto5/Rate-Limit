package io.github.lauto5.rateLimit.domain.algorithm;

import io.github.lauto5.rateLimit.domain.algorithmState.LeakyBucketState;
import io.github.lauto5.rateLimit.domain.policies.LeakyBucketPolicy;

public interface LeakyBucketAlgorithm extends RateLimitAlgorithm<LeakyBucketState, LeakyBucketPolicy> {

}
