package io.github.lauto5.rateLimit.domain.model;

/**
 * Represents the outcome of a rate-limiting decision for a request.
 *
 * <p>Concrete implementations determine whether a request is allowed and carry the additional
 * information relevant to that outcome.
 */
public interface AlgorithmDecision {

    boolean isAllowed();
	
}
