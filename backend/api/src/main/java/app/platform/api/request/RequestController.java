package app.platform.api.request;

import app.platform.api.request.RequestDtos.CreateRequestPayload;
import app.platform.api.request.RequestDtos.RequestDto;
import app.platform.api.request.RequestDtos.RequestEventDto;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/requests")
public class RequestController {

    /** SSE poller pool — one shared scheduler, emitters are cheap. */
    private final ScheduledExecutorService ssePool = Executors.newScheduledThreadPool(2);

    private final RequestService service;

    public RequestController(RequestService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('USER','PLATFORM_ADMIN')")
    public ResponseEntity<RequestDto> submit(
            @Valid @RequestBody CreateRequestPayload payload,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication auth) {
        var request = service.submit(
                auth.getName(),
                auth.getName(), // email == principal name for Cognito email-alias pools
                payload.templateId(),
                payload.environment(),
                payload.region(),
                payload.resourceName(),
                payload.configuration(),
                idempotencyKey);
        // Long-running work never blocks HTTP threads: 202 + Location, progress via SSE (3.01)
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/requests/" + request.id()))
                .body(RequestDto.from(request));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<RequestDto> list(
            @RequestParam(defaultValue = "me") String requester,
            @RequestParam(defaultValue = "20") int limit,
            Authentication auth) {
        return service.listMine(auth.getName(), Math.min(limit, 100)).stream()
                .map(RequestDto::from)
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public RequestDto get(@PathVariable UUID id) {
        return RequestDto.from(service.get(id));
    }

    @GetMapping("/{id}/events")
    @PreAuthorize("isAuthenticated()")
    public List<RequestEventDto> listEvents(@PathVariable UUID id) {
        return service.listEvents(id).stream()
                .map(e -> new RequestEventDto(
                        e.from() == null ? null : e.from().name(),
                        e.to().name(),
                        e.reason(),
                        e.source(),
                        e.occurredAt()))
                .toList();
    }

    /**
     * SSE stream: full status snapshot every 2s while in progress, 15s keepalive (ALB idle
     * timeout is 120s — see 6.04), completes on terminal state. `id:` field set so clients can
     * resume with Last-Event-ID.
     */
    @GetMapping(value = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("isAuthenticated()")
    public SseEmitter streamEvents(@PathVariable UUID id) {
        service.get(id);
        SseEmitter emitter = new SseEmitter(0L);
        AtomicReference<String> lastStatus = new AtomicReference<>();
        var future = ssePool.scheduleAtFixedRate(
                () -> {
                    try {
                        var request = service.get(id);
                        String status = request.status().name();
                        if (!status.equals(lastStatus.getAndSet(status))) {
                            emitter.send(SseEmitter.event()
                                    .id(String.valueOf(System.currentTimeMillis()))
                                    .name("status")
                                    .data(RequestDto.from(request)));
                        } else {
                            emitter.send(SseEmitter.event().comment("keepalive"));
                        }
                        if (request.status().isTerminal()) {
                            emitter.complete();
                        }
                    } catch (Exception e) {
                        emitter.completeWithError(e);
                    }
                },
                0,
                2,
                TimeUnit.SECONDS);
        emitter.onCompletion(() -> future.cancel(true));
        emitter.onTimeout(() -> future.cancel(true));
        return emitter;
    }

    @PostMapping("/{id}/promote")
    @PreAuthorize("hasAnyRole('USER','PLATFORM_ADMIN')")
    public ResponseEntity<RequestDto> promote(
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication auth) {
        var promoted = service.promote(auth.getName(), auth.getName(), id, idempotencyKey);
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/requests/" + promoted.id()))
                .body(RequestDto.from(promoted));
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize("hasAnyRole('USER','PLATFORM_ADMIN')")
    public ResponseEntity<RequestDto> retry(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/requests/" + id))
                .body(RequestDto.from(service.retry(auth.getName(), id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER','PLATFORM_ADMIN')")
    public ResponseEntity<RequestDto> delete(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/requests/" + id))
                .body(RequestDto.from(service.delete(auth.getName(), id)));
    }
}
