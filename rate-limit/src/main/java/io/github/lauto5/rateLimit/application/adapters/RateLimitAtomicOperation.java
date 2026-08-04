package io.github.lauto5.rateLimit.application.adapters;

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
	public AtomicOperationResult<S> apply(StoreState<S> currentState) {

		// 1 - Si no existe estado persistido, crear el estado inicial
		S state;

		if (currentState == null) {
			state = algorithm.createInitialState(policy, context);
		} else {
			state = currentState.getState();
		}

		// 2 - Ejecutar el algoritmo
		AlgorithmResult<S> algorithmResult = algorithm.execute(state, policy, context);

		// 3 - Calcular la fecha de expiración del estado
		Instant expiresAt = context.getNow().plus(algorithmResult.getExpireIn());

		// 4 - Construir el resultado que utilizará el Store
		return new AtomicOperationResult<>(expiresAt , algorithmResult);
	}

	@Override
	public AlgorithmContext getContext() {
		return this.context;
	}

}