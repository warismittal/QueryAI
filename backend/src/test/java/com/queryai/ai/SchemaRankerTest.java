package com.queryai.ai;

import com.queryai.ai.SchemaRanker.TableInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaRankerTest {

    private static TableInfo table(String name, Set<String> neighbours, String... columns) {
        return new TableInfo(name, List.of(columns), null, List.of(), neighbours);
    }

    @Test
    void returnsAllTablesWhenUnderTheLimit() {
        var tables = List.of(table("orders", Set.of()), table("customers", Set.of()));
        assertThat(SchemaRanker.rank("anything", List.of(), tables, 12)).containsExactlyInAnyOrder("orders", "customers");
    }

    @Test
    void ranksByRelevanceAndAddsForeignKeyNeighbours() {
        List<TableInfo> tables = new ArrayList<>();
        tables.add(table("customers", Set.of("orders"), "id", "first_name", "segment"));
        tables.add(table("orders", Set.of("customers", "order_items"), "id", "customer_id", "total_amount", "order_date"));
        tables.add(table("order_items", Set.of("orders", "products"), "id", "order_id", "product_id"));
        tables.add(table("products", Set.of("order_items"), "id", "name", "unit_price"));
        for (int i = 0; i < 10; i++) {
            tables.add(table("audit_log_" + i, Set.of(), "id", "payload"));
        }
        List<String> selected = SchemaRanker.rank("Top customers by total order amount", List.of("customer", "order"), tables, 4);
        assertThat(selected).hasSizeLessThanOrEqualTo(4).contains("customers", "orders").noneMatch(t -> t.startsWith("audit_log"));
    }

    @Test
    void semanticSimilarityBreaksLexicalTies() {
        List<TableInfo> tables = new ArrayList<>();
        tables.add(table("product_reviews", Set.of(), "id", "rating"));
        tables.add(table("inventory", Set.of(), "id", "quantity_on_hand"));
        for (int i = 0; i < 6; i++) {
            tables.add(table("misc_" + i, Set.of(), "id"));
        }
        // "loved" shares no token with any table name; the semantic signal decides
        List<String> selected = SchemaRanker.rank("which item is loved", List.of(), tables, 3,
                java.util.Map.of("product_reviews", 0.62, "inventory", 0.10, "misc_0", 0.05));
        assertThat(selected.get(0)).isEqualTo("product_reviews");
        assertThat(SchemaRanker.rank("x", List.of(), List.of(table("a", Set.of()), table("b", Set.of())), 12,
                java.util.Map.of("a", 0.1, "b", 0.9))).containsExactly("b", "a");
    }

    @Test
    void stemsPlurals() {
        assertThat(SchemaRanker.stem("categories")).isEqualTo("category");
        assertThat(SchemaRanker.stem("orders")).isEqualTo("order");
        assertThat(SchemaRanker.stem("address")).isEqualTo("address");
    }
}
