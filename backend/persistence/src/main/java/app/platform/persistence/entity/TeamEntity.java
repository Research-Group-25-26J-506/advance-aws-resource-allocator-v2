package app.platform.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "teams")
public class TeamEntity {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "cost_center", nullable = false)
    private String costCenter;

    protected TeamEntity() {}

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCostCenter() {
        return costCenter;
    }
}
