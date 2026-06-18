package com.cadiducho.cservidoresmc.cache;

public class SystemClock implements Clock {

    @Override
    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}
