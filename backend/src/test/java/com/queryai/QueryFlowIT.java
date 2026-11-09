package com.queryai;

import com.fasterxml.jackson.databind.JsonNode;
import com.queryai.ai.AiDtos.IntentAnalysis;
import com.queryai.ai.AiDtos.IntentType;
import com.queryai.ai.AiDtos.InterpretationOutput;
import com.queryai.ai.AiDtos.SqlGenerationOutput;
import com.queryai.ai.LlmClient.LlmResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end: connect → discover schema → validate/execute → AI analysis → history → saved query → dashboard. */
class QueryFlowIT extends IntegrationTestBase {

    @Test
    void fullAnalyticsWorkflowAgainstARealDatabase() throws Exception {
        Session s = register("Analyst");

        // connection diagnostics before saving
        postJson(s, "/api/v1/datasources/test", dataSourceRequest(s.workspaceId(), "ignored"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.tableCount").value(2));

        // create data source; password must never be echoed
        JsonNode ds = body(postJson(s, "/api/v1/datasources", dataSourceRequest(s.workspaceId(), "Shop"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hasPassword").value(true))
                .andExpect(jsonPath("$.password").doesNotExist()));
        long dsId = ds.get("id").asLong();

        // schema discovery with keys and relationships
        getAuth(s, "/api/v1/datasources/" + dsId + "/schema")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tables[*].name", containsInAnyOrder("customers", "orders")))
                .andExpect(jsonPath("$.relationships[0].toTable").value("customers"));

        // execute real SQL
        JsonNode exec = body(postJson(s, "/api/v1/sql/execute", Map.of("dataSourceId", dsId,
                "sql", "SELECT segment, COUNT(*) AS customers FROM customers GROUP BY segment ORDER BY segment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.result.rowCount").value(2))
                .andExpect(jsonPath("$.result.rows[0][0]").value("Consumer"))
                .andExpect(jsonPath("$.result.rows[0][1]").value(2))
                .andExpect(jsonPath("$.validation.databaseChecked").value(true)));
        long runId = exec.get("queryRunId").asLong();

        // destructive SQL is blocked and recorded, never executed
        postJson(s, "/api/v1/sql/execute", Map.of("dataSourceId", dsId, "sql", "DELETE FROM orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"))
                .andExpect(jsonPath("$.result").doesNotExist());
        postJson(s, "/api/v1/sql/execute", Map.of("dataSourceId", dsId, "sql", "SELECT * FROM public.users"))
                .andExpect(jsonPath("$.status").value("BLOCKED"));
        postJson(s, "/api/v1/sql/execute", Map.of("dataSourceId", dsId, "sql", "SELECT missing_column FROM orders"))
                .andExpect(jsonPath("$.status").value("BLOCKED"))
                .andExpect(jsonPath("$.validation.issues[*].code", hasItem("DATABASE_REJECTED")));

        // named parameters are bound safely
        postJson(s, "/api/v1/sql/execute", Map.of("dataSourceId", dsId,
                "sql", "SELECT id FROM orders WHERE order_date >= :since ORDER BY id", "parameters", Map.of("since", "2026-01-01")))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.result.rowCount").value(3));

        // feedback + history
        postJson(s, "/api/v1/history/" + runId + "/feedback", Map.of("rating", "UP")).andExpect(status().isOk())
                .andExpect(jsonPath("$.myFeedback").value("UP"));
        getAuth(s, "/api/v1/history?workspaceId=" + s.workspaceId() + "&status=BLOCKED")
                .andExpect(jsonPath("$.totalElements").value(3));

        // AI analysis (LLM mocked; SQL is executed for real)
        String aiSql = "SELECT c.name, SUM(o.total_amount) AS spending FROM customers c JOIN orders o ON o.customer_id = c.id "
                + "WHERE o.status NOT IN ('cancelled', 'returned') GROUP BY c.name ORDER BY spending DESC LIMIT 10";
        when(llm.isConfigured()).thenReturn(true);
        when(llm.description()).thenReturn("mock · test");
        when(llm.structured(anyString(), anyString(), eq(IntentAnalysis.class))).thenReturn(new LlmResponse<>(
                new IntentAnalysis(IntentType.RANKING, "Customers ranked by spending", List.of("spending"), List.of("customer"),
                        List.of(), null, List.of("customer", "order"), false, null), "mock", 1));
        when(llm.structured(anyString(), anyString(), eq(SqlGenerationOutput.class))).thenReturn(new LlmResponse<>(
                new SqlGenerationOutput(aiSql, "Sums completed order totals per customer", List.of("Excludes cancelled/returned"),
                        false, null, 0.9), "mock", 1));
        when(llm.structured(anyString(), anyString(), eq(InterpretationOutput.class))).thenReturn(new LlmResponse<>(
                new InterpretationOutput("Grace spent the most.", List.of(), List.of("Compare with last year?")), "mock", 1));

        JsonNode analysis = body(postJson(s, "/api/v1/analytics/analyze", Map.of("dataSourceId", dsId, "question", "Who spends the most?"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANSWERED"))
                .andExpect(jsonPath("$.result.rows[0][0]").value("Grace"))
                .andExpect(jsonPath("$.result.rows[0][1]").value(300))
                .andExpect(jsonPath("$.chart.type").exists()));
        long conversationId = analysis.get("conversationId").asLong();
        getAuth(s, "/api/v1/conversations/" + conversationId)
                .andExpect(jsonPath("$.messages", hasSize(2)))
                .andExpect(jsonPath("$.messages[1].sql").value(aiSql));

        // saved query with versioning
        JsonNode saved = body(postJson(s, "/api/v1/saved-queries", Map.of("workspaceId", s.workspaceId(), "dataSourceId", dsId,
                "name", "Spend by customer", "sql", aiSql, "visibility", "WORKSPACE", "template", false))
                .andExpect(status().isCreated()));
        long savedId = saved.get("id").asLong();
        putJson(s, "/api/v1/saved-queries/" + savedId, Map.of("name", "Spend by customer", "sql", "SELECT COUNT(*) AS n FROM orders",
                "visibility", "WORKSPACE", "template", false, "versionNote", "simplify"))
                .andExpect(jsonPath("$.currentVersion").value(2));
        getAuth(s, "/api/v1/saved-queries/" + savedId + "/versions").andExpect(jsonPath("$", hasSize(2)));
        postJson(s, "/api/v1/saved-queries", Map.of("workspaceId", s.workspaceId(), "dataSourceId", dsId,
                "name", "Bad", "sql", "DROP TABLE orders", "template", false))
                .andExpect(status().isBadRequest());

        // dashboard with a live widget
        JsonNode dash = body(postJson(s, "/api/v1/dashboards", Map.of("workspaceId", s.workspaceId(), "name", "KPIs", "visibility", "WORKSPACE"))
                .andExpect(status().isCreated()));
        long dashId = dash.get("id").asLong();
        JsonNode detail = body(postJson(s, "/api/v1/dashboards/" + dashId + "/widgets",
                Map.of("savedQueryId", savedId, "title", "Orders", "chartType", "METRIC"))
                .andExpect(status().isCreated()));
        long widgetId = detail.get("widgets").get(0).get("id").asLong();
        postJson(s, "/api/v1/dashboards/" + dashId + "/widgets/" + widgetId + "/data", Map.of())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.result.rows[0][0]").value(5));

        // overview reflects measured activity
        getAuth(s, "/api/v1/analytics/overview?workspaceId=" + s.workspaceId())
                .andExpect(jsonPath("$.dataSourceCount").value(1))
                .andExpect(jsonPath("$.executedInWindow", greaterThanOrEqualTo(4)));

        // tenancy: another user cannot see or use this workspace's resources
        Session other = register("Mallory");
        getAuth(other, "/api/v1/datasources/" + dsId).andExpect(status().isNotFound());
        postJson(other, "/api/v1/sql/execute", Map.of("dataSourceId", dsId, "sql", "SELECT 1")).andExpect(status().isNotFound());
        getAuth(other, "/api/v1/saved-queries/" + savedId).andExpect(status().isNotFound());
        getAuth(other, "/api/v1/history/" + runId).andExpect(status().isNotFound());

        // invite as VIEWER: may run shared dashboards but not ad-hoc SQL
        postJson(s, "/api/v1/workspaces/" + s.workspaceId() + "/members", Map.of("email", other.email(), "role", "VIEWER"))
                .andExpect(status().isCreated());
        postJson(other, "/api/v1/dashboards/" + dashId + "/widgets/" + widgetId + "/data", Map.of())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
        postJson(other, "/api/v1/sql/execute", Map.of("dataSourceId", dsId, "sql", "SELECT 1")).andExpect(status().isForbidden());

        // audit trail exists for the workspace admin
        JsonNode audit = body(getAuth(s, "/api/v1/audit?workspaceId=" + s.workspaceId()).andExpect(status().isOk()));
        assertThat(audit.get("totalElements").asLong()).isGreaterThan(5);

        // cleanup path works
        mvc.perform(delete("/api/v1/dashboards/" + dashId).header("Authorization", "Bearer " + s.token()))
                .andExpect(status().isNoContent());
    }
}
