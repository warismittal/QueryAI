package com.queryai.workspace;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, Long> {

    @Query("select m from WorkspaceMember m where m.workspaceId = :workspaceId and m.user.id = :userId")
    Optional<WorkspaceMember> findMembership(Long workspaceId, Long userId);

    @Query("select m from WorkspaceMember m join fetch m.user where m.workspaceId = :workspaceId order by m.joinedAt")
    List<WorkspaceMember> findByWorkspaceWithUser(Long workspaceId);

    long countByWorkspaceId(Long workspaceId);

    long countByWorkspaceIdAndRole(Long workspaceId, WorkspaceRole role);
}
