package io.github.lauto5.rateLimit.application;

/**
 * Indicates that persisted state could not be decoded because the data is corrupted,
 * truncated or encoded in an incompatible format.
 *
 * <p>Los adapters de persistencia deben capturar esta excepcion y decidir como reaccionar
 * (por ejemplo, tratar el estado como inexistente y resetearlo) en lugar de fallar con datos
 * ambiguos.
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