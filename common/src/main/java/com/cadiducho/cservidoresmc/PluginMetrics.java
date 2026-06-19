package com.cadiducho.cservidoresmc;

import java.util.concurrent.atomic.AtomicLong;

public class PluginMetrics {

    private final AtomicLong apiRequests = new AtomicLong();
    private final AtomicLong apiFailures = new AtomicLong();
    private final AtomicLong retries = new AtomicLong();
    private final AtomicLong httpRejections = new AtomicLong();
    private final AtomicLong voteChecks = new AtomicLong();
    private final AtomicLong rewardsDelivered = new AtomicLong();

    public void incrementApiRequests() {
        apiRequests.incrementAndGet();
    }

    public void incrementApiFailures() {
        apiFailures.incrementAndGet();
    }

    public void incrementRetries() {
        retries.incrementAndGet();
    }

    public void incrementHttpRejections() {
        httpRejections.incrementAndGet();
    }

    public void incrementVoteChecks() {
        voteChecks.incrementAndGet();
    }

    public void incrementRewardsDelivered() {
        rewardsDelivered.incrementAndGet();
    }

    public long getApiRequests() {
        return apiRequests.get();
    }

    public long getApiFailures() {
        return apiFailures.get();
    }

    public long getRetries() {
        return retries.get();
    }

    public long getHttpRejections() {
        return httpRejections.get();
    }

    public long getVoteChecks() {
        return voteChecks.get();
    }

    public long getRewardsDelivered() {
        return rewardsDelivered.get();
    }
}
