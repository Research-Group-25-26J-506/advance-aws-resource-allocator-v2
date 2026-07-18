package app.platform.api.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

/**
 * Operator DLQ console (Phase C — Operate). A request whose work message fails the handler
 * maxReceiveCount times lands in the dead-letter queue; before this, it was invisible and there
 * was no supported way to retry it. This exposes the DLQ to PLATFORM_ADMINs: see how deep it is,
 * peek what's stuck, and redrive everything back to the source queue via a native SQS
 * message-move task (no manual receive/send/delete dance, no message loss).
 */
@RestController
@RequestMapping("/api/v1/admin/dlq")
public class DlqController {

    private static final Logger log = LoggerFactory.getLogger(DlqController.class);

    public record RedriveRequest(Integer maxMessagesPerSecond) {}

    private final SqsClient sqs;
    private final String dlqUrl;
    private final String dlqArn;

    public DlqController(
            SqsClient sqs,
            @Value("${PLATFORM_REQUESTS_DLQ_URL:}") String dlqUrl,
            @Value("${PLATFORM_REQUESTS_DLQ_ARN:}") String dlqArn) {
        this.sqs = sqs;
        this.dlqUrl = dlqUrl;
        this.dlqArn = dlqArn;
    }

    /** Depth + any in-flight redrive task, so the UI can show "N stuck" and "redrive running". */
    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public Map<String, Object> summary() {
        if (dlqUrl.isBlank()) {
            return Map.of("configured", false, "visible", 0, "notVisible", 0);
        }
        var attrs = sqs.getQueueAttributes(b -> b.queueUrl(dlqUrl)
                        .attributeNames(
                                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE))
                .attributes();
        int visible = intAttr(attrs.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES));
        int notVisible = intAttr(attrs.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE));

        Map<String, Object> out = new java.util.HashMap<>();
        out.put("configured", true);
        out.put("visible", visible);
        out.put("notVisible", notVisible);
        out.put("redrive", latestMoveTask());
        return out;
    }

    /** Peek (non-destructive): read up to 10 with visibility 0 so they immediately reappear. */
    @GetMapping("/messages")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public List<Map<String, Object>> peek() {
        if (dlqUrl.isBlank()) {
            return List.of();
        }
        var messages = sqs.receiveMessage(b -> b.queueUrl(dlqUrl)
                        .maxNumberOfMessages(10)
                        .visibilityTimeout(0)
                        .waitTimeSeconds(1)
                        .messageSystemAttributeNames(MessageSystemAttributeName.ALL))
                .messages();
        List<Map<String, Object>> out = new ArrayList<>();
        for (var m : messages) {
            var sys = m.attributesAsStrings();
            out.add(Map.of(
                    "messageId", m.messageId(),
                    "receiveCount", sys.getOrDefault("ApproximateReceiveCount", "?"),
                    "firstSentAt", sys.getOrDefault("SentTimestamp", "?"),
                    "body", m.body() == null ? "" : m.body()));
        }
        return out;
    }

    /** Redrive everything back to the source queue. Returns the move-task handle for polling. */
    @PostMapping("/redrive")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<Map<String, Object>> redrive(@RequestBody(required = false) RedriveRequest req) {
        if (dlqArn.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "DLQ not configured"));
        }
        Integer rate = req == null ? null : req.maxMessagesPerSecond();
        var result = sqs.startMessageMoveTask(b -> {
            b.sourceArn(dlqArn);
            if (rate != null && rate > 0) {
                b.maxNumberOfMessagesPerSecond(rate);
            }
        });
        log.info("DLQ redrive started: taskHandle={}", result.taskHandle());
        return ResponseEntity.accepted().body(Map.of("taskHandle", result.taskHandle()));
    }

    private Map<String, Object> latestMoveTask() {
        try {
            var tasks = sqs.listMessageMoveTasks(b -> b.sourceArn(dlqArn).maxResults(1)).results();
            if (tasks.isEmpty()) {
                return Map.of("status", "NONE");
            }
            var t = tasks.get(0);
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("status", t.status());
            m.put("moved", t.approximateNumberOfMessagesMoved());
            m.put("toMove", t.approximateNumberOfMessagesToMove());
            if (t.failureReason() != null) {
                m.put("failureReason", t.failureReason());
            }
            return m;
        } catch (Exception e) {
            // ListMessageMoveTasks throws if the DLQ never had a task — treat as "none".
            return Map.of("status", "NONE");
        }
    }

    private static int intAttr(String v) {
        try {
            return v == null ? 0 : Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
