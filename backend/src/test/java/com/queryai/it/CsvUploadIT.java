package com.queryai.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.queryai.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CSV upload end to end: multipart file → typed warehouse table → schema → SQL / AI → list → replace → delete. */
class CsvUploadIT extends IntegrationTestBase {

    static final String PRODUCTS = """
            sku,Product Name,price,launched,in stock,units,contact email
            A-1,Desk lamp,39.00,2026-01-04,true,3,lamps@example.com
            A-2,Notebook,4.50,2025-11-20,false,40,paper@example.com
            A-3,"Headphones, wireless",129,2026-02-10,true,2,audio@example.com
            """;

    @Test
    void uploadedCsvBecomesATypedQueryableTable() throws Exception {
        Session s = register("Uploader");

        JsonNode up = body(uploadCsv(s, s.workspaceId(), "My Products.csv", PRODUCTS, null, false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.table").value("my_products"))
                .andExpect(jsonPath("$.schema").value("uploads_w" + s.workspaceId()))
                .andExpect(jsonPath("$.rowCount").value(3))
                .andExpect(jsonPath("$.replaced").value(false))
                .andExpect(jsonPath("$.dataSourceName").value("Uploaded files")));

        Map<String, String> types = new HashMap<>();
        up.get("columns").forEach(c -> types.put(c.get("name").asText(), c.get("type").asText()));
        assertThat(types).containsEntry("sku", "TEXT")
                .containsEntry("product_name", "TEXT")
                .containsEntry("price", "NUMERIC")
                .containsEntry("launched", "DATE")
                .containsEntry("in_stock", "BOOLEAN")
                .containsEntry("units", "BIGINT")
                .containsEntry("contact_email", "TEXT");
        long dsId = up.get("dataSourceId").asLong();

        // the workspace now has a read-only data source over its upload schema, and the table is in its schema
        getAuth(s, "/api/v1/datasources/" + dsId + "/schema")
                .andExpect(jsonPath("$.tables[*].name", hasItem("my_products")));

        execute(s, dsId, "SELECT product_name, units FROM my_products WHERE in_stock AND launched >= DATE '2026-01-01' ORDER BY price DESC")
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.result.rowCount").value(2))
                .andExpect(jsonPath("$.result.rows[0][0]").value("Headphones, wireless"))
                .andExpect(jsonPath("$.result.rows[0][1]").value(2));
        execute(s, dsId, "SELECT SUM(units) AS total FROM my_products")
                .andExpect(jsonPath("$.result.rows[0][0]").value(45));

        // uploads are read-only like every other source
        execute(s, dsId, "DELETE FROM my_products").andExpect(jsonPath("$.status").value("BLOCKED"));

        // the AI can answer questions about the uploaded file
        mockAi("SELECT product_name, units FROM my_products ORDER BY units DESC LIMIT 5", "Notebooks sell the most.");
        analyze(s, dsId, "Which product sells the most units?")
                .andExpect(jsonPath("$.status").value("ANSWERED"))
                .andExpect(jsonPath("$.result.rows[0][0]").value("Notebook"));

        getAuth(s, "/api/v1/uploads?workspaceId=" + s.workspaceId())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].table").value("my_products"))
                .andExpect(jsonPath("$[0].originalFile").value("My Products.csv"))
                .andExpect(jsonPath("$[0].rowCount").value(3))
                .andExpect(jsonPath("$[0].columnCount").value(7));
    }

    @Test
    void sameNameIsAConflictUnlessReplaceIsRequested() throws Exception {
        Session s = register("Replacer");
        long first = body(uploadCsv(s, s.workspaceId(), "sales.csv", "region,amount\nNorth,10\nSouth,20\n", null, false)
                .andExpect(status().isCreated())).get("id").asLong();

        uploadCsv(s, s.workspaceId(), "sales.csv", "region,amount\nEast,99\n", null, false)
                .andExpect(status().isConflict());

        JsonNode replaced = body(uploadCsv(s, s.workspaceId(), "sales.csv", "region,amount,target\nEast,99,100\n", null, true)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.replaced").value(true))
                .andExpect(jsonPath("$.rowCount").value(1)));
        assertThat(replaced.get("id").asLong()).isEqualTo(first);

        execute(s, replaced.get("dataSourceId").asLong(), "SELECT region, target FROM sales")
                .andExpect(jsonPath("$.result.rowCount").value(1))
                .andExpect(jsonPath("$.result.rows[0][0]").value("East"));

        // an explicit table name lets the same file be loaded twice
        uploadCsv(s, s.workspaceId(), "sales.csv", "region,amount\nWest,5\n", "Sales 2025", false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.table").value("sales_2025"));
        getAuth(s, "/api/v1/uploads?workspaceId=" + s.workspaceId()).andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void deletingAnUploadDropsTheTable() throws Exception {
        Session s = register("Tidy");
        JsonNode up = body(uploadCsv(s, s.workspaceId(), "temp.csv", "k,v\na,1\n", null, false).andExpect(status().isCreated()));
        long id = up.get("id").asLong();
        long dsId = up.get("dataSourceId").asLong();

        deleteAuth(s, "/api/v1/uploads/" + id).andExpect(status().isNoContent());
        getAuth(s, "/api/v1/uploads?workspaceId=" + s.workspaceId()).andExpect(jsonPath("$", empty()));
        getAuth(s, "/api/v1/datasources/" + dsId + "/schema").andExpect(jsonPath("$.tables[*].name", not(hasItem("temp"))));
        execute(s, dsId, "SELECT * FROM temp").andExpect(jsonPath("$.status").value("BLOCKED"));
    }

    @Test
    void rejectsUnsupportedOrEmptyFiles() throws Exception {
        Session s = register("Picky");
        uploadCsv(s, s.workspaceId(), "report.xlsx", "not,a,csv\n1,2,3\n", null, false).andExpect(status().isBadRequest());
        uploadCsv(s, s.workspaceId(), "empty.csv", "", null, false).andExpect(status().isBadRequest());
        uploadCsv(s, s.workspaceId(), "header-only.csv", "a,b,c\n", null, false).andExpect(status().isBadRequest());
    }

    @Test
    void uploadsRespectWorkspaceRoles() throws Exception {
        Session owner = register("Lead");
        Session analyst = register("Analyst");
        Session viewer = register("Viewer");
        invite(owner, analyst, "ANALYST");
        invite(owner, viewer, "VIEWER");

        uploadCsv(viewer, owner.workspaceId(), "nope.csv", "a\n1\n", null, false).andExpect(status().isForbidden());
        JsonNode up = body(uploadCsv(analyst, owner.workspaceId(), "products.csv", PRODUCTS, null, false)
                .andExpect(status().isCreated()));

        // personal data in uploaded files is masked for non-admins like any other source
        execute(analyst, up.get("dataSourceId").asLong(), "SELECT contact_email FROM products ORDER BY sku")
                .andExpect(jsonPath("$.result.maskedColumns", hasItem("contact_email")))
                .andExpect(jsonPath("$.result.rows[0][0]", not("lamps@example.com")));
        execute(owner, up.get("dataSourceId").asLong(), "SELECT contact_email FROM products ORDER BY sku")
                .andExpect(jsonPath("$.result.rows[0][0]").value("lamps@example.com"));

        // only the uploader or a workspace admin may delete it
        Session other = register("Other");
        invite(owner, other, "ANALYST");
        deleteAuth(other, "/api/v1/uploads/" + up.get("id").asLong()).andExpect(status().isForbidden());
        deleteAuth(owner, "/api/v1/uploads/" + up.get("id").asLong()).andExpect(status().isNoContent());
    }
}
