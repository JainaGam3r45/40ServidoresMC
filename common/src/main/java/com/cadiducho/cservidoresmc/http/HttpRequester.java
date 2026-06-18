package com.cadiducho.cservidoresmc.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class HttpRequester {

    public String get(URL url, String requestName, HttpConfig config, HttpLogger logger) throws IOException {
        return request(url, "GET", requestName, config, logger);
    }

    public String request(URL url, String method, String requestName, HttpConfig config, HttpLogger logger) throws IOException {
        IOException lastFailure = null;
        int maxAttempts = config.getRetries() + 1;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            HttpURLConnection connection = null;
            try {
                connection = openConnection(url, method, config);
                int statusCode = connection.getResponseCode();
                String body = readResponseBody(connection, statusCode);

                if (isSuccess(statusCode)) {
                    logger.debug(requestName + " completado con HTTP " + statusCode + " en intento " + attempt + ".");
                    return body;
                }

                HttpException failure = new HttpException(
                        requestName + " falló con HTTP " + statusCode + bodySummary(body),
                        statusCode,
                        body
                );

                if (!isRetryableStatus(statusCode) || attempt == maxAttempts) {
                    throw failure;
                }

                lastFailure = failure;
                logger.retry(requestName + " recibió HTTP " + statusCode + "; reintento " + attempt + " de " + config.getRetries() + ".");
                sleepBeforeRetry(config, attempt);
            } catch (SocketTimeoutException e) {
                lastFailure = e;
                if (attempt == maxAttempts) {
                    throw e;
                }
                logger.retry(requestName + " agotó el tiempo de espera; reintento " + attempt + " de " + config.getRetries() + ".");
                sleepBeforeRetry(config, attempt);
            } catch (InterruptedIOException e) {
                Thread.currentThread().interrupt();
                throw e;
            } catch (IOException e) {
                lastFailure = e;
                if (e instanceof HttpException || attempt == maxAttempts) {
                    throw e;
                }
                logger.retry(requestName + " falló por I/O transitorio; reintento " + attempt + " de " + config.getRetries() + ".");
                sleepBeforeRetry(config, attempt);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        throw lastFailure == null ? new IOException(requestName + " falló sin respuesta HTTP.") : lastFailure;
    }

    protected HttpURLConnection openConnection(URL url, String method, HttpConfig config) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(config.getConnectTimeout());
        connection.setReadTimeout(config.getReadTimeout());
        connection.setUseCaches(false);
        return connection;
    }

    private String readResponseBody(HttpURLConnection connection, int statusCode) throws IOException {
        InputStream stream = isSuccess(statusCode) ? connection.getInputStream() : connection.getErrorStream();
        if (stream == null) {
            return "";
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder body = new StringBuilder();
            char[] buffer = new char[1024];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                body.append(buffer, 0, read);
            }
            return body.toString();
        }
    }

    private boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private boolean isRetryableStatus(int statusCode) {
        return statusCode >= 500 && statusCode < 600;
    }

    private void sleepBeforeRetry(HttpConfig config, int attempt) throws InterruptedIOException {
        try {
            Thread.sleep((long) config.getRetryBackoff() * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            InterruptedIOException interrupted = new InterruptedIOException("HTTP retry interrupted.");
            interrupted.initCause(e);
            throw interrupted;
        }
    }

    private String bodySummary(String body) {
        if (body == null || body.trim().isEmpty()) {
            return ".";
        }

        String compact = body.replace('\n', ' ').replace('\r', ' ').trim();
        if (compact.length() > 160) {
            compact = compact.substring(0, 160) + "...";
        }
        return ": " + compact;
    }
}
