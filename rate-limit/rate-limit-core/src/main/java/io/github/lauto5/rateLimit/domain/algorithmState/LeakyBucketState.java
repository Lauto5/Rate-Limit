package io.github.lauto5.rateLimit.domain.algorithmState;

/**
 * Serializable state for the leaky bucket algorithm.
 *
 * <p>Holds the current water level of the bucket and the instant (in epoch milliseconds) of
 * the last drain update.
 */
public final class LeakyBucketState implements AlgorithmState {

	private final double water;
	private final long lastLeak;
	
	public LeakyBucketState(double water, long lastLeak) {
		super();
		this.water = water;
		this.lastLeak = lastLeak;
	}
	
	public double getWater() {
		return water;
	}
	
	public long getLastLeak() {
		return lastLeak;
	}
	
}
