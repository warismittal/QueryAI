package com.queryai.audit;

import com.queryai.common.PageResponse;
import com.queryai.common.RequestUtils;
import com.queryai.security.CurrentUser;
import com.queryai.user.User;
import com.queryai.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Records security-relevant activity. Successful events join the caller's transaction (so they are only kept if the
 * operation commits); failures are written in a separate transaction so they survive the caller's rollback.
 */
@Service
public class AuditService {
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository repository;
    private final CurrentUser currentUser;
    private final UserRepository users;
    private final TransactionTemplate requiresNew;

    public AuditService(AuditEventRepository repository, CurrentUser currentUser, UserRepository users,
                        PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.currentUser = currentUser;
        this.users = users;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Records an event for the currently authenticated user. */
    @Transactional
    public void record(AuditAction action, Long workspaceId, String resourceType, Object resourceId, Map<String, ?> details) {
        Long userId = currentUser.idIfPresent().orElse(null);
        String email = userId == null ? null : users.findById(userId).map(User::getEmail).orElse(null);
        persist(build(action, workspaceId, userId, email, resourceType, resourceId, details, true), true);
    }

    /** Records an event for the current user that represents a failed or blocked operation. */
    public void recordFailure(AuditAction action, Long workspaceId, String resourceType, Object resourceId, Map<String, ?> details) {
        Long userId = currentUser.idIfPresent().orElse(null);
        persist(build(action, workspaceId, userId, null, resourceType, resourceId, details, false), false);
    }

    public void recordFor(User user, AuditAction action, Long workspaceId, String resourceType, Object resourceId,
                          Map<String, ?> details, boolean success) {
        persist(build(action, workspaceId, user.getId(), user.getEmail(), resourceType, resourceId, details, success), success);
    }

    public void recordAnonymous(AuditAction action, Map<String, ?> details) {
        persist(build(action, null, null, null, null, null, details, false), false);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEventDto> search(Long workspaceId, AuditAction action, int page, int size) {
        return PageResponse.of(repository.search(workspaceId, action, PageRequest.of(Math.max(0, page), Math.min(Math.max(size, 1), 200))),
                AuditEventDto::from);
    }

    @Transactional(readOnly = true)
    public List<AuditEventDto> recentForUser(Long userId) {
        return repository.findTop15ByUserIdOrderByCreatedAtDesc(userId).stream().map(AuditEventDto::from).toList();
    }

    private AuditEvent build(AuditAction action, Long workspaceId, Long userId, String email, String resourceType,
                             Object resourceId, Map<String, ?> details, boolean success) {
        AuditEvent event = new AuditEvent();
        event.setAction(action);
        event.setWorkspaceId(workspaceId);
        event.setUserId(userId);
        event.setUserEmail(email);
        event.setResourceType(resourceType);
        event.setResourceId(resourceId == null ? null : String.valueOf(resourceId));
        event.setDetails(details == null || details.isEmpty() ? null : new HashMap<>(details));
        event.setIpAddress(RequestUtils.clientIp());
        event.setSuccess(success);
        return event;
    }

    private void persist(AuditEvent event, boolean joinCurrentTransaction) {
        try {
            if (joinCurrentTransaction) {
                repository.save(event);
            } else {
                requiresNew.executeWithoutResult(status -> repository.save(event));
            }
        } catch (RuntimeException e) {
            // Auditing must never break the user-facing operation; log loudly instead.
            log.error("Failed to persist audit event {}", event.getAction(), e);
        }
    }

    public record AuditEventDto(Long id, Long workspaceId, Long userId, String userEmail, AuditAction action,
                                String resourceType, String resourceId, Map<String, Object> details, String ipAddress,
                                boolean success, Instant createdAt) {
        public static AuditEventDto from(AuditEvent e) {
            return new AuditEventDto(e.getId(), e.getWorkspaceId(), e.getUserId(), e.getUserEmail(), e.getAction(),
                    e.getResourceType(), e.getResourceId(), e.getDetails(), e.getIpAddress(), e.isSuccess(), e.getCreatedAt());
        }
    }
}
