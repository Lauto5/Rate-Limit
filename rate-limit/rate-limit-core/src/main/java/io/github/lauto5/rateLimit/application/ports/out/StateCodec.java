package io.github.lauto5.rateLimit.application.ports.out;

import io.github.lauto5.rateLimit.domain.algorithmState.AlgorithmState;

/**
 * Strategy for serializing and deserializing algorithm state.
 *
 * <p>A {@code StateCodec} converts an {@link AlgorithmState} into a {@code byte[]} (and back)
 * so that state can be persisted in a {@link RateLimitStore}. Each {@code RateLimitAlgorithm}
 * provides the codec appropriate for its state type.
 *
 * @param <S> the concrete algorithm state type
 */
public interface StateCodec<S extends AlgorithmState> {

	/**
	 * Encodes the given state into bytes.
	 *
	 * @param state the state to encode
	 * @return the encoded byte representation
	 */
	byte[] encode(S state);

	/**
	 * Decodes the given bytes back into a state.
	 *
	 * @param data the encoded bytes
	 * @return the decoded state
	 */
	S decode(byte[] data);

}
