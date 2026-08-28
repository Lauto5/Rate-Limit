package io.github.lauto5.rateLimit.application;

import java.time.Instant;

import io.github.lauto5.rateLimit.application.ports.out.AtomicOperation;
import io.github.lauto5.rateLimit.application.ports.out.AtomicOperationResult;
import io.github.lauto5.rateLimit.application.ports.out.StoreState;
import io.github.lauto5.rateLimit.domain.algorithm.RateLimitAlgorithm;
import io.github.lauto5.rateLimit.domain.algorithmState.AlgorithmState;
import io.github.lauto5.rateLimit.domain.context.AlgorithmContext;
import io.github.lauto5.rateLimit.domain.model.AlgorithmResult;
import io.github.lauto5.rateLimit.domain.policies.RateLimitPolicy;

public final class RateLimitAtomicOperation<S extends AlgorithmState, P extends RateLimitPolicy>
		implements AtomicOperation<S> {

	private final RateLimitAlgorithm<S, P> algorithm;
	private final P policy;
	private final AlgorithmContext context;

	public RateLimitAtomicOperation(RateLimitAlgorithm<S, P> algorithm, P policy, AlgorithmContext context) {
		super();
		this.algorithm = algorithm;
		this.policy = policy;
		this.context = context;
	}

	@Override
	public AtomicOperationResult<S> apply(StoreState<S> currentStoreState) {

		/*
		 * 1. 
		 * If the current store state does not exist, then create a new one.
		 * 
		 */
		
		S state;

		if (currentStoreState == null) {
			state = algorithm.createInitialState(policy, context);
		} else {
			state = currentStoreState.getState();
		}

		/*
		 * 2.
		 * Run the algorithm
		 * 
		 */
		
		AlgorithmResult<S> algorithmResult = algorithm.execute(state, policy, context);

		/*
		 * 3.
		 * Calculate the expireAt to create the atomic operation
		 * 
		 */
		
		Instant expiresAt = context.getNow().plus(algorithmResult.getExpireIn());

		return new AtomicOperationResult<>(expiresAt , algorithmResult);
	}

	@Override
	public Instant getNow() {
		return this.context.getNow();
	}

}