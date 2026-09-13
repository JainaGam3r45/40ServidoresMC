package com.cadiducho.cservidoresmc.http;

import com.cadiducho.cservidoresmc.config.CSConfiguration;

public class HttpConfig {

    public static final int DEFAULT_TIMEOUT = 5000;
    public static final int DEFAULT_RETRIES = 2;
    public static final int DEFAULT_RETRY_BACKOFF = 250;
    public static final int MAX_RETRIES = 5;

    private final int connectTimeout;
    private final int readTimeout;
    private final int retries;
    private final int retryBackoff;
    private final String userAgent;
    private final String authorization;
    private final String body;

    public HttpConfig(int connectTimeout, int readTimeout, int retries, int retryBackoff) {
        this(connectTimeout, readTimeout, retries, retryBackoff, null, null, null);
    }

    public HttpConfig(int connectTimeout, int readTimeout, int retries, int retryBackoff, String userAgent) {
        this(connectTimeout, readTimeout, retries, retryBackoff, userAgent, null, null);
    }

    public HttpConfig(int connectTimeout, int readTimeout, int retries, int retryBackoff,
                      String userAgent, String authorization, String body) {
        this.connectTimeout = positiveOrDefault(connectTimeout, DEFAULT_TIMEOUT);
        this.readTimeout = positiveOrDefault(readTimeout, DEFAULT_TIMEOUT);
        this.retries = Math.min(MAX_RETRIES, Math.max(0, retries));
        this.retryBackoff = positiveOrDefault(retryBackoff, DEFAULT_RETRY_BACKOFF);
        this.userAgent = blankToNull(userAgent);
        this.authorization = blankToNull(authorization);
        this.body = body;
    }

    public static HttpConfig from(CSConfiguration configuration) {
        return from(configuration, null);
    }

    public static HttpConfig from(CSConfiguration configuration, String userAgent) {
        return from(configuration, userAgent, null, null);
    }

    public static HttpConfig from(CSConfiguration configuration, String userAgent, String authorization, String body) {
        if (configuration == null) {
            return new HttpConfig(DEFAULT_TIMEOUT, DEFAULT_TIMEOUT, DEFAULT_RETRIES, DEFAULT_RETRY_BACKOFF,
                    userAgent, authorization, body);
        }

        int readTimeout = configuration.getInt("api.readTimeout", "readTimeOut", DEFAULT_TIMEOUT);
        int connectTimeout = configuration.getInt("api.connectTimeout", "connectTimeOut", readTimeout);
        int retries = configuration.getInt("api.retries", "httpRetries", DEFAULT_RETRIES);
        int retryBackoff = configuration.getInt("api.retryBackoffMillis", "httpRetryBackoff", DEFAULT_RETRY_BACKOFF);

        return new HttpConfig(connectTimeout, readTimeout, retries, retryBackoff, userAgent, authorization, body);
    }

    public HttpConfig withAuthorization(String authorization) {
        return new HttpConfig(connectTimeout, readTimeout, retries, retryBackoff, userAgent, authorization, body);
    }

    public HttpConfig withBody(String body) {
        return new HttpConfig(connectTimeout, readTimeout, retries, retryBackoff, userAgent, authorization, body);
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public int getRetries() {
        return retries;
    }

    public int getRetryBackoff() {
        return retryBackoff;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getAuthorization() {
        return authorization;
    }

    public String getBody() {
        return body;
    }

    private static int positiveOrDefault(int value, int defaultValue) {
        return value > 0 ? value : defaultValue;
    }

    private static String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
