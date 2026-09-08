package io.github.lauto5.rateLimit.application.ports.out;

/**
 * Abstraction for structured logging within the rate-limit subsystem.
 *
 * <p>Implementations route log messages to a specific destination (console, file, remote
 * service, etc.). Each message is tagged with a {@link Level} that indicates its severity.
 *
 * <p>The interface exposes a general {@link #log(Level, String)} method as well as
 * convenience methods ({@link #debug}, {@link #info}, {@link #warn}, {@link #error}) that
 * delegate to {@code log} with the appropriate level.
 */
public interface Logger {
    
    enum Level {
        DEBUG, INFO, WARN, ERROR
    }

    void log(Level level, String message);

    default void debug(String message) {
        log(Level.DEBUG, message);
    }

    default void info(String message) {
        log(Level.INFO, message);
    }

    default void warn(String message) {
        log(Level.WARN, message);
    }

    default void error(String message) {
        log(Level.ERROR, message);
    }

    default void error(String message, Throwable throwable) {
        log(Level.ERROR, message + " - Exception: " + throwable.getMessage());
    }
}