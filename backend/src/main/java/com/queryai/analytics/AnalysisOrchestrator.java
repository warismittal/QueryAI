package com.queryai.analytics;

import com.queryai.ai.*;
import com.queryai.ai.AiDtos.IntentAnalysis;
import com.queryai.ai.AiDtos.SqlGenerationOutput;
import com.queryai.ai.ChartRecommender.Recommendation;
import com.queryai.ai.LlmClient.LlmResponse;
import com.queryai.ai.ResultInterpretationService.Interpretation;
import com.queryai.ai.SchemaRetrievalService.RetrievedSchema;
import com.queryai.ai.SqlGenerationService.GenerationContext;
import com.queryai.analytics.AnalyticsDtos.*;
import com.queryai.audit.AuditAction;
import com.queryai.audit.AuditService;
import com.queryai.common.ApiException;
import com.queryai.common.RequestUtils;
import com.queryai.config.QueryAiProperties;
import com.queryai.conversations.Conversation;
import com.queryai.conversations.ConversationMessage.Role;
import com.queryai.conversations.ConversationService;
import com.queryai.datasource.DataSourceAccess;
import com.queryai.datasource.DataSourceEntity;
import com.queryai.history.ExecutionStatus;
import com.queryai.history.QueryRun;
import com.queryai.history.QueryRunRepository;
import com.queryai.history.QuerySource;
import com.queryai.schema.GlossaryTerm;
import com.queryai.schema.SchemaService;
import com.queryai.sql.SqlDtos.ExecuteResponse;
import com.queryai.sql.SqlDtos.SqlValidationResult;
import com.queryai.sql.SqlService;
import com.queryai.sql.SqlValidationService;
import com.queryai.workspace.WorkspaceRole;
import com.queryai.workspace.WorkspaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * The natural-language analysis pipeline:
 * intent → schema retrieval → glossary retrieval → SQL generation → validation (with automatic repair) →
 * read-only execution → interpretation → chart recommendation, with every step timed and the outcome persisted to
 * query history and the conversation.
 * <p>
 * Deliberately not {@code @Transactional}: each persistence step commits on its own so a failure late in the pipeline
 * still leaves an accurate history record.
 */
@Service
public class AnalysisOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(AnalysisOrchestrator.class);

    private final DataSourceAccess access;
    private final WorkspaceService workspaceService;
    private final LlmClient llm;
    private final IntentAnalysisService intentService;
    private final SchemaRetrievalService schemaRetrieval;
    private final GlossaryRetrievalService glossaryRetrieval;
    private final SqlGenerationService generation;
    private final SqlValidationService validation;
    private final SqlService sqlService;
    private final ResultInterpretationService interpretation;
    private final ChartRecommendationService charts;
    private final ConversationService conversations;
    private final SchemaService schemaService;
    private final QueryRunRepository queryRuns;
    private final AuditService audit;
    private final int repairAttempts;

    public AnalysisOrchestrator(DataSourceAccess access, WorkspaceService workspaceService, LlmClient llm,
                                IntentAnalysisService intentService, SchemaRetrievalService schemaRetrieval,
                                GlossaryRetrievalService glossaryRetrieval, SqlGenerationService generation,
                                SqlValidationService validation, SqlService sqlService,
                                ResultInterpretationService interpretation, ChartRecommendationService charts,
                                ConversationService conversations, SchemaService schemaService,
                                QueryRunRepository queryRuns, AuditService audit, QueryAiProperties properties) {
        this.access = access;
        this.workspaceService = workspaceService;
        this.llm = llm;
        this.intentService = intentService;
        this.schemaRetrieval = schemaRetrieval;
        this.glossaryRetrieval = glossaryRetrieval;
        this.generation = generation;
        this.validation = validation;
        this.sqlService = sqlService;
        this.interpretation = interpretation;
        this.charts = charts;
        this.conversations = conversations;
        this.schemaService = schemaService;
        this.queryRuns = queryRuns;
        this.audit = audit;
        this.repairAttempts = Math.max(0, properties.ai().repairAttempts());
    }

    public AnalyzeResponse analyze(AnalyzeRequest request) {
        DataSourceEntity ds = access.requireQueryable(request.dataSourceId(), WorkspaceRole.ANALYST);
        workspaceService.ensureAiQuota(ds.getWorkspaceId());
        if (!llm.isConfigured()) {
            throw ApiException.unavailable("LLM_NOT_CONFIGURED", "No AI model is configured on the server. "
                    + "Set an LLM API key to enable natural-language questions; you can still write SQL in the editor.");
        }
        String question = request.question().trim();
        Conversation conversation = conversations.resolve(request.conversationId(), ds, question);
        String transcript = conversations.transcript(conversation.getId());
        conversations.append(conversation.getId(), Role.USER, question, null, null, null);

        QueryRun run = sqlService.newRun(ds, QuerySource.AI);
        run.setQuestion(question);
        run.setConversationId(conversation.getId());

        List<Stage> stages = new ArrayList<>();
        Pipeline p = new Pipeline();
        long generationStart = System.currentTimeMillis();
        try {
            // 1. intent
            long t = System.currentTimeMillis();
            List<String> tableNames = schemaService.ensureSnapshot(ds).getSnapshot().tables().stream()
                    .map(tm -> tm.name()).toList();
            LlmResponse<IntentAnalysis> intent = intentService.analyse(question, tableNames, transcript);
            p.intent = intent.value();
            p.model = intent.model();
            stages.add(new Stage("Intent analysis", "OK", System.currentTimeMillis() - t, p.intent.summary()));

            // 2. schema retrieval
            t = System.currentTimeMillis();
            RetrievedSchema schema = schemaRetrieval.retrieve(ds, question, p.intent.keywords());
            p.tables = schema.tables();
            stages.add(new Stage("Schema retrieval", "OK", System.currentTimeMillis() - t,
                    schema.tables().size() + " of " + schema.totalTables() + " tables selected (" + schema.method() + "): "
                            + String.join(", ", schema.tables())));

            // 3. glossary
            t = System.currentTimeMillis();
            List<GlossaryTerm> terms = glossaryRetrieval.relevantTerms(ds.getId(), question, p.intent.keywords());
            stages.add(new Stage("Business glossary", "OK", System.currentTimeMillis() - t,
                    terms.isEmpty() ? "No glossary terms apply" : terms.size() + " definitions applied: "
                            + String.join(", ", terms.stream().map(GlossaryTerm::getTerm).toList())));

            // 4. generation
            t = System.currentTimeMillis();
            GenerationContext ctx = new GenerationContext(ds.getDbType(), schema.promptText(),
                    GlossaryRetrievalService.render(terms), transcript, IntentAnalysisService.describe(p.intent), question);
            LlmResponse<SqlGenerationOutput> generated = generation.generate(ctx);
            p.output = generated.value();
            p.model = generated.model();
            stages.add(new Stage("SQL generation", "OK", System.currentTimeMillis() - t,
                    p.output.needsClarification() ? "Clarification requested" : "Generated " + ds.getDbType().displayName() + " SQL"));

            if (p.output.needsClarification() || p.output.sql() == null || p.output.sql().isBlank()) {
                String clarification = Optional.ofNullable(p.output.clarificationQuestion()).filter(s -> !s.isBlank())
                        .orElse(p.output.explanation());
                run.setExecutionStatus(ExecutionStatus.CLARIFICATION_NEEDED);
                run.setExplanation(clarification);
                run.setGenerationMs(System.currentTimeMillis() - generationStart);
                run.setLlmModel(p.model);
                queryRuns.save(run);
                conversations.append(conversation.getId(), Role.ASSISTANT, clarification, null, run.getId(),
                        Map.of("status", AnalysisStatus.CLARIFICATION_NEEDED.name()));
                return response(conversation, run, AnalysisStatus.CLARIFICATION_NEEDED, question, p, null, null, null,
                        null, stages, 0, null, clarification);
            }

            // 5. validation with automatic repair
            t = System.currentTimeMillis();
            String sql = p.output.sql();
            SqlValidationResult result = validation.validate(ds, sql, request.parameters());
            int repairs = 0;
            while (result.blocked() && repairs < repairAttempts) {
                repairs++;
                stages.add(new Stage("Validation", "FAILED", System.currentTimeMillis() - t, result.errorSummary()));
                t = System.currentTimeMillis();
                LlmResponse<SqlGenerationOutput> repaired = generation.repair(ctx, sql, result.errorSummary());
                p.output = repaired.value();
                if (p.output.sql() == null || p.output.sql().isBlank()) {
                    break;
                }
                sql = p.output.sql();
                stages.add(new Stage("Self-repair #" + repairs, "OK", System.currentTimeMillis() - t, "Regenerated SQL using validation errors"));
                t = System.currentTimeMillis();
                result = validation.validate(ds, sql, request.parameters());
            }
            stages.add(new Stage("Validation", result.blocked() ? "FAILED" : "OK", System.currentTimeMillis() - t,
                    result.blocked() ? result.errorSummary() : result.status() + (result.databaseChecked() ? " (database dry run passed)" : "")));
            run.setGeneratedSql(sql);
            run.setGenerationMs(System.currentTimeMillis() - generationStart);
            run.setLlmModel(p.model);
            run.setExplanation(p.output.explanation());
            audit.record(AuditAction.SQL_GENERATED, ds.getWorkspaceId(), "DATA_SOURCE", ds.getId(),
                    Map.of("conversationId", conversation.getId(), "repairs", repairs, "valid", !result.blocked()));

            if (!request.shouldExecute() && !result.blocked()) {
                run.setFinalSql(result.normalizedSql());
                run.setValidationStatus(result.status());
                queryRuns.save(run);
                conversations.append(conversation.getId(), Role.ASSISTANT, p.output.explanation(), sql, run.getId(),
                        Map.of("status", AnalysisStatus.GENERATED.name(), "validation", result.status().name()));
                return response(conversation, run, AnalysisStatus.GENERATED, question, p, result, null, null, null, stages, repairs, null, null);
            }

            // 6. execution (always re-checked by SqlService; blocked SQL is recorded and never executed)
            t = System.currentTimeMillis();
            ExecuteResponse executed = sqlService.executeWithValidation(ds, request.parameters(), run, null, result, sql);
            if (executed.status() == ExecutionStatus.BLOCKED || executed.status() == ExecutionStatus.FAILED) {
                stages.add(new Stage("Execution", executed.status() == ExecutionStatus.BLOCKED ? "SKIPPED" : "FAILED",
                        System.currentTimeMillis() - t, executed.error()));
                AnalysisStatus status = executed.status() == ExecutionStatus.BLOCKED ? AnalysisStatus.BLOCKED : AnalysisStatus.FAILED;
                String message = (status == AnalysisStatus.BLOCKED ? "I generated SQL but it did not pass validation, so it was not executed: "
                        : "The query failed when executed: ") + executed.error();
                conversations.append(conversation.getId(), Role.ASSISTANT, message, sql, run.getId(),
                        Map.of("status", status.name(), "validation", result.status().name()));
                return response(conversation, run, status, question, p, result, null, null, null, stages, repairs, executed.error(), null);
            }
            stages.add(new Stage("Execution", "OK", executed.result().durationMs(),
                    executed.result().rowCount() + (executed.result().truncated() ? "+ rows (truncated)" : " rows")));

            // 7. interpretation + 8. chart recommendation
            t = System.currentTimeMillis();
            Interpretation explained = interpretation.interpret(question, executed.validation().normalizedSql(), executed.result());
            Recommendation chart = charts.recommend(executed.result());
            stages.add(new Stage("Interpretation", "OK", System.currentTimeMillis() - t,
                    "ai".equals(explained.generatedBy()) ? "Explained by the AI model" : "System summary (AI explanation unavailable)"));
            run.setExplanation(RequestUtils.truncate(explained.summary(), 4000));
            queryRuns.save(run);

            Map<String, Object> meta = new HashMap<>();
            meta.put("status", AnalysisStatus.ANSWERED.name());
            meta.put("rowCount", executed.result().rowCount());
            meta.put("durationMs", executed.result().durationMs());
            meta.put("chart", chart.type().name());
            meta.put("validation", result.status().name());
            conversations.append(conversation.getId(), Role.ASSISTANT, explained.summary(), sql, run.getId(), meta);
            return response(conversation, run, AnalysisStatus.ANSWERED, question, p, executed.validation(), executed.result(),
                    explained, chart, stages, repairs, null, null);
        } catch (ApiException e) {
            log.info("Analysis failed for data source {}: {} {}", ds.getId(), e.getCode(), e.getMessage());
            stages.add(new Stage("Pipeline", "FAILED", 0, e.getMessage()));
            run.setExecutionStatus(ExecutionStatus.FAILED);
            run.setErrorMessage(RequestUtils.truncate(e.getMessage(), 4000));
            run.setGenerationMs(System.currentTimeMillis() - generationStart);
            run.setLlmModel(p.model);
            if (p.output != null && run.getGeneratedSql() == null) {
                run.setGeneratedSql(p.output.sql());
            }
            queryRuns.save(run);
            conversations.append(conversation.getId(), Role.ASSISTANT, "I couldn't complete this request: " + e.getMessage(),
                    null, run.getId(), Map.of("status", AnalysisStatus.FAILED.name()));
            return response(conversation, run, AnalysisStatus.FAILED, question, p, null, null, null, null, stages, 0, e.getMessage(), null);
        }
    }

    private static final class Pipeline {
        IntentAnalysis intent;
        SqlGenerationOutput output;
        List<String> tables = List.of();
        String model;
    }

    private AnalyzeResponse response(Conversation conversation, QueryRun run, AnalysisStatus status, String question,
                                     Pipeline p, SqlValidationResult validation, com.queryai.sql.QueryResult result,
                                     Interpretation interpretation, Recommendation chart, List<Stage> stages, int repairs,
                                     String error, String clarification) {
        SqlGenerationOutput o = p.output;
        String sql = run.getGeneratedSql() != null ? run.getGeneratedSql()
                : o == null || o.sql() == null || o.sql().isBlank() ? null : o.sql();
        return new AnalyzeResponse(conversation.getId(), run.getId(), status, question, p.intent, p.tables,
                sql, o == null ? null : o.explanation(),
                o == null || o.assumptions() == null ? List.of() : o.assumptions(), o == null ? null : o.confidence(),
                clarification, validation, result, interpretation, chart, stages, p.model, repairs, error);
    }
}
