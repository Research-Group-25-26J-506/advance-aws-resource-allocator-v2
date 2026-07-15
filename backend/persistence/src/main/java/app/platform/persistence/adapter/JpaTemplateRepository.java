package app.platform.persistence.adapter;

import app.platform.domain.model.Template;
import app.platform.domain.model.TemplateVersion;
import app.platform.domain.port.TemplateRepository;
import app.platform.persistence.entity.TemplateEntity;
import app.platform.persistence.entity.TemplateVersionEntity;
import app.platform.persistence.repo.SpringDataRepos;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaTemplateRepository implements TemplateRepository {

    private final SpringDataRepos.Templates templates;
    private final SpringDataRepos.TemplateVersions versions;

    public JpaTemplateRepository(SpringDataRepos.Templates templates, SpringDataRepos.TemplateVersions versions) {
        this.templates = templates;
        this.versions = versions;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Template> listPublished() {
        return templates.findByMaturityIn(List.of("stable", "beta")).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Template> findTemplate(String templateId) {
        return templates.findById(templateId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TemplateVersion> findVersion(UUID templateVersionId) {
        return versions.findById(templateVersionId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TemplateVersion> findLatestPublished(String templateId) {
        return versions.findFirstByTemplateIdAndStatusOrderByVersionDesc(templateId, "PUBLISHED")
                .map(this::toDomain);
    }

    private Template toDomain(TemplateEntity e) {
        String latest = versions.findFirstByTemplateIdAndStatusOrderByVersionDesc(e.getId(), "PUBLISHED")
                .map(TemplateVersionEntity::getVersion)
                .orElse(null);
        return new Template(
                e.getId(),
                e.getDisplayName(),
                e.getDescription(),
                e.getCategory(),
                e.getMaturity(),
                e.getApplication(),
                latest);
    }

    private TemplateVersion toDomain(TemplateVersionEntity e) {
        return new TemplateVersion(
                e.getId(),
                e.getTemplateId(),
                e.getVersion(),
                e.getCommitSha(),
                e.getS3KeyBody(),
                e.getS3KeySchema(),
                e.getS3KeyManifest(),
                e.getStatus());
    }
}
