package io.github.lauto5.rateLimit.domain.algorithm;

import io.github.lauto5.rateLimit.domain.policies.TokenBucketPolicy;
import io.github.lauto5.rateLimit.domain.states.TokenBucketState;

public interface TokenBucketAlgorithm extends RateLimitAlgorithm<TokenBucketState, TokenBucketPolicy> {

}
