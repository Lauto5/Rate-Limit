package io.github.lauto5.rateLimit.domain.algorithm;

import io.github.lauto5.rateLimit.domain.algorithmState.LeakyBucketState;
import io.github.lauto5.rateLimit.domain.policies.LeakyBucketPolicy;

/**
 * Rate-limiting algorithm that models a queue which drains at a constant rate.
 *
 * <p>As requests arrive, water is added to the bucket, while water drains out continuously at
 * the configured {@code leakRate}. A request is allowed only if adding it does not overflow the
 * bucket's {@code capacity}. This smooths traffic by enforcing a steady throughput.
 *
 * @see LeakyBucketPolicy
 * @see LeakyBucketState
 */
public interface LeakyBucketAlgorithm extends RateLimitAlgorithm<LeakyBucketState, LeakyBucketPolicy> {

}
