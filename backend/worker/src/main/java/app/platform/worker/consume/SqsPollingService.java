package app.platform.worker.consume;

import app.platform.messaging.WorkMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

/**
 * Long-polling SQS consumer (3.04). WaitTimeSeconds=20, configurable concurrency, visibility
 * heartbeat for long handlers, graceful drain on shutdown, MDC cleared per message.
 */
@Service
public class SqsPollingService {

    private static final Logger log = LoggerFactory.getLogger(SqsPollingService.class);

    private final SqsClient sqs;
    private final ObjectMapper mapper;
    private final WorkDispatcher dispatcher;
    private final String queueUrl;
    private final int concurrency;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger inflight = new AtomicInteger();
    // Receipt handles currently being processed — released (visibility 0) on shutdown so a
    // task replaced mid-flight during a deploy never strands a message (Phase A reliability).
    private final java.util.Set<String> inflightHandles = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private ExecutorService pollerPool;
    private ExecutorService handlerPool;
    private ScheduledExecutorService heartbeatPool;

    private final Counter consumed;
    private final Timer processingDuration;

    public SqsPollingService(
            SqsClient sqs,
            ObjectMapper mapper,
            WorkDispatcher dispatcher,
            MeterRegistry metrics,
            @Value("${platform.sqs.requests-queue-url}") String queueUrl,
            @Value("${platform.worker.concurrency:10}") int concurrency) {
        this.sqs = sqs;
        this.mapper = mapper;
        this.dispatcher = dispatcher;
        this.queueUrl = queueUrl;
        this.concurrency = concurrency;
        this.consumed = metrics.counter("messages_consumed");
        this.processingDuration = metrics.timer("processing_duration_seconds");
        metrics.gauge("worker_inflight", inflight);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (queueUrl.isBlank()) {
            log.warn("No queue URL configured — consumer idle (fine for bare local runs)");
            return;
        }
        running.set(true);
        pollerPool = Executors.newSingleThreadExecutor(r -> new Thread(r, "sqs-poller"));
        handlerPool = Executors.newFixedThreadPool(concurrency, r -> new Thread(r, "sqs-handler"));
        heartbeatPool = Executors.newScheduledThreadPool(1, r -> new Thread(r, "sqs-heartbeat"));
        pollerPool.submit(this::pollLoop);
        log.info("SQS consumer started: queue={} concurrency={}", queueUrl, concurrency);
    }

    private void pollLoop() {
        while (running.get()) {
            try {
                List<Message> messages = sqs.receiveMessage(b -> b.queueUrl(queueUrl)
                                .waitTimeSeconds(20)
                                .maxNumberOfMessages(Math.min(10, concurrency))
                                .messageAttributeNames("All"))
                        .messages();
                for (Message message : messages) {
                    inflight.incrementAndGet();
                    handlerPool.submit(() -> handle(message));
                }
            } catch (Exception e) {
                if (running.get()) {
                    log.error("Poll loop error — backing off 5s", e);
                    sleep(Duration.ofSeconds(5));
                }
            }
        }
    }

    private void handle(Message message) {
        long start = System.nanoTime();
        inflightHandles.add(message.receiptHandle());
        // Heartbeat: extend visibility every 60s so slow handlers aren't redelivered mid-flight
        ScheduledFuture<?> heartbeat = heartbeatPool.scheduleAtFixedRate(
                () -> sqs.changeMessageVisibility(b ->
                        b.queueUrl(queueUrl).receiptHandle(message.receiptHandle()).visibilityTimeout(300)),
                60,
                60,
                TimeUnit.SECONDS);
        try {
            WorkMessage work = mapper.readValue(message.body(), WorkMessage.class);
            MDC.put("request_id", String.valueOf(work.requestId()));
            consumed.increment();

            WorkDispatcher.Outcome outcome = dispatcher.dispatch(work, message);
            if (outcome == WorkDispatcher.Outcome.RETRYABLE) {
                // leave on queue — visibility timeout expiry redelivers; DLQ after 5 receives
                log.warn("Retryable failure; message returns to queue");
            } else {
                sqs.deleteMessage(b -> b.queueUrl(queueUrl).receiptHandle(message.receiptHandle()));
            }
        } catch (Exception e) {
            // Unparseable/unexpected → let redelivery count climb toward the DLQ (poison handling)
            log.error("Message handling failed; will redeliver (poison → DLQ after 5)", e);
        } finally {
            heartbeat.cancel(false);
            inflightHandles.remove(message.receiptHandle());
            processingDuration.record(Duration.ofNanos(System.nanoTime() - start));
            inflight.decrementAndGet();
            MDC.clear(); // thread pools leak context otherwise
        }
    }

    /** Graceful shutdown: stop polling, drain in-flight handlers, 60s cap (pairs with ECS StopTimeout). */
    @PreDestroy
    public void stop() {
        running.set(false);
        if (pollerPool != null) {
            pollerPool.shutdown();
        }
        if (handlerPool != null) {
            handlerPool.shutdown();
            try {
                if (!handlerPool.awaitTermination(60, TimeUnit.SECONDS)) {
                    handlerPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // Anything still in-flight after the drain window couldn't finish — release its message
        // (visibility 0) so SQS redelivers it immediately to a healthy task, not after 5 minutes.
        for (String handle : inflightHandles) {
            try {
                sqs.changeMessageVisibility(
                        b -> b.queueUrl(queueUrl).receiptHandle(handle).visibilityTimeout(0));
            } catch (Exception e) {
                log.warn("Could not release in-flight message on shutdown", e);
            }
        }
        if (heartbeatPool != null) {
            heartbeatPool.shutdownNow();
        }
        log.info("SQS consumer drained and stopped ({} messages released for redelivery)", inflightHandles.size());
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
