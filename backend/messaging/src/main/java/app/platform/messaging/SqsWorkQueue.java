package app.platform.messaging;

import app.platform.domain.port.WorkQueue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

@Component
public class SqsWorkQueue implements WorkQueue {

    private final SqsClient sqs;
    private final ObjectMapper mapper;
    private final String queueUrl;

    public SqsWorkQueue(SqsClient sqs, ObjectMapper mapper, @Value("${platform.sqs.requests-queue-url}") String queueUrl) {
        this.sqs = sqs;
        this.mapper = mapper;
        this.queueUrl = queueUrl;
    }

    @Override
    public void enqueueProvision(UUID requestId, String idempotencyKey) {
        send(new WorkMessage(WorkMessage.MessageType.PROVISION, requestId, idempotencyKey));
    }

    @Override
    public void enqueueDelete(UUID requestId, String idempotencyKey) {
        send(new WorkMessage(WorkMessage.MessageType.DELETE, requestId, idempotencyKey));
    }

    private void send(WorkMessage message) {
        // W3C traceparent rides as a message attribute so worker spans continue the API trace (4.05)
        Map<String, MessageAttributeValue> attributes = new HashMap<>();
        W3CTraceContextPropagator.getInstance()
                .inject(Context.current(), attributes, (map, key, value) -> map.put(
                        key,
                        MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue(value)
                                .build()));
        try {
            sqs.sendMessage(b -> b.queueUrl(queueUrl)
                    .messageBody(mapper.writeValueAsString(message))
                    .messageAttributes(attributes));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise work message", e);
        }
    }
}
