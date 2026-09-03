package io.github.lauto5.rateLimit.domain.algorithm;

import io.github.lauto5.rateLimit.application.ports.out.StateCodec;
import io.github.lauto5.rateLimit.domain.algorithmState.AlgorithmState;
import io.github.lauto5.rateLimit.domain.context.AlgorithmContext;
import io.github.lauto5.rateLimit.domain.model.AlgorithmResult;
import io.github.lauto5.rateLimit.domain.policies.RateLimitPolicy;

/**
 * Core contract implemented by every rate-limiting algorithm.
 *
 * <p>A {@code RateLimitAlgorithm} operates over an immutable state type {@code S} and a policy
 * type {@code P}. For each request it evaluates the current state under the policy, returning a
 * new {@link AlgorithmResult} that carries the decision together with the updated state. It is
 * also responsible for creating an initial state and for providing the {@link StateCodec} used
 * to persist that state.
 *
 * <p>Implementations must be stateless and thread-safe, as a single instance may serve many
 * concurrent evaluations.
 *
 * @param <S> the concrete algorithm state type
 * @param <P> the concrete policy type
 */
public interface RateLimitAlgorithm <S extends AlgorithmState , P extends RateLimitPolicy> {

	/**
	 * Evaluates a single request against the given state and policy.
	 *
	 * @param state   the current state of the algorithm
	 * @param policy  the policy whose limits are enforced
	 * @param context the evaluation context carrying the current instant
	 * @return the algorithmic result, including the decision and updated state
	 */
	public AlgorithmResult<S> execute(S state , P policy , AlgorithmContext context);
	
	/**
	 * Creates the initial state used when no state exists yet for an identifier.
	 *
	 * @param policy  the policy under which the state is created
	 * @param context the evaluation context carrying the current instant
	 * @return a freshly created initial state
	 */
	public S createInitialState(P policy , AlgorithmContext context);
	
	/**
	 * @return the codec used to encode and decode instances of the state {@code S}
	 */
	public StateCodec<S> getCodec();
	
}
