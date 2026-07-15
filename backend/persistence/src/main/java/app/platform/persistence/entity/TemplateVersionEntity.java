package app.platform.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "template_versions")
public class TemplateVersionEntity {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "template_id", nullable = false)
    private String templateId;

    @Column(nullable = false)
    private String version;

    @Column(name = "commit_sha", nullable = false)
    private String commitSha;

    @Column(name = "s3_key_body", nullable = false)
    private String s3KeyBody;

    @Column(name = "s3_key_schema", nullable = false)
    private String s3KeySchema;

    @Column(name = "s3_key_manifest", nullable = false)
    private String s3KeyManifest;

    @Column(nullable = false)
    private String status;

    protected TemplateVersionEntity() {}

    public UUID getId() {
        return id;
    }

    public String getTemplateId() {
        return templateId;
    }

    public String getVersion() {
        return version;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public String getS3KeyBody() {
        return s3KeyBody;
    }

    public String getS3KeySchema() {
        return s3KeySchema;
    }

    public String getS3KeyManifest() {
        return s3KeyManifest;
    }

    public String getStatus() {
        return status;
    }
}
