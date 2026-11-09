package com.queryai.datasource;

import com.queryai.datasource.DatabaseType.SslMode;

/** Fully resolved (decrypted) connection parameters. Never serialised or logged. */
public record ConnectionSettings(DatabaseType type, String host, int port, String database, String schema,
                                 String username, String password, SslMode sslMode) {

    public String jdbcUrl(int connectTimeoutSeconds) {
        return type.jdbcUrl(host, port, database, schema, sslMode, connectTimeoutSeconds);
    }

    public String effectiveSchema() {
        if (type == DatabaseType.MYSQL) {
            return database;
        }
        return schema == null || schema.isBlank() ? "public" : schema;
    }

    @Override
    public String toString() {
        return "ConnectionSettings[" + type + " " + username + "@" + host + ":" + port + "/" + database + "]";
    }
}
