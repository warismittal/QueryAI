package com.queryai.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    @Query("""
            select e from AuditEvent e
            where (:workspaceId is null or e.workspaceId = :workspaceId)
              and (:action is null or e.action = :action)
            order by e.createdAt desc
            """)
    Page<AuditEvent> search(@Param("workspaceId") Long workspaceId, @Param("action") AuditAction action, Pageable pageable);

    List<AuditEvent> findTop10ByWorkspaceIdOrderByCreatedAtDesc(Long workspaceId);

    List<AuditEvent> findTop15ByUserIdOrderByCreatedAtDesc(Long userId);
}
