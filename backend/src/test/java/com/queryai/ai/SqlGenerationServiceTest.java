package com.queryai.ai;

import com.queryai.ai.AiDtos.SqlGenerationOutput;
import com.queryai.ai.LlmClient.LlmResponse;
import com.queryai.ai.SqlGenerationService.GenerationContext;
import com.queryai.datasource.DatabaseType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SqlGenerationServiceTest {
    private final LlmClient llm = mock(LlmClient.class);
    private final SqlGenerationService service = new SqlGenerationService(llm,
            Clock.fixed(Instant.parse("2026-10-11T00:00:00Z"), ZoneOffset.UTC));

    private final GenerationContext ctx = new GenerationContext(DatabaseType.MYSQL, "TABLE orders\n  id int PRIMARY KEY",
            "- revenue: sum of total_amount", "(new conversation)", "Intent: AGGREGATION", "Total revenue?");

    @Test
    void sendsDialectSchemaGlossaryAndDateAndCleansSql() {
        when(llm.structured(anyString(), anyString(), eq(SqlGenerationOutput.class))).thenReturn(new LlmResponse<>(
                new SqlGenerationOutput("```sql\nSELECT SUM(total_amount) FROM orders;\n```", "Sums revenue", List.of(), false, null, 0.9),
                "test-model", 5));

        LlmResponse<SqlGenerationOutput> out = service.generate(ctx);

        assertThat(out.value().sql()).isEqualTo("SELECT SUM(total_amount) FROM orders");
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(llm).structured(system.capture(), user.capture(), eq(SqlGenerationOutput.class));
        assertThat(system.getValue()).contains("MySQL").contains("2026-10-11").contains("NO FILTER clause");
        assertThat(user.getValue()).contains("TABLE orders").contains("revenue: sum of total_amount").contains("Total revenue?");
    }

    @Test
    void repairPromptIncludesFailedSqlAndErrors() {
        when(llm.structured(anyString(), anyString(), eq(SqlGenerationOutput.class))).thenReturn(new LlmResponse<>(
                new SqlGenerationOutput("SELECT 1", "fixed", List.of(), false, null, 0.5), "m", 1));
        service.repair(ctx, "SELECT * FROM nope", "Table 'nope' does not exist");
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(llm).structured(anyString(), user.capture(), eq(SqlGenerationOutput.class));
        assertThat(user.getValue()).contains("SELECT * FROM nope").contains("Table 'nope' does not exist");
    }

    @Test
    void stripFencesHandlesPlainSql() {
        assertThat(SqlGenerationService.stripFences(" SELECT 1 ;; ")).isEqualTo("SELECT 1");
        assertThat(SqlGenerationService.stripFences(null)).isEmpty();
    }
}
