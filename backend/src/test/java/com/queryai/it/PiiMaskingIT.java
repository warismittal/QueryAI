package com.queryai.it;

import com.queryai.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Personal data is masked for everyone below workspace admin, in SQL results, AI answers and dashboards. */
class PiiMaskingIT extends IntegrationTestBase {

    static final String PEOPLE = "SELECT name, email, segment FROM customers ORDER BY id";

    @Test
    void piiColumnsAreDetectedAutomatically() throws Exception {
        Session owner = register("Steward");
        long dsId = createDataSource(owner, "Shop");
        getAuth(owner, "/api/v1/datasources/" + dsId + "/pii")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maskingEnabled").value(true))
                .andExpect(jsonPath("$.youSeeRawValues").value(true))
                .andExpect(jsonPath("$.columns[?(@.column == 'email')].piiType", hasItem("EMAIL")))
                .andExpect(jsonPath("$.columns[?(@.column == 'segment')]", empty()));
    }

    @Test
    void analystsSeeMaskedValuesWhileAdminsSeeRawOnes() throws Exception {
        Session owner = register("Steward");
        Session analyst = register("Analyst");
        invite(owner, analyst, "ANALYST");
        long dsId = createDataSource(owner, "Shop");

        execute(owner, dsId, PEOPLE)
                .andExpect(jsonPath("$.result.rows[0][1]").value("ada@example.com"))
                .andExpect(jsonPath("$.result.maskedColumns", empty()));

        execute(analyst, dsId, PEOPLE)
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.result.maskedColumns", hasItem("email")))
                .andExpect(jsonPath("$.result.rows[0][1]", not("ada@example.com")))
                .andExpect(jsonPath("$.result.rows[0][2]").value("Consumer"));   // non-personal columns untouched
        getAuth(analyst, "/api/v1/datasources/" + dsId + "/pii").andExpect(jsonPath("$.youSeeRawValues").value(false));

        // aliases and expressions do not get around masking
        execute(analyst, dsId, "SELECT email AS contact FROM customers ORDER BY id")
                .andExpect(jsonPath("$.result.rows[0][0]", not("ada@example.com")));
        execute(analyst, dsId, "SELECT UPPER(email) AS shout FROM customers ORDER BY id")
                .andExpect(jsonPath("$.result.rows[0][0]", not("ADA@EXAMPLE.COM")));
    }

    @Test
    void adminsCanPreviewTheAnalystView() throws Exception {
        Session owner = register("Steward");
        long dsId = createDataSource(owner, "Shop");
        mvc.perform(post("/api/v1/sql/execute").header("Authorization", "Bearer " + owner.token())
                        .header("X-QueryAI-Pii-Preview", "masked")
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("dataSourceId", dsId, "sql", PEOPLE))))
                .andExpect(jsonPath("$.result.maskedColumns", hasItem("email")))
                .andExpect(jsonPath("$.result.rows[0][1]", not("ada@example.com")));
    }

    @Test
    void policiesCanBeChangedPerColumnAndPerSource() throws Exception {
        Session owner = register("Steward");
        Session analyst = register("Analyst");
        invite(owner, analyst, "ANALYST");
        long dsId = createDataSource(owner, "Shop");

        // mark a column the detector does not know about
        putJson(owner, "/api/v1/datasources/" + dsId + "/pii/columns",
                Map.of("table", "customers", "column", "segment", "piiType", "OTHER", "masked", true))
                .andExpect(status().isOk());
        execute(analyst, dsId, PEOPLE).andExpect(jsonPath("$.result.maskedColumns", hasItem("segment")));

        // unknown columns are rejected
        putJson(owner, "/api/v1/datasources/" + dsId + "/pii/columns",
                Map.of("table", "customers", "column", "nope", "masked", true))
                .andExpect(status().isNotFound());

        // analysts cannot change policies
        putJson(analyst, "/api/v1/datasources/" + dsId + "/pii/settings", Map.of("enabled", false))
                .andExpect(status().isForbidden());

        // turning masking off for the source shows raw values to everyone
        putJson(owner, "/api/v1/datasources/" + dsId + "/pii/settings", Map.of("enabled", false))
                .andExpect(jsonPath("$.maskingEnabled").value(false));
        execute(analyst, dsId, PEOPLE)
                .andExpect(jsonPath("$.result.rows[0][1]").value("ada@example.com"))
                .andExpect(jsonPath("$.result.maskedColumns", empty()));

        getAuth(owner, "/api/v1/audit?workspaceId=" + owner.workspaceId() + "&action=PII_POLICY_UPDATED")
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void aiAnswersAreMaskedForAnalystsToo() throws Exception {
        Session owner = register("Steward");
        Session analyst = register("Analyst");
        invite(owner, analyst, "ANALYST");
        long dsId = createDataSource(owner, "Shop");
        mockAi("SELECT name, email FROM customers ORDER BY id LIMIT 10", "Three customers.");

        analyze(analyst, dsId, "List our customers with their emails")
                .andExpect(jsonPath("$.status").value("ANSWERED"))
                .andExpect(jsonPath("$.result.maskedColumns", hasItem("email")))
                .andExpect(jsonPath("$.result.rows[0][1]", not("ada@example.com")));
    }
}
