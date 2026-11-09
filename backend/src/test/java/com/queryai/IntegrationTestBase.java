package com.queryai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.queryai.ai.AiDtos.IntentAnalysis;
import com.queryai.ai.AiDtos.IntentType;
import com.queryai.ai.AiDtos.InterpretationOutput;
import com.queryai.ai.AiDtos.SqlGenerationOutput;
import com.queryai.ai.LlmClient;
import com.queryai.ai.LlmClient.LlmResponse;
import com.queryai.user.SystemRole;
import com.queryai.user.User;
import com.queryai.user.UserRepository;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the full application against real databases (Testcontainers):
 * <ul>
 *   <li>PostgreSQL with pgvector — QueryAI's own tables (public schema, via Flyway), a small target business schema
 *       ("demo") and the CSV-upload warehouse (one schema per workspace, read through a read-only role);</li>
 *   <li>MySQL 8 — a second target database, so both dialects are exercised end to end.</li>
 * </ul>
 * Only the LLM is mocked. All subclasses share one Spring context and one pair of containers, so every test registers
 * its own users (and therefore its own workspaces) and never depends on state from another test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("integration")
public abstract class IntegrationTestBase {

    protected static final String PASSWORD = "Passw0rd!";

    /** pgvector image so semantic schema search runs on the real {@code vector} extension, as in production. */
    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withInitScript("it/demo-schema.sql");

    protected static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withInitScript("it/mysql-schema.sql");

    static {
        Startables.deepStart(POSTGRES, MYSQL).join();
    }

    @DynamicPropertySource
    static void warehouse(DynamicPropertyRegistry r) {
        r.add("queryai.warehouse.enabled", () -> "true");
        r.add("queryai.warehouse.jdbc-url", POSTGRES::getJdbcUrl);
        r.add("queryai.warehouse.username", POSTGRES::getUsername);
        r.add("queryai.warehouse.password", POSTGRES::getPassword);
        r.add("queryai.warehouse.reader-username", () -> "queryai_it_reader");
        r.add("queryai.warehouse.reader-password", () -> "it_reader_pass");
        r.add("queryai.warehouse.bootstrap-demo", () -> "false");
        r.add("queryai.embeddings.provider", () -> "local");
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected UserRepository users;

    @MockitoBean
    protected LlmClient llm;

    protected record Session(String token, long userId, long workspaceId, String email) {
    }

    // ---------------------------------------------------------------- users & workspaces

    /** Registers a fresh user (with their own personal workspace) as a regular, non-admin platform user. */
    protected Session register(String name) throws Exception {
        String email = name.toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        JsonNode auth = body(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD, "fullName", name))))
                .andExpect(status().isCreated()));
        String token = auth.get("token").asText();
        long userId = auth.get("user").get("id").asLong();
        if (!"USER".equals(auth.get("user").path("systemRole").asText())) {
            // the very first account on a fresh database is made a platform admin; tests opt into that explicitly instead
            User u = users.findById(userId).orElseThrow();
            u.setSystemRole(SystemRole.USER);
            users.save(u);
            token = body(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                    .andExpect(status().isOk())).get("token").asText();
        }
        JsonNode workspaces = body(mvc.perform(get("/api/v1/workspaces").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()));
        return new Session(token, userId, workspaces.get(0).get("id").asLong(), email);
    }

    /** Logs in again (fresh token, e.g. after a role change, since roles are carried in the JWT). */
    protected Session login(Session s) throws Exception {
        JsonNode auth = body(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", s.email(), "password", PASSWORD))))
                .andExpect(status().isOk()));
        return new Session(auth.get("token").asText(), s.userId(), s.workspaceId(), s.email());
    }

    /** Makes the user a platform administrator and returns a session whose token carries the new role. */
    protected Session promoteToPlatformAdmin(Session s) throws Exception {
        User u = users.findById(s.userId()).orElseThrow();
        u.setSystemRole(SystemRole.ADMIN);
        users.save(u);
        return login(s);
    }

    /** Adds {@code member} to {@code owner}'s workspace with the given role. The member's session keeps their own workspace id. */
    protected void invite(Session owner, Session member, String role) throws Exception {
        postJson(owner, "/api/v1/workspaces/" + owner.workspaceId() + "/members", Map.of("email", member.email(), "role", role))
                .andExpect(status().isCreated());
    }

    // ---------------------------------------------------------------- data sources

    protected Map<String, Object> dataSourceRequest(long workspaceId, String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("workspaceId", workspaceId);
        m.put("name", name);
        m.put("dbType", "POSTGRESQL");
        m.put("host", POSTGRES.getHost());
        m.put("port", POSTGRES.getFirstMappedPort());
        m.put("databaseName", POSTGRES.getDatabaseName());
        m.put("schemaName", "demo");
        m.put("username", POSTGRES.getUsername());
        m.put("password", POSTGRES.getPassword());
        m.put("sslMode", "DISABLE");
        return m;
    }

    protected Map<String, Object> mysqlDataSourceRequest(long workspaceId, String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("workspaceId", workspaceId);
        m.put("name", name);
        m.put("dbType", "MYSQL");
        m.put("host", MYSQL.getHost());
        m.put("port", MYSQL.getFirstMappedPort());
        m.put("databaseName", MYSQL.getDatabaseName());
        m.put("username", MYSQL.getUsername());
        m.put("password", MYSQL.getPassword());
        m.put("sslMode", "DISABLE");
        return m;
    }

    /** Creates the PostgreSQL "demo" data source in the session's own workspace and returns its id. */
    protected long createDataSource(Session s, String name) throws Exception {
        return body(postJson(s, "/api/v1/datasources", dataSourceRequest(s.workspaceId(), name))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    protected long createMySqlDataSource(Session s, String name) throws Exception {
        return body(postJson(s, "/api/v1/datasources", mysqlDataSourceRequest(s.workspaceId(), name))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    protected ResultActions execute(Session s, long dataSourceId, String sql) throws Exception {
        return postJson(s, "/api/v1/sql/execute", Map.of("dataSourceId", dataSourceId, "sql", sql));
    }

    // ---------------------------------------------------------------- AI

    /** Configures the mocked LLM to answer any question with {@code sql} (which is then validated and executed for real). */
    protected void mockAi(String sql, String summary) {
        when(llm.isConfigured()).thenReturn(true);
        when(llm.description()).thenReturn("mock · test");
        when(llm.structured(anyString(), anyString(), eq(IntentAnalysis.class))).thenReturn(new LlmResponse<>(
                new IntentAnalysis(IntentType.RANKING, "Customers ranked by spending", List.of("spending"), List.of("customer"),
                        List.of(), null, List.of("customer", "order"), false, null), "mock", 1));
        when(llm.structured(anyString(), anyString(), eq(SqlGenerationOutput.class))).thenReturn(new LlmResponse<>(
                new SqlGenerationOutput(sql, "Generated by the test double", List.of(), false, null, 0.9), "mock", 1));
        when(llm.structured(anyString(), anyString(), eq(InterpretationOutput.class))).thenReturn(new LlmResponse<>(
                new InterpretationOutput(summary, List.of(), List.of("What changed since last year?")), "mock", 1));
    }

    protected ResultActions analyze(Session s, long dataSourceId, String question) throws Exception {
        return postJson(s, "/api/v1/analytics/analyze", Map.of("dataSourceId", dataSourceId, "question", question));
    }

    // ---------------------------------------------------------------- HTTP helpers

    protected ResultActions postJson(Session s, String url, Object payload) throws Exception {
        return mvc.perform(post(url).header("Authorization", "Bearer " + s.token())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)));
    }

    protected ResultActions putJson(Session s, String url, Object payload) throws Exception {
        return mvc.perform(put(url).header("Authorization", "Bearer " + s.token())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)));
    }

    protected ResultActions getAuth(Session s, String url) throws Exception {
        return mvc.perform(get(url).header("Authorization", "Bearer " + s.token()));
    }

    protected ResultActions deleteAuth(Session s, String url) throws Exception {
        return mvc.perform(delete(url).header("Authorization", "Bearer " + s.token()));
    }

    protected ResultActions uploadCsv(Session s, long workspaceId, String fileName, String csv, String tableName,
                                      boolean replace) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", fileName, "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        var request = multipart("/api/v1/uploads").file(file)
                .param("workspaceId", String.valueOf(workspaceId))
                .param("replace", String.valueOf(replace))
                .header("Authorization", "Bearer " + s.token());
        if (tableName != null) {
            request.param("tableName", tableName);
        }
        return mvc.perform(request);
    }

    protected JsonNode body(ResultActions actions) throws Exception {
        return json.readTree(actions.andReturn().getResponse().getContentAsString());
    }
}
