package com.queryai.it;

import com.queryai.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Validated, read-only SQL execution against PostgreSQL. */
class SqlExecutionIT extends IntegrationTestBase {

    @Test
    void executesValidatedSelectsAndReportsResults() throws Exception {
        Session s = register("Runner");
        long dsId = createDataSource(s, "Shop");

        execute(s, dsId, "SELECT segment, COUNT(*) AS customers FROM customers GROUP BY segment ORDER BY segment")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.queryRunId").isNumber())
                .andExpect(jsonPath("$.result.columns[0].name").value("segment"))
                .andExpect(jsonPath("$.result.rowCount").value(2))
                .andExpect(jsonPath("$.result.rows[0][0]").value("Consumer"))
                .andExpect(jsonPath("$.result.rows[0][1]").value(2))
                .andExpect(jsonPath("$.validation.databaseChecked").value(true));
    }

    @Test
    void destructiveAndOutOfScopeStatementsAreBlockedNotExecuted() throws Exception {
        Session s = register("Guard");
        long dsId = createDataSource(s, "Shop");

        for (String sql : new String[]{
                "DELETE FROM orders",
                "UPDATE customers SET name = 'x'",
                "DROP TABLE orders",
                "SELECT 1; DROP TABLE orders",
                "WITH gone AS (DELETE FROM orders RETURNING *) SELECT * FROM gone",
                "SELECT * FROM public.users"}) {
            execute(s, dsId, sql)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("BLOCKED"))
                    .andExpect(jsonPath("$.result").doesNotExist());
        }

        // nothing was changed
        execute(s, dsId, "SELECT COUNT(*) AS n FROM orders").andExpect(jsonPath("$.result.rows[0][0]").value(5));
    }

    @Test
    void unknownColumnsAreRejectedByTheDatabaseDryRun() throws Exception {
        Session s = register("Checker");
        long dsId = createDataSource(s, "Shop");
        execute(s, dsId, "SELECT missing_column FROM orders")
                .andExpect(jsonPath("$.status").value("BLOCKED"))
                .andExpect(jsonPath("$.validation.issues[*].code", hasItem("DATABASE_REJECTED")));
    }

    @Test
    void namedParametersAreBoundSafely() throws Exception {
        Session s = register("Binder");
        long dsId = createDataSource(s, "Shop");

        postJson(s, "/api/v1/sql/execute", Map.of("dataSourceId", dsId,
                "sql", "SELECT id FROM orders WHERE order_date >= :since ORDER BY id", "parameters", Map.of("since", "2026-01-01")))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.result.rowCount").value(3));

        // an injection attempt is just a (non-matching) string value
        postJson(s, "/api/v1/sql/execute", Map.of("dataSourceId", dsId,
                "sql", "SELECT id FROM customers WHERE name = :name", "parameters", Map.of("name", "x' OR '1'='1")))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.result.rowCount").value(0));
    }

    @Test
    void validateOnlyDoesNotExecute() throws Exception {
        Session s = register("Validator");
        long dsId = createDataSource(s, "Shop");
        postJson(s, "/api/v1/sql/validate", Map.of("dataSourceId", dsId, "sql", "SELECT name FROM customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", not("INVALID")))
                .andExpect(jsonPath("$.referencedTables", hasItem("customers")));
        postJson(s, "/api/v1/sql/validate", Map.of("dataSourceId", dsId, "sql", "TRUNCATE orders"))
                .andExpect(jsonPath("$.status").value("INVALID"));
    }
}
