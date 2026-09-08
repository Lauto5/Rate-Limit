package io.github.lauto5.rateLimit.domain.algorithmState;

/**
 * Marker interface for the serializable state of a rate-limiting algorithm.
 *
 * <p>Each algorithm defines a concrete state that captures the information needed to compute
 * decisions on subsequent requests and that can be serialized and stored by a
 * {@code StateCodec}.
 */
public interface AlgorithmState { }
