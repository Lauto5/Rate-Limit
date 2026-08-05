package io.github.lauto5.rateLimit.testdoubles;

import io.github.lauto5.rateLimit.domain.algorithm.RateLimitAlgorithm;
import io.github.lauto5.rateLimit.domain.algorithmState.AlgorithmState;
import io.github.lauto5.rateLimit.domain.context.AlgorithmContext;
import io.github.lauto5.rateLimit.domain.model.AlgorithmResult;
import io.github.lauto5.rateLimit.domain.policies.RateLimitPolicy;

public final class StubRateLimitAlgorithm<
        S extends AlgorithmState,
        P extends RateLimitPolicy>
        implements RateLimitAlgorithm<S, P> {

    private final S initialState;

    private final AlgorithmResult<S> executeResult;
    
    private S receivedState;

    private P receivedPolicy;

    private AlgorithmContext receivedContext;

    public StubRateLimitAlgorithm(
            S initialState,
            AlgorithmResult<S> executeResult) {

        this.initialState = initialState;
        this.executeResult = executeResult;
    }

    @Override
    public S createInitialState(P policy, AlgorithmContext context) {
        return initialState;
    }

    @Override
    public AlgorithmResult<S> execute(
            S state,
            P policy,
            AlgorithmContext context) {
    	
        this.receivedState = state;
        this.receivedPolicy = policy;
        this.receivedContext = context;
    	
        return executeResult;
    }

    public S getReceivedState() {
        return receivedState;
    }

    public P getReceivedPolicy() {
        return receivedPolicy;
    }

    public AlgorithmContext getReceivedContext() {
        return receivedContext;
    }
    
}
