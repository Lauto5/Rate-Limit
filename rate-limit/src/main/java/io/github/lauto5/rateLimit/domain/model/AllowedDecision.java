package io.github.lauto5.rateLimit.domain.model;

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
