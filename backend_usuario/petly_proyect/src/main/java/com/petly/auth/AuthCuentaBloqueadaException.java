package com.petly.auth;

public class AuthCuentaBloqueadaException extends RuntimeException {
    private final long retryAfterSeconds;
    public AuthCuentaBloqueadaException(String message) { this(message, 60); }
    public AuthCuentaBloqueadaException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }
    public long getRetryAfterSeconds() { return retryAfterSeconds; }
}
