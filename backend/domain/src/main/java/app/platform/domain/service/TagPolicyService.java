package app.platform.domain.service;

import app.platform.domain.model.Request;
import app.platform.domain.model.Tag;
import app.platform.domain.model.Team;
import app.platform.domain.model.TemplateVersion;
import java.util.ArrayList;
import java.util.List;

/**
 * Centralised mandatory-tag computation (3.08). Every provisioned resource carries these — cost
 * reporting, drift attribution and the audit trail all key off them.
 */
public class TagPolicyService {

    public List<Tag> mandatoryTags(Request request, Team team, TemplateVersion version, String application) {
        List<Tag> tags = new ArrayList<>();
        tags.add(new Tag("Owner", request.requesterEmail()));
        tags.add(new Tag("CostCenter", team.costCenter()));
        tags.add(new Tag("Environment", request.environment().name()));
        tags.add(new Tag("Application", application));
        tags.add(new Tag("ManagedBy", "Platform"));
        tags.add(new Tag("PlatformRequestId", request.id().toString()));
        tags.add(new Tag("SourceCommitSha", version.commitSha()));
        return List.copyOf(tags);
    }
}
