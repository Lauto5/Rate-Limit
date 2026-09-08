package io.github.lauto5.rateLimit.domain.algorithm;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.github.lauto5.rateLimit.application.ports.out.StateCodec;
import io.github.lauto5.rateLimit.domain.algorithmState.SlidingWindowLogState;
import io.github.lauto5.rateLimit.domain.context.AlgorithmContext;
import io.github.lauto5.rateLimit.domain.model.AlgorithmResult;
import io.github.lauto5.rateLimit.domain.policies.SlidingWindowLogPolicy;

/**
 * Default implementation of {@link SlidingWindowLogAlgorithm}.
 *
 * <p>Timestamps that fall outside the current sliding window are discarded. A request is
 * allowed whenever the number of remaining timestamps is below the configured limit, and the
 * timestamp of the request is then recorded. State is serialized as a UTF-8 string containing
 * the list of request timestamps in epoch milliseconds.
 */
public final class SlidingWindowLogAlgorithmImpl implements SlidingWindowLogAlgorithm {

	private static final StateCodec<SlidingWindowLogState> CODEC = new StateCodec<SlidingWindowLogState>() {

		@Override
		public byte[] encode(SlidingWindowLogState state) {

			String raw = state.getTimestamps().stream()
					.map(String::valueOf)
					.collect(Collectors.joining(","));

			return raw.getBytes(StandardCharsets.UTF_8);
		}

		@Override
		public SlidingWindowLogState decode(byte[] data) {

			String raw = new String(data, StandardCharsets.UTF_8);

			List<Long> timestamps = new ArrayList<>();

			if (!raw.isEmpty()) {
				for (String part : raw.split(",")) {
					timestamps.add(Long.parseLong(part));
				}
			}

			return new SlidingWindowLogState(timestamps);
		}

	};

	@Override
	public StateCodec<SlidingWindowLogState> getCodec() {
		return CODEC;
	}
	
	@Override
	public AlgorithmResult<SlidingWindowLogState> execute(SlidingWindowLogState state, SlidingWindowLogPolicy policy,
			AlgorithmContext context) {

		Instant now = context.getNow();

		long nowMillis = now.toEpochMilli();

		long windowStartMillis = nowMillis - policy.getWindowSize().toMillis();

		/*
		 * 1 :
		 *
		 * The timestamps that already fell out of
		 * the current sliding window are discarded.
		 */

		List<Long> relevantTimestamps = pruneExpiredTimestamps(state.getTimestamps(), windowStartMillis);

		/*
		 * 2 :
		 *
		 * If the number of valid timestamps has not reached
		 * the limit, the current request is recorded.
		 */

		if (relevantTimestamps.size() < policy.getLimit()) {

			List<Long> newTimestamps = new ArrayList<>(relevantTimestamps);

			newTimestamps.add(nowMillis);

			SlidingWindowLogState newState = new SlidingWindowLogState(newTimestamps);

			int remaining = policy.getLimit() - newTimestamps.size();

			Duration expireIn = policy.getWindowSize();

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
		 * The limit was reached within the window.
		 * The exact instant at which the oldest timestamp
		 * will stop counting within the window is computed.
		 */

		SlidingWindowLogState deniedState = new SlidingWindowLogState(relevantTimestamps);

		Duration retryAfter = timeUntilOldestTimestampExpires(relevantTimestamps, nowMillis, policy.getWindowSize());

		Instant resetAt = now.plus(retryAfter);

		Duration expireIn = policy.getWindowSize();

		return AlgorithmResult.denied(
				deniedState,
				retryAfter,
				resetAt,
				expireIn
		);

	}

	@Override
	public SlidingWindowLogState createInitialState(SlidingWindowLogPolicy policy, AlgorithmContext context) {
		return new SlidingWindowLogState(new ArrayList<>());
	}

	private List<Long> pruneExpiredTimestamps(List<Long> timestamps, long windowStartMillis) {

		List<Long> pruned = new ArrayList<>();

		for (Long timestamp : timestamps) {

			if (timestamp > windowStartMillis) {
				pruned.add(timestamp);
			}

		}

		return pruned;
	}

	private Duration timeUntilOldestTimestampExpires(List<Long> timestamps, long nowMillis, Duration windowSize) {

		long oldestTimestamp = findOldestTimestamp(timestamps);

		long expiresAtMillis = oldestTimestamp + windowSize.toMillis();

		long retryAfterMillis = expiresAtMillis - nowMillis;

		return Duration.ofMillis(Math.max(retryAfterMillis, 0));
	}

	private long findOldestTimestamp(List<Long> timestamps) {

		long oldest = Long.MAX_VALUE;

		for (long timestamp : timestamps) {
			if (timestamp < oldest) {
				oldest = timestamp;
			}
		}

		return oldest;
	}

}
