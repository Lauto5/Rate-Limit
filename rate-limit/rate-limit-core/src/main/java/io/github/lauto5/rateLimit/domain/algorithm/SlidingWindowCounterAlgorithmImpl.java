package io.github.lauto5.rateLimit.domain.algorithm;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import io.github.lauto5.rateLimit.application.ports.out.StateCodec;
import io.github.lauto5.rateLimit.domain.algorithmState.SlidingWindowCounterState;
import io.github.lauto5.rateLimit.domain.context.AlgorithmContext;
import io.github.lauto5.rateLimit.domain.model.AlgorithmResult;
import io.github.lauto5.rateLimit.domain.policies.SlidingWindowCounterPolicy;

/**
 * Default implementation of {@link SlidingWindowCounterAlgorithm}.
 *
 * <p>The window is divided into a fixed number of sub-windows, each holding a request count.
 * Buckets that have fully left the window are pruned, and the effective count is computed by
 * weighting buckets that only partially overlap the current window. A request is allowed while
 * the weighted count is below the configured limit. State is serialized as a UTF-8 string
 * containing the count of each relevant bucket, keyed by its window bucket index.
 */
public final class SlidingWindowCounterAlgorithmImpl
		implements SlidingWindowCounterAlgorithm {

	private static final StateCodec<SlidingWindowCounterState> CODEC = new StateCodec<SlidingWindowCounterState>() {

		@Override
		public byte[] encode(SlidingWindowCounterState state) {

			String raw = state.getWindows().entrySet().stream()
					.map(entry -> entry.getKey() + ":" + entry.getValue())
					.collect(Collectors.joining(","));

			return raw.getBytes(StandardCharsets.UTF_8);
		}

		@Override
		public SlidingWindowCounterState decode(byte[] data) {

			String raw = new String(data, StandardCharsets.UTF_8);

			Map<Long, Integer> windows = new HashMap<>();

			if (!raw.isEmpty()) {
				for (String entry : raw.split(",")) {
					String[] keyValue = entry.split(":");
					windows.put(Long.parseLong(keyValue[0]), Integer.parseInt(keyValue[1]));
				}
			}

			return new SlidingWindowCounterState(windows);
		}

	};

	@Override
	public StateCodec<SlidingWindowCounterState> getCodec() {
		return CODEC;
	}
	
	@Override
	public AlgorithmResult<SlidingWindowCounterState> execute(
			SlidingWindowCounterState state,
			SlidingWindowCounterPolicy policy,
			AlgorithmContext context) {

		Instant now = context.getNow();

		long subWindowMillis = subWindowDurationMillis(policy);

		long currentBucket =
				bucketFor(now, subWindowMillis);

		long windowStartMillis =
				now.toEpochMilli()
						- policy.getWindowSize().toMillis();

		/*
		 * 1.
		 *
		 * We remove the completely expired buckets.
		 */
		Map<Long, Integer> relevantWindows =
				pruneExpiredWindows(
						state.getWindows(),
						windowStartMillis,
						subWindowMillis
				);

		/*
		 * 2.
		 *
		 * We compute the weighted count within
		 * the current sliding window.
		 */
		double weightedCount =
				weightedCount(
						relevantWindows,
						windowStartMillis,
						subWindowMillis
				);

		/*
		 * 3.
		 *
		 * If there is enough capacity for a new request,
		 * the current bucket is incremented.
		 */
		if (weightedCount < policy.getLimit()) {

			Map<Long, Integer> newWindows =
					incrementBucket(
							relevantWindows,
							currentBucket
					);

			SlidingWindowCounterState newState =
					new SlidingWindowCounterState(newWindows);

			double weightedCountAfter =
					weightedCount + 1;

			int remaining =
					Math.max(
							0,
							(int) Math.floor(
									policy.getLimit()
											- weightedCountAfter
							)
					);

			/*
			 * The state must survive for at least
			 * one full window since the last request.
			 */
			Duration expireIn =
					policy.getWindowSize();

			/*
			 * For a sliding window, resetAt represents
			 * when the current request stops contributing
			 * entirely to the limit.
			 */
			Instant resetAt =
					now.plus(expireIn);

			return AlgorithmResult.allowed(
					newState,
					remaining,
					resetAt,
					expireIn
			);
		}

		/*
		 * 4.
		 *
		 * Request denied.
		 *
		 * We compute the earliest instant at which
		 * a new request could be allowed again.
		 */
		SlidingWindowCounterState deniedState =
				new SlidingWindowCounterState(
						relevantWindows
				);

		Duration retryAfter =
				timeUntilBelowLimit(
						relevantWindows,
						now,
						policy
				);

		Instant resetAt =
				now.plus(retryAfter);

		/*
		 * IMPORTANT:
		 *
		 * retryAfter does NOT represent the TTL of the state.
		 *
		 * Other relevant buckets may still exist,
		 * therefore the state must survive for
		 * the whole window.
		 */
		Duration expireIn =
				policy.getWindowSize();

		return AlgorithmResult.denied(
				deniedState,
				retryAfter,
				resetAt,
				expireIn
		);
	}

	@Override
	public SlidingWindowCounterState createInitialState(
			SlidingWindowCounterPolicy policy,
			AlgorithmContext context) {

		return new SlidingWindowCounterState(
				new HashMap<>()
		);
	}

	// ============================================================
	// Bucket calculations
	// ============================================================

	private long subWindowDurationMillis(
			SlidingWindowCounterPolicy policy) {

		return policy.getWindowSize().toMillis()
				/ policy.getSubWindows();
	}

	private long bucketFor(
			Instant instant,
			long subWindowMillis) {

		return instant.toEpochMilli()
				/ subWindowMillis;
	}

	// ============================================================
	// Window pruning
	// ============================================================

	private Map<Long, Integer> pruneExpiredWindows(
			Map<Long, Integer> windows,
			long windowStartMillis,
			long subWindowMillis) {

		Map<Long, Integer> pruned =
				new HashMap<>();

		for (Map.Entry<Long, Integer> entry
				: windows.entrySet()) {

			long bucketEndMillis =
					(entry.getKey() + 1)
							* subWindowMillis;

			/*
			 * The bucket remains relevant if any part of it
			 * still overlaps the current window.
			 */
			if (bucketEndMillis > windowStartMillis) {

				pruned.put(
						entry.getKey(),
						entry.getValue()
				);
			}
		}

		return pruned;
	}

	// ============================================================
	// Weighted count
	// ============================================================

	private double weightedCount(
			Map<Long, Integer> windows,
			long windowStartMillis,
			long subWindowMillis) {

		double total = 0.0;

		for (Map.Entry<Long, Integer> entry
				: windows.entrySet()) {

			long bucketStartMillis =
					entry.getKey()
							* subWindowMillis;

			long bucketEndMillis =
					bucketStartMillis
							+ subWindowMillis;

			double weight =
					overlapWeight(
							bucketStartMillis,
							bucketEndMillis,
							windowStartMillis,
							subWindowMillis
					);

			total +=
					entry.getValue()
							* weight;
		}

		return total;
	}

	private double overlapWeight(
			long bucketStartMillis,
			long bucketEndMillis,
			long windowStartMillis,
			long subWindowMillis) {

		/*
		 * Bucket entirely inside the window.
		 */
		if (bucketStartMillis >= windowStartMillis) {
			return 1.0;
		}

		/*
		 * Bucket partially overlapping the window.
		 */
		long overlapMillis =
				bucketEndMillis
						- windowStartMillis;

		return overlapMillis
				/ (double) subWindowMillis;
	}

	// ============================================================
	// State mutation
	// ============================================================

	private Map<Long, Integer> incrementBucket(
			Map<Long, Integer> windows,
			long bucket) {

		Map<Long, Integer> newWindows =
				new HashMap<>(windows);

		int currentValue =
				newWindows.getOrDefault(
						bucket,
						0
				);

		newWindows.put(
				bucket,
				currentValue + 1
		);

		return newWindows;
	}

	// ============================================================
	// Retry calculation
	// ============================================================

	private Duration timeUntilBelowLimit(
			Map<Long, Integer> windows,
			Instant now,
			SlidingWindowCounterPolicy policy) {

		if (windows.isEmpty()) {
			return Duration.ZERO;
		}

		long subWindowMillis =
				subWindowDurationMillis(policy);

		long windowMillis =
				policy.getWindowSize().toMillis();

		long nowMillis =
				now.toEpochMilli();

		long currentWindowStart =
				nowMillis - windowMillis;

		/*
		 * We simulate the evolution of the window start.
		 *
		 * We do not advance millisecond by millisecond.
		 *
		 * We advance in segments defined by the bucket
		 * boundaries, because that is where the
		 * composition of the window changes.
		 */
		long simulatedWindowStart =
				currentWindowStart;

		Map<Long, Integer> remainingWindows =
				new HashMap<>(windows);

		while (!remainingWindows.isEmpty()) {

			double currentCount =
					weightedCount(
							remainingWindows,
							simulatedWindowStart,
							subWindowMillis
					);

			/*
			 * There must be room for a new
			 * request.
			 */
			if (currentCount < policy.getLimit()) {

				long elapsedMillis =
						simulatedWindowStart
								- currentWindowStart;

				return Duration.ofMillis(
						Math.max(elapsedMillis, 0)
				);
			}

			long oldestBucket =
					findOldestBucket(
							remainingWindows
					);

			long oldestBucketStart =
					oldestBucket
							* subWindowMillis;

			long oldestBucketEnd =
					oldestBucketStart
							+ subWindowMillis;

			int oldestCount =
					remainingWindows.get(oldestBucket);

			/*
			 * The oldest bucket is partially
			 * inside the window.
			 *
			 * While the window start advances inside
			 * the bucket, its contribution decreases linearly.
			 */
			double currentWeight =
					overlapWeight(
							oldestBucketStart,
							oldestBucketEnd,
							simulatedWindowStart,
							subWindowMillis
					);

			double currentContribution =
					oldestCount
							* currentWeight;

			/*
			 * We need to reduce the count until:
			 *
			 * count < limit
			 *
			 * Since the next request adds 1, we need:
			 *
			 * count <= limit - 1
			 */
			double requiredReduction =
					currentCount
							- (policy.getLimit() - 1);

			/*
			 * The oldest bucket disappears at a linear
			 * rate of:
			 *
			 * oldestCount / subWindowMillis
			 */
			double decayRatePerMillis =
					oldestCount
							/ (double) subWindowMillis;

			if (decayRatePerMillis > 0) {

				long millisNeeded =
						(long) Math.ceil(
								requiredReduction
										/ decayRatePerMillis
						);

				long millisUntilBucketExpires =
						oldestBucketEnd
								- simulatedWindowStart;

				/*
				 * If we can drop below the limit before the
				 * bucket fully expires, we find
				 * the retryAfter directly.
				 */
				if (millisNeeded
						< millisUntilBucketExpires) {

					long elapsedMillis =
							(simulatedWindowStart
									- currentWindowStart)
									+ millisNeeded;

					return Duration.ofMillis(
							Math.max(elapsedMillis, 0)
					);
				}
			}

			/*
			 * The decrease of the current bucket
			 * was not enough.
			 *
			 * We advance until it fully expires and
			 * continue with the next bucket.
			 */
			simulatedWindowStart =
					oldestBucketEnd;

			remainingWindows.remove(
					oldestBucket
			);
		}

		/*
		 * If all buckets have disappeared,
		 * the next request will be allowed.
		 */
		long elapsedMillis =
				simulatedWindowStart
						- currentWindowStart;

		return Duration.ofMillis(
				Math.max(elapsedMillis, 0)
		);
	}

	// ============================================================
	// Utilities
	// ============================================================

	private long findOldestBucket(
			Map<Long, Integer> windows) {

		long oldest =
				Long.MAX_VALUE;

		for (long bucket
				: windows.keySet()) {

			if (bucket < oldest) {
				oldest = bucket;
			}
		}

		return oldest;
	}
}
