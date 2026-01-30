package com.example.envdoc.metrics;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

/**
 * Listener для сбора метрик запросов к GigaChat.
 */
@Slf4j
public class GigaChatMetricsListener implements ChatModelListener {

    private static final String REQUEST_START_TIME = "request.start.time";

    private final Counter requestsTotal;
    private final Counter errorsTotal;
    private final DistributionSummary tokensInput;
    private final DistributionSummary tokensOutput;
    private final Timer duration;

    public GigaChatMetricsListener(MeterRegistry meterRegistry) {
        this.requestsTotal = Counter.builder("gigachat.requests.total")
            .description("Total number of GigaChat requests")
            .register(meterRegistry);

        this.errorsTotal = Counter.builder("gigachat.errors.total")
            .description("Total number of GigaChat errors")
            .register(meterRegistry);

        this.tokensInput = DistributionSummary.builder("gigachat.tokens.input")
            .description("Distribution of input tokens per request")
            .register(meterRegistry);

        this.tokensOutput = DistributionSummary.builder("gigachat.tokens.output")
            .description("Distribution of output tokens per request")
            .register(meterRegistry);

        this.duration = Timer.builder("gigachat.duration")
            .description("Duration of GigaChat requests")
            .register(meterRegistry);
    }

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        requestsTotal.increment();
        requestContext.attributes().put(REQUEST_START_TIME, System.nanoTime());
        log.debug("GigaChat request started");
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        Object startTimeObj = responseContext.attributes().get(REQUEST_START_TIME);
        if (startTimeObj instanceof Long startTime) {
            long durationNanos = System.nanoTime() - startTime;
            duration.record(Duration.ofNanos(durationNanos));
        }

        TokenUsage tokenUsage = responseContext.chatResponse().tokenUsage();
        if (tokenUsage != null) {
            if (tokenUsage.inputTokenCount() != null) {
                tokensInput.record(tokenUsage.inputTokenCount());
            }
            if (tokenUsage.outputTokenCount() != null) {
                tokensOutput.record(tokenUsage.outputTokenCount());
            }
        }

        log.debug("GigaChat request completed successfully");
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        errorsTotal.increment();

        Object startTimeObj = errorContext.attributes().get(REQUEST_START_TIME);
        if (startTimeObj instanceof Long startTime) {
            long durationNanos = System.nanoTime() - startTime;
            duration.record(Duration.ofNanos(durationNanos));
        }

        log.warn("GigaChat request failed: {}", errorContext.error().getMessage());
    }
}
