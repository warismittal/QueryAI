package com.queryai.it;

import com.queryai.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Monthly AI quota per workspace: set by a platform admin, enforced on every natural-language question. */
class AiQuotaIT extends IntegrationTestBase {

    @Test
    void workspaceStopsAnsweringOnceTheMonthlyQuotaIsUsed() throws Exception {
        Session owner = register("Thrifty");
        Session admin = promoteToPlatformAdmin(register("Operator"));
        long dsId = createDataSource(owner, "Shop");
        mockAi(AiAnalysisIT.SPEND_SQL, "Grace spent the most.");

        putJson(admin, "/api/v1/admin/workspaces/" + owner.workspaceId() + "/quota", Map.of("monthlyQueryQuota", 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyQueryQuota").value(2));

        analyze(owner, dsId, "Who spends the most?").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ANSWERED"));
        // hand-written SQL never counts against the AI quota
        execute(owner, dsId, "SELECT 1 AS one").andExpect(jsonPath("$.status").value("SUCCESS"));
        analyze(owner, dsId, "And the least?").andExpect(status().isOk());

        getAuth(owner, "/api/v1/workspaces/" + owner.workspaceId() + "/usage")
                .andExpect(jsonPath("$.quota").value(2))
                .andExpect(jsonPath("$.aiQueriesUsed").value(2))
                .andExpect(jsonPath("$.remaining").value(0));

        analyze(owner, dsId, "One more?")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.message", containsString("monthly quota of 2")));

        // SQL keeps working when the AI quota is exhausted
        execute(owner, dsId, "SELECT COUNT(*) AS n FROM orders").andExpect(jsonPath("$.status").value("SUCCESS"));

        // raising the quota restores AI answers immediately
        putJson(admin, "/api/v1/admin/workspaces/" + owner.workspaceId() + "/quota", Map.of("monthlyQueryQuota", 3))
                .andExpect(status().isOk());
        analyze(owner, dsId, "One more?").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ANSWERED"));
    }

    @Test
    void aZeroQuotaDisablesAiForTheWorkspace() throws Exception {
        Session owner = register("Frozen");
        Session admin = promoteToPlatformAdmin(register("Operator"));
        long dsId = createDataSource(owner, "Shop");
        mockAi(AiAnalysisIT.SPEND_SQL, "Grace spent the most.");

        putJson(admin, "/api/v1/admin/workspaces/" + owner.workspaceId() + "/quota", Map.of("monthlyQueryQuota", 0))
                .andExpect(status().isOk());
        analyze(owner, dsId, "Who spends the most?").andExpect(status().isTooManyRequests());
    }

    @Test
    void onlyPlatformAdminsCanChangeQuotas() throws Exception {
        Session owner = register("Greedy");
        putJson(owner, "/api/v1/admin/workspaces/" + owner.workspaceId() + "/quota", Map.of("monthlyQueryQuota", 1_000_000))
                .andExpect(status().isForbidden());

        Session admin = promoteToPlatformAdmin(register("Operator"));
        putJson(admin, "/api/v1/admin/workspaces/" + owner.workspaceId() + "/quota", Map.of("monthlyQueryQuota", -1))
                .andExpect(status().isBadRequest());
    }
}
