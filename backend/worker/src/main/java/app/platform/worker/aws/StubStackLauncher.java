package app.platform.worker.aws;

import app.platform.domain.model.Environment;
import app.platform.domain.model.RequestStatus;
import app.platform.domain.port.RequestRepository;
import app.platform.domain.port.StackLauncher;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Local-profile stub driver (8.06): LocalStack CFN is not faithful enough, so stack lifecycles
 * are faked — CREATE completes ~8s after initiation, standing in for the EventBridge listener.
 * Known divergence from real AWS; documented in dev/README.md.
 */
@Component
@Profile("local")
public class StubStackLauncher implements StackLauncher {

    private static final Logger log = LoggerFactory.getLogger(StubStackLauncher.class);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final RequestRepository requests;

    public StubStackLauncher(RequestRepository requests) {
        this.requests = requests;
    }

    @Override
    public String createStack(StackLaunch launch) {
        String stackId = "arn:aws:cloudformation:%s:000000000000:stack/%s/%s"
                .formatted(launch.region(), launch.stackName(), UUID.randomUUID());
        log.info("[stub] CreateStack {} with {} params, {} tags",
                launch.stackName(), launch.parameters().size(), launch.tags().size());

        UUID requestId = UUID.fromString(launch.tags().stream()
                .filter(t -> t.key().equals("PlatformRequestId"))
                .findFirst()
                .orElseThrow()
                .value());
        scheduler.schedule(() -> complete(requestId), 8, TimeUnit.SECONDS);
        return stackId;
    }

    private void complete(UUID requestId) {
        requests.findById(requestId).ifPresent(request -> {
            if (request.status() != RequestStatus.CREATE_IN_PROGRESS) {
                return;
            }
            request.transitionTo(RequestStatus.CREATE_COMPLETE);
            requests.save(request);
            requests.appendEvent(
                    requestId,
                    RequestStatus.CREATE_IN_PROGRESS,
                    RequestStatus.CREATE_COMPLETE,
                    "[stub] stack lifecycle simulated",
                    "CLOUDFORMATION",
                    Instant.now());
            log.info("[stub] Request {} -> CREATE_COMPLETE", requestId);
        });
    }

    @Override
    public void updateStack(StackLaunch launch) {
        log.info("[stub] UpdateStack {} with {} params", launch.stackName(), launch.parameters().size());
        // Real AWS: the EventBridge listener flips UPDATE_IN_PROGRESS -> UPDATE_COMPLETE. The stack
        // name here is the stored stack id, so resolve the request by stack id like delete does.
        scheduler.schedule(() -> completeUpdate(launch.stackName()), 6, TimeUnit.SECONDS);
    }

    private void completeUpdate(String stackId) {
        requests.findByStackId(stackId).ifPresent(request -> {
            if (request.status() != RequestStatus.UPDATE_IN_PROGRESS) {
                return;
            }
            request.transitionTo(RequestStatus.UPDATE_COMPLETE);
            requests.save(request);
            requests.appendEvent(
                    request.id(),
                    RequestStatus.UPDATE_IN_PROGRESS,
                    RequestStatus.UPDATE_COMPLETE,
                    "[stub] stack update simulated",
                    "CLOUDFORMATION",
                    Instant.now());
            log.info("[stub] Request {} -> UPDATE_COMPLETE", request.id());
        });
    }

    @Override
    public void deleteStack(String stackId, String executionRoleArn, Environment env, String region) {
        log.info("[stub] DeleteStack {}", stackId);
        // Real AWS: the EventBridge listener flips DELETE_IN_PROGRESS -> DELETE_COMPLETE.
        // Locally we simulate it, same as create.
        scheduler.schedule(() -> completeDelete(stackId), 5, TimeUnit.SECONDS);
    }

    private void completeDelete(String stackId) {
        requests.findByStackId(stackId).ifPresent(request -> {
            if (request.status() != RequestStatus.DELETE_IN_PROGRESS) {
                return;
            }
            request.transitionTo(RequestStatus.DELETE_COMPLETE);
            requests.save(request);
            requests.appendEvent(
                    request.id(),
                    RequestStatus.DELETE_IN_PROGRESS,
                    RequestStatus.DELETE_COMPLETE,
                    "[stub] stack deletion simulated",
                    "CLOUDFORMATION",
                    Instant.now());
            log.info("[stub] Request {} -> DELETE_COMPLETE", request.id());
        });
    }
}
