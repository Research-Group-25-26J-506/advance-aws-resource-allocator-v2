package app.platform.domain.port;

import app.platform.domain.model.Template;
import app.platform.domain.model.TemplateVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TemplateRepository {

    List<Template> listPublished();

    Optional<Template> findTemplate(String templateId);

    Optional<TemplateVersion> findVersion(UUID templateVersionId);

    Optional<TemplateVersion> findLatestPublished(String templateId);
}
