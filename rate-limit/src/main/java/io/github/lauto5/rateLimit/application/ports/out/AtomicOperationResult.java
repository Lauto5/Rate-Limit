package io.github.lauto5.rateLimit.application.ports.out;

import java.time.Instant;

import io.github.lauto5.rateLimit.domain.algorithmState.AlgorithmState;
import io.github.lauto5.rateLimit.domain.model.AlgorithmResult;

public final class AtomicOperationResult<S extends AlgorithmState> {

    private final Instant expiresAt;

    private final AlgorithmResult<S> algorithmResult;

	public AtomicOperationResult(Instant expiresAt, AlgorithmResult<S> algorithmResult) {
		super();
		this.expiresAt = expiresAt;
		this.algorithmResult = algorithmResult;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public AlgorithmResult<S> getAlgorithmResult() {
		return algorithmResult;
	}
    
    public S getState() {
    	return this.algorithmResult.getState();
    }
    
    public StoreState<S> getStoreState(){
    	return new StoreState<>(this.getState(), this.getExpiresAt()); 
    }
}
