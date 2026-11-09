package com.queryai.history;

import com.queryai.common.ApiException;
import com.queryai.common.PageResponse;
import com.queryai.datasource.DataSourceAccess;
import com.queryai.datasource.DataSourceEntity;
import com.queryai.datasource.DataSourceRepository;
import com.queryai.security.CurrentUser;
import com.queryai.sql.SqlDtos.ExecuteResponse;
import com.queryai.sql.SqlService;
import com.queryai.workspace.WorkspaceAccessService;
import com.queryai.workspace.WorkspaceRole;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class HistoryService {
    private final QueryRunRepository runs;
    private final QueryFeedbackRepository feedback;
    private final WorkspaceAccessService workspaceAccess;
    private final DataSourceAccess dataSourceAccess;
    private final DataSourceRepository dataSources;
    private final CurrentUser currentUser;
    private final SqlService sqlService;

    public HistoryService(QueryRunRepository runs, QueryFeedbackRepository feedback, WorkspaceAccessService workspaceAccess,
                          DataSourceAccess dataSourceAccess, DataSourceRepository dataSources, CurrentUser currentUser,
                          SqlService sqlService) {
        this.runs = runs;
        this.feedback = feedback;
        this.workspaceAccess = workspaceAccess;
        this.dataSourceAccess = dataSourceAccess;
        this.dataSources = dataSources;
        this.currentUser = currentUser;
        this.sqlService = sqlService;
    }

    public record QueryRunDto(Long id, Long workspaceId, Long dataSourceId, String dataSourceName, Long userId,
                              Long conversationId, QuerySource source, String question, String generatedSql, String finalSql,
                              ValidationStatus validationStatus, ExecutionStatus executionStatus, Integer rowCount,
                              boolean truncated, Long durationMs, Long generationMs, String errorMessage, String explanation,
                              String llmModel, QueryFeedback.Rating myFeedback, Instant createdAt) {
    }

    public record FeedbackRequest(@NotNull QueryFeedback.Rating rating, @Size(max = 2000) String comment) {
    }

    @Transactional(readOnly = true)
    public PageResponse<QueryRunDto> search(Long workspaceId, boolean onlyMine, Long dataSourceId, ExecutionStatus status,
                                            QuerySource source, String q, int page, int size) {
        WorkspaceRole role = workspaceAccess.require(workspaceId, WorkspaceRole.VIEWER);
        // Analysts and viewers see only their own history; admins can audit everyone's.
        Long userFilter = onlyMine || !role.atLeast(WorkspaceRole.ADMIN) ? currentUser.id() : null;
        var result = runs.search(workspaceId, userFilter, dataSourceId, status, source,
                q == null || q.isBlank() ? null : q.trim(), PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)));
        Function<QueryRun, QueryRunDto> mapper = mapper(result.getContent());
        return PageResponse.of(result, mapper);
    }

    @Transactional(readOnly = true)
    public List<QueryRunDto> recent(Long workspaceId) {
        List<QueryRun> list = runs.findTop8ByWorkspaceIdAndUserIdOrderByCreatedAtDesc(workspaceId, currentUser.id());
        return list.stream().map(mapper(list)).toList();
    }

    @Transactional(readOnly = true)
    public QueryRunDto get(Long id) {
        QueryRun run = visible(id);
        return mapper(List.of(run)).apply(run);
    }

    @Transactional
    public QueryRunDto giveFeedback(Long id, FeedbackRequest request) {
        QueryRun run = visible(id);
        QueryFeedback fb = feedback.findByQueryRunIdAndUserId(id, currentUser.id()).orElseGet(QueryFeedback::new);
        fb.setQueryRunId(id);
        fb.setUserId(currentUser.id());
        fb.setRating(request.rating());
        fb.setComment(request.comment());
        feedback.save(fb);
        return mapper(List.of(run)).apply(run);
    }

    /** Re-executes the final SQL of a previous run (validation runs again against the current schema). */
    public ExecuteResponse rerun(Long id) {
        QueryRun previous = visible(id);
        String sql = previous.getFinalSql() != null ? previous.getFinalSql() : previous.getGeneratedSql();
        if (sql == null || sql.isBlank() || previous.getDataSourceId() == null) {
            throw ApiException.badRequest("This history entry has no SQL that can be re-run");
        }
        DataSourceEntity ds = dataSourceAccess.requireQueryable(previous.getDataSourceId(), WorkspaceRole.ANALYST);
        QueryRun run = sqlService.newRun(ds, QuerySource.RERUN);
        run.setQuestion(previous.getQuestion());
        return sqlService.runValidated(ds, sql, Map.of(), run, null);
    }

    private QueryRun visible(Long id) {
        QueryRun run = runs.findById(id).orElseThrow(() -> ApiException.notFound("Query run"));
        WorkspaceRole role = workspaceAccess.require(run.getWorkspaceId(), WorkspaceRole.VIEWER);
        if (!role.atLeast(WorkspaceRole.ADMIN) && !Objects.equals(run.getUserId(), currentUser.id())) {
            throw ApiException.notFound("Query run");
        }
        return run;
    }

    private Function<QueryRun, QueryRunDto> mapper(List<QueryRun> batch) {
        Long me = currentUser.idIfPresent().orElse(null);
        Map<Long, QueryFeedback.Rating> ratings = me == null || batch.isEmpty() ? Map.of()
                : feedback.findByQueryRunIdInAndUserId(batch.stream().map(QueryRun::getId).toList(), me).stream()
                .collect(Collectors.toMap(QueryFeedback::getQueryRunId, QueryFeedback::getRating, (a, b) -> a));
        Set<Long> dsIds = batch.stream().map(QueryRun::getDataSourceId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> names = new HashMap<>();
        dataSources.findAllById(dsIds).forEach(d -> names.put(d.getId(), d.getName()));
        return r -> new QueryRunDto(r.getId(), r.getWorkspaceId(), r.getDataSourceId(), names.get(r.getDataSourceId()),
                r.getUserId(), r.getConversationId(), r.getSource(), r.getQuestion(), r.getGeneratedSql(), r.getFinalSql(),
                r.getValidationStatus(), r.getExecutionStatus(), r.getRowCount(), r.isTruncated(), r.getDurationMs(),
                r.getGenerationMs(), r.getErrorMessage(), r.getExplanation(), r.getLlmModel(), ratings.get(r.getId()),
                r.getCreatedAt());
    }
}
