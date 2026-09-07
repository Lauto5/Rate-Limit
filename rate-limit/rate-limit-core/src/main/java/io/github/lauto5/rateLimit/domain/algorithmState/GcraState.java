package io.github.lauto5.rateLimit.domain.algorithmState;

public class GcraState implements AlgorithmState {

	private final long tat;

	public GcraState(long tat) {
		super();
		this.tat = tat;
	}

	public long getTat() {
		return tat;
	}
	
}
