package com.example.envdoc.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация метрик Micrometer.
 */
@Configuration
public class MetricsConfig {

    /**
     * TimedAspect для поддержки аннотации @Timed.
     */
    @Bean
    @ConditionalOnClass(name = "org.aspectj.lang.ProceedingJoinPoint")
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }
}
