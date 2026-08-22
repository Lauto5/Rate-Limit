package io.github.lauto5.rateLimit;

import java.time.Clock;

import io.github.lauto5.rateLimit.application.RateLimitExecutor;
import io.github.lauto5.rateLimit.application.ports.in.RateLimitResult;
import io.github.lauto5.rateLimit.application.ports.out.RateLimitStore;
import io.github.lauto5.rateLimit.application.service.RateLimitService;
import io.github.lauto5.rateLimit.domain.algorithm.RateLimitAlgorithm;
import io.github.lauto5.rateLimit.domain.algorithmState.AlgorithmState;
import io.github.lauto5.rateLimit.domain.policies.RateLimitPolicy;

public final class RateLimit<P extends RateLimitPolicy> {

    private final RateLimitExecutor<P> executor;

    private RateLimit(RateLimitExecutor<P> executor) {
        this.executor = executor;
    }

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

    public RateLimitResult use(
            String identifier,
            P policy) {

        return executor.execute(identifier, policy);
    }
}
