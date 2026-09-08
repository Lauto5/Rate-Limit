package io.github.lauto5.rateLimit.application.logging;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import io.github.lauto5.rateLimit.application.ports.out.Logger;

/**
 * {@link Logger} implementation that writes formatted log messages to {@code System.out} or
 * {@code System.err} depending on the severity level. Messages whose level is below the
 * configured minimum level are silently discarded.
 *
 * <p>Each output line follows the format:
 * {@code [timestamp] [LEVEL] [thread] (name): message}.
 */
public class ConsoleLogger implements Logger {

	private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

	private final String name;
	private final Level minLevel;

	public ConsoleLogger(Class<?> clazz) {
		this(clazz.getSimpleName(), Level.INFO);
	}

	public ConsoleLogger(Class<?> clazz, Level minLevel) {
		this(clazz.getSimpleName(), minLevel);
	}

	public ConsoleLogger(String name, Level minLevel) {
		this.name = name;
		this.minLevel = minLevel;
	}

	@Override
	public void log(Level level, String message) {
		if (level.ordinal() < minLevel.ordinal()) {
			return; // Discard logs below the configured minimum level
		}

		String timestamp = LocalDateTime.now().format(FORMATTER);
		String threadName = Thread.currentThread().getName();

		String formattedMessage = String.format("[%s] [%s] [%s] (%s): %s",
				timestamp,
				level,
				threadName,
				name,
				message
		);

		if (level == Level.ERROR) {
			System.err.println(formattedMessage);
		} else {
			System.out.println(formattedMessage);
		}
	}
}
