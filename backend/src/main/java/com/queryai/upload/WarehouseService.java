package com.queryai.upload;

import com.queryai.common.ApiException;
import com.queryai.config.QueryAiProperties;
import com.queryai.config.QueryAiProperties.Warehouse;
import com.queryai.upload.ColumnTypeInference.SqlType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Low-level access to the PostgreSQL warehouse: DDL and bulk loading with the admin login, read access granted to the
 * read-only role that QueryAI data sources use. All identifiers are produced by {@link Identifiers} and double-quoted.
 */
@Service
public class WarehouseService {
    private static final Logger log = LoggerFactory.getLogger(WarehouseService.class);
    private static final Pattern URL = Pattern.compile("jdbc:postgresql://([^:/?]+)(?::(\\d+))?/([^?]+).*");
    private static final Pattern ROLE = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,62}");
    private static final int BATCH = 1000;

    private final Warehouse cfg;

    public WarehouseService(QueryAiProperties properties) {
        this.cfg = properties.warehouse();
    }

    public record Location(String host, int port, String database) {
    }

    public record LoadedColumn(String name, String sourceHeader, SqlType type) {
    }

    public boolean enabled() {
        return cfg != null && cfg.enabled();
    }

    public Warehouse config() {
        return cfg;
    }

    public void requireEnabled() {
        if (!enabled()) {
            throw ApiException.unavailable("UPLOADS_DISABLED", "File uploads are not configured on this server "
                    + "(set QUERYAI_WAREHOUSE_ENABLED=true and the warehouse connection settings).");
        }
    }

    /** Where data sources should connect to read the warehouse (parsed from the JDBC URL). */
    public Location location() {
        Matcher m = URL.matcher(cfg.jdbcUrl());
        if (!m.matches()) {
            throw new IllegalStateException("queryai.warehouse.jdbc-url must look like jdbc:postgresql://host:port/database");
        }
        return new Location(m.group(1), m.group(2) == null ? 5432 : Integer.parseInt(m.group(2)), m.group(3));
    }

    public static String workspaceSchema(Long workspaceId) {
        return "uploads_w" + workspaceId;
    }

    public Connection adminConnection() throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", cfg.username());
        props.setProperty("password", cfg.password() == null ? "" : cfg.password());
        props.setProperty("reWriteBatchedInserts", "true");
        props.setProperty("ApplicationName", "QueryAI warehouse loader");
        return DriverManager.getConnection(cfg.jdbcUrl(), props);
    }

    String reader() {
        if (cfg.readerUsername() == null || !ROLE.matcher(cfg.readerUsername()).matches()) {
            throw new IllegalStateException("queryai.warehouse.reader-username must be a plain role name");
        }
        return cfg.readerUsername();
    }

    /** Creates the workspace schema (if needed) and grants the reader role read access to present and future tables. */
    public void ensureSchema(Connection con, String schema) throws SQLException {
        requireSafe(schema);
        try (Statement st = con.createStatement()) {
            st.execute("CREATE SCHEMA IF NOT EXISTS " + q(schema));
            grantRead(st, schema);
        }
    }

    void grantRead(Statement st, String schema) throws SQLException {
        String reader = q(reader());
        st.execute("GRANT USAGE ON SCHEMA " + q(schema) + " TO " + reader);
        st.execute("GRANT SELECT ON ALL TABLES IN SCHEMA " + q(schema) + " TO " + reader);
        st.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA " + q(schema) + " GRANT SELECT ON TABLES TO " + reader);
    }

    public boolean tableExists(Connection con, String schema, String table) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT 1 FROM information_schema.tables WHERE table_schema = ? AND table_name = ?")) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Creates {@code schema.table} and loads all rows in one transaction. Returns the number of rows written.
     * When {@code replace} is set an existing table of the same name is dropped first (inside the same transaction).
     */
    public long createAndLoad(String schema, String table, List<LoadedColumn> columns, List<String[]> rows, String comment,
                              boolean replace) {
        requireSafe(schema);
        requireSafe(table);
        columns.forEach(c -> requireSafe(c.name()));
        try (Connection con = adminConnection()) {
            con.setAutoCommit(false);
            try {
                ensureSchema(con, schema);
                if (tableExists(con, schema, table)) {
                    if (!replace) {
                        throw ApiException.conflict("A table named '" + table + "' already exists. Choose another name or replace it.");
                    }
                    try (Statement st = con.createStatement()) {
                        st.execute("DROP TABLE " + q(schema) + "." + q(table));
                    }
                }
                StringJoiner ddl = new StringJoiner(",\n  ", "CREATE TABLE " + q(schema) + "." + q(table) + " (\n  ", "\n)");
                for (LoadedColumn c : columns) {
                    ddl.add(q(c.name()) + " " + c.type().ddl());
                }
                try (Statement st = con.createStatement()) {
                    st.execute(ddl.toString());
                    st.execute("COMMENT ON TABLE " + q(schema) + "." + q(table) + " IS " + literal(comment));
                    for (LoadedColumn c : columns) {
                        if (!c.name().equals(c.sourceHeader())) {
                            st.execute("COMMENT ON COLUMN " + q(schema) + "." + q(table) + "." + q(c.name())
                                    + " IS " + literal("Original header: " + c.sourceHeader()));
                        }
                    }
                }
                long written = insert(con, schema, table, columns, rows);
                try (Statement st = con.createStatement()) {
                    st.execute("GRANT SELECT ON " + q(schema) + "." + q(table) + " TO " + q(reader()));
                }
                con.commit();
                try (Statement st = con.createStatement()) {
                    st.execute("ANALYZE " + q(schema) + "." + q(table)); // fresh row estimates for the schema explorer
                } catch (SQLException e) {
                    log.debug("ANALYZE failed for {}.{}: {}", schema, table, e.getMessage());
                }
                return written;
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        } catch (ApiException e) {
            throw e;
        } catch (SQLException e) {
            throw ApiException.badGateway("WAREHOUSE_ERROR", "Could not load the file into the warehouse: " + e.getMessage(), e);
        }
    }

    private long insert(Connection con, String schema, String table, List<LoadedColumn> columns, List<String[]> rows)
            throws SQLException {
        StringJoiner names = new StringJoiner(", ");
        StringJoiner marks = new StringJoiner(", ");
        columns.forEach(c -> {
            names.add(q(c.name()));
            marks.add("?");
        });
        String sql = "INSERT INTO " + q(schema) + "." + q(table) + " (" + names + ") VALUES (" + marks + ")";
        long count = 0;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            int pending = 0;
            for (int r = 0; r < rows.size(); r++) {
                String[] row = rows.get(r);
                for (int i = 0; i < columns.size(); i++) {
                    LoadedColumn c = columns.get(i);
                    Object value;
                    try {
                        value = ColumnTypeInference.convert(i < row.length ? row[i] : null, c.type());
                    } catch (RuntimeException e) {
                        throw ApiException.badRequest("Row " + (r + 2) + ", column '" + c.sourceHeader() + "': cannot read '"
                                + (i < row.length ? row[i] : "") + "' as " + c.type().ddl().toLowerCase(Locale.ROOT));
                    }
                    bind(ps, i + 1, value, c.type());
                }
                ps.addBatch();
                pending++;
                count++;
                if (pending == BATCH) {
                    ps.executeBatch();
                    pending = 0;
                }
            }
            if (pending > 0) {
                ps.executeBatch();
            }
        }
        return count;
    }

    private static void bind(PreparedStatement ps, int index, Object value, SqlType type) throws SQLException {
        if (value == null) {
            ps.setNull(index, switch (type) {
                case BOOLEAN -> Types.BOOLEAN;
                case BIGINT -> Types.BIGINT;
                case NUMERIC -> Types.NUMERIC;
                case DATE -> Types.DATE;
                case TIMESTAMP -> Types.TIMESTAMP;
                case TEXT -> Types.VARCHAR;
            });
            return;
        }
        if (value instanceof LocalDate d) {
            ps.setObject(index, d);
        } else if (value instanceof LocalDateTime t) {
            ps.setObject(index, t);
        } else {
            ps.setObject(index, value);
        }
    }

    public void dropTable(String schema, String table) {
        requireSafe(schema);
        requireSafe(table);
        try (Connection con = adminConnection(); Statement st = con.createStatement()) {
            st.execute("DROP TABLE IF EXISTS " + q(schema) + "." + q(table));
        } catch (SQLException e) {
            throw ApiException.badGateway("WAREHOUSE_ERROR", "Could not delete the table: " + e.getMessage(), e);
        }
    }

    static void requireSafe(String identifier) {
        if (!Identifiers.isSafe(identifier)) {
            throw ApiException.badRequest("Invalid identifier '" + identifier + "'");
        }
    }

    static String q(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    static String literal(String text) {
        return "'" + (text == null ? "" : text.replace("'", "''")) + "'";
    }
}
