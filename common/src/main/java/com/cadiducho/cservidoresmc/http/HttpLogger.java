package com.cadiducho.cservidoresmc.http;

public interface HttpLogger {

    void debug(String text);

    void error(String text);

    default void retry(String text) {
        debug(text);
    }
}
