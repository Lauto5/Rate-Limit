package io.github.lauto5.rateLimit.infraestructure;

import io.github.lauto5.rateLimit.application.ports.out.Logger;

/**
 * Default {@link Logger} that discards every log message.
 *
 * <p>This implementation is used when no explicit logger is supplied, so the library remains
 * silent by default. Callers can inject a custom {@link Logger} (for example a
 * {@link ConsoleLogger}) to opt in to diagnostic output.
 */
public class NoOpLogger implements Logger {

	private static final NoOpLogger INSTANCE = new NoOpLogger();

	private NoOpLogger() {
	}

	/**
	 * Returns the shared no-op logger instance.
	 *
	 * @return a singleton {@code NoOpLogger}
	 */
	public static NoOpLogger getInstance() {
		return INSTANCE;
	}

	@Override
	public void log(Level level, String message) {
		// intentionally does nothing
	}
}
