package io.github.lauto5.rateLimit.domain.model;

public final class AllowedDecision implements AlgorithmDecision {

    private final int remaining;

    public AllowedDecision(int remaining) {
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
