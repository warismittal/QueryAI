package com.queryai.sql;

import com.queryai.ai.ChartRecommender;
import com.queryai.history.ExecutionStatus;
import com.queryai.history.ValidationStatus;
import com.queryai.sql.SqlSafetyValidator.Issue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SqlDtos {
    private SqlDtos() {
    }

    public record ValidateRequest(@NotNull Long dataSourceId, @NotBlank @Size(max = 100_000) String sql,
                                  Map<String, Object> parameters) {
    }

    public record ExecuteRequest(@NotNull Long dataSourceId, @NotBlank @Size(max = 100_000) String sql,
                                 Map<String, Object> parameters, @Size(max = 4000) String question,
                                 Long conversationId, @Min(1) @Max(50000) Integer maxRows) {
    }

    public record SqlValidationResult(ValidationStatus status, List<Issue> issues, Set<String> referencedTables,
                                      List<String> parameters, boolean hasLimit, String normalizedSql,
                                      boolean databaseChecked, Double estimatedCost, Long estimatedRows) {
        public boolean blocked() {
            return status == ValidationStatus.INVALID;
        }

        public String errorSummary() {
            return issues.stream().filter(i -> i.severity() == SqlSafetyValidator.Severity.ERROR)
                    .map(Issue::message).reduce((a, b) -> a + "; " + b).orElse(null);
        }
    }

    public record ExecuteResponse(Long queryRunId, ExecutionStatus status, SqlValidationResult validation,
                                  QueryResult result, String error, ChartRecommender.Recommendation chart) {
    }
}
