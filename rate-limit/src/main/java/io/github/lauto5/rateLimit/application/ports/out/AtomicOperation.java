package io.github.lauto5.rateLimit.application.ports.out;

import io.github.lauto5.rateLimit.domain.algorithmState.AlgorithmState;

public interface AtomicOperation <S extends AlgorithmState , R >{

	public R apply(StoreState<S> state);
	
}
