package io.github.lauto5.rateLimit.application;

import java.time.Instant;

import io.github.lauto5.rateLimit.application.ports.out.AtomicOperation;
import io.github.lauto5.rateLimit.application.ports.out.AtomicOperationResult;
import io.github.lauto5.rateLimit.application.ports.out.StateCodec;
import io.github.lauto5.rateLimit.application.ports.out.StoreState;
import io.github.lauto5.rateLimit.domain.algorithm.RateLimitAlgorithm;
import io.github.lauto5.rateLimit.domain.algorithmState.AlgorithmState;
import io.github.lauto5.rateLimit.domain.context.AlgorithmContext;
import io.github.lauto5.rateLimit.domain.model.AlgorithmResult;
import io.github.lauto5.rateLimit.domain.policies.RateLimitPolicy;

/**
 * Default {@link AtomicOperation} that orchestrates a rate-limit evaluation within a store.
 *
 * <p>Given a {@link RateLimitAlgorithm}, a policy and an {@link AlgorithmContext}, this
 * operation: (1) creates an initial state when the store holds none, (2) runs the algorithm
 * against the resolved state, and (3) computes the expiry instant for the resulting state. It
 * is executed atomically by a {@link RateLimitStore}.
 *
 * @param <S> the concrete algorithm state type
 * @param <P> the concrete policy type
 */
public final class RateLimitAtomicOperation<S extends AlgorithmState, P extends RateLimitPolicy>
		implements AtomicOperation<S> {

	private final RateLimitAlgorithm<S, P> algorithm;
	private final P policy;
	private final AlgorithmContext context;

	/**
	 * Creates an atomic rate-limit operation.
	 *
	 * @param algorithm the algorithm that evaluates each request
	 * @param policy    the policy whose limits are enforced
	 * @param context   the evaluation context carrying the current instant
	 */
	public RateLimitAtomicOperation(RateLimitAlgorithm<S, P> algorithm, P policy, AlgorithmContext context) {
		super();
		this.algorithm = algorithm;
		this.policy = policy;
		this.context = context;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>If {@code currentStoreState} is {@code null}, an initial state is created via
	 * {@link RateLimitAlgorithm#createInitialState}. The resolved state is then passed to
	 * {@link RateLimitAlgorithm#execute} and the resulting state's expiry is derived from the
	 * {@code expireIn} duration of the algorithm result.
	 */
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

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Instant getNow() {
		return this.context.getNow();
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public StateCodec<S> getCodec() {
		return algorithm.getCodec();
	}

}