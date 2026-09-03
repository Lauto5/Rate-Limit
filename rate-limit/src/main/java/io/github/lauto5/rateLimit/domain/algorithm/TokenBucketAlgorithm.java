package io.github.lauto5.rateLimit.domain.algorithm;

import io.github.lauto5.rateLimit.domain.algorithmState.TokenBucketState;
import io.github.lauto5.rateLimit.domain.policies.TokenBucketPolicy;

/**
 * Rate-limiting algorithm based on a token bucket.
 *
 * <p>The bucket is filled with tokens at a constant {@code refillRate}, up to a maximum
 * {@code capacity}. Each allowed request consumes one token. This allows bursting up to the
 * capacity while imposing a long-term average rate.
 *
 * @see TokenBucketPolicy
 * @see TokenBucketState
 */
public interface TokenBucketAlgorithm extends RateLimitAlgorithm<TokenBucketState, TokenBucketPolicy> {

}
