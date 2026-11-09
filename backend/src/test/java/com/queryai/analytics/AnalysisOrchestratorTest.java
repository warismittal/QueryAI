package com.queryai.analytics;

import com.queryai.ai.*;
import com.queryai.ai.AiDtos.IntentAnalysis;
import com.queryai.ai.AiDtos.IntentType;
import com.queryai.ai.AiDtos.SqlGenerationOutput;
import com.queryai.ai.ChartRecommender.ChartType;
import com.queryai.ai.ChartRecommender.Recommendation;
import com.queryai.ai.LlmClient.LlmResponse;
import com.queryai.ai.ResultInterpretationService.Interpretation;
import com.queryai.ai.SchemaRetrievalService.RetrievedSchema;
import com.queryai.analytics.AnalyticsDtos.AnalysisStatus;
import com.queryai.analytics.AnalyticsDtos.AnalyzeRequest;
import com.queryai.audit.AuditService;
import com.queryai.common.ApiException;
import com.queryai.config.QueryAiProperties;
import com.queryai.conversations.Conversation;
import com.queryai.conversations.ConversationService;
import com.queryai.datasource.DataSourceAccess;
import com.queryai.datasource.DataSourceEntity;
import com.queryai.datasource.DatabaseType;
import com.queryai.history.ExecutionStatus;
import com.queryai.history.QueryRun;
import com.queryai.history.QueryRunRepository;
import com.queryai.history.QuerySource;
import com.queryai.history.ValidationStatus;
import com.queryai.schema.SchemaModel.SchemaSnapshot;
import com.queryai.schema.SchemaModel.TableMeta;
import com.queryai.schema.SchemaService;
import com.queryai.schema.SchemaSnapshotEntity;
import com.queryai.sql.QueryResult;
import com.queryai.sql.SqlDtos.ExecuteResponse;
import com.queryai.sql.SqlDtos.SqlValidationResult;
import com.queryai.sql.SqlSafetyValidator.Issue;
import com.queryai.sql.SqlSafetyValidator.Severity;
import com.queryai.sql.SqlService;
import com.queryai.sql.SqlValidationService;
import com.queryai.workspace.WorkspaceRole;
import com.queryai.workspace.WorkspaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AnalysisOrchestratorTest {
    private final DataSourceAccess access = mock(DataSourceAccess.class);
    private final WorkspaceService workspaceService = mock(WorkspaceService.class);
    private final LlmClient llm = mock(LlmClient.class);
    private final IntentAnalysisService intent = mock(IntentAnalysisService.class);
    private final SchemaRetrievalService schemaRetrieval = mock(SchemaRetrievalService.class);
    private final GlossaryRetrievalService glossary = mock(GlossaryRetrievalService.class);
    private final SqlGenerationService generation = mock(SqlGenerationService.class);
    private final SqlValidationService validation = mock(SqlValidationService.class);
    private final SqlService sqlService = mock(SqlService.class);
    private final ResultInterpretationService interpretation = mock(ResultInterpretationService.class);
    private final ChartRecommendationService charts = mock(ChartRecommendationService.class);
    private final ConversationService conversations = mock(ConversationService.class);
    private final SchemaService schemaService = mock(SchemaService.class);
    private final QueryRunRepository queryRuns = mock(QueryRunRepository.class);
    private final AuditService audit = mock(AuditService.class);

    private AnalysisOrchestrator orchestrator;
    private DataSourceEntity ds;

    @BeforeEach
    void setUp() {
        QueryAiProperties props = new QueryAiProperties(null, null, new QueryAiProperties.Ai("openai", true, 12, 6, 1), null, null, null, null);
        orchestrator = new AnalysisOrchestrator(access, workspaceService, llm, intent, schemaRetrieval, glossary, generation,
                validation, sqlService, interpretation, charts, conversations, schemaService, queryRuns, audit, props);

        ds = new DataSourceEntity();
        ds.setId(1L);
        ds.setWorkspaceId(7L);
        ds.setDbType(DatabaseType.POSTGRESQL);
        when(access.requireQueryable(1L, WorkspaceRole.ANALYST)).thenReturn(ds);
        when(llm.isConfigured()).thenReturn(true);

        Conversation conversation = new Conversation();
        conversation.setId(3L);
        when(conversations.resolve(any(), eq(ds), anyString())).thenReturn(conversation);
        when(conversations.transcript(3L)).thenReturn("(new conversation)");
        when(sqlService.newRun(ds, QuerySource.AI)).thenAnswer(inv -> {
            QueryRun run = new QueryRun();
            run.setId(42L);
            run.setSource(QuerySource.AI);
            return run;
        });

        SchemaSnapshotEntity snapshot = new SchemaSnapshotEntity();
        snapshot.setSnapshot(new SchemaSnapshot("POSTGRESQL", "public", Instant.now(),
                List.of(new TableMeta("orders", "TABLE", null, 10L, List.of(), List.of("id"), List.of(), List.of()))));
        when(schemaService.ensureSnapshot(ds)).thenReturn(snapshot);
        when(intent.analyse(anyString(), anyList(), anyString())).thenReturn(new LlmResponse<>(new IntentAnalysis(
                IntentType.AGGREGATION, "Count orders", List.of("count"), List.of(), List.of(), null, List.of("order"), false, null), "m", 1));
        when(schemaRetrieval.retrieve(eq(ds), anyString(), anyList())).thenReturn(new RetrievedSchema(List.of("orders"), "TABLE orders", 1));
        when(glossary.relevantTerms(eq(1L), anyString(), anyList())).thenReturn(List.of());
    }

    private static SqlValidationResult valid(String sql) {
        return new SqlValidationResult(ValidationStatus.VALID, List.of(), Set.of("orders"), List.of(), true, sql, true, 10.0, 1L);
    }

    @Test
    void answersQuestionWithRealExecutionResult() {
        String sql = "SELECT COUNT(*) AS orders FROM orders";
        when(generation.generate(any())).thenReturn(new LlmResponse<>(
                new SqlGenerationOutput(sql, "Counts orders", List.of(), false, null, 0.95), "m", 1));
        when(validation.validate(ds, sql, null)).thenReturn(valid(sql));
        QueryResult result = new QueryResult(List.of(new QueryResult.ResultColumn("orders", "int8", QueryResult.Category.NUMBER)),
                List.of(List.of(7090L)), 1, false, 12, 5000);
        when(sqlService.executeWithValidation(eq(ds), any(), any(), any(), any(), eq(sql)))
                .thenReturn(new ExecuteResponse(42L, ExecutionStatus.SUCCESS, valid(sql), result, null, null));
        when(interpretation.interpret(anyString(), anyString(), eq(result)))
                .thenReturn(new Interpretation("There are 7090 orders.", List.of(), List.of(), "ai"));
        when(charts.recommend(result)).thenReturn(new Recommendation(ChartType.METRIC, null, List.of("orders"), "single", List.of()));

        var response = orchestrator.analyze(new AnalyzeRequest(1L, "How many orders?", null, true, null));

        assertThat(response.status()).isEqualTo(AnalysisStatus.ANSWERED);
        assertThat(response.result().rows()).containsExactly(List.of(7090L));
        assertThat(response.interpretation().summary()).contains("7090");
        assertThat(response.stages()).extracting(AnalyticsDtos.Stage::name)
                .contains("Intent analysis", "Schema retrieval", "Business glossary", "SQL generation", "Validation", "Execution", "Interpretation");
        verify(workspaceService).ensureAiQuota(7L);
        verify(conversations, times(2)).append(eq(3L), any(), anyString(), any(), any(), any());
    }

    @Test
    void asksForClarificationWithoutExecuting() {
        when(generation.generate(any())).thenReturn(new LlmResponse<>(
                new SqlGenerationOutput("", "Ambiguous", List.of(), true, "Which year do you mean?", 0.2), "m", 1));

        var response = orchestrator.analyze(new AnalyzeRequest(1L, "Show sales", null, true, null));

        assertThat(response.status()).isEqualTo(AnalysisStatus.CLARIFICATION_NEEDED);
        assertThat(response.clarificationQuestion()).isEqualTo("Which year do you mean?");
        verify(sqlService, never()).executeWithValidation(any(), any(), any(), any(), any(), any());
        verify(queryRuns).save(argThat(r -> r.getExecutionStatus() == ExecutionStatus.CLARIFICATION_NEEDED));
    }

    @Test
    void repairsInvalidSqlOnceUsingValidationErrors() {
        String bad = "SELECT * FROM nope";
        String good = "SELECT COUNT(*) FROM orders";
        when(generation.generate(any())).thenReturn(new LlmResponse<>(new SqlGenerationOutput(bad, "x", List.of(), false, null, 0.5), "m", 1));
        when(generation.repair(any(), eq(bad), anyString())).thenReturn(new LlmResponse<>(new SqlGenerationOutput(good, "y", List.of(), false, null, 0.8), "m", 1));
        when(validation.validate(ds, bad, null)).thenReturn(new SqlValidationResult(ValidationStatus.INVALID,
                List.of(new Issue(Severity.ERROR, "UNKNOWN_TABLE", "Table 'nope' does not exist")), Set.of("nope"), List.of(), false, bad, false, null, null));
        when(validation.validate(ds, good, null)).thenReturn(valid(good));

        var response = orchestrator.analyze(new AnalyzeRequest(1L, "How many orders?", null, false, null));

        assertThat(response.status()).isEqualTo(AnalysisStatus.GENERATED);
        assertThat(response.repairAttempts()).isEqualTo(1);
        assertThat(response.sql()).isEqualTo(good);
        verify(generation).repair(any(), eq(bad), contains("does not exist"));
    }

    @Test
    void refusesWhenNoModelIsConfigured() {
        when(llm.isConfigured()).thenReturn(false);
        assertThatThrownBy(() -> orchestrator.analyze(new AnalyzeRequest(1L, "q", null, true, null)))
                .isInstanceOf(ApiException.class).hasMessageContaining("No AI model");
        verifyNoInteractions(generation);
    }
}
