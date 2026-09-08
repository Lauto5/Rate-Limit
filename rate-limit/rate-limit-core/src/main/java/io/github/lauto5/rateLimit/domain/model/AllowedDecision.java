package io.github.lauto5.rateLimit.domain.model;

/**
 * A decision indicating that a request was allowed.
 *
 * <p>Holds the number of remaining requests that can still be made within the current limit.
 */
public final class AllowedDecision implements AlgorithmDecision {

	private final int remaining;

	public AllowedDecision(int remaining) {
		
		if(remaining < 0) {
			throw new IllegalArgumentException("remaining must be greater than 0");
		}
		
		this.remaining = remaining;
	}

	public long getRemaining() {
		return remaining;
	}

	@Override
	public boolean isAllowed() {
		return true;
	}
	
}
