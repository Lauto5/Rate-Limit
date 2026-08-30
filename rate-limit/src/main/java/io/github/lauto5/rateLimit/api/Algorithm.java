package io.github.lauto5.rateLimit.api;

import io.github.lauto5.rateLimit.domain.algorithm.FixedWindowAlgorithm;
import io.github.lauto5.rateLimit.domain.algorithm.FixedWindowAlgorithmImpl;
import io.github.lauto5.rateLimit.domain.algorithm.LeakyBucketAlgorithm;
import io.github.lauto5.rateLimit.domain.algorithm.LeakyBucketAlgorithmImpl;
import io.github.lauto5.rateLimit.domain.algorithm.SlidingWindowCounterAlgorithm;
import io.github.lauto5.rateLimit.domain.algorithm.SlidingWindowCounterAlgorithmImpl;
import io.github.lauto5.rateLimit.domain.algorithm.SlidingWindowLogAlgorithm;
import io.github.lauto5.rateLimit.domain.algorithm.SlidingWindowLogAlgorithmImpl;
import io.github.lauto5.rateLimit.domain.algorithm.TokenBucketAlgorithm;
import io.github.lauto5.rateLimit.domain.algorithm.TokenBucketAlgorithmImpl;

public class Algorithm {

	public static FixedWindowAlgorithm fixedWindow() {
		return new FixedWindowAlgorithmImpl();
	}
	
	public static TokenBucketAlgorithm tokenBucket() {
		return new TokenBucketAlgorithmImpl();
	}
	
	public static SlidingWindowCounterAlgorithm slidingWindowCounter()
	{
		return new SlidingWindowCounterAlgorithmImpl();
	}
	
	public static SlidingWindowLogAlgorithm slidingWindowLog()
	{
		return new SlidingWindowLogAlgorithmImpl();
	}
	
	public static LeakyBucketAlgorithm leakyBucket()
	{
		return new LeakyBucketAlgorithmImpl();
	}
	
}
