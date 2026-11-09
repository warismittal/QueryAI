package com.queryai.audit;

import com.queryai.audit.AuditService.AuditEventDto;
import com.queryai.common.ApiException;
import com.queryai.common.PageResponse;
import com.queryai.security.CurrentUser;
import com.queryai.workspace.WorkspaceAccessService;
import com.queryai.workspace.WorkspaceRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/audit")
@Tag(name = "Audit log")
public class AuditController {
    private final AuditService auditService;
    private final WorkspaceAccessService workspaceAccess;
    private final CurrentUser currentUser;

    public AuditController(AuditService auditService, WorkspaceAccessService workspaceAccess, CurrentUser currentUser) {
        this.auditService = auditService;
        this.workspaceAccess = workspaceAccess;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(summary = "Workspace audit events (workspace admins) or all events (platform admins, no workspaceId)")
    public PageResponse<AuditEventDto> search(@RequestParam(required = false) Long workspaceId,
                                              @RequestParam(required = false) AuditAction action,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "25") int size) {
        if (workspaceId == null) {
            if (!currentUser.isSystemAdmin()) {
                throw ApiException.forbidden("Platform-wide audit logs are restricted to administrators");
            }
        } else {
            workspaceAccess.require(workspaceId, WorkspaceRole.ADMIN);
        }
        return auditService.search(workspaceId, action, page, size);
    }

    @GetMapping("/me")
    @Operation(summary = "The caller's own recent activity")
    public java.util.List<AuditEventDto> mine() {
        return auditService.recentForUser(currentUser.id());
    }
}
