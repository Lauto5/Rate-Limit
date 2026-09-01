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

public class SlidingWindowLogAlgorithmImpl implements SlidingWindowLogAlgorithm {

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
		 * Se descartan los timestamps que ya quedaron
		 * fuera de la ventana deslizante actual.
		 */

		List<Long> relevantTimestamps = pruneExpiredTimestamps(state.getTimestamps(), windowStartMillis);

		/*
		 * 2 :
		 *
		 * Si la cantidad de timestamps vigentes no alcanzó
		 * el límite, se registra la solicitud actual.
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
		 * Se alcanzó el límite dentro de la ventana.
		 * Se calcula el instante exacto en el que el timestamp
		 * más antiguo dejará de contar dentro de la ventana.
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
