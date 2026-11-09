package com.queryai.workspace;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class WorkspaceDtos {
    private WorkspaceDtos() {
    }

    public record WorkspaceDto(Long id, String name, String description, Long ownerId, int monthlyQueryQuota,
                               WorkspaceRole myRole, long memberCount, Instant createdAt) {
    }

    public record CreateWorkspaceRequest(@NotBlank @Size(max = 120) String name, @Size(max = 500) String description) {
    }

    public record UpdateWorkspaceRequest(@NotBlank @Size(max = 120) String name, @Size(max = 500) String description) {
    }

    public record MemberDto(Long userId, String email, String fullName, WorkspaceRole role, Instant joinedAt) {
        static MemberDto from(WorkspaceMember m) {
            return new MemberDto(m.getUser().getId(), m.getUser().getEmail(), m.getUser().getFullName(), m.getRole(), m.getJoinedAt());
        }
    }

    public record AddMemberRequest(@NotBlank @Email String email, @NotNull WorkspaceRole role) {
    }

    public record ChangeRoleRequest(@NotNull WorkspaceRole role) {
    }

    public record UsageDto(Long workspaceId, Instant periodStart, long aiQueriesUsed, long totalQueries, int quota,
                           long remaining) {
    }
}
