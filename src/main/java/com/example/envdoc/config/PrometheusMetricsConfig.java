package com.example.envdoc.config;

import io.micrometer.core.instrument.Clock;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.prometheus.client.CollectorRegistry;
import org.springframework.boot.actuate.metrics.export.prometheus.PrometheusScrapeEndpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Гарантирует наличие Prometheus endpoint даже если авто-конфигурация не сработала.
 */
@Configuration
@ConditionalOnClass({PrometheusMeterRegistry.class, PrometheusScrapeEndpoint.class})
public class PrometheusMetricsConfig {

    @Bean
    @ConditionalOnMissingBean
    public PrometheusMeterRegistry prometheusMeterRegistry() {
        return new PrometheusMeterRegistry(
                io.micrometer.prometheus.PrometheusConfig.DEFAULT,
                new CollectorRegistry(),
                Clock.SYSTEM
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public PrometheusScrapeEndpoint prometheusScrapeEndpoint(PrometheusMeterRegistry registry) {
        return new PrometheusScrapeEndpoint(registry.getPrometheusRegistry());
    }
}
