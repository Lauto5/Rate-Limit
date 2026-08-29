package io.github.lauto5.rateLimit.api;

import io.github.lauto5.rateLimit.domain.algorithm.FixedWindowAlgorithm;
import io.github.lauto5.rateLimit.domain.algorithm.FixedWindowAlgorithmImpl;
import io.github.lauto5.rateLimit.domain.algorithm.TokenBucketAlgorithm;
import io.github.lauto5.rateLimit.domain.algorithm.TokenBucketAlgorithmImpl;

public class Algorithm {

	public static FixedWindowAlgorithm fixedWindow() {
		return new FixedWindowAlgorithmImpl();
	}
	
	public static TokenBucketAlgorithm tokenBucket() {
		return new TokenBucketAlgorithmImpl();
	}
	
}
