package com.cadiducho.cservidoresmc.scheduler;

public interface CancellableTask {

    void cancel();

    boolean isCancelled();
}
