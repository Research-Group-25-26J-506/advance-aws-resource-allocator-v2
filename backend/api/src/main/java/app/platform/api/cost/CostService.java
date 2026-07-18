package app.platform.api.cost;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;
import software.amazon.awssdk.services.costexplorer.model.DateInterval;
import software.amazon.awssdk.services.costexplorer.model.Expression;
import software.amazon.awssdk.services.costexplorer.model.Granularity;
import software.amazon.awssdk.services.costexplorer.model.GroupDefinition;
import software.amazon.awssdk.services.costexplorer.model.GroupDefinitionType;
import software.amazon.awssdk.services.costexplorer.model.MetricValue;
import software.amazon.awssdk.services.costexplorer.model.TagValues;

/**
 * Cost visibility (governance, via cost tags — not quotas). Reads AWS Cost Explorer for month-to-
 * date spend, a monthly trend, and breakdowns by the platform's own cost-allocation tags
 * (CostCenter = team, Environment) plus per-resource spend by PlatformRequestId.
 *
 * <p>Cost Explorer bills ~$0.01 per request, so every call is cached (default 6h). Tag breakdowns
 * only return data once those tag keys are activated as cost-allocation tags in the Billing
 * console — until then the queries succeed but come back empty, which the API reports honestly.
 */
@Service
public class CostService {

    private static final Logger log = LoggerFactory.getLogger(CostService.class);
    private static final String METRIC = "UnblendedCost";
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    // The platform's mandatory tags (TagPolicyService) usable as cost dimensions.
    public static final String TAG_TEAM = "CostCenter";
    public static final String TAG_ENV = "Environment";
    public static final String TAG_REQUEST = "PlatformRequestId";

    public record MonthAmount(String month, double amount) {}

    public record CostSummary(
            boolean available,
            String currency,
            double monthToDate,
            double previousMonth,
            List<MonthAmount> trend,
            String updatedAt,
            String note) {}

    public record CostSlice(String key, double amount) {}

    public record ResourceCost(boolean available, String currency, double monthToDate) {}

    private final CostExplorerClient ce;
    private final Duration ttl;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(Object value, Instant expiresAt) {}

    public CostService(CostExplorerClient ce, @Value("${platform.cost.cache-ttl-minutes:360}") long ttlMinutes) {
        this.ce = ce;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    /** MTD total, previous full month, and a 6-month monthly trend. */
    public CostSummary summary() {
        return cached("summary", this::loadSummary);
    }

    /** Current-month spend grouped by a cost-allocation tag (team or environment). */
    public List<CostSlice> breakdownByTag(String tagKey) {
        return cached("tag:" + tagKey, () -> loadBreakdown(tagKey));
    }

    /** Current-month spend attributed to one provisioned resource (by PlatformRequestId tag). */
    public ResourceCost forResource(String requestId) {
        return cached("res:" + requestId, () -> loadResource(requestId));
    }

    private CostSummary loadSummary() {
        LocalDate today = LocalDate.now();
        LocalDate start = YearMonth.from(today).minusMonths(5).atDay(1);
        LocalDate end = today.plusDays(1); // Cost Explorer end is exclusive
        try {
            var resp = ce.getCostAndUsage(b -> b.timePeriod(interval(start, end))
                    .granularity(Granularity.MONTHLY)
                    .metrics(METRIC));
            List<MonthAmount> trend = new ArrayList<>();
            String currency = "USD";
            for (var r : resp.resultsByTime()) {
                MetricValue mv = r.total().get(METRIC);
                double amt = mv == null ? 0.0 : parse(mv.amount());
                if (mv != null && mv.unit() != null) {
                    currency = mv.unit();
                }
                trend.add(new MonthAmount(r.timePeriod().start().substring(0, 7), round(amt)));
            }
            double mtd = trend.isEmpty() ? 0.0 : trend.get(trend.size() - 1).amount();
            double prev = trend.size() >= 2 ? trend.get(trend.size() - 2).amount() : 0.0;
            return new CostSummary(true, currency, mtd, prev, trend, Instant.now().toString(), null);
        } catch (Exception e) {
            log.warn("Cost Explorer summary unavailable: {}", e.getMessage());
            return new CostSummary(false, "USD", 0, 0, List.of(), Instant.now().toString(), reason(e));
        }
    }

    private List<CostSlice> loadBreakdown(String tagKey) {
        LocalDate start = YearMonth.from(LocalDate.now()).atDay(1);
        LocalDate end = LocalDate.now().plusDays(1);
        try {
            var resp = ce.getCostAndUsage(b -> b.timePeriod(interval(start, end))
                    .granularity(Granularity.MONTHLY)
                    .metrics(METRIC)
                    .groupBy(GroupDefinition.builder()
                            .type(GroupDefinitionType.TAG)
                            .key(tagKey)
                            .build()));
            List<CostSlice> slices = new ArrayList<>();
            for (var r : resp.resultsByTime()) {
                for (var g : r.groups()) {
                    // Tag group keys arrive as "TagKey$TagValue"; untagged spend is "TagKey$".
                    String raw = g.keys().isEmpty() ? "" : g.keys().get(0);
                    int dollar = raw.indexOf('$');
                    String value = dollar >= 0 ? raw.substring(dollar + 1) : raw;
                    if (value.isBlank()) {
                        value = "(untagged)";
                    }
                    MetricValue mv = g.metrics().get(METRIC);
                    double amt = mv == null ? 0.0 : parse(mv.amount());
                    if (amt > 0) {
                        slices.add(new CostSlice(value, round(amt)));
                    }
                }
            }
            slices.sort((a, c) -> Double.compare(c.amount(), a.amount()));
            return slices;
        } catch (Exception e) {
            log.warn("Cost Explorer breakdown by {} unavailable: {}", tagKey, e.getMessage());
            return List.of();
        }
    }

    private ResourceCost loadResource(String requestId) {
        LocalDate start = YearMonth.from(LocalDate.now()).atDay(1);
        LocalDate end = LocalDate.now().plusDays(1);
        try {
            Expression filter = Expression.builder()
                    .tags(TagValues.builder().key(TAG_REQUEST).values(requestId).build())
                    .build();
            var resp = ce.getCostAndUsage(b -> b.timePeriod(interval(start, end))
                    .granularity(Granularity.MONTHLY)
                    .metrics(METRIC)
                    .filter(filter));
            double amt = 0.0;
            String currency = "USD";
            for (var r : resp.resultsByTime()) {
                MetricValue mv = r.total().get(METRIC);
                if (mv != null) {
                    amt += parse(mv.amount());
                    if (mv.unit() != null) {
                        currency = mv.unit();
                    }
                }
            }
            return new ResourceCost(true, currency, round(amt));
        } catch (Exception e) {
            log.warn("Cost Explorer resource cost unavailable: {}", e.getMessage());
            return new ResourceCost(false, "USD", 0);
        }
    }

    private static DateInterval interval(LocalDate start, LocalDate end) {
        return DateInterval.builder().start(start.format(ISO)).end(end.format(ISO)).build();
    }

    private static double parse(String s) {
        try {
            return s == null ? 0.0 : Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String reason(Exception e) {
        String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return m.length() > 200 ? m.substring(0, 200) : m;
    }

    @SuppressWarnings("unchecked")
    private <T> T cached(String key, Supplier<T> loader) {
        Cached hit = cache.get(key);
        if (hit != null && hit.expiresAt().isAfter(Instant.now())) {
            return (T) hit.value();
        }
        T value = loader.get();
        cache.put(key, new Cached(value, Instant.now().plus(ttl)));
        return value;
    }
}
