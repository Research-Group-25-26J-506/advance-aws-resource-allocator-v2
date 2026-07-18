package app.platform.domain.service;

import app.platform.domain.model.Request;
import app.platform.domain.model.Tag;
import app.platform.domain.model.Team;
import app.platform.domain.model.TemplateVersion;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Centralised tag computation (3.08). Every provisioned resource carries the mandatory tags — cost
 * reporting, drift attribution and the audit trail all key off them — followed by any user-supplied
 * tags from the wizard's TagEditor. Mandatory tags ALWAYS win: a custom tag can never shadow a
 * mandatory key, use the reserved {@code aws:} prefix, or push the resource past AWS's 50-tag limit.
 */
public class TagPolicyService {

    /** AWS allows 50 tags/resource; we reserve room for the mandatory set. */
    private static final int MAX_CUSTOM_TAGS = 40;
    private static final int MAX_KEY_LEN = 128;
    private static final int MAX_VALUE_LEN = 256;

    public List<Tag> mandatoryTags(Request request, Team team, TemplateVersion version, String application) {
        List<Tag> tags = new ArrayList<>();
        tags.add(new Tag("Owner", request.requesterEmail()));
        tags.add(new Tag("CostCenter", team.costCenter()));
        tags.add(new Tag("Environment", request.environment().name()));
        tags.add(new Tag("Application", application));
        tags.add(new Tag("ManagedBy", "Platform"));
        tags.add(new Tag("PlatformRequestId", request.id().toString()));
        tags.add(new Tag("SourceCommitSha", version.commitSha()));

        appendCustomTags(tags, request.customTags());
        return List.copyOf(tags);
    }

    private void appendCustomTags(List<Tag> tags, Map<String, String> custom) {
        if (custom == null || custom.isEmpty()) {
            return;
        }
        Set<String> reserved = new LinkedHashSet<>();
        for (Tag t : tags) {
            reserved.add(t.key());
        }
        int added = 0;
        for (Map.Entry<String, String> e : custom.entrySet()) {
            if (added >= MAX_CUSTOM_TAGS) {
                break;
            }
            String key = e.getKey() == null ? "" : e.getKey().trim();
            String value = e.getValue() == null ? "" : e.getValue().trim();
            if (key.isEmpty() || key.length() > MAX_KEY_LEN || value.length() > MAX_VALUE_LEN) {
                continue; // skip empty/oversized keys silently — the UI validates first
            }
            if (key.toLowerCase(Locale.ROOT).startsWith("aws:") || reserved.contains(key)) {
                continue; // reserved prefix or a mandatory key — mandatory always wins
            }
            reserved.add(key); // de-dupe repeated custom keys
            tags.add(new Tag(key, value));
            added++;
        }
    }
}
