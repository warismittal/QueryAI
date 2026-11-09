package com.queryai.it;

import com.queryai.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The second supported dialect: a real MySQL 8 container, end to end. */
class MySqlIT extends IntegrationTestBase {

    @Test
    void connectsAndDiscoversTheMySqlSchema() throws Exception {
        Session s = register("Dolphin");
        postJson(s, "/api/v1/datasources/test", mysqlDataSourceRequest(s.workspaceId(), "ignored"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.tableCount").value(2));

        long dsId = createMySqlDataSource(s, "Store (MySQL)");
        getAuth(s, "/api/v1/datasources/" + dsId + "/schema")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dbType").value("MYSQL"))
                .andExpect(jsonPath("$.tables[*].name", containsInAnyOrder("products", "sales")))
                .andExpect(jsonPath("$.relationships[0].fromTable").value("sales"))
                .andExpect(jsonPath("$.relationships[0].toTable").value("products"));
    }

    @Test
    void executesMySqlDialectQueriesInAReadOnlyTransaction() throws Exception {
        Session s = register("Sakila");
        long dsId = createMySqlDataSource(s, "Store (MySQL)");

        execute(s, dsId, "SELECT p.`name`, SUM(s.quantity) AS units FROM sales s JOIN products p ON p.id = s.product_id "
                + "GROUP BY p.`name` ORDER BY units DESC LIMIT 2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.result.rowCount").value(2))
                .andExpect(jsonPath("$.result.rows[0][0]").value("Notebook"))
                .andExpect(jsonPath("$.result.rows[0][1]").value(65));

        execute(s, dsId, "SELECT DATE_FORMAT(sold_on, '%Y-%m') AS month, COUNT(*) AS n FROM sales GROUP BY month ORDER BY month")
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.result.rows[0][0]").value("2026-01"))
                .andExpect(jsonPath("$.result.rows[0][1]").value(2));

        // runs on a pooled connection again after the read-only transaction was rolled back
        execute(s, dsId, "SELECT COUNT(*) AS n FROM products").andExpect(jsonPath("$.result.rows[0][0]").value(3));
    }

    @Test
    void writesAreBlockedOnMySqlToo() throws Exception {
        Session s = register("Warden");
        long dsId = createMySqlDataSource(s, "Store (MySQL)");

        for (String sql : new String[]{"DELETE FROM sales", "UPDATE products SET price = 0", "DROP TABLE sales",
                "SELECT * FROM products INTO OUTFILE '/tmp/x'", "SELECT 1; DROP TABLE sales"}) {
            execute(s, dsId, sql).andExpect(jsonPath("$.status").value("BLOCKED"));
        }
        execute(s, dsId, "SELECT COUNT(*) AS n FROM sales").andExpect(jsonPath("$.result.rows[0][0]").value(4));
    }

    @Test
    void namedParametersWorkOnMySql() throws Exception {
        Session s = register("Param");
        long dsId = createMySqlDataSource(s, "Store (MySQL)");
        postJson(s, "/api/v1/sql/execute", Map.of("dataSourceId", dsId,
                "sql", "SELECT name FROM products WHERE price > :min ORDER BY price", "parameters", Map.of("min", 10)))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.result.rowCount").value(2))
                .andExpect(jsonPath("$.result.rows[0][0]").value("Desk lamp"));
    }
}
