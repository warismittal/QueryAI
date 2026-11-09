package com.queryai.dashboard;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DashboardRepository extends JpaRepository<Dashboard, Long> {

    @Query("""
            select d from Dashboard d
            where d.workspaceId = :workspaceId
              and (d.ownerId = :userId or d.visibility = com.queryai.savedquery.Visibility.WORKSPACE)
            order by d.updatedAt desc
            """)
    List<Dashboard> findVisible(Long workspaceId, Long userId);

    long countByWorkspaceId(Long workspaceId);
}
