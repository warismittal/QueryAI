package com.queryai.sql;

import com.queryai.ai.ChartRecommendationService;
import com.queryai.audit.AuditAction;
import com.queryai.audit.AuditService;
import com.queryai.common.ApiException;
import com.queryai.common.RequestUtils;
import com.queryai.datasource.DataSourceAccess;
import com.queryai.datasource.DataSourceEntity;
import com.queryai.history.ExecutionStatus;
import com.queryai.history.QueryRun;
import com.queryai.history.QueryRunRepository;
import com.queryai.history.QuerySource;
import com.queryai.pii.PiiService;
import com.queryai.security.CurrentUser;
import com.queryai.sql.SqlDtos.*;
import com.queryai.workspace.WorkspaceRole;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Facade for validating and executing SQL. Every execution path in QueryAI (manual SQL, AI answers, saved queries,
 * dashboards, reruns) goes through {@link #runValidated}, so validation can never be skipped and every attempt is
 * recorded in query history.
 */
@Service
public class SqlService {
    private final DataSourceAccess access;
    private final SqlValidationService validationService;
    private final SqlExecutionService executionService;
    private final QueryRunRepository queryRuns;
    private final CurrentUser currentUser;
    private final AuditService audit;
    private final ChartRecommendationService charts;
    private final PiiService pii;

    public SqlService(DataSourceAccess access, SqlValidationService validationService, SqlExecutionService executionService,
                      QueryRunRepository queryRuns, CurrentUser currentUser, AuditService audit,
                      ChartRecommendationService charts, PiiService pii) {
        this.charts = charts;
        this.pii = pii;
        this.access = access;
        this.validationService = validationService;
        this.executionService = executionService;
        this.queryRuns = queryRuns;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    public SqlValidationResult validate(ValidateRequest request) {
        DataSourceEntity ds = access.requireQueryable(request.dataSourceId(), WorkspaceRole.ANALYST);
        return validationService.validate(ds, request.sql(), request.parameters());
    }

    public ExecuteResponse execute(ExecuteRequest request, QuerySource source) {
        DataSourceEntity ds = access.requireQueryable(request.dataSourceId(), WorkspaceRole.ANALYST);
        QueryRun run = newRun(ds, source);
        run.setQuestion(request.question());
        run.setConversationId(request.conversationId());
        return runValidated(ds, request.sql(), request.parameters(), run, request.maxRows());
    }

    public QueryRun newRun(DataSourceEntity ds, QuerySource source) {
        QueryRun run = new QueryRun();
        run.setWorkspaceId(ds.getWorkspaceId());
        run.setDataSourceId(ds.getId());
        run.setUserId(currentUser.id());
        run.setSource(source);
        return run;
    }

    /**
     * Validates, executes (only when validation passed) and persists the run. Database errors are captured in the
     * response and in history rather than thrown, so callers can show the real failure to the user.
     */
    public ExecuteResponse runValidated(DataSourceEntity ds, String sql, Map<String, Object> params, QueryRun run, Integer maxRows) {
        return executeWithValidation(ds, params, run, maxRows, validationService.validate(ds, sql, params), sql);
    }

    /** Executes using a validation result computed moments earlier for exactly this SQL (avoids a second EXPLAIN). */
    public ExecuteResponse executeWithValidation(DataSourceEntity ds, Map<String, Object> params, QueryRun run, Integer maxRows,
                                                 SqlValidationResult validation, String sql) {
        run.setFinalSql(validation.normalizedSql().isEmpty() ? sql : validation.normalizedSql());
        run.setValidationStatus(validation.status());
        if (validation.blocked()) {
            run.setExecutionStatus(ExecutionStatus.BLOCKED);
            run.setErrorMessage(RequestUtils.truncate(validation.errorSummary(), 4000));
            queryRuns.save(run);
            audit.recordFailure(AuditAction.QUERY_BLOCKED, ds.getWorkspaceId(), "QUERY_RUN", run.getId(),
                    Map.of("dataSourceId", ds.getId(), "reason", String.valueOf(validation.errorSummary())));
            return new ExecuteResponse(run.getId(), ExecutionStatus.BLOCKED, validation, null, validation.errorSummary(), null);
        }
        int cap = Math.min(maxRows == null ? ds.getMaxRows() : maxRows, ds.getMaxRows());
        try {
            QueryResult raw = executionService.execute(ds, validation.normalizedSql(), params, cap, ds.getQueryTimeoutSec());
            // personal data is masked here, so every consumer (UI, AI explanation, dashboards, exports) gets the safe copy
            QueryResult result = pii.protect(ds, raw, validation.referencedTables(), validation.normalizedSql());
            run.setExecutionStatus(ExecutionStatus.SUCCESS);
            run.setRowCount(result.rowCount());
            run.setTruncated(result.truncated());
            run.setDurationMs(result.durationMs());
            queryRuns.save(run);
            Map<String, Object> details = new HashMap<>();
            details.put("dataSourceId", ds.getId());
            details.put("rows", result.rowCount());
            details.put("source", run.getSource().name());
            if (!result.maskedColumns().isEmpty()) {
                details.put("maskedColumns", result.maskedColumns());
            }
            audit.record(AuditAction.QUERY_EXECUTED, ds.getWorkspaceId(), "QUERY_RUN", run.getId(), details);
            return new ExecuteResponse(run.getId(), ExecutionStatus.SUCCESS, validation, result, null, charts.recommend(result));
        } catch (ApiException e) {
            run.setExecutionStatus(ExecutionStatus.FAILED);
            run.setErrorMessage(RequestUtils.truncate(e.getMessage(), 4000));
            queryRuns.save(run);
            audit.recordFailure(AuditAction.QUERY_FAILED, ds.getWorkspaceId(), "QUERY_RUN", run.getId(),
                    Map.of("dataSourceId", ds.getId(), "error", RequestUtils.truncate(e.getMessage(), 500)));
            return new ExecuteResponse(run.getId(), ExecutionStatus.FAILED, validation, null, e.getMessage(), null);
        }
    }
}
