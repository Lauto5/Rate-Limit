package io.github.lauto5.rateLimit.application;

import java.util.Arrays;

import io.github.lauto5.rateLimit.domain.algorithm.StateCodec;
import io.github.lauto5.rateLimit.domain.algorithmState.AlgorithmState;

/**
 * Decorator that prefixes state serialization with a format marker for versioning.
 *
 * <p>The serialized payload has the following structure:
 *
 * <pre>
 * [0x52][0x4C][0x01][... delegated codec payload ...]
 *   ^      ^      ^
 *   'R'    'L'   version (1)
 * </pre>
 *
 * During decoding, the marker is validated. If the data is truncated, corrupted, or encoded
 * in an unsupported version, {@link CorruptedStateException} is thrown instead of attempting
 * to interpret ambiguous data.
 *
 * <p><b>Versioning policy:</b> currently only one version exists (0x01) and no migrations are
 * supported. The versioning boundary is defined as follows:
 *
 * <ul>
 *   <li>an unknown version produces {@link CorruptedStateException} (ambiguous bytes are never
 *       interpreted);</li>
 *   <li>the Redis store treats the state as non-existent (<em>fail-open</em> policy) and
 *       rewrites it;</li>
 *   <li>a rolling deployment with different formats is only possible by introducing a new
 *       version while retaining readers for previous versions.</li>
 * </ul>
 *
 * @param <S> the concrete algorithm state type
 */
public final class VersionedStateCodec<S extends AlgorithmState> implements StateCodec<S> {

	private static final byte[] MAGIC = { 'R', 'L' };
	private static final byte CURRENT_VERSION = 0x01;
	private static final int HEADER_LENGTH = MAGIC.length + 1;

	private final StateCodec<S> delegate;

	/**
	 * Creates a codec that wraps the given delegate with versioning.
	 *
	 * @param delegate the codec that knows how to serialize a concrete algorithm state
	 */
	public VersionedStateCodec(StateCodec<S> delegate) {
		this.delegate = delegate;
	}

	@Override
	public byte[] encode(S state) {
		byte[] payload = delegate.encode(state);
		byte[] framed = new byte[HEADER_LENGTH + payload.length];
		framed[0] = MAGIC[0];
		framed[1] = MAGIC[1];
		framed[2] = CURRENT_VERSION;
		System.arraycopy(payload, 0, framed, HEADER_LENGTH, payload.length);
		return framed;
	}

	@Override
	public S decode(byte[] data) {
		if (data == null || data.length < HEADER_LENGTH) {
			throw new CorruptedStateException("Truncated or missing state");
		}
		if (data[0] != MAGIC[0] || data[1] != MAGIC[1]) {
			throw new CorruptedStateException(
					"Invalid format marker: " + describeBytes(data));
		}
		if (data[2] != CURRENT_VERSION) {
			throw new CorruptedStateException(
					"Unsupported format version: " + (data[2] & 0xFF));
		}

		byte[] payload = Arrays.copyOfRange(data, HEADER_LENGTH, data.length);

		try {
			return delegate.decode(payload);
		} catch (RuntimeException e) {
			/*
			 * The header is valid but the concrete codec could not interpret the payload
			 * (e.g., a NumberFormatException from the FixedWindow codec). The failure is
			 * normalized to CorruptedStateException so that the store applies its fail-open
			 * policy: treat the state as non-existent and rewrite it rather than failing
			 * the request.
			 */
			throw new CorruptedStateException(
					"Unreadable state payload: " + e.getMessage(), e);
		}
	}

	private static String describeBytes(byte[] data) {
		int len = Math.min(data.length, 4);
		StringBuilder hex = new StringBuilder(len * 2);
		for (int i = 0; i < len; i++) {
			hex.append(String.format("%02X", data[i] & 0xFF));
		}
		return hex.toString();
	}
}