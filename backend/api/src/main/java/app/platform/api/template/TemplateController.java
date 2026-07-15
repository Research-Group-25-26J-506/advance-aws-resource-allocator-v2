package app.platform.api.template;

import app.platform.domain.error.NotFoundException;
import app.platform.domain.model.Template;
import app.platform.domain.port.TemplateRepository;
import app.platform.domain.port.TemplateStore;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/templates")
public class TemplateController {

    private final TemplateRepository templates;
    private final TemplateStore store;

    public TemplateController(TemplateRepository templates, TemplateStore store) {
        this.templates = templates;
        this.store = store;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<Template> list() {
        return templates.listPublished();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> get(@PathVariable String id) {
        Template template =
                templates.findTemplate(id).orElseThrow(() -> new NotFoundException("Template", id));
        var version = templates.findLatestPublished(id).orElseThrow(() -> new NotFoundException("Published version", id));
        // schema.json is fetched from the registry so the wizard can render the dynamic form (2.04)
        String schemaJson = store.fetchSchema(version.s3KeySchema());
        return Map.of(
                "template", template,
                "latestVersion", version.version(),
                "schema", schemaJson);
    }
}
