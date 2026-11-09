package com.queryai.workspace;

import com.queryai.common.ApiException;
import com.queryai.security.CurrentUser;
import com.queryai.user.SystemRole;
import com.queryai.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Central authorization checks for workspace-scoped resources. Every service that touches workspace data calls into
 * this class, so tenancy rules live in exactly one place.
 */
@Service
public class WorkspaceAccessService {
    private final WorkspaceMemberRepository members;
    private final WorkspaceRepository workspaces;
    private final CurrentUser currentUser;

    public WorkspaceAccessService(WorkspaceMemberRepository members, WorkspaceRepository workspaces, CurrentUser currentUser) {
        this.members = members;
        this.workspaces = workspaces;
        this.currentUser = currentUser;
    }

    /** Returns the caller's effective role or throws 404 when they are not a member (avoids leaking existence). */
    @Transactional(readOnly = true)
    public WorkspaceRole roleIn(Long workspaceId) {
        User user = currentUser.require();
        if (!workspaces.existsById(workspaceId)) {
            throw ApiException.notFound("Workspace");
        }
        return members.findMembership(workspaceId, user.getId())
                .map(WorkspaceMember::getRole)
                .orElseGet(() -> {
                    if (user.getSystemRole() == SystemRole.ADMIN) {
                        return WorkspaceRole.OWNER; // platform administrators can manage every workspace
                    }
                    throw ApiException.notFound("Workspace");
                });
    }

    @Transactional(readOnly = true)
    public WorkspaceRole require(Long workspaceId, WorkspaceRole minimum) {
        if (workspaceId == null) {
            throw ApiException.badRequest("workspaceId is required");
        }
        WorkspaceRole role = roleIn(workspaceId);
        if (!role.atLeast(minimum)) {
            throw ApiException.forbidden("This action requires the " + minimum + " role in the workspace");
        }
        return role;
    }

    public void requireMember(Long workspaceId) {
        require(workspaceId, WorkspaceRole.VIEWER);
    }
}
