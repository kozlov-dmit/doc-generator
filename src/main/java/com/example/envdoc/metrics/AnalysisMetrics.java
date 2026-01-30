package com.example.envdoc.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Метрики для процесса анализа переменных окружения.
 */
@Component
public class AnalysisMetrics {

    private final MeterRegistry meterRegistry;
    private final Timer totalDuration;
    private final Counter completedTotal;
    private final Counter failedTotal;
    private final AtomicInteger activeJobs;
    private final AtomicInteger lastVariablesCount;

    public AnalysisMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.totalDuration = Timer.builder("analysis.duration.total")
            .description("Total duration of analysis process")
            .register(meterRegistry);

        this.completedTotal = Counter.builder("analysis.completed.total")
            .description("Total number of completed analyses")
            .register(meterRegistry);

        this.failedTotal = Counter.builder("analysis.failed.total")
            .description("Total number of failed analyses")
            .register(meterRegistry);

        this.activeJobs = new AtomicInteger(0);
        Gauge.builder("analysis.jobs.active", activeJobs, AtomicInteger::get)
            .description("Number of active analysis jobs")
            .register(meterRegistry);

        this.lastVariablesCount = new AtomicInteger(0);
        Gauge.builder("analysis.variables.count", lastVariablesCount, AtomicInteger::get)
            .description("Number of variables found in last analysis")
            .register(meterRegistry);
    }

    /**
     * Создаёт Timer.Sample для измерения времени шага.
     */
    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    /**
     * Записывает время выполнения общего анализа.
     */
    public void recordTotalDuration(Timer.Sample sample) {
        sample.stop(totalDuration);
    }

    /**
     * Записывает время выполнения отдельного шага.
     */
    public void recordStepDuration(Timer.Sample sample, String stepName) {
        Timer stepTimer = Timer.builder("analysis.step.duration")
            .tag("step", stepName)
            .description("Duration of analysis step")
            .register(meterRegistry);
        sample.stop(stepTimer);
    }

    /**
     * Увеличивает счётчик активных задач.
     */
    public void incrementActiveJobs() {
        activeJobs.incrementAndGet();
    }

    /**
     * Уменьшает счётчик активных задач.
     */
    public void decrementActiveJobs() {
        activeJobs.decrementAndGet();
    }

    /**
     * Обновляет количество найденных переменных.
     */
    public void setVariablesCount(int count) {
        lastVariablesCount.set(count);
    }

    /**
     * Отмечает успешное завершение анализа.
     */
    public void recordCompleted() {
        completedTotal.increment();
    }

    /**
     * Отмечает неудачное завершение анализа.
     */
    public void recordFailed() {
        failedTotal.increment();
    }
}
