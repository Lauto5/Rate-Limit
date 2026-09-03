package io.github.lauto5.rateLimit;

import java.time.Clock;

import io.github.lauto5.rateLimit.api.RateLimitResult;
import io.github.lauto5.rateLimit.application.RateLimitExecutor;
import io.github.lauto5.rateLimit.application.RateLimitService;
import io.github.lauto5.rateLimit.application.ports.out.RateLimitStore;
import io.github.lauto5.rateLimit.domain.algorithm.RateLimitAlgorithm;
import io.github.lauto5.rateLimit.domain.algorithmState.AlgorithmState;
import io.github.lauto5.rateLimit.domain.policies.RateLimitPolicy;

/**
 * Public facade and entry point for the rate-limiting library.
 *
 * <p>A {@code RateLimit} instance couples a {@link RateLimitAlgorithm} with a persistent
 * {@link RateLimitStore} and an optional {@link Clock}, exposing a single {@link #use} method
 * to evaluate whether a request is permitted for a given identifier and policy.
 *
 * <p>Instances are immutable and thread-safe once constructed. Build an instance using one of
 * the static {@link #build} factories, optionally providing a {@code Clock} for testing
 * purposes.
 *
 * @param <P> the concrete type of {@link RateLimitPolicy} used by this limiter
 * @see RateLimitAlgorithm
 * @see RateLimitStore
 * @see RateLimitResult
 */
public final class RateLimit<P extends RateLimitPolicy> {

    private final RateLimitExecutor<P> executor;

    private RateLimit(RateLimitExecutor<P> executor) {
        this.executor = executor;
    }

    /**
     * Builds a {@link RateLimit} using the system UTC clock.
     *
     * @param algorithm the algorithm that evaluates each request against the configured state
     * @param store     the persistent store used to hold and atomically update algorithm state
     * @param <S>       the concrete algorithm state type
     * @param <P>       the concrete policy type
     * @return a ready-to-use {@code RateLimit} instance
     */
    public static <S extends AlgorithmState, P extends RateLimitPolicy>
    RateLimit<P> build(
            RateLimitAlgorithm<S, P> algorithm,
            RateLimitStore store) {

        return build(
                algorithm,
                store,
                Clock.systemUTC()
        );
    }

    /**
     * Builds a {@link RateLimit} using the provided {@link Clock}.
     *
     * @param algorithm the algorithm that evaluates each request against the configured state
     * @param store     the persistent store used to hold and atomically update algorithm state
     * @param clock     the clock used to obtain the current instant, enabling deterministic testing
     * @param <S>       the concrete algorithm state type
     * @param <P>       the concrete policy type
     * @return a ready-to-use {@code RateLimit} instance
     */
    public static <S extends AlgorithmState, P extends RateLimitPolicy>
    RateLimit<P> build(
            RateLimitAlgorithm<S, P> algorithm,
            RateLimitStore store,
            Clock clock) {

        RateLimitService<S, P> service =
                new RateLimitService<>(
                        store,
                        algorithm,
                        clock
                );

        return new RateLimit<>(service);
    }

    /**
     * Evaluates a request against the configured algorithm and policy, updating the relevant
     * state stored for the given identifier.
     *
     * @param identifier the logical key identifying the request stream being limited
     * @param policy     the rate-limiting configuration to enforce
     * @return the result of the request: whether it was allowed and, if not, when to retry
     */
    public RateLimitResult use(
            String identifier,
            P policy) {

        return executor.execute(identifier, policy);
    }
}
