package com.queryai.history;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface QueryRunRepository extends JpaRepository<QueryRun, Long> {

    long countByWorkspaceIdAndSourceAndCreatedAtGreaterThanEqual(Long workspaceId, QuerySource source, Instant since);

    long countByWorkspaceIdAndCreatedAtGreaterThanEqual(Long workspaceId, Instant since);

    long countByCreatedAtGreaterThanEqual(Instant since);

    @Query("""
            select r from QueryRun r
            where r.workspaceId = :workspaceId
              and (:userId is null or r.userId = :userId)
              and (:dataSourceId is null or r.dataSourceId = :dataSourceId)
              and (:status is null or r.executionStatus = :status)
              and (:source is null or r.source = :source)
              and (:q is null
                   or lower(coalesce(r.question, '')) like lower(concat('%', cast(:q as string), '%'))
                   or lower(coalesce(r.finalSql, '')) like lower(concat('%', cast(:q as string), '%')))
            order by r.createdAt desc
            """)
    Page<QueryRun> search(@Param("workspaceId") Long workspaceId, @Param("userId") Long userId,
                          @Param("dataSourceId") Long dataSourceId, @Param("status") ExecutionStatus status,
                          @Param("source") QuerySource source, @Param("q") String q, Pageable pageable);

    @Query("""
            select new com.queryai.history.QueryRunStats(
                count(r),
                sum(case when r.executionStatus = com.queryai.history.ExecutionStatus.SUCCESS then 1L else 0L end),
                avg(r.durationMs))
            from QueryRun r
            where r.workspaceId = :workspaceId and r.createdAt >= :since
              and r.executionStatus in (com.queryai.history.ExecutionStatus.SUCCESS, com.queryai.history.ExecutionStatus.FAILED)
            """)
    QueryRunStats executionStats(@Param("workspaceId") Long workspaceId, @Param("since") Instant since);

    @Query(value = """
            select cast(date_trunc('day', created_at) as date) as day,
                   count(*) as total,
                   sum(case when execution_status = 'SUCCESS' then 1 else 0 end) as succeeded,
                   sum(case when execution_status = 'FAILED' then 1 else 0 end) as failed
            from query_runs
            where workspace_id = :workspaceId and created_at >= :since
            group by 1 order by 1
            """, nativeQuery = true)
    List<Object[]> dailyActivity(@Param("workspaceId") Long workspaceId, @Param("since") Instant since);

    List<QueryRun> findTop8ByWorkspaceIdAndUserIdOrderByCreatedAtDesc(Long workspaceId, Long userId);

    List<QueryRun> findByConversationIdOrderByCreatedAtAsc(Long conversationId);
}
