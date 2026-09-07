package io.github.lauto5.rateLimit.testdoubles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.lauto5.rateLimit.application.ports.out.Logger;

/**
 * Test double that records every log invocation for later assertions.
 */
public final class RecordingLogger implements Logger {

    public static final class Entry {
        private final Level level;
        private final String message;

        public Entry(Level level, String message) {
            this.level = level;
            this.message = message;
        }

        public Level getLevel() {
            return level;
        }

        public String getMessage() {
            return message;
        }
    }

    private final List<Entry> entries =
            new ArrayList<>();

    @Override
    public void log(Level level, String message) {
        entries.add(new Entry(level, message));
    }

    public List<Entry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public boolean any(Level level) {
        for (Entry entry : entries) {
            if (entry.getLevel() == level) {
                return true;
            }
        }
        return false;
    }

    public boolean anyMessageContaining(String fragment) {
        for (Entry entry : entries) {
            if (entry.getMessage().contains(fragment)) {
                return true;
            }
        }
        return false;
    }
}