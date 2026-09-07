package io.github.lauto5.rateLimit.api;

import io.github.lauto5.rateLimit.domain.algorithm.FixedWindowAlgorithm;
import io.github.lauto5.rateLimit.domain.algorithm.FixedWindowAlgorithmImpl;
import io.github.lauto5.rateLimit.domain.algorithm.GcraAlgorithm;
import io.github.lauto5.rateLimit.domain.algorithm.GcraAlgorithmImpl;
import io.github.lauto5.rateLimit.domain.algorithm.LeakyBucketAlgorithm;
import io.github.lauto5.rateLimit.domain.algorithm.LeakyBucketAlgorithmImpl;
import io.github.lauto5.rateLimit.domain.algorithm.SlidingWindowCounterAlgorithm;
import io.github.lauto5.rateLimit.domain.algorithm.SlidingWindowCounterAlgorithmImpl;
import io.github.lauto5.rateLimit.domain.algorithm.SlidingWindowLogAlgorithm;
import io.github.lauto5.rateLimit.domain.algorithm.SlidingWindowLogAlgorithmImpl;
import io.github.lauto5.rateLimit.domain.algorithm.TokenBucketAlgorithm;
import io.github.lauto5.rateLimit.domain.algorithm.TokenBucketAlgorithmImpl;

/**
 * Static factory for the built-in rate-limiting algorithms.
 *
 * <p>Each factory method returns a concrete implementation of the corresponding
 * {@code RateLimitAlgorithm}. The returned algorithm can be passed to
 * {@link io.github.lauto5.rateLimit.RateLimit#build} along with a store to create a limiter.
 *
 * <p>This class is not intended to be instantiated.
 */
public class Algorithm {

	/**
	 * Returns a new Fixed Window algorithm implementation.
	 *
	 * @return a {@link FixedWindowAlgorithm} that enforces limits over fixed, non-overlapping
	 *         time windows
	 */
	public static FixedWindowAlgorithm fixedWindow() {
		return new FixedWindowAlgorithmImpl();
	}
	
	/**
	 * Returns a new Token Bucket algorithm implementation.
	 *
	 * @return a {@link TokenBucketAlgorithm} that allows bursts up to a capacity and refills
	 *         tokens at a fixed rate
	 */
	public static TokenBucketAlgorithm tokenBucket() {
		return new TokenBucketAlgorithmImpl();
	}
	
	/**
	 * Returns a new Sliding Window Counter algorithm implementation.
	 *
	 * @return a {@link SlidingWindowCounterAlgorithm} that approximates a sliding window by
	 *         weighting fixed sub-window buckets
	 */
	public static SlidingWindowCounterAlgorithm slidingWindowCounter()
	{
		return new SlidingWindowCounterAlgorithmImpl();
	}
	
	/**
	 * Returns a new Sliding Window Log algorithm implementation.
	 *
	 * @return a {@link SlidingWindowLogAlgorithm} that tracks each request timestamp within a
	 *         precisely sliding window
	 */
	public static SlidingWindowLogAlgorithm slidingWindowLog()
	{
		return new SlidingWindowLogAlgorithmImpl();
	}
	
	/**
	 * Returns a new Leaky Bucket algorithm implementation.
	 *
	 * @return a {@link LeakyBucketAlgorithm} that smooths traffic by draining a queue at a
	 *         fixed leak rate
	 */
	public static LeakyBucketAlgorithm leakyBucket()
	{
		return new LeakyBucketAlgorithmImpl();
	}
	
	/**
	 * Returns a new GCRA (Generic Cell Rate Algorithm) implementation.
	 *
	 * @return a {@link GcraAlgorithm} that throttles traffic using an emission interval and a
	 *         burst tolerance
	 */
	public static GcraAlgorithm gcra()
	{
		return new GcraAlgorithmImpl();
	}
	
}
