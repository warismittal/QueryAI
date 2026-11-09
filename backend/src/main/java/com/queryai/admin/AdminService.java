package com.queryai.admin;

import com.queryai.ai.LlmClient;
import com.queryai.audit.AuditAction;
import com.queryai.audit.AuditService;
import com.queryai.common.ApiException;
import com.queryai.common.PageResponse;
import com.queryai.datasource.DataSourceRepository;
import com.queryai.history.QueryRunRepository;
import com.queryai.security.CurrentUser;
import com.queryai.user.SystemRole;
import com.queryai.user.User;
import com.queryai.user.UserDtos.UserDto;
import com.queryai.user.UserRepository;
import com.queryai.workspace.WorkspaceDtos.WorkspaceDto;
import com.queryai.workspace.WorkspaceRepository;
import com.queryai.workspace.WorkspaceService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

/** Platform administration. All endpoints are additionally protected by the ROLE_ADMIN URL rule. */
@Service
public class AdminService {
    private final UserRepository users;
    private final WorkspaceRepository workspaces;
    private final WorkspaceService workspaceService;
    private final DataSourceRepository dataSources;
    private final QueryRunRepository queryRuns;
    private final CurrentUser currentUser;
    private final AuditService audit;
    private final LlmClient llm;

    public AdminService(UserRepository users, WorkspaceRepository workspaces, WorkspaceService workspaceService,
                        DataSourceRepository dataSources, QueryRunRepository queryRuns, CurrentUser currentUser,
                        AuditService audit, LlmClient llm) {
        this.users = users;
        this.workspaces = workspaces;
        this.workspaceService = workspaceService;
        this.dataSources = dataSources;
        this.queryRuns = queryRuns;
        this.currentUser = currentUser;
        this.audit = audit;
        this.llm = llm;
    }

    public record UpdateUserRequest(SystemRole systemRole, Boolean enabled) {
    }

    public record QuotaRequest(@Min(0) @Max(1_000_000) int monthlyQueryQuota) {
    }

    public record PlatformStats(long users, long enabledUsers, long workspaces, long dataSources, long queriesLast24h,
                                long queriesLast30d, boolean llmConfigured, String llmDescription, Instant measuredAt) {
    }

    private void requireAdmin() {
        if (!currentUser.isSystemAdmin()) {
            throw ApiException.forbidden("Administrator role required");
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<UserDto> users(String q, int page, int size) {
        requireAdmin();
        return PageResponse.of(users.search(q == null || q.isBlank() ? null : q.trim(),
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100))), UserDto::from);
    }

    @Transactional
    public UserDto updateUser(Long id, UpdateUserRequest request) {
        requireAdmin();
        User user = users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        if (user.getId().equals(currentUser.id())) {
            throw ApiException.badRequest("You cannot change your own role or disable your own account");
        }
        Map<String, Object> changes = new HashMap<>();
        if (request.systemRole() != null && request.systemRole() != user.getSystemRole()) {
            changes.put("role", request.systemRole().name());
            user.setSystemRole(request.systemRole());
        }
        if (request.enabled() != null && request.enabled() != user.isEnabled()) {
            changes.put("enabled", request.enabled());
            user.setEnabled(request.enabled());
        }
        audit.record(AuditAction.ADMIN_USER_UPDATED, null, "USER", id, changes);
        return UserDto.from(user);
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkspaceDto> workspaces(int page, int size) {
        requireAdmin();
        return PageResponse.of(workspaces.findAllOrdered(PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100))),
                ws -> workspaceService.toDto(ws, null));
    }

    @Transactional
    public WorkspaceDto setQuota(Long workspaceId, QuotaRequest request) {
        requireAdmin();
        return workspaceService.setQuota(workspaceId, request.monthlyQueryQuota());
    }

    @Transactional(readOnly = true)
    public PlatformStats stats() {
        requireAdmin();
        Instant now = Instant.now();
        return new PlatformStats(users.count(), users.countByEnabledTrue(), workspaces.count(), dataSources.count(),
                queryRuns.countByCreatedAtGreaterThanEqual(now.minus(1, ChronoUnit.DAYS)),
                queryRuns.countByCreatedAtGreaterThanEqual(now.minus(30, ChronoUnit.DAYS)),
                llm.isConfigured(), llm.description(), now);
    }
}
