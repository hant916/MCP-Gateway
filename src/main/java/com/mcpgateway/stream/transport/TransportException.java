package com.mcpgateway.stream.transport;

/**
 * Exception thrown when transport operations fail.
 *
 * This is NOT treated as an error condition for streaming -
 * fallback to ASYNC_JOB is the SUCCESS path.
 */
public class TransportException extends RuntimeException {

    public TransportException(String message) {
        super(message);
    }

    public TransportException(String message, Throwable cause) {
        super(message, cause);
    }

    public TransportException(Throwable cause) {
        super(cause);
    }

    /**
     * Check if this exception indicates the connection was closed by client.
     */
    public boolean isClientDisconnect() {
        String msg = getMessage();
        return msg != null && (
                msg.contains("connection closed") ||
                msg.contains("client disconnected") ||
                msg.contains("broken pipe")
        );
    }

    /**
     * Check if this exception indicates a timeout.
     */
    public boolean isTimeout() {
        String msg = getMessage();
        return msg != null && msg.contains("timeout");
    }
}
