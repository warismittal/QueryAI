package com.queryai.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.queryai.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Every run lands in history (with feedback and re-run); every sensitive action lands in the audit log. */
class HistoryAndAuditIT extends IntegrationTestBase {

    @Test
    void historyRecordsSuccessfulAndBlockedRunsWithFilters() throws Exception {
        Session s = register("Historian");
        long dsId = createDataSource(s, "Shop");

        long ok = body(execute(s, dsId, "SELECT COUNT(*) AS n FROM customers")).get("queryRunId").asLong();
        execute(s, dsId, "DELETE FROM orders");
        execute(s, dsId, "DROP TABLE customers");

        getAuth(s, "/api/v1/history?workspaceId=" + s.workspaceId())
                .andExpect(jsonPath("$.totalElements").value(3));
        getAuth(s, "/api/v1/history?workspaceId=" + s.workspaceId() + "&status=BLOCKED")
                .andExpect(jsonPath("$.totalElements").value(2));
        getAuth(s, "/api/v1/history?workspaceId=" + s.workspaceId() + "&status=SUCCESS&dataSourceId=" + dsId)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(ok))
                .andExpect(jsonPath("$.content[0].rowCount").value(1))
                .andExpect(jsonPath("$.content[0].dataSourceName").value("Shop"));
        getAuth(s, "/api/v1/history?workspaceId=" + s.workspaceId() + "&q=customers")
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(1)));
        getAuth(s, "/api/v1/history?workspaceId=" + s.workspaceId() + "&size=1&page=1")
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void feedbackAndRerunWorkOnHistoryEntries() throws Exception {
        Session s = register("Rater");
        long dsId = createDataSource(s, "Shop");
        long runId = body(execute(s, dsId, "SELECT COUNT(*) AS n FROM orders")).get("queryRunId").asLong();

        postJson(s, "/api/v1/history/" + runId + "/feedback", Map.of("rating", "UP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myFeedback").value("UP"));
        postJson(s, "/api/v1/history/" + runId + "/feedback", Map.of("rating", "DOWN", "comment", "Wrong table"))
                .andExpect(jsonPath("$.myFeedback").value("DOWN"));

        JsonNode rerun = body(postJson(s, "/api/v1/history/" + runId + "/rerun", Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.result.rows[0][0]").value(5)));
        assertThat(rerun.get("queryRunId").asLong()).isNotEqualTo(runId);
        getAuth(s, "/api/v1/history/" + runId).andExpect(jsonPath("$.finalSql").value("SELECT COUNT(*) AS n FROM orders"));
    }

    @Test
    void auditLogCapturesSensitiveActionsForWorkspaceAdminsOnly() throws Exception {
        Session owner = register("Auditor");
        long dsId = createDataSource(owner, "Shop");
        execute(owner, dsId, "SELECT 1 AS one");
        execute(owner, dsId, "DELETE FROM orders");
        postJson(owner, "/api/v1/saved-queries", Map.of("workspaceId", owner.workspaceId(), "dataSourceId", dsId,
                "name", "One", "sql", "SELECT 1 AS one", "template", false)).andExpect(status().isCreated());
        Session analyst = register("Member");
        invite(owner, analyst, "ANALYST");

        JsonNode audit = body(getAuth(owner, "/api/v1/audit?workspaceId=" + owner.workspaceId() + "&size=100")
                .andExpect(status().isOk()));
        Set<String> actions = new HashSet<>();
        audit.get("content").forEach(e -> actions.add(e.get("action").asText()));
        assertThat(actions).contains("DATASOURCE_CREATED", "QUERY_EXECUTED", "QUERY_BLOCKED", "SAVED_QUERY_CREATED", "MEMBER_ADDED");

        getAuth(owner, "/api/v1/audit?workspaceId=" + owner.workspaceId() + "&action=QUERY_BLOCKED")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].userEmail").value(owner.email()));

        // a regular member may not read the workspace audit log, and nobody but platform admins sees the global one
        getAuth(analyst, "/api/v1/audit?workspaceId=" + owner.workspaceId()).andExpect(status().isForbidden());
        getAuth(owner, "/api/v1/audit").andExpect(status().isForbidden());
        getAuth(owner, "/api/v1/audit/me").andExpect(status().isOk());

        Session platformAdmin = promoteToPlatformAdmin(register("Root"));
        getAuth(platformAdmin, "/api/v1/audit?size=5").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", greaterThan(5)));
    }
}
