package com.cadiducho.cservidoresmc.http;

/**
 * Thrown when the API responds with HTTP 429.
 */
public class RateLimitedException extends HttpException {

    private final long retryAfterMillis;

    public RateLimitedException(String message, String body, long retryAfterMillis) {
        super(message, 429, body);
        this.retryAfterMillis = Math.max(0L, retryAfterMillis);
    }

    public long getRetryAfterMillis() {
        return retryAfterMillis;
    }
}
