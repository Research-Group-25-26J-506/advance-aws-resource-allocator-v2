package app.platform.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "request_events")
public class RequestEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID requestId;

    @Column(name = "from_status")
    private String fromStatus;

    @Column(name = "to_status", nullable = false)
    private String toStatus;

    @Column
    private String reason;

    @Column(nullable = false)
    private String source;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected RequestEventEntity() {}

    public RequestEventEntity(
            UUID requestId, String fromStatus, String toStatus, String reason, String source, Instant occurredAt) {
        this.requestId = requestId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
        this.source = source;
        this.occurredAt = occurredAt;
    }

    public Long getId() {
        return id;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public String getReason() {
        return reason;
    }

    public String getSource() {
        return source;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
