package io.github.lauto5.rateLimit.application;

import io.github.lauto5.rateLimit.api.RateLimitResult;
import io.github.lauto5.rateLimit.domain.policies.RateLimitPolicy;

/**
 * Executes a rate-limit evaluation for a given identifier and policy.
 *
 * <p>This interface decouples the public API from the concrete orchestration logic that
 * coordinates algorithm evaluation, state persistence and result mapping.
 *
 * @param <P> the concrete type of {@link RateLimitPolicy} handled by this executor
 */
public interface RateLimitExecutor<P extends RateLimitPolicy> {

    /**
     * Evaluates a request for the specified identifier under the given policy.
     *
     * @param identifier the logical key identifying the request stream being limited
     * @param policy     the rate-limiting configuration to enforce
     * @return the result of the evaluation
     */
    RateLimitResult execute(String identifier, P policy);
}