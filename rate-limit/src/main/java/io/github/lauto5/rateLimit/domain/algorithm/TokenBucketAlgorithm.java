package io.github.lauto5.rateLimit.domain.algorithm;

import io.github.lauto5.rateLimit.domain.algorithmState.TokenBucketState;
import io.github.lauto5.rateLimit.domain.policies.TokenBucketPolicy;

public interface TokenBucketAlgorithm extends RateLimitAlgorithm<TokenBucketState, TokenBucketPolicy> {

}
