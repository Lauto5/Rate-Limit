package io.github.lauto5.rateLimit.application.service;

import java.time.Instant;

import io.github.lauto5.rateLimit.application.adapters.RateLimitAtomicOperation;
import io.github.lauto5.rateLimit.application.ports.in.RateLimitResult;
import io.github.lauto5.rateLimit.application.ports.out.AtomicOperationResult;
import io.github.lauto5.rateLimit.application.ports.out.RateLimitStore;
import io.github.lauto5.rateLimit.domain.algorithm.RateLimitAlgorithm;
import io.github.lauto5.rateLimit.domain.algorithmState.AlgorithmState;
import io.github.lauto5.rateLimit.domain.context.AlgorithmContext;
import io.github.lauto5.rateLimit.domain.mapper.RateLimitResultMapper;
import io.github.lauto5.rateLimit.domain.policies.RateLimitPolicy;

public final class RateLimitService<S extends AlgorithmState , P extends RateLimitPolicy>{

    private final RateLimitStore store;
    private final RateLimitAlgorithm<S, P> algorithm;
    
    

    public RateLimitService(RateLimitStore store, RateLimitAlgorithm<S, P> algorithm) {
		super();
		this.store = store;
		this.algorithm = algorithm;
	}



	public RateLimitResult use(
            String identifier,
            P policy) {

        AlgorithmContext context =
                new AlgorithmContext(Instant.now());

        RateLimitAtomicOperation<S, P> operation =new RateLimitAtomicOperation<S,P>(algorithm,policy,context);

        AtomicOperationResult<S> result =
                store.executeAtomically(identifier, operation);

        return RateLimitResultMapper.fromAtomicOperationResult(result);
    }
}
