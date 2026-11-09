package com.queryai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Strongly typed application settings bound from the {@code queryai.*} namespace. */
@ConfigurationProperties(prefix = "queryai")
public record QueryAiProperties(Security security, DataSourceSettings datasource, Ai ai, Sql sql, Demo demo,
                                Embeddings embeddings, Warehouse warehouse) {

    public record Security(String jwtSecret, long jwtTtlMinutes, String credentialKey, List<String> corsAllowedOrigins,
                           int authRateLimitPerMinute, int aiRateLimitPerMinute) {
    }

    public record DataSourceSettings(List<String> blockedHosts, int defaultMaxRows, int hardMaxRows,
                                     int connectTimeoutSeconds, int poolSize) {
    }

    public record Ai(String provider, boolean sendResultSamples, int maxSchemaTables, int maxConversationTurns,
                     int repairAttempts) {
    }

    public record Sql(double warnCost, double maxCost) {
    }

    /**
     * {@code publicMode}: for internet-facing demos. The demo account becomes a regular user with the ANALYST role in a
     * workspace owned by a separate administrator, so visitors can explore and query but not reconfigure the demo
     * (and they see personal data masked, which showcases PII protection).
     */
    public record Demo(boolean enabled, String email, String password, boolean publicMode, Target postgres, Target mysql) {
    }

    public record Target(boolean enabled, String host, int port, String database, String schema, String username,
                         String password) {
    }

    /**
     * Embedding model used for semantic schema search. {@code provider}: {@code local} (built-in, no network),
     * {@code openai} (any OpenAI-compatible /embeddings endpoint: OpenAI, Ollama, Gemini, Jina ...) or {@code none}.
     */
    public record Embeddings(String provider, String url, String apiKey, String model, int timeoutSeconds) {
    }

    /**
     * The PostgreSQL "warehouse" that receives uploaded CSV files (and, when {@code bootstrapDemo} is set, the demo
     * e-commerce dataset). QueryAI writes with the admin login and lets users query through the read-only role.
     */
    public record Warehouse(boolean enabled, String jdbcUrl, String username, String password, String readerUsername,
                            String readerPassword, boolean bootstrapDemo, String demoSchema, int maxUploadRows,
                            long maxUploadBytes) {
    }
}
