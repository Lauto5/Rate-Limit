package io.github.lauto5.rateLimit.domain.algorithm;

import java.time.Duration;
import java.time.Instant;

import io.github.lauto5.rateLimit.domain.algorithmState.LeakyBucketState;
import io.github.lauto5.rateLimit.domain.context.AlgorithmContext;
import io.github.lauto5.rateLimit.domain.model.AlgorithmResult;
import io.github.lauto5.rateLimit.domain.policies.LeakyBucketPolicy;

public class LeakyBucketAlgorithmImpl implements LeakyBucketAlgorithm {

	private static final double REQUEST_COST = 1.0;

	@Override
	public AlgorithmResult<LeakyBucketState> execute(LeakyBucketState state, LeakyBucketPolicy policy,
			AlgorithmContext context) {

		Instant now = context.getNow();

		long nowMillis = now.toEpochMilli();

		/*
		 * 1 :
		 *
		 * Calculamos cuánta agua se filtró (leak) desde la
		 * última actualización, en base al tiempo transcurrido
		 * y a la tasa de drenaje de la política.
		 */

		long elapsedMillis = nowMillis - state.getLastLeak();

		double elapsedSeconds = elapsedMillis / 1000.0;

		double leakedWater = elapsedSeconds * policy.getLeakRate();

		double currentWater = Math.max(0.0, state.getWater() - leakedWater);

		/*
		 * 2 :
		 *
		 * Si agregar esta solicitud no desborda la capacidad
		 * del balde, se permite y se acumula el agua.
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
		 * El balde desborda. Se informa cuánto falta
		 * para que drene lo suficiente como para
		 * aceptar una nueva solicitud.
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
