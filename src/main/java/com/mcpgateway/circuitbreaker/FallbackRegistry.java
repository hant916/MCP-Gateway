package com.mcpgateway.circuitbreaker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for fallback strategies.
 *
 * Allows centralized management of fallback behaviors across the application.
 * Fallbacks can be registered programmatically or via configuration.
 */
@Slf4j
@Component
public class FallbackRegistry {

    private final Map<String, Fallback<?>> fallbacks = new ConcurrentHashMap<>();
    private final Map<String, Class<?>> fallbackTypes = new ConcurrentHashMap<>();

    /**
     * Register a fallback for a specific name.
     */
    public <T> void register(String name, Fallback<T> fallback, Class<T> type) {
        fallbacks.put(name, fallback);
        fallbackTypes.put(name, type);
        log.info("Registered fallback for '{}' with type {}", name, type.getSimpleName());
    }

    /**
     * Register a simple value fallback.
     */
    public <T> void registerValue(String name, T value) {
        register(name, Fallback.of(name + "_fallback", value), (Class<T>) value.getClass());
    }

    /**
     * Get fallback for a name.
     */
    @SuppressWarnings("unchecked")
    public <T> Fallback<T> getFallback(String name) {
        return (Fallback<T>) fallbacks.get(name);
    }

    /**
     * Check if fallback exists.
     */
    public boolean hasFallback(String name) {
        return fallbacks.containsKey(name);
    }

    /**
     * Remove fallback.
     */
    public void remove(String name) {
        fallbacks.remove(name);
        fallbackTypes.remove(name);
        log.info("Removed fallback for '{}'", name);
    }

    /**
     * Get all registered fallback names.
     */
    public java.util.Set<String> getAllNames() {
        return fallbacks.keySet();
    }

    /**
     * Get fallback type.
     */
    public Class<?> getType(String name) {
        return fallbackTypes.get(name);
    }

    /**
     * Clear all fallbacks.
     */
    public void clear() {
        fallbacks.clear();
        fallbackTypes.clear();
        log.info("Cleared all fallbacks");
    }
}
