package app.platform.api.misc;

import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;

/**
 * Service log visibility: lists deployed app services (by their /platform/apps/* log groups)
 * and tails their CloudWatch logs directly in the platform - no request record needed, so it
 * works for services deployed via any path.
 */
@RestController
@RequestMapping("/api/v1/apps")
public class AppLogsController {

    private static final String PREFIX = "/platform/apps/";

    private final CloudWatchLogsClient logs;

    public AppLogsController(CloudWatchLogsClient logs) {
        this.logs = logs;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> apps() {
        return logs
                .describeLogGroupsPaginator(b -> b.logGroupNamePrefix(PREFIX))
                .logGroups()
                .stream()
                .map(g -> Map.<String, Object>of(
                        "logGroup", g.logGroupName(),
                        "name", g.logGroupName().substring(PREFIX.length()),
                        "storedBytes", g.storedBytes() == null ? 0 : g.storedBytes()))
                .toList();
    }

    @GetMapping("/logs")
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> tail(
            @RequestParam String logGroup, @RequestParam(defaultValue = "200") int limit) {
        if (!logGroup.startsWith(PREFIX)) {
            return List.of();
        }
        long since = System.currentTimeMillis() - 3600_000L; // last hour
        return logs
                .filterLogEvents(b -> b.logGroupName(logGroup)
                        .startTime(since)
                        .limit(Math.min(limit, 1000)))
                .events()
                .stream()
                .map(e -> Map.<String, Object>of("timestamp", e.timestamp(), "message", e.message()))
                .sorted((a, b) -> Long.compare((long) b.get("timestamp"), (long) a.get("timestamp")))
                .toList();
    }
}
