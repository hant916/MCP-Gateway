package com.mcpgateway.circuitbreaker;

import lombok.Builder;
import lombok.Data;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Fallback definition for circuit breaker.
 * Provides a typed, reusable fallback strategy.
 *
 * @param <T> The return type
 */
@Data
@Builder
public class Fallback<T> {

    private final String name;
    private final Supplier<T> valueSupplier;
    private final Function<Throwable, T> exceptionHandler;
    private final boolean cacheResult;
    private T cachedValue;

    /**
     * Execute fallback and return value.
     */
    public T execute(Throwable cause) {
        if (exceptionHandler != null) {
            return exceptionHandler.apply(cause);
        }

        if (cacheResult && cachedValue != null) {
            return cachedValue;
        }

        T value = valueSupplier.get();

        if (cacheResult) {
            cachedValue = value;
        }

        return value;
    }

    /**
     * Create a simple value fallback.
     */
    public static <T> Fallback<T> of(String name, T value) {
        return Fallback.<T>builder()
                .name(name)
                .valueSupplier(() -> value)
                .cacheResult(true)
                .build();
    }

    /**
     * Create a supplier-based fallback.
     */
    public static <T> Fallback<T> of(String name, Supplier<T> supplier) {
        return Fallback.<T>builder()
                .name(name)
                .valueSupplier(supplier)
                .cacheResult(false)
                .build();
    }

    /**
     * Create an exception-aware fallback.
     */
    public static <T> Fallback<T> withExceptionHandler(String name, Function<Throwable, T> handler) {
        return Fallback.<T>builder()
                .name(name)
                .exceptionHandler(handler)
                .cacheResult(false)
                .build();
    }
}
