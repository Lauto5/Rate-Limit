package io.github.lauto5.rateLimit.domain.algorithm;

import java.time.Duration;
import java.time.Instant;

import io.github.lauto5.rateLimit.domain.algorithmState.LeakyBucketState;
import io.github.lauto5.rateLimit.domain.context.AlgorithmContext;
import io.github.lauto5.rateLimit.domain.model.AlgorithmResult;
import io.github.lauto5.rateLimit.domain.policies.LeakyBucketPolicy;

/**
 * Default implementation of {@link LeakyBucketAlgorithm}.
 *
 * <p>Water is added to the bucket with each request and drains continuously at the configured
 * leak rate. A request is allowed whenever adding it does not overflow the bucket capacity.
 * State is serialized as a UTF-8 string containing the current water level and the timestamp
 * of the last leak in epoch milliseconds.
 */
public final class LeakyBucketAlgorithmImpl implements LeakyBucketAlgorithm {

	private static final StateCodec<LeakyBucketState> CODEC = new StateCodec<LeakyBucketState>() {

		@Override
		public byte[] encode(LeakyBucketState state) {

			return PipeDelimitedCodec.encode(
					String.valueOf(state.getWater()),
					String.valueOf(state.getLastLeak())
			);
		}

		@Override
		public LeakyBucketState decode(byte[] data) {

			String[] parts = PipeDelimitedCodec.decode(data);

			double water = Double.parseDouble(parts[0]);
			long lastLeak = Long.parseLong(parts[1]);

			return new LeakyBucketState(water, lastLeak);
		}

	};

	@Override
	public StateCodec<LeakyBucketState> getCodec() {
		return CODEC;
	}
	
	private static final double REQUEST_COST = 1.0;

	@Override
	public AlgorithmResult<LeakyBucketState> execute(LeakyBucketState state, LeakyBucketPolicy policy,
			AlgorithmContext context) {

		Instant now = context.getNow();

		long nowMillis = now.toEpochMilli();

		/*
		 * 1 :
		 *
		 * We compute how much water leaked since
		 * the last update, based on the elapsed time
		 * and the drain rate of the policy.
		 */

		long elapsedMillis = nowMillis - state.getLastLeak();

		double elapsedSeconds = elapsedMillis / 1000.0;

		double leakedWater = elapsedSeconds * policy.getLeakRate();

		double currentWater = Math.max(0.0, state.getWater() - leakedWater);

		/*
		 * 2 :
		 *
		 * If adding this request does not overflow the bucket
		 * capacity, it is allowed and the water is accumulated.
		 */

		if (currentWater + REQUEST_COST <= policy.getCapacity()) {

			double newWater = currentWater + REQUEST_COST;

			LeakyBucketState newState = new LeakyBucketState(newWater, nowMillis);

			int remaining = (int) (policy.getCapacity() - newWater);

			Duration expireIn = timeUntilEmpty(newWater, policy);

			Instant resetAt = now.plus(expireIn);

			return AlgorithmResult.allowed(
					newState,
					remaining,
					resetAt,
					expireIn
			);

		}

		/*
		 * 3 :
		 *
		 * The bucket overflows. It reports how long it will take
		 * to drain enough water to accept a new request.
		 */

		LeakyBucketState deniedState = new LeakyBucketState(currentWater, nowMillis);

		Duration retryAfter = timeUntilCanAccept(currentWater, policy);

		Instant resetAt = now.plus(retryAfter);

		return AlgorithmResult.denied(
				deniedState,
				retryAfter,
				resetAt,
				retryAfter
		);

	}

	@Override
	public LeakyBucketState createInitialState(LeakyBucketPolicy policy, AlgorithmContext context) {
		return new LeakyBucketState(0.0, context.getNow().toEpochMilli());
	}

	private Duration timeUntilCanAccept(double currentWater, LeakyBucketPolicy policy) {

		double excessWater = (currentWater + REQUEST_COST) - policy.getCapacity();

		double secondsNeeded = excessWater / policy.getLeakRate();

		return Duration.ofMillis((long) Math.ceil(secondsNeeded * 1000));
	}

	private Duration timeUntilEmpty(double currentWater, LeakyBucketPolicy policy) {

		if (currentWater <= 0) {
			return Duration.ZERO;
		}

		double secondsUntilEmpty = currentWater / policy.getLeakRate();

		return Duration.ofMillis((long) Math.ceil(secondsUntilEmpty * 1000));
	}

}
