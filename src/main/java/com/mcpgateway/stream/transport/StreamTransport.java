package com.mcpgateway.stream.transport;

import com.mcpgateway.stream.StreamToken;

/**
 * Transport interface for delivering stream tokens to clients.
 *
 * CRITICAL: yield ≠ flush
 * Every send() should be followed by flush() for real streaming.
 */
public interface StreamTransport {

    /**
     * Send a token to the client.
     *
     * @throws TransportException if sending fails
     */
    void send(StreamToken token) throws TransportException;

    /**
     * Flush the output buffer.
     * CRITICAL: Without flush, data may be buffered and not delivered.
     *
     * @throws TransportException if flushing fails
     */
    void flush() throws TransportException;

    /**
     * Close the transport.
     */
    void close();

    /**
     * Check if the transport is still connected.
     */
    boolean isConnected();

    /**
     * Get the transport type name.
     */
    String getType();

    /**
     * Exception for transport errors.
     */
    class TransportException extends RuntimeException {
        public TransportException(String message) {
            super(message);
        }

        public TransportException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
