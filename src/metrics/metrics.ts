import { BufferedMetricsLogger } from 'datadog-metrics';
import { Config } from '../config/config';

class Metrics {
    private metrics: BufferedMetricsLogger;
    constructor(private config: Config, private prefix: string) {
        if (this.config.metrics.enabled) {
            this.metrics = new BufferedMetricsLogger({
                apiKey: this.config.metrics.api_key,
                appKey: this.config.metrics.app_key,
                prefix: this.prefix + '.',
                host: this.config.metrics.host,
                flushIntervalSeconds: this.config.metrics.flush_interval,
            });
        }
    }

    public gauge(key: string, value: number): void {
        if (this.config.metrics.enabled) {
            this.metrics.gauge(key, value);
        }
    }

    public increment(key: string, value?: number): void {
        if (this.config.metrics.enabled) {
            this.metrics.increment(key, value);
        }
    }

    public histogram(key: string, value: number): void {
        if (this.config.metrics.enabled) {
            this.metrics.histogram(key, value);
        }
    }

    public flush(): void {
        if (this.config.metrics.enabled) {
            this.metrics.flush();
        }
    }
}
