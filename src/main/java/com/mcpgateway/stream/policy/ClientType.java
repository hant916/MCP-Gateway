package com.mcpgateway.stream.policy;

/**
 * Client types for stream policy decisions.
 */
public enum ClientType {

    /**
     * Web browser client (supports SSE natively).
     */
    BROWSER,

    /**
     * Command-line client (curl, httpie, etc.).
     */
    CLI,

    /**
     * SDK client (official or third-party).
     */
    SDK,

    /**
     * Mobile application client.
     */
    MOBILE,

    /**
     * Server-to-server communication.
     */
    SERVER,

    /**
     * Unknown or undetectable client type.
     */
    UNKNOWN
}
