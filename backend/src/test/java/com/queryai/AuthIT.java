package com.queryai;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIT extends IntegrationTestBase {

    @Test
    void registerLoginAndAccessProtectedEndpoints() throws Exception {
        Session s = register("Ada");

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", s.email(), "password", "Passw0rd!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(emptyString())))
                .andExpect(jsonPath("$.user.email").value(s.email()));

        getAuth(s, "/api/v1/auth/me").andExpect(status().isOk()).andExpect(jsonPath("$.email").value(s.email()));
        getAuth(s, "/api/v1/workspaces").andExpect(status().isOk())
                .andExpect(jsonPath("$[0].myRole").value("OWNER"));
    }

    @Test
    void rejectsBadCredentialsDuplicatesWeakPasswordsAndMissingTokens() throws Exception {
        Session s = register("Grace");
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", s.email(), "password", "wrong-pass1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", s.email(), "password", "Passw0rd!", "fullName", "Dup"))))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "weak@example.com", "password", "short", "fullName", "Weak"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password", containsString("8-72")));

        mvc.perform(get("/api/v1/workspaces")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/workspaces").header("Authorization", "Bearer not-a-jwt")).andExpect(status().isUnauthorized());
    }

    @Test
    void nonAdminsCannotReachAdministration() throws Exception {
        register("Bootstrap"); // ensures at least one earlier account exists
        Session regular = register("Linus");
        getAuth(regular, "/api/v1/admin/stats").andExpect(status().isForbidden());
    }

    @Test
    void publicSystemInfoAndOpenApiAreAvailable() throws Exception {
        mvc.perform(get("/api/v1/system/info")).andExpect(status().isOk())
                .andExpect(jsonPath("$.supportedDatabases", hasItems("POSTGRESQL", "MYSQL")));
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/analytics/analyze']").exists());
    }

    @Test
    void guestAccessIsOffUnlessThePublicWorkspaceIsEnabled() throws Exception {
        mvc.perform(post("/api/v1/auth/guest")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/system/info")).andExpect(status().isOk()).andExpect(jsonPath("$.guestAccess").value(false));
    }
}
