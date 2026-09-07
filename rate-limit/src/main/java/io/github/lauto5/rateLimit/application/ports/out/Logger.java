package io.github.lauto5.rateLimit.application.ports.out;

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