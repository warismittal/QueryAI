package com.queryai.evaluation;

import com.queryai.ai.GlossaryRetrievalService;
import com.queryai.ai.LlmClient;
import com.queryai.ai.LlmClient.LlmResponse;
import com.queryai.ai.AiDtos.SqlGenerationOutput;
import com.queryai.ai.SchemaRetrievalService;
import com.queryai.ai.SchemaRetrievalService.RetrievedSchema;
import com.queryai.ai.SqlGenerationService;
import com.queryai.ai.SqlGenerationService.GenerationContext;
import com.queryai.audit.AuditAction;
import com.queryai.audit.AuditService;
import com.queryai.common.ApiException;
import com.queryai.datasource.DataSourceAccess;
import com.queryai.datasource.DataSourceEntity;
import com.queryai.evaluation.EvaluationRun.CaseResult;
import com.queryai.security.CurrentUser;
import com.queryai.sql.QueryResult;
import com.queryai.sql.SqlDtos.SqlValidationResult;
import com.queryai.sql.SqlExecutionService;
import com.queryai.sql.SqlValidationService;
import com.queryai.workspace.WorkspaceRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Query evaluation: replays benchmark questions through SQL generation and compares the generated query's real
 * results with those of a reference query (execution accuracy). Used to measure prompt/model changes objectively.
 */
@Service
public class EvaluationService {
    private static final int MAX_CASES_PER_RUN = 25;

    private final EvaluationCaseRepository cases;
    private final EvaluationRunRepository runs;
    private final DataSourceAccess access;
    private final SchemaRetrievalService schemaRetrieval;
    private final GlossaryRetrievalService glossaryRetrieval;
    private final SqlGenerationService generation;
    private final SqlValidationService validation;
    private final SqlExecutionService executor;
    private final LlmClient llm;
    private final CurrentUser currentUser;
    private final AuditService audit;

    public EvaluationService(EvaluationCaseRepository cases, EvaluationRunRepository runs, DataSourceAccess access,
                             SchemaRetrievalService schemaRetrieval, GlossaryRetrievalService glossaryRetrieval,
                             SqlGenerationService generation, SqlValidationService validation, SqlExecutionService executor,
                             LlmClient llm, CurrentUser currentUser, AuditService audit) {
        this.cases = cases;
        this.runs = runs;
        this.access = access;
        this.schemaRetrieval = schemaRetrieval;
        this.glossaryRetrieval = glossaryRetrieval;
        this.generation = generation;
        this.validation = validation;
        this.executor = executor;
        this.llm = llm;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    public record CaseDto(Long id, Long dataSourceId, String question, String expectedSql, Instant createdAt) {
        static CaseDto from(EvaluationCase c) {
            return new CaseDto(c.getId(), c.getDataSourceId(), c.getQuestion(), c.getExpectedSql(), c.getCreatedAt());
        }
    }

    public record CaseRequest(@NotBlank @Size(max = 4000) String question, @NotBlank @Size(max = 100_000) String expectedSql) {
    }

    public record RunDto(Long id, Long dataSourceId, int totalCases, int passedCases, double accuracy, String llmModel,
                         List<CaseResult> results, Instant createdAt) {
        static RunDto from(EvaluationRun r) {
            return new RunDto(r.getId(), r.getDataSourceId(), r.getTotalCases(), r.getPassedCases(),
                    r.getTotalCases() == 0 ? 0 : (double) r.getPassedCases() / r.getTotalCases(), r.getLlmModel(),
                    r.getResults(), r.getCreatedAt());
        }
    }

    @Transactional(readOnly = true)
    public List<CaseDto> listCases(Long dataSourceId) {
        access.requireVisible(dataSourceId);
        return cases.findByDataSourceIdOrderByIdAsc(dataSourceId).stream().map(CaseDto::from).toList();
    }

    @Transactional
    public CaseDto createCase(Long dataSourceId, CaseRequest request) {
        DataSourceEntity ds = access.requireQueryable(dataSourceId, WorkspaceRole.ANALYST);
        if (!validation.validateStatic(ds, request.expectedSql()).valid()) {
            throw ApiException.badRequest("The reference SQL does not pass QueryAI's safety validation");
        }
        EvaluationCase c = new EvaluationCase();
        c.setDataSourceId(dataSourceId);
        c.setQuestion(request.question().trim());
        c.setExpectedSql(request.expectedSql().trim());
        c.setCreatedBy(currentUser.id());
        return CaseDto.from(cases.save(c));
    }

    @Transactional
    public void deleteCase(Long dataSourceId, Long caseId) {
        access.requireQueryable(dataSourceId, WorkspaceRole.ANALYST);
        EvaluationCase c = cases.findById(caseId).filter(x -> x.getDataSourceId().equals(dataSourceId))
                .orElseThrow(() -> ApiException.notFound("Evaluation case"));
        cases.delete(c);
    }

    @Transactional(readOnly = true)
    public List<RunDto> listRuns(Long dataSourceId) {
        access.requireVisible(dataSourceId);
        return runs.findTop10ByDataSourceIdOrderByCreatedAtDesc(dataSourceId).stream().map(RunDto::from).toList();
    }

    public RunDto run(Long dataSourceId) {
        DataSourceEntity ds = access.requireQueryable(dataSourceId, WorkspaceRole.ANALYST);
        if (!llm.isConfigured()) {
            throw ApiException.unavailable("LLM_NOT_CONFIGURED", "Configure an AI model to run evaluations");
        }
        List<EvaluationCase> all = cases.findByDataSourceIdOrderByIdAsc(dataSourceId);
        if (all.isEmpty()) {
            throw ApiException.badRequest("Add at least one evaluation case first");
        }
        List<CaseResult> results = new ArrayList<>();
        String model = llm.description();
        int timeout = Math.min(ds.getQueryTimeoutSec(), 60);
        for (EvaluationCase c : all.stream().limit(MAX_CASES_PER_RUN).toList()) {
            long started = System.currentTimeMillis();
            String generatedSql = null;
            try {
                RetrievedSchema schema = schemaRetrieval.retrieve(ds, c.getQuestion(), List.of());
                GenerationContext ctx = new GenerationContext(ds.getDbType(), schema.promptText(),
                        GlossaryRetrievalService.render(glossaryRetrieval.relevantTerms(ds.getId(), c.getQuestion(), List.of())),
                        "(new conversation)", "(not analysed)", c.getQuestion());
                LlmResponse<SqlGenerationOutput> out = generation.generate(ctx);
                model = out.model();
                generatedSql = out.value().sql();
                long genMs = System.currentTimeMillis() - started;
                if (out.value().needsClarification() || generatedSql == null || generatedSql.isBlank()) {
                    results.add(new CaseResult(c.getId(), c.getQuestion(), false, null, "Model asked for clarification", null, null, genMs));
                    continue;
                }
                SqlValidationResult v = validation.validate(ds, generatedSql, Map.of());
                if (v.blocked()) {
                    results.add(new CaseResult(c.getId(), c.getQuestion(), false, generatedSql, "Validation failed: " + v.errorSummary(), null, null, genMs));
                    continue;
                }
                QueryResult expected = executor.execute(ds, c.getExpectedSql(), Map.of(), 10_000, timeout);
                QueryResult actual = executor.execute(ds, v.normalizedSql(), Map.of(), 10_000, timeout);
                ResultComparator.Comparison cmp = ResultComparator.compare(expected.rows(), actual.rows());
                results.add(new CaseResult(c.getId(), c.getQuestion(), cmp.match(), generatedSql, cmp.reason(),
                        expected.rowCount(), actual.rowCount(), genMs));
            } catch (ApiException e) {
                results.add(new CaseResult(c.getId(), c.getQuestion(), false, generatedSql, e.getMessage(), null, null,
                        System.currentTimeMillis() - started));
            }
        }
        EvaluationRun run = new EvaluationRun();
        run.setDataSourceId(dataSourceId);
        run.setTriggeredBy(currentUser.id());
        run.setTotalCases(results.size());
        run.setPassedCases((int) results.stream().filter(CaseResult::passed).count());
        run.setResults(results);
        run.setLlmModel(model);
        runs.save(run);
        audit.record(AuditAction.EVALUATION_RUN, ds.getWorkspaceId(), "DATA_SOURCE", dataSourceId,
                Map.of("total", run.getTotalCases(), "passed", run.getPassedCases()));
        return RunDto.from(run);
    }
}
