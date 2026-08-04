package io.github.lauto5.rateLimit.application.ports.out;

import io.github.lauto5.rateLimit.domain.algorithmState.AlgorithmState;
import io.github.lauto5.rateLimit.domain.context.AlgorithmContext;

public interface AtomicOperation <S extends AlgorithmState>{

	public AtomicOperationResult<S> apply(StoreState<S> currentState);
	
    public AlgorithmContext getContext();
}
