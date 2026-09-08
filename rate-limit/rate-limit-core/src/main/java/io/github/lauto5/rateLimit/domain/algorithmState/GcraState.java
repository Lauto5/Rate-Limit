package io.github.lauto5.rateLimit.domain.algorithmState;

/**
 * Serializable state for the GCRA algorithm.
 *
 * <p>Holds the theoretical arrival time (TAT) of the next request, expressed in epoch
 * milliseconds.
 */
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
