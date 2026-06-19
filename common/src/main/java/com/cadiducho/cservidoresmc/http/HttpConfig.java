package com.cadiducho.cservidoresmc.http;

import com.cadiducho.cservidoresmc.config.CSConfiguration;

public class HttpConfig {

    public static final int DEFAULT_TIMEOUT = 5000;
    public static final int DEFAULT_RETRIES = 2;
    public static final int DEFAULT_RETRY_BACKOFF = 250;

    private final int connectTimeout;
    private final int readTimeout;
    private final int retries;
    private final int retryBackoff;

    public HttpConfig(int connectTimeout, int readTimeout, int retries, int retryBackoff) {
        this.connectTimeout = positiveOrDefault(connectTimeout, DEFAULT_TIMEOUT);
        this.readTimeout = positiveOrDefault(readTimeout, DEFAULT_TIMEOUT);
        this.retries = Math.max(0, retries);
        this.retryBackoff = positiveOrDefault(retryBackoff, DEFAULT_RETRY_BACKOFF);
    }

    public static HttpConfig from(CSConfiguration configuration) {
        if (configuration == null) {
            return new HttpConfig(DEFAULT_TIMEOUT, DEFAULT_TIMEOUT, DEFAULT_RETRIES, DEFAULT_RETRY_BACKOFF);
        }

        int readTimeout = configuration.getInt("api.readTimeout", "readTimeOut", DEFAULT_TIMEOUT);
        int connectTimeout = configuration.getInt("api.connectTimeout", "connectTimeOut", readTimeout);
        int retries = configuration.getInt("api.retries", "httpRetries", DEFAULT_RETRIES);
        int retryBackoff = configuration.getInt("api.retryBackoffMillis", "httpRetryBackoff", DEFAULT_RETRY_BACKOFF);

        return new HttpConfig(connectTimeout, readTimeout, retries, retryBackoff);
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

    private static int positiveOrDefault(int value, int defaultValue) {
        return value > 0 ? value : defaultValue;
    }
}
