package io.github.lauto5.rateLimit.application.ports.out;

import io.github.lauto5.rateLimit.domain.algorithmState.AlgorithmState;

public interface StateCodec<S extends AlgorithmState> {

	byte[] encode(S state);

	S decode(byte[] data);

}
