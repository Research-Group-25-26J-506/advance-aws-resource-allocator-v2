package app.platform.persistence.repo;

import app.platform.persistence.entity.RequestEntity;
import app.platform.persistence.entity.RequestEventEntity;
import app.platform.persistence.entity.TeamEntity;
import app.platform.persistence.entity.TemplateEntity;
import app.platform.persistence.entity.TemplateVersionEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataRepos {

    interface Requests extends JpaRepository<RequestEntity, UUID> {
        List<RequestEntity> findByRequesterIdOrderBySubmittedAtDesc(String requesterId, Pageable pageable);

        Optional<RequestEntity> findByIdempotencyKey(String idempotencyKey);
    }

    interface RequestEvents extends JpaRepository<RequestEventEntity, Long> {
        List<RequestEventEntity> findByRequestIdOrderByOccurredAtAsc(UUID requestId);
    }

    interface Templates extends JpaRepository<TemplateEntity, String> {
        List<TemplateEntity> findByMaturityIn(List<String> maturities);
    }

    interface TemplateVersions extends JpaRepository<TemplateVersionEntity, UUID> {
        Optional<TemplateVersionEntity> findFirstByTemplateIdAndStatusOrderByVersionDesc(
                String templateId, String status);
    }

    interface Teams extends JpaRepository<TeamEntity, UUID> {}
}
