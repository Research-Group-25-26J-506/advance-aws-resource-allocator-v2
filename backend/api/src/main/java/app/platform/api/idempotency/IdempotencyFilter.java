package app.platform.api.idempotency;

import app.platform.common.UuidV7;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

/**
 * Idempotency-Key middleware (3.13). Write methods must carry a UUIDv7 Idempotency-Key.
 * DynamoDB platform-idempotency stores the first response for 24h; repeats replay it with
 * X-Idempotent-Replay: true; same key + different payload → 422; in-flight → 409 + Retry-After.
 * Partition key is hash(actor + endpoint + key) so two users generating the same UUID never
 * collide into each other's responses.
 */
@Component
@Order(10)
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final Duration TTL = Duration.ofHours(24);
    /** In-flight rows older than this are treated as crashed and retried, not 409'd forever. */
    private static final Duration STALE_RESERVATION = Duration.ofMinutes(5);

    private final DynamoDbClient dynamo;
    private final String tableName;
    private final boolean enabled;

    public IdempotencyFilter(
            DynamoDbClient dynamo,
            @Value("${platform.ddb.idempotency-table:platform-idempotency-dev}") String tableName,
            @Value("${platform.idempotency.enabled:true}") boolean enabled) {
        this.dynamo = dynamo;
        this.tableName = tableName;
        this.enabled = enabled;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled
                || !WRITE_METHODS.contains(request.getMethod())
                || !request.getRequestURI().startsWith("/api/")
                || request.getRequestURI().startsWith("/api/v1/webhooks"); // HMAC-authed, own dedupe
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader("Idempotency-Key");
        if (key == null || !UuidV7.isValid(key)) {
            problem(response, 400, "MISSING_IDEMPOTENCY_KEY", "Idempotency-Key header (UUIDv7) is required");
            return;
        }

        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        // Force-read the body so the request hash sees it (getContentAsByteArray is lazy)
        wrappedRequest.getParameterMap();
        byte[] body = wrappedRequest.getInputStream().readAllBytes();

        String requestHash = sha256(request.getMethod() + "|" + request.getRequestURI() + "|"
                + new String(body, StandardCharsets.UTF_8));
        String pk = sha256(actorId() + "|" + request.getRequestURI() + "|" + key);
        long now = Instant.now().getEpochSecond();

        try {
            // Reserve: first writer wins; stale crashed reservations are reclaimable
            dynamo.putItem(b -> b.tableName(tableName)
                    .item(Map.of(
                            "pk", AttributeValue.fromS(pk),
                            "actor_id", AttributeValue.fromS(actorId()),
                            "endpoint", AttributeValue.fromS(request.getRequestURI()),
                            "request_hash", AttributeValue.fromS(requestHash),
                            "reserved_at", AttributeValue.fromN(String.valueOf(now)),
                            "expires_at", AttributeValue.fromN(String.valueOf(now + TTL.toSeconds()))))
                    .conditionExpression("attribute_not_exists(pk) OR (attribute_not_exists(response_status)"
                            + " AND reserved_at < :stale)")
                    .expressionAttributeValues(Map.of(
                            ":stale", AttributeValue.fromN(String.valueOf(now - STALE_RESERVATION.toSeconds())))));
        } catch (ConditionalCheckFailedException e) {
            replayOrConflict(pk, requestHash, response);
            return;
        }

        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        chain.doFilter(new BodyCachedRequest(wrappedRequest, body), wrappedResponse);

        storeResponse(pk, wrappedResponse, now);
        wrappedResponse.copyBodyToResponse();
    }

    private void replayOrConflict(String pk, String requestHash, HttpServletResponse response) throws IOException {
        var item = dynamo.getItem(b -> b.tableName(tableName)
                        .key(Map.of("pk", AttributeValue.fromS(pk)))
                        .consistentRead(true))
                .item();
        if (item == null || item.isEmpty()) {
            problem(response, 409, "IDEMPOTENCY_RACE", "Request in flight; retry shortly");
            return;
        }
        if (!item.get("request_hash").s().equals(requestHash)) {
            problem(response, 422, "IDEMPOTENCY_KEY_REUSED",
                    "Idempotency key already used with a different payload");
            return;
        }
        if (!item.containsKey("response_status")) {
            response.setHeader("Retry-After", "2");
            problem(response, 409, "REQUEST_IN_FLIGHT", "Original request still processing");
            return;
        }
        response.setStatus(Integer.parseInt(item.get("response_status").n()));
        response.setHeader("X-Idempotent-Replay", "true");
        response.setContentType(item.getOrDefault("content_type", AttributeValue.fromS("application/json")).s());
        response.getWriter().write(item.get("response_body").s());
    }

    private void storeResponse(String pk, ContentCachingResponseWrapper response, long reservedAt) {
        String bodyText = new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
        // DynamoDB 400KB item cap: oversized bodies should spill to S3 (pointer in item);
        // truncated storage keeps the filter safe until the S3 spill lands.
        String stored = bodyText.length() > 350_000 ? bodyText.substring(0, 350_000) : bodyText;
        dynamo.updateItem(b -> b.tableName(tableName)
                .key(Map.of("pk", AttributeValue.fromS(pk)))
                .updateExpression("SET response_status = :s, response_body = :b, content_type = :c")
                .expressionAttributeValues(Map.of(
                        ":s", AttributeValue.fromN(String.valueOf(response.getStatus())),
                        ":b", AttributeValue.fromS(stored),
                        ":c",
                                AttributeValue.fromS(response.getContentType() == null
                                        ? "application/json"
                                        : response.getContentType()))));
    }

    private String actorId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "anonymous" : auth.getName();
    }

    private void problem(HttpServletResponse response, int status, String code, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.getWriter()
                .write("{\"status\":%d,\"title\":\"%s\",\"detail\":\"%s\",\"code\":\"%s\"}"
                        .formatted(status, code, detail, code));
    }

    private static String sha256(String input) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
