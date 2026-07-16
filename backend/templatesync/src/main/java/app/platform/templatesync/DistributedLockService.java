package app.platform.templatesync;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

/**
 * DynamoDB conditional-write lock (3.10). TTL-based with heartbeat; AutoCloseable release.
 * Fencing caveat: a paused holder can act after expiry — writers must re-check DB state before
 * each mutation (the sync handler does).
 */
@Service
public class DistributedLockService {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockService.class);

    private final DynamoDbClient dynamo;
    private final String table;
    private final ScheduledExecutorService heartbeats = Executors.newScheduledThreadPool(1);
    private final String holderPrefix = System.getenv().getOrDefault("HOSTNAME", "local");

    public DistributedLockService(
            DynamoDbClient dynamo, @Value("${platform.ddb.locks-table:platform-locks-dev}") String table) {
        this.dynamo = dynamo;
        this.table = table;
    }

    public class Lock implements AutoCloseable {
        private final String name;
        private final String holder;
        private final ScheduledFuture<?> heartbeat;

        private Lock(String name, String holder, Duration ttl) {
            this.name = name;
            this.holder = holder;
            this.heartbeat = heartbeats.scheduleAtFixedRate(
                    () -> extend(name, holder, ttl), ttl.toSeconds() / 3, ttl.toSeconds() / 3, TimeUnit.SECONDS);
        }

        @Override
        public void close() {
            heartbeat.cancel(false);
            try {
                dynamo.deleteItem(b -> b.tableName(table)
                        .key(Map.of("name", AttributeValue.fromS(name)))
                        .conditionExpression("holder = :h")
                        .expressionAttributeValues(Map.of(":h", AttributeValue.fromS(holder))));
            } catch (ConditionalCheckFailedException e) {
                log.warn("Lock {} was not ours to release (expired + retaken?)", name);
            }
        }
    }

    public static class LockTimeoutException extends RuntimeException {
        public LockTimeoutException(String name) {
            super("Could not acquire lock '" + name + "' — another sync is in progress");
        }
    }

    public Lock acquire(String name, Duration ttl) {
        String holder = holderPrefix + "-" + UUID.randomUUID();
        long now = Instant.now().getEpochSecond();
        try {
            dynamo.putItem(b -> b.tableName(table)
                    .item(Map.of(
                            "name", AttributeValue.fromS(name),
                            "holder", AttributeValue.fromS(holder),
                            "acquired_at", AttributeValue.fromN(String.valueOf(now)),
                            "expires_at", AttributeValue.fromN(String.valueOf(now + ttl.toSeconds()))))
                    .conditionExpression("attribute_not_exists(#n) OR expires_at < :now")
                    .expressionAttributeNames(Map.of("#n", "name"))
                    .expressionAttributeValues(Map.of(":now", AttributeValue.fromN(String.valueOf(now)))));
        } catch (ConditionalCheckFailedException e) {
            throw new LockTimeoutException(name);
        }
        log.info("Acquired lock {} as {}", name, holder);
        return new Lock(name, holder, ttl);
    }

    private void extend(String name, String holder, Duration ttl) {
        try {
            long expires = Instant.now().getEpochSecond() + ttl.toSeconds();
            dynamo.updateItem(b -> b.tableName(table)
                    .key(Map.of("name", AttributeValue.fromS(name)))
                    .updateExpression("SET expires_at = :e, heartbeat_at = :h")
                    .conditionExpression("holder = :holder")
                    .expressionAttributeValues(Map.of(
                            ":e", AttributeValue.fromN(String.valueOf(expires)),
                            ":h", AttributeValue.fromN(String.valueOf(Instant.now().getEpochSecond())),
                            ":holder", AttributeValue.fromS(holder))));
        } catch (Exception e) {
            log.error("Lock heartbeat failed for {} — sync may lose the lock", name, e);
        }
    }
}
