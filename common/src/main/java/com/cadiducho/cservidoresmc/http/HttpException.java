package com.cadiducho.cservidoresmc.http;

import java.io.IOException;

public class HttpException extends IOException {

    private final int statusCode;
    private final String body;

    public HttpException(String message, int statusCode, String body) {
        super(message);
        this.statusCode = statusCode;
        this.body = body;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getBody() {
        return body;
    }
}
