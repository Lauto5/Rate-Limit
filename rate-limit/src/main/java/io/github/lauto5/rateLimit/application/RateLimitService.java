package io.github.lauto5.rateLimit.application;

import java.time.Clock;
import java.time.Instant;

import io.github.lauto5.rateLimit.api.RateLimitResult;
import io.github.lauto5.rateLimit.application.ports.out.AtomicOperationResult;
import io.github.lauto5.rateLimit.application.ports.out.RateLimitStore;
import io.github.lauto5.rateLimit.domain.algorithm.RateLimitAlgorithm;
import io.github.lauto5.rateLimit.domain.algorithmState.AlgorithmState;
import io.github.lauto5.rateLimit.domain.context.AlgorithmContext;
import io.github.lauto5.rateLimit.domain.policies.RateLimitPolicy;

/**
 * Orchestrates a rate-limit evaluation end to end at the application layer.
 *
 * <p>Given a {@link RateLimitStore}, a {@link RateLimitAlgorithm} and a {@link Clock}, a
 * {@code RateLimitService} builds an {@link AlgorithmContext} from the current instant, wraps
 * the algorithm and policy into a {@link RateLimitAtomicOperation}, submits it to the store for
 * atomic execution and maps the resulting {@link AtomicOperationResult} into a public
 * {@link RateLimitResult}.
 *
 * @param <S> the concrete algorithm state type
 * @param <P> the concrete policy type
 */
public final class RateLimitService<S extends AlgorithmState , P extends RateLimitPolicy> implements RateLimitExecutor<P>{

    private final RateLimitStore store;
    private final RateLimitAlgorithm<S, P> algorithm;
    private final Clock clock;

	/**
	 * Creates a rate-limit service.
	 *
	 * @param store     the store that persists and atomically updates state
	 * @param algorithm the algorithm that evaluates each request
	 * @param clock     the clock used to obtain the current instant
	 */
	public RateLimitService(RateLimitStore store, RateLimitAlgorithm<S, P> algorithm, Clock clock) {
		super();
		this.store = store;
		this.algorithm = algorithm;
		this.clock = clock;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public RateLimitResult execute(String identifier, P policy) {
	    AlgorithmContext context =
	            new AlgorithmContext(Instant.now(clock));

        RateLimitAtomicOperation<S, P> operation =new RateLimitAtomicOperation<S,P>(algorithm,policy,context);

        AtomicOperationResult<S> result =
                store.executeAtomically(identifier, operation);

        return RateLimitResultMapper.fromAtomicOperationResult(result);
	}
}
