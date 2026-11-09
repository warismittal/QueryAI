package com.queryai.sql;

import com.queryai.datasource.DatabaseType;
import com.queryai.sql.SqlSafetyValidator.Report;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SqlSafetyValidatorTest {
    private static final Set<String> TABLES = Set.of("customers", "orders", "order_items", "products", "categories",
            "payments", "product_reviews", "inventory", "warehouses");
    private final SqlSafetyValidator validator = new SqlSafetyValidator();

    private Report pg(String sql) {
        return validator.validate(sql, DatabaseType.POSTGRESQL, TABLES, "public");
    }

    private Report my(String sql) {
        return validator.validate(sql, DatabaseType.MYSQL, TABLES, "commerce");
    }

    private static boolean has(Report r, String code) {
        return r.issues().stream().anyMatch(i -> i.code().equals(code));
    }

    @Test
    void acceptsComplexAnalyticalQueryAndExtractsMetadata() {
        Report r = pg("""
                WITH spend AS (
                  SELECT c.id, c.first_name || ' ' || c.last_name AS customer,
                         SUM(o.total_amount) FILTER (WHERE EXTRACT(YEAR FROM o.order_date) = 2026) AS spend_2026,
                         SUM(o.total_amount) FILTER (WHERE EXTRACT(YEAR FROM o.order_date) = 2025) AS spend_2025
                  FROM customers c JOIN orders o ON o.customer_id = c.id
                  WHERE o.status NOT IN ('cancelled','returned') AND o.order_date >= :start::date
                  GROUP BY c.id, customer)
                SELECT customer, ROUND(100.0 * (spend_2026 - spend_2025) / NULLIF(spend_2025, 0), 1) AS pct_change
                FROM spend ORDER BY spend_2026 DESC NULLS LAST LIMIT 10;
                """);
        assertThat(r.valid()).as(r.issues().toString()).isTrue();
        assertThat(r.referencedTables()).containsExactly("customers", "orders");
        assertThat(r.parameters()).containsExactly("start");
        assertThat(r.hasLimit()).isTrue();
        assertThat(r.normalizedSql()).doesNotEndWith(";");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "DELETE FROM orders",
            "UPDATE orders SET status = 'x'",
            "INSERT INTO orders VALUES (1)",
            "DROP TABLE orders",
            "TRUNCATE orders",
            "COPY orders TO '/tmp/x'",
            "SELECT * INTO backup FROM orders",
            "SELECT * FROM orders FOR UPDATE",
            "WITH d AS (DELETE FROM orders RETURNING *) SELECT * FROM d",
            "SELECT 1; DROP TABLE orders",
            "GRANT ALL ON orders TO public",
            "CALL do_something()"
    })
    void rejectsWritesDdlAndMultipleStatements(String sql) {
        assertThat(pg(sql).valid()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT pg_sleep(10)",
            "SELECT pg_catalog.pg_sleep(10)",
            "SELECT \"pg_sleep\"(10)",
            "SELECT pg_read_file('/etc/passwd')",
            "SELECT nextval('seq')",
            "SELECT query_to_xml('select * from pg_shadow', true, true, '')",
            "SELECT dblink('host=evil', 'select 1')"
    })
    void rejectsDangerousFunctions(String sql) {
        assertThat(has(pg(sql), "FORBIDDEN_FUNCTION")).isTrue();
    }

    @Test
    void rejectsSystemCatalogsUnknownTablesAndForeignSchemas() {
        assertThat(has(pg("SELECT * FROM pg_catalog.pg_tables"), "SYSTEM_CATALOG")).isTrue();
        assertThat(has(pg("SELECT * FROM information_schema.tables"), "SYSTEM_CATALOG")).isTrue();
        assertThat(has(pg("SELECT * FROM pg_shadow"), "SYSTEM_CATALOG")).isTrue();
        assertThat(has(pg("SELECT * FROM salaries"), "UNKNOWN_TABLE")).isTrue();
        assertThat(has(pg("SELECT * FROM hr.customers"), "UNAUTHORIZED_SCHEMA")).isTrue();
        assertThat(has(pg("SELECT * FROM (SELECT * FROM secrets) t"), "UNKNOWN_TABLE")).isTrue();
        assertThat(has(pg("SELECT id FROM orders WHERE customer_id IN (SELECT id FROM vip)"), "UNKNOWN_TABLE")).isTrue();
        assertThat(has(pg("SELECT id FROM orders UNION SELECT id FROM users"), "UNKNOWN_TABLE")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT id FROM orders -- drop table x\n LIMIT 1",
            "SELECT 'delete; drop' AS s FROM orders",
            "SELECT o.update FROM orders o LIMIT 1",
            "SELECT \"update\" FROM orders LIMIT 1",
            "SELECT EXTRACT(YEAR FROM order_date) y, COUNT(*) FROM orders GROUP BY 1",
            "SELECT SUBSTRING(email FROM 1 FOR 3) FROM customers",
            "SELECT id FROM orders WHERE status IS DISTINCT FROM 'x'",
            "SELECT g FROM generate_series(1, 10) g",
            "SELECT c.id, x.total FROM customers c CROSS JOIN LATERAL (SELECT SUM(total_amount) total FROM orders o WHERE o.customer_id = c.id) x",
            "SELECT $$drop table x$$ AS t",
            "SELECT 1 /* a /* b */ c */",
            "SELECT id FROM orders UNION ALL SELECT id FROM customers",
            "SELECT id, RANK() OVER (PARTITION BY customer_id ORDER BY total_amount DESC) FROM orders",
            "SELECT CAST(order_date AS DATE), total_amount::numeric FROM orders",
            "SELECT launched_on AS release FROM products",
            "WITH RECURSIVE tree(id, parent) AS (SELECT id, parent_category_id FROM categories UNION ALL "
                    + "SELECT c.id, c.parent_category_id FROM categories c JOIN tree t ON c.parent_category_id = t.id) SELECT * FROM tree"
    })
    void acceptsLegitimateReadOnlyConstructs(String sql) {
        Report r = pg(sql);
        assertThat(r.valid()).as(r.issues().toString()).isTrue();
    }

    @Test
    void reportsHelpfulWarnings() {
        assertThat(has(pg("SELECT * FROM orders, customers"), "CROSS_JOIN")).isTrue();
        assertThat(has(pg("SELECT * FROM orders"), "NO_LIMIT")).isTrue();
        assertThat(has(pg("SELECT * FROM orders WHERE id = $1"), "POSITIONAL_PARAMETER")).isTrue();
        assertThat(has(pg("SELECT 'abc"), "SYNTAX")).isTrue();
        assertThat(validator.validate("SELECT 1 FROM orders", DatabaseType.POSTGRESQL, null, null).issues())
                .anyMatch(i -> i.code().equals("SCHEMA_UNKNOWN"));
    }

    @Nested
    class MySql {
        @Test
        void handlesBackticksHashCommentsAndStrings() {
            assertThat(my("SELECT `c`.`id`, YEAR(o.order_date) FROM customers c JOIN orders o ON o.customer_id = c.id LIMIT 5").valid()).isTrue();
            assertThat(my("SELECT id FROM orders # ; drop table x\n LIMIT 1").valid()).isTrue();
            assertThat(my("SELECT 'it\\'s; delete' AS s FROM orders").valid()).isTrue();
            assertThat(my("SELECT \"drop; table\" AS s FROM orders").valid()).isTrue();
            assertThat(my("SELECT * FROM commerce.orders").valid()).isTrue();
        }

        @Test
        void rejectsMySqlSpecificAttacks() {
            assertThat(has(my("SELECT 1 /*!50000 , SLEEP(5) */"), "EXECUTABLE_COMMENT")).isTrue();
            assertThat(has(my("SELECT SLEEP(5)"), "FORBIDDEN_FUNCTION")).isTrue();
            assertThat(has(my("SELECT LOAD_FILE('/etc/passwd')"), "FORBIDDEN_FUNCTION")).isTrue();
            assertThat(my("SELECT * FROM orders INTO OUTFILE '/tmp/x'").valid()).isFalse();
            assertThat(has(my("SELECT * FROM mysql.user"), "SYSTEM_CATALOG")).isTrue();
        }
    }

    @Test
    void collectsDistinctNamedParameters() {
        Report r = pg("SELECT * FROM orders WHERE order_date >= :start AND order_date < :end AND :start IS NOT NULL LIMIT 5");
        assertThat(r.parameters()).isEqualTo(List.of("start", "end"));
    }
}
