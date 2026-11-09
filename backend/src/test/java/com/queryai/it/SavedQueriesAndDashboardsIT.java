package com.queryai.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.queryai.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Saved queries (versioning, run, restore) and dashboards (widgets, live data, layout, sharing). */
class SavedQueriesAndDashboardsIT extends IntegrationTestBase {

    static final String SPEND_SQL = AiAnalysisIT.SPEND_SQL;

    private long saveQuery(Session s, long dsId, String name, String sql) throws Exception {
        return body(postJson(s, "/api/v1/saved-queries", Map.of("workspaceId", s.workspaceId(), "dataSourceId", dsId,
                "name", name, "sql", sql, "visibility", "WORKSPACE", "template", false))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    @Test
    void savedQueriesAreVersionedRunnableAndRestorable() throws Exception {
        Session s = register("Librarian");
        long dsId = createDataSource(s, "Shop");
        long id = saveQuery(s, dsId, "Spend by customer", SPEND_SQL);

        getAuth(s, "/api/v1/saved-queries/" + id)
                .andExpect(jsonPath("$.currentVersion").value(1))
                .andExpect(jsonPath("$.sql").value(SPEND_SQL))
                .andExpect(jsonPath("$.canEdit").value(true));

        putJson(s, "/api/v1/saved-queries/" + id, Map.of("name", "Spend by customer", "sql", "SELECT COUNT(*) AS n FROM orders",
                "visibility", "WORKSPACE", "template", false, "versionNote", "simplify"))
                .andExpect(jsonPath("$.currentVersion").value(2));
        getAuth(s, "/api/v1/saved-queries/" + id + "/versions").andExpect(jsonPath("$", hasSize(2)));

        postJson(s, "/api/v1/saved-queries/" + id + "/run", Map.of())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.result.rows[0][0]").value(5));

        postJson(s, "/api/v1/saved-queries/" + id + "/versions/1/restore", Map.of())
                .andExpect(jsonPath("$.sql").value(SPEND_SQL))
                .andExpect(jsonPath("$.currentVersion").value(3));

        getAuth(s, "/api/v1/saved-queries?workspaceId=" + s.workspaceId() + "&q=spend")
                .andExpect(jsonPath("$[*].id", hasItem((int) id)));
    }

    @Test
    void unsafeSqlCannotBeSaved() throws Exception {
        Session s = register("Careful");
        long dsId = createDataSource(s, "Shop");
        postJson(s, "/api/v1/saved-queries", Map.of("workspaceId", s.workspaceId(), "dataSourceId", dsId,
                "name", "Bad", "sql", "DROP TABLE orders", "template", false))
                .andExpect(status().isBadRequest());
    }

    @Test
    void savedQueriesCanBeDuplicatedAndDeleted() throws Exception {
        Session s = register("Copier");
        long dsId = createDataSource(s, "Shop");
        long id = saveQuery(s, dsId, "Orders", "SELECT COUNT(*) AS n FROM orders");

        long copy = body(postJson(s, "/api/v1/saved-queries/" + id + "/duplicate", Map.of())
                .andExpect(status().isCreated())).get("id").asLong();
        deleteAuth(s, "/api/v1/saved-queries/" + copy).andExpect(status().isNoContent());
        getAuth(s, "/api/v1/saved-queries/" + copy).andExpect(status().isNotFound());
        getAuth(s, "/api/v1/saved-queries/" + id).andExpect(status().isOk());
    }

    @Test
    void dashboardsRenderLiveWidgetsAndKeepTheirLayout() throws Exception {
        Session s = register("Curator");
        long dsId = createDataSource(s, "Shop");
        long countId = saveQuery(s, dsId, "Order count", "SELECT COUNT(*) AS n FROM orders");
        long spendId = saveQuery(s, dsId, "Spend", SPEND_SQL);

        long dashId = body(postJson(s, "/api/v1/dashboards", Map.of("workspaceId", s.workspaceId(), "name", "KPIs", "visibility", "WORKSPACE"))
                .andExpect(status().isCreated())).get("id").asLong();
        JsonNode detail = body(postJson(s, "/api/v1/dashboards/" + dashId + "/widgets",
                Map.of("savedQueryId", countId, "title", "Orders", "chartType", "METRIC"))
                .andExpect(status().isCreated()));
        long metric = detail.get("widgets").get(0).get("id").asLong();
        detail = body(postJson(s, "/api/v1/dashboards/" + dashId + "/widgets",
                Map.of("savedQueryId", spendId, "title", "Spend", "chartType", "BAR", "xColumn", "name", "yColumns", List.of("spending")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.widgets", hasSize(2))));
        long bar = detail.get("widgets").get(1).get("id").asLong();

        postJson(s, "/api/v1/dashboards/" + dashId + "/widgets/" + metric + "/data", Map.of())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.result.rows[0][0]").value(5));
        postJson(s, "/api/v1/dashboards/" + dashId + "/widgets/" + bar + "/data", Map.of())
                .andExpect(jsonPath("$.result.rows[0][0]").value("Grace"));

        putJson(s, "/api/v1/dashboards/" + dashId + "/layout", Map.of("items", List.of(
                Map.of("id", bar, "x", 0, "y", 0, "w", 8, "h", 6),
                Map.of("id", metric, "x", 8, "y", 0, "w", 4, "h", 3))))
                .andExpect(status().isOk());
        getAuth(s, "/api/v1/dashboards/" + dashId)
                .andExpect(jsonPath("$.widgets[?(@.title == 'Spend')].w", hasItem(8)))
                .andExpect(jsonPath("$.widgets[?(@.title == 'Orders')].x", hasItem(8)));

        getAuth(s, "/api/v1/dashboards?workspaceId=" + s.workspaceId())
                .andExpect(jsonPath("$[0].widgetCount").value(2));

        deleteAuth(s, "/api/v1/dashboards/" + dashId + "/widgets/" + metric).andExpect(status().is2xxSuccessful());
        deleteAuth(s, "/api/v1/dashboards/" + dashId).andExpect(status().isNoContent());
        getAuth(s, "/api/v1/dashboards/" + dashId).andExpect(status().isNotFound());
    }

    @Test
    void viewersCanRunSharedDashboardsButNotAdHocSql() throws Exception {
        Session owner = register("Publisher");
        Session viewer = register("Reader");
        long dsId = createDataSource(owner, "Shop");
        long countId = saveQuery(owner, dsId, "Order count", "SELECT COUNT(*) AS n FROM orders");
        long dashId = body(postJson(owner, "/api/v1/dashboards", Map.of("workspaceId", owner.workspaceId(), "name", "Shared", "visibility", "WORKSPACE"))
                .andExpect(status().isCreated())).get("id").asLong();
        long widget = body(postJson(owner, "/api/v1/dashboards/" + dashId + "/widgets",
                Map.of("savedQueryId", countId, "title", "Orders", "chartType", "METRIC"))).get("widgets").get(0).get("id").asLong();

        invite(owner, viewer, "VIEWER");
        postJson(viewer, "/api/v1/dashboards/" + dashId + "/widgets/" + widget + "/data", Map.of())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
        execute(viewer, dsId, "SELECT 1").andExpect(status().isForbidden());
        putJson(viewer, "/api/v1/saved-queries/" + countId, Map.of("name", "Hijacked", "sql", "SELECT 1",
                "visibility", "WORKSPACE", "template", false)).andExpect(status().isForbidden());
        deleteAuth(viewer, "/api/v1/dashboards/" + dashId).andExpect(status().isForbidden());
    }
}
