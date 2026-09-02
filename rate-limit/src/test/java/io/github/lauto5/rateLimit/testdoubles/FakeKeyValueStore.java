package io.github.lauto5.rateLimit.testdoubles;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import io.github.lauto5.rateLimit.application.ports.out.KeyValueStorePort;

/**
 * Implementación fake/in-memory de KeyValueStorePort para pruebas unitarias.
 * Simula el comportamiento de un almacén clave-valor con operaciones CAS y TTL.
 */
public final class FakeKeyValueStore implements KeyValueStorePort {

    private final Map<String, byte[]> store = new ConcurrentHashMap<>();
    private final Map<String, Long> expirations = new ConcurrentHashMap<>();
    private final AtomicLong currentTime = new AtomicLong(System.currentTimeMillis());

    @Override
    public byte[] get(String identifier) {
        cleanup();
        return store.get(identifier);
    }

    @Override
    public boolean compareAndSwap(String identifier, byte[] expectedValue, byte[] newValue, long ttlMillis) {
        cleanup();

        byte[] current = store.get(identifier);
        boolean expectedExists = (expectedValue != null);
        boolean currentExists = (current != null);

        // Verificar condiciones CAS
        if (expectedExists && !currentExists) {
            return false;
        }
        
        if (!expectedExists && currentExists) {
            return false;
        }

        if (expectedExists && currentExists) {
            // Comparar contenidos byte a byte
            if (current.length != expectedValue.length) {
                return false;
            }
            for (int i = 0; i < current.length; i++) {
                if (current[i] != expectedValue[i]) {
                    return false;
                }
            }
        }

        // Actualizar valor y expiración
        store.put(identifier, newValue);
        expirations.put(identifier, currentTime.get() + ttlMillis);
        return true;
    }

    /**
     * Avanza el tiempo virtual, útil para pruebas de expiración.
     */
    public void advanceTime(long millis) {
        currentTime.addAndGet(millis);
        cleanup();
    }

    /**
     * Limpia todas las entradas del almacenamiento.
     */
    public void clear() {
        store.clear();
        expirations.clear();
    }

    /**
     * Verifica si una clave existe y no ha expirado.
     */
    public boolean exists(String identifier) {
        cleanup();
        return store.containsKey(identifier);
    }

    /**
     * Obtiene el TTL restante en milisegundos para una clave.
     * Retorna null si la clave no existe o ya expiró.
     */
    public Long getRemainingTtl(String identifier) {
        cleanup();
        Long expiry = expirations.get(identifier);
        if (expiry == null) {
            return null;
        }
        long remaining = expiry - currentTime.get();
        return remaining > 0 ? remaining : null;
    }

    /**
     * Obtiene el valor sin verificar expiración (para debugging).
     */
    public byte[] getRaw(String identifier) {
        return store.get(identifier);
    }

    private void cleanup() {
        long now = currentTime.get();
        expirations.entrySet().removeIf(entry -> {
            if (now > entry.getValue()) {
                store.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }

    @Override
    public void close() {
        clear();
    }
}