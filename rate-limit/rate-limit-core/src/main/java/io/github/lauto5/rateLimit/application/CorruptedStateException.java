package io.github.lauto5.rateLimit.application;

/**
 * Indicates that persisted state could not be decoded because the data is corrupted,
 * truncated or encoded in an incompatible format.
 *
 * <p>Persistence adapters must catch this exception and decide how to react (e.g., treat the
 * state as non-existent and reset it) rather than failing with ambiguous data.
 */
public class CorruptedStateException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public CorruptedStateException(String message) {
		super(message);
	}

	public CorruptedStateException(String message, Throwable cause) {
		super(message, cause);
	}
}