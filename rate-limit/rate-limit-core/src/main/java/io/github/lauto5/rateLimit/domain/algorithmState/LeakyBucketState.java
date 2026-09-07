package io.github.lauto5.rateLimit.domain.algorithmState;

public class LeakyBucketState implements AlgorithmState {

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
