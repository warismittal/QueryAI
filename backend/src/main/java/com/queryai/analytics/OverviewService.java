package com.queryai.analytics;

import com.queryai.ai.LlmClient;
import com.queryai.audit.AuditEventRepository;
import com.queryai.audit.AuditService.AuditEventDto;
import com.queryai.dashboard.DashboardRepository;
import com.queryai.datasource.DataSourceEntity;
import com.queryai.datasource.DataSourceRepository;
import com.queryai.history.HistoryService;
import com.queryai.history.HistoryService.QueryRunDto;
import com.queryai.history.QueryFeedback;
import com.queryai.history.QueryFeedbackRepository;
import com.queryai.history.QueryRunRepository;
import com.queryai.history.QueryRunStats;
import com.queryai.savedquery.SavedQueryRepository;
import com.queryai.workspace.WorkspaceAccessService;
import com.queryai.workspace.WorkspaceDtos.UsageDto;
import com.queryai.workspace.WorkspaceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Real, measured workspace statistics for the home dashboard. Nothing here is sample or demo data. */
@Service
public class OverviewService {
    private final WorkspaceAccessService access;
    private final DataSourceRepository dataSources;
    private final QueryRunRepository queryRuns;
    private final QueryFeedbackRepository feedback;
    private final SavedQueryRepository savedQueries;
    private final DashboardRepository dashboards;
    private final AuditEventRepository auditEvents;
    private final WorkspaceService workspaceService;
    private final HistoryService historyService;
    private final LlmClient llm;

    public OverviewService(WorkspaceAccessService access, DataSourceRepository dataSources, QueryRunRepository queryRuns,
                           QueryFeedbackRepository feedback, SavedQueryRepository savedQueries, DashboardRepository dashboards,
                           AuditEventRepository auditEvents, WorkspaceService workspaceService, HistoryService historyService,
                           LlmClient llm) {
        this.access = access;
        this.dataSources = dataSources;
        this.queryRuns = queryRuns;
        this.feedback = feedback;
        this.savedQueries = savedQueries;
        this.dashboards = dashboards;
        this.auditEvents = auditEvents;
        this.workspaceService = workspaceService;
        this.historyService = historyService;
        this.llm = llm;
    }

    public record DailyActivity(LocalDate day, long total, long succeeded, long failed) {
    }

    public record DataSourceSummary(Long id, String name, String dbType, String status) {
    }

    public record Overview(Long workspaceId, Instant measuredAt, int windowDays, long dataSourceCount, long connectedDataSources,
                           List<DataSourceSummary> dataSources, long queriesInWindow, long executedInWindow,
                           Double successRate, Double avgExecutionMs, long savedQueryCount, long dashboardCount,
                           long positiveFeedback, long negativeFeedback, UsageDto usage, List<DailyActivity> activity,
                           List<QueryRunDto> recentQueries, List<AuditEventDto> recentActivity, boolean llmConfigured,
                           String llmDescription) {
    }

    @Transactional(readOnly = true)
    public Overview overview(Long workspaceId) {
        access.requireMember(workspaceId);
        int window = 30;
        Instant since = Instant.now().minus(window, ChronoUnit.DAYS);
        List<DataSourceEntity> sources = dataSources.findByWorkspaceIdOrderByName(workspaceId);
        QueryRunStats stats = queryRuns.executionStats(workspaceId, since);
        long executed = stats == null || stats.executed() == null ? 0 : stats.executed();
        long succeeded = stats == null || stats.succeeded() == null ? 0 : stats.succeeded();
        List<DailyActivity> activity = queryRuns.dailyActivity(workspaceId, since).stream().map(r -> new DailyActivity(
                toLocalDate(r[0]), ((Number) r[1]).longValue(), ((Number) r[2]).longValue(), ((Number) r[3]).longValue())).toList();
        return new Overview(workspaceId, Instant.now(), window, sources.size(),
                sources.stream().filter(s -> s.getStatus() == DataSourceEntity.Status.CONNECTED).count(),
                sources.stream().map(s -> new DataSourceSummary(s.getId(), s.getName(), s.getDbType().name(), s.getStatus().name())).toList(),
                queryRuns.countByWorkspaceIdAndCreatedAtGreaterThanEqual(workspaceId, since), executed,
                executed == 0 ? null : (double) succeeded / executed,
                stats == null ? null : stats.avgDurationMs(),
                savedQueries.countByWorkspaceId(workspaceId), dashboards.countByWorkspaceId(workspaceId),
                feedback.countByWorkspaceAndRating(workspaceId, QueryFeedback.Rating.UP),
                feedback.countByWorkspaceAndRating(workspaceId, QueryFeedback.Rating.DOWN),
                workspaceService.usage(workspaceId), activity, historyService.recent(workspaceId),
                auditEvents.findTop10ByWorkspaceIdOrderByCreatedAtDesc(workspaceId).stream().map(AuditEventDto::from).toList(),
                llm.isConfigured(), llm.description());
    }

    private static LocalDate toLocalDate(Object o) {
        if (o instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        if (o instanceof LocalDate ld) {
            return ld;
        }
        return LocalDate.parse(String.valueOf(o).substring(0, 10));
    }
}
