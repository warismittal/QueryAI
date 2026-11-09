package com.queryai.datasource;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Supported target database engines and their JDBC conventions. */
public enum DatabaseType {
    POSTGRESQL("PostgreSQL", "org.postgresql.Driver", 5432),
    MYSQL("MySQL", "com.mysql.cj.jdbc.Driver", 3306);

    private final String displayName;
    private final String driverClass;
    private final int defaultPort;

    DatabaseType(String displayName, String driverClass, int defaultPort) {
        this.displayName = displayName;
        this.driverClass = driverClass;
        this.defaultPort = defaultPort;
    }

    public String displayName() {
        return displayName;
    }

    public String driverClass() {
        return driverClass;
    }

    public int defaultPort() {
        return defaultPort;
    }

    public String jdbcUrl(String host, int port, String database, String schema, SslMode sslMode, int connectTimeoutSeconds) {
        return switch (this) {
            case POSTGRESQL -> "jdbc:postgresql://" + host + ":" + port + "/" + database
                    + "?sslmode=" + sslMode.postgresValue()
                    + (schema == null || schema.isBlank() ? ""
                    : "&currentSchema=" + URLEncoder.encode(schema, StandardCharsets.UTF_8))
                    + "&connectTimeout=" + connectTimeoutSeconds
                    + "&ApplicationName=QueryAI";
            case MYSQL -> "jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?sslMode=" + sslMode.mysqlValue()
                    + "&connectTimeout=" + (connectTimeoutSeconds * 1000)
                    + "&allowPublicKeyRetrieval=" + (sslMode == SslMode.DISABLE)
                    + "&allowMultiQueries=false&allowLoadLocalInfile=false&useCursorFetch=true";
        };
    }

    /** Quotes an identifier for this dialect, escaping embedded quote characters. */
    public String quoteIdentifier(String identifier) {
        return switch (this) {
            case POSTGRESQL -> "\"" + identifier.replace("\"", "\"\"") + "\"";
            case MYSQL -> "`" + identifier.replace("`", "``") + "`";
        };
    }

    public enum SslMode {
        DISABLE("disable", "DISABLED"), PREFER("prefer", "PREFERRED"), REQUIRE("require", "REQUIRED");

        private final String pg;
        private final String my;

        SslMode(String pg, String my) {
            this.pg = pg;
            this.my = my;
        }

        public String postgresValue() {
            return pg;
        }

        public String mysqlValue() {
            return my;
        }
    }
}
