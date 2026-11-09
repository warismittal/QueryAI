package com.queryai.it;

import com.queryai.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tenancy: a user who is not a member of a workspace can neither see nor use anything in it. Resources are reported
 * as "not found" (404), never "forbidden", so their existence does not leak.
 */
class WorkspaceIsolationIT extends IntegrationTestBase {

    record Owned(long dataSource, long run, long saved, long dashboard, long widget, long conversation, long upload) {
    }

    private Owned seed(Session ada) throws Exception {
        long ds = createDataSource(ada, "Ada's shop");
        long run = body(execute(ada, ds, "SELECT COUNT(*) AS n FROM orders")).get("queryRunId").asLong();
        long saved = body(postJson(ada, "/api/v1/saved-queries", Map.of("workspaceId", ada.workspaceId(), "dataSourceId", ds,
                "name", "Private numbers", "sql", "SELECT COUNT(*) AS n FROM orders", "visibility", "WORKSPACE", "template", false))
                .andExpect(status().isCreated())).get("id").asLong();
        long dash = body(postJson(ada, "/api/v1/dashboards", Map.of("workspaceId", ada.workspaceId(), "name", "Board", "visibility", "WORKSPACE"))
                .andExpect(status().isCreated())).get("id").asLong();
        long widget = body(postJson(ada, "/api/v1/dashboards/" + dash + "/widgets",
                Map.of("savedQueryId", saved, "title", "Orders", "chartType", "METRIC"))).get("widgets").get(0).get("id").asLong();
        mockAi(AiAnalysisIT.SPEND_SQL, "Grace spent the most.");
        long conversation = body(analyze(ada, ds, "Who spends the most?").andExpect(status().isOk())).get("conversationId").asLong();
        long upload = body(uploadCsv(ada, ada.workspaceId(), "secrets.csv", "code,amount\nA,1\nB,2\n", null, false)
                .andExpect(status().isCreated())).get("id").asLong();
        return new Owned(ds, run, saved, dash, widget, conversation, upload);
    }

    @Test
    void anOutsiderCannotReadAnythingInAnotherWorkspace() throws Exception {
        Session ada = register("Ada");
        Owned o = seed(ada);
        Session mallory = register("Mallory");

        for (String url : List.of(
                "/api/v1/workspaces/" + ada.workspaceId(),
                "/api/v1/workspaces/" + ada.workspaceId() + "/members",
                "/api/v1/workspaces/" + ada.workspaceId() + "/usage",
                "/api/v1/datasources/" + o.dataSource(),
                "/api/v1/datasources/" + o.dataSource() + "/schema",
                "/api/v1/datasources/" + o.dataSource() + "/schema/search?q=orders",
                "/api/v1/datasources/" + o.dataSource() + "/pii",
                "/api/v1/datasources/" + o.dataSource() + "/tables/customers/preview",
                "/api/v1/history/" + o.run(),
                "/api/v1/saved-queries/" + o.saved(),
                "/api/v1/saved-queries/" + o.saved() + "/versions",
                "/api/v1/dashboards/" + o.dashboard(),
                "/api/v1/conversations/" + o.conversation(),
                "/api/v1/datasources?workspaceId=" + ada.workspaceId(),
                "/api/v1/saved-queries?workspaceId=" + ada.workspaceId(),
                "/api/v1/dashboards?workspaceId=" + ada.workspaceId(),
                "/api/v1/conversations?workspaceId=" + ada.workspaceId(),
                "/api/v1/history?workspaceId=" + ada.workspaceId(),
                "/api/v1/uploads?workspaceId=" + ada.workspaceId(),
                "/api/v1/audit?workspaceId=" + ada.workspaceId(),
                "/api/v1/analytics/overview?workspaceId=" + ada.workspaceId())) {
            getAuth(mallory, url).andExpect(status().isNotFound());
        }
    }

    @Test
    void anOutsiderCannotUseOrChangeAnythingInAnotherWorkspace() throws Exception {
        Session ada = register("Ada");
        Owned o = seed(ada);
        Session mallory = register("Mallory");
        mockAi(AiAnalysisIT.SPEND_SQL, "Grace spent the most.");

        execute(mallory, o.dataSource(), "SELECT 1").andExpect(status().isNotFound());
        analyze(mallory, o.dataSource(), "Who spends the most?").andExpect(status().isNotFound());
        postJson(mallory, "/api/v1/history/" + o.run() + "/rerun", Map.of()).andExpect(status().isNotFound());
        postJson(mallory, "/api/v1/history/" + o.run() + "/feedback", Map.of("rating", "DOWN")).andExpect(status().isNotFound());
        postJson(mallory, "/api/v1/saved-queries/" + o.saved() + "/run", Map.of()).andExpect(status().isNotFound());
        postJson(mallory, "/api/v1/dashboards/" + o.dashboard() + "/widgets/" + o.widget() + "/data", Map.of())
                .andExpect(status().isNotFound());
        putJson(mallory, "/api/v1/saved-queries/" + o.saved(), Map.of("name", "Mine now", "sql", "SELECT 1",
                "visibility", "WORKSPACE", "template", false)).andExpect(status().isNotFound());
        deleteAuth(mallory, "/api/v1/datasources/" + o.dataSource()).andExpect(status().isNotFound());
        deleteAuth(mallory, "/api/v1/dashboards/" + o.dashboard()).andExpect(status().isNotFound());
        deleteAuth(mallory, "/api/v1/uploads/" + o.upload()).andExpect(status().isNotFound());
        uploadCsv(mallory, ada.workspaceId(), "x.csv", "a\n1\n", null, false).andExpect(status().isNotFound());
        postJson(mallory, "/api/v1/workspaces/" + ada.workspaceId() + "/members", Map.of("email", mallory.email(), "role", "ADMIN"))
                .andExpect(status().isNotFound());
        // creating resources that point into a foreign workspace / data source is refused too
        postJson(mallory, "/api/v1/datasources", dataSourceRequest(ada.workspaceId(), "Sneaky")).andExpect(status().isNotFound());
        postJson(mallory, "/api/v1/saved-queries", Map.of("workspaceId", mallory.workspaceId(), "dataSourceId", o.dataSource(),
                "name", "Cross-tenant", "sql", "SELECT 1", "template", false)).andExpect(status().isNotFound());

        // Ada's data is untouched
        getAuth(ada, "/api/v1/saved-queries/" + o.saved()).andExpect(jsonPath("$.name").value("Private numbers"));
        getAuth(ada, "/api/v1/uploads?workspaceId=" + ada.workspaceId()).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void listingsOnlyEverContainTheCallersOwnWorkspace() throws Exception {
        Session ada = register("Ada");
        seed(ada);
        Session mallory = register("Mallory");
        createDataSource(mallory, "Mallory's shop");

        getAuth(mallory, "/api/v1/workspaces").andExpect(jsonPath("$[*].id", not(hasItem((int) ada.workspaceId()))));
        getAuth(mallory, "/api/v1/datasources?workspaceId=" + mallory.workspaceId())
                .andExpect(jsonPath("$[*].name", contains("Mallory's shop")));
        getAuth(mallory, "/api/v1/history?workspaceId=" + mallory.workspaceId()).andExpect(jsonPath("$.totalElements").value(0));
        getAuth(mallory, "/api/v1/uploads?workspaceId=" + mallory.workspaceId()).andExpect(jsonPath("$", empty()));

        // uploads live in a per-workspace warehouse schema: Mallory's upload source cannot reach Ada's tables
        long malloryUploads = body(uploadCsv(mallory, mallory.workspaceId(), "mine.csv", "a\n1\n", null, false)
                .andExpect(status().isCreated())).get("dataSourceId").asLong();
        execute(mallory, malloryUploads, "SELECT * FROM uploads_w" + ada.workspaceId() + ".secrets")
                .andExpect(jsonPath("$.status").value("BLOCKED"));
    }

    @Test
    void removingAMemberRevokesAccessImmediately() throws Exception {
        Session ada = register("Ada");
        long ds = createDataSource(ada, "Shop");
        Session bob = register("Bob");
        invite(ada, bob, "ANALYST");
        execute(bob, ds, "SELECT 1 AS one").andExpect(jsonPath("$.status").value("SUCCESS"));

        deleteAuth(ada, "/api/v1/workspaces/" + ada.workspaceId() + "/members/" + bob.userId()).andExpect(status().is2xxSuccessful());
        execute(bob, ds, "SELECT 1 AS one").andExpect(status().isNotFound());
    }
}
