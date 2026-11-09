package com.queryai.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.queryai.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Connecting a PostgreSQL target, discovering its schema and searching it semantically (pgvector). */
class ConnectionAndSchemaIT extends IntegrationTestBase {

    @Test
    void connectionDiagnosticsRunBeforeSaving() throws Exception {
        Session s = register("Connector");
        postJson(s, "/api/v1/datasources/test", dataSourceRequest(s.workspaceId(), "ignored"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.tableCount").value(2));

        Map<String, Object> wrongPassword = dataSourceRequest(s.workspaceId(), "ignored");
        wrongPassword.put("password", "definitely-wrong");
        postJson(s, "/api/v1/datasources/test", wrongPassword)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void createdDataSourceNeverEchoesItsPassword() throws Exception {
        Session s = register("Keeper");
        JsonNode ds = body(postJson(s, "/api/v1/datasources", dataSourceRequest(s.workspaceId(), "Shop"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hasPassword").value(true))
                .andExpect(jsonPath("$.password").doesNotExist()));
        long id = ds.get("id").asLong();

        getAuth(s, "/api/v1/datasources/" + id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Shop"))
                .andExpect(jsonPath("$.password").doesNotExist());
        getAuth(s, "/api/v1/datasources?workspaceId=" + s.workspaceId())
                .andExpect(jsonPath("$[*].name", hasItem("Shop")));
    }

    @Test
    void schemaDiscoveryFindsTablesColumnsKeysAndRelationships() throws Exception {
        Session s = register("Explorer");
        long dsId = createDataSource(s, "Shop");

        getAuth(s, "/api/v1/datasources/" + dsId + "/schema")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableCount").value(2))
                .andExpect(jsonPath("$.tables[*].name", containsInAnyOrder("customers", "orders")))
                .andExpect(jsonPath("$.tables[?(@.name == 'customers')].description", hasItem("Registered customers")))
                .andExpect(jsonPath("$.tables[?(@.name == 'customers')].columns[*].name",
                        containsInAnyOrder("id", "name", "email", "segment")))
                .andExpect(jsonPath("$.relationships[0].fromTable").value("orders"))
                .andExpect(jsonPath("$.relationships[0].toTable").value("customers"));

        postJson(s, "/api/v1/datasources/" + dsId + "/schema/refresh", Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableCount").value(2));
    }

    @Test
    void semanticSchemaSearchRunsOnPgvector() throws Exception {
        Session s = register("Seeker");
        long dsId = createDataSource(s, "Shop");

        JsonNode search = body(getAuth(s, "/api/v1/datasources/" + dsId + "/schema/search?q=purchases and their delivery status")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.engine").value("pgvector"))
                .andExpect(jsonPath("$.matches", not(empty()))));
        assertThat(search.get("matches").get(0).get("table").asText()).isEqualTo("orders");

        // the same index answers a differently-worded question about the other table
        getAuth(s, "/api/v1/datasources/" + dsId + "/schema/search?q=registered customer email")
                .andExpect(jsonPath("$.matches[0].table").value("customers"));
    }

    @Test
    void dataSourcesCanBeDeleted() throws Exception {
        Session s = register("Cleaner");
        long dsId = createDataSource(s, "Temporary");
        deleteAuth(s, "/api/v1/datasources/" + dsId).andExpect(status().isNoContent());
        getAuth(s, "/api/v1/datasources/" + dsId).andExpect(status().isNotFound());
    }
}
