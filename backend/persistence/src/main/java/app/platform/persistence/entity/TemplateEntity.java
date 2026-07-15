package app.platform.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "templates")
public class TemplateEntity {

    @Id
    private String id;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String maturity;

    @Column
    private String application;

    protected TemplateEntity() {}

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public String getMaturity() {
        return maturity;
    }

    public String getApplication() {
        return application;
    }
}
