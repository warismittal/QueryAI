package com.queryai.analytics;

import com.queryai.ai.AiDtos.IntentAnalysis;
import com.queryai.ai.ChartRecommender.Recommendation;
import com.queryai.ai.ResultInterpretationService.Interpretation;
import com.queryai.sql.QueryResult;
import com.queryai.sql.SqlDtos.SqlValidationResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public final class AnalyticsDtos {
    private AnalyticsDtos() {
    }

    public enum AnalysisStatus { ANSWERED, GENERATED, CLARIFICATION_NEEDED, BLOCKED, FAILED }

    public record AnalyzeRequest(@NotNull Long dataSourceId, @NotBlank @Size(max = 4000) String question,
                                 Long conversationId, Boolean execute, Map<String, Object> parameters) {
        public boolean shouldExecute() {
            return execute == null || execute;
        }
    }

    public record Stage(String name, String status, long durationMs, String detail) {
    }

    public record AnalyzeResponse(Long conversationId, Long queryRunId, AnalysisStatus status, String question,
                                  IntentAnalysis intent, List<String> tablesUsed, String sql, String sqlExplanation,
                                  List<String> assumptions, Double confidence, String clarificationQuestion,
                                  SqlValidationResult validation, QueryResult result, Interpretation interpretation,
                                  Recommendation chart, List<Stage> stages, String model, int repairAttempts,
                                  String error) {
    }

    public record InterpretRequest(@Size(max = 4000) String question, @NotBlank @Size(max = 100_000) String sql,
                                   @NotNull QueryResult result) {
    }

    public record InterpretResponse(Interpretation interpretation, Recommendation chart) {
    }
}
