package com.queryai.workspace;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {

    @Query("select w from Workspace w where w.id in (select m.workspaceId from WorkspaceMember m where m.user.id = :userId) order by w.name")
    List<Workspace> findForUser(Long userId);

    @Query("select w from Workspace w order by w.createdAt desc")
    Page<Workspace> findAllOrdered(Pageable pageable);
}
