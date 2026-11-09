package com.queryai.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.queryai.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Natural-language analysis: the LLM is a test double, everything after it (validation, execution, history) is real. */
class AiAnalysisIT extends IntegrationTestBase {

    static final String SPEND_SQL = "SELECT c.name, SUM(o.total_amount) AS spending FROM customers c JOIN orders o ON o.customer_id = c.id "
            + "WHERE o.status NOT IN ('cancelled', 'returned') GROUP BY c.name ORDER BY spending DESC LIMIT 10";

    @Test
    void answersAQuestionWithRealRowsAChartAndAConversation() throws Exception {
        Session s = register("Asker");
        long dsId = createDataSource(s, "Shop");
        mockAi(SPEND_SQL, "Grace spent the most.");

        JsonNode analysis = body(analyze(s, dsId, "Who spends the most?")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANSWERED"))
                .andExpect(jsonPath("$.sql").value(SPEND_SQL))
                .andExpect(jsonPath("$.result.rows[0][0]").value("Grace"))
                .andExpect(jsonPath("$.result.rows[0][1]").value(300))
                .andExpect(jsonPath("$.interpretation.summary").value("Grace spent the most."))
                .andExpect(jsonPath("$.chart.type").exists())
                .andExpect(jsonPath("$.stages", hasSize(greaterThanOrEqualTo(3)))));

        long conversationId = analysis.get("conversationId").asLong();
        getAuth(s, "/api/v1/conversations/" + conversationId)
                .andExpect(jsonPath("$.messages", hasSize(2)))
                .andExpect(jsonPath("$.messages[0].content").value("Who spends the most?"))
                .andExpect(jsonPath("$.messages[1].sql").value(SPEND_SQL));
        getAuth(s, "/api/v1/conversations?workspaceId=" + s.workspaceId())
                .andExpect(jsonPath("$[*].id", hasItem((int) conversationId)));

        // a follow-up continues the same conversation
        analyze(s, dsId, "And the least?");
        postJson(s, "/api/v1/analytics/analyze", Map.of("dataSourceId", dsId, "question", "Only consumers?",
                "conversationId", conversationId))
                .andExpect(jsonPath("$.conversationId").value(conversationId));

        // AI runs are recorded in history with their source
        getAuth(s, "/api/v1/history?workspaceId=" + s.workspaceId() + "&source=AI")
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].generatedSql").value(SPEND_SQL));
    }

    @Test
    void unsafeGeneratedSqlIsBlockedEvenWhenTheModelProducesIt() throws Exception {
        Session s = register("Skeptic");
        long dsId = createDataSource(s, "Shop");
        mockAi("DELETE FROM orders", "Deleted everything.");

        analyze(s, dsId, "Remove all orders").andExpect(status().isOk())
                .andExpect(jsonPath("$.status", not("ANSWERED")))
                .andExpect(jsonPath("$.result").doesNotExist());
        postJson(s, "/api/v1/sql/execute", Map.of("dataSourceId", dsId, "sql", "SELECT COUNT(*) AS n FROM orders"))
                .andExpect(jsonPath("$.result.rows[0][0]").value(5));
    }

    @Test
    void explainsWhenNoModelIsConfigured() throws Exception {
        Session s = register("Offline");
        long dsId = createDataSource(s, "Shop");
        // the mocked LlmClient reports isConfigured() == false by default
        analyze(s, dsId, "Who spends the most?")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("LLM_NOT_CONFIGURED"));
    }
}
