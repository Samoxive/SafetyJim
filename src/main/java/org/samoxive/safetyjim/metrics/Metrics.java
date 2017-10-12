package org.samoxive.safetyjim.metrics;

import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.StatsDClient;

public class Metrics {
    private StatsDClient client;
    private boolean enabled;

    public Metrics(String prefix, String host, int port, boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            this.client = new NonBlockingStatsDClient(prefix, host, port);
        }
    }

    public void gauge(String key, int value) {
        if (enabled) {
            this.client.gauge(key, value);
        }
    }

    public void increment(String key) {
        if (enabled) {
            this.client.increment(key);
        }
    }

    public void histogram(String key, int value) {
        if (enabled) {
            this.client.histogram(key, value);
        }
    }


}
