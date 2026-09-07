package io.github.lauto5.rateLimit.application;

import java.time.Instant;

import io.github.lauto5.rateLimit.api.RateLimitResult;
import io.github.lauto5.rateLimit.application.ports.out.AtomicOperationResult;
import io.github.lauto5.rateLimit.domain.algorithmState.AlgorithmState;
import io.github.lauto5.rateLimit.domain.model.AlgorithmDecision;
import io.github.lauto5.rateLimit.domain.model.AlgorithmResult;
import io.github.lauto5.rateLimit.domain.model.AllowedDecision;
import io.github.lauto5.rateLimit.domain.model.DeniedDecision;

/**
 * Maps internal algorithm results into public {@link RateLimitResult} instances.
 *
 * <p>This utility converts an {@link AtomicOperationResult} into the {@code RateLimitResult}
 * exposed through the public API, dispatching on whether the underlying decision is an
 * {@link AllowedDecision} or a {@link DeniedDecision}.
 *
 * <p>This class is not intended to be instantiated, as it only provides static utility methods.
 */
public class RateLimitResultMapper {

    private RateLimitResultMapper() {
    }

    /**
     * Maps an atomic operation result to its public API representation.
     *
     * @param atomicResult the atomic operation result to map
     * @param <S>          the concrete algorithm state type
     * @return the corresponding {@code RateLimitResult}
     * @throws IllegalArgumentException if {@code atomicResult} is {@code null}
     * @throws IllegalStateException    if the underlying decision is of an unexpected type
     */
    public static <S extends AlgorithmState> RateLimitResult fromAtomicOperationResult(
            AtomicOperationResult<S> atomicResult) {

        if (atomicResult == null) {
            throw new IllegalArgumentException("AtomicOperationResult cannot be null");
        }

        AlgorithmResult<S> algorithmResult = atomicResult.getAlgorithmResult();
        AlgorithmDecision decision = algorithmResult.getDecision();

        if (decision.isAllowed()) {
            return mapAllowedDecision(decision, algorithmResult.getResetAt());
        } else {
            return mapDeniedDecision(decision, algorithmResult.getResetAt());
        }
    }

    private static RateLimitResult mapAllowedDecision(AlgorithmDecision decision, Instant resetAt) {
        if (!(decision instanceof AllowedDecision)) {
            throw new IllegalStateException("Decision is not an AllowedDecision instance");
        }

        AllowedDecision allowedDecision = (AllowedDecision) decision;
        return RateLimitResult.allowed(
                allowedDecision.getRemaining(),
                resetAt
        );
    }

    private static RateLimitResult mapDeniedDecision(AlgorithmDecision decision, Instant resetAt) {
        if (!(decision instanceof DeniedDecision)) {
            throw new IllegalStateException("Decision is not a DeniedDecision instance");
        }

        DeniedDecision deniedDecision = (DeniedDecision) decision;
        return RateLimitResult.denied(
                deniedDecision.getRetryAfter(),
                resetAt
        );
    }
	
}
