package com.queryai.schema;

import com.queryai.datasource.DataSourceEntity;
import com.queryai.datasource.DatabaseType;
import com.queryai.schema.SchemaModel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

/** Reads tables, columns, keys, indexes, comments and row estimates through JDBC metadata. */
@Component
public class SchemaIntrospector {
    private static final Logger log = LoggerFactory.getLogger(SchemaIntrospector.class);
    private static final int MAX_TABLES = 500;

    public SchemaSnapshot introspect(DataSource dataSource, DataSourceEntity ds) throws SQLException {
        boolean mysql = ds.getDbType() == DatabaseType.MYSQL;
        String catalog = mysql ? ds.getDatabaseName() : null;
        String schema = mysql ? null : ds.effectiveSchema();

        try (Connection connection = dataSource.getConnection()) {
            try {
                return introspect(connection, ds, catalog, schema);
            } finally {
                if (!connection.getAutoCommit()) {
                    connection.rollback(); // metadata queries can open an implicit transaction; never hand it back open
                }
            }
        }
    }

    private SchemaSnapshot introspect(Connection connection, DataSourceEntity ds, String catalog, String schema) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();

        Map<String, String[]> tables = new LinkedHashMap<>(); // name -> {type, remarks}
        try (ResultSet rs = meta.getTables(catalog, schema, "%", new String[]{"TABLE", "VIEW", "MATERIALIZED VIEW", "PARTITIONED TABLE"})) {
            while (rs.next() && tables.size() < MAX_TABLES) {
                tables.put(rs.getString("TABLE_NAME"), new String[]{rs.getString("TABLE_TYPE"), emptyToNull(rs.getString("REMARKS"))});
            }
        }

        Map<String, List<ColumnMeta>> columns = new HashMap<>();
        try (ResultSet rs = meta.getColumns(catalog, schema, "%", "%")) {
            while (rs.next()) {
                String table = rs.getString("TABLE_NAME");
                if (!tables.containsKey(table)) {
                    continue;
                }
                columns.computeIfAbsent(table, k -> new ArrayList<>()).add(new ColumnMeta(
                        rs.getString("COLUMN_NAME"),
                        formatType(rs.getString("TYPE_NAME"), rs.getInt("COLUMN_SIZE"), rs.getInt("DECIMAL_DIGITS")),
                        rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls,
                        emptyToNull(rs.getString("COLUMN_DEF")),
                        emptyToNull(rs.getString("REMARKS")),
                        rs.getInt("ORDINAL_POSITION")));
            }
        }

        Map<String, Long> estimates = rowEstimates(connection, ds);
        List<TableMeta> result = new ArrayList<>();
        for (Map.Entry<String, String[]> entry : tables.entrySet()) {
            String table = entry.getKey();
            List<ColumnMeta> cols = columns.getOrDefault(table, List.of()).stream()
                    .sorted(Comparator.comparingInt(ColumnMeta::ordinal)).toList();
            result.add(new TableMeta(table, normalizeType(entry.getValue()[0]), entry.getValue()[1], estimates.get(table),
                    cols, primaryKey(meta, catalog, schema, table), foreignKeys(meta, catalog, schema, table),
                    indexes(meta, catalog, schema, table)));
        }
        return new SchemaSnapshot(ds.getDbType().name(), ds.effectiveSchema(), Instant.now(), result);
    }

    private List<String> primaryKey(DatabaseMetaData meta, String catalog, String schema, String table) throws SQLException {
        TreeMap<Integer, String> cols = new TreeMap<>();
        try (ResultSet rs = meta.getPrimaryKeys(catalog, schema, table)) {
            while (rs.next()) {
                cols.put(rs.getInt("KEY_SEQ"), rs.getString("COLUMN_NAME"));
            }
        }
        return new ArrayList<>(cols.values());
    }

    private List<ForeignKeyMeta> foreignKeys(DatabaseMetaData meta, String catalog, String schema, String table) throws SQLException {
        Map<String, List<String[]>> byName = new LinkedHashMap<>();
        Map<String, String> target = new HashMap<>();
        try (ResultSet rs = meta.getImportedKeys(catalog, schema, table)) {
            while (rs.next()) {
                String name = Optional.ofNullable(rs.getString("FK_NAME")).orElse(table + "_fk_" + rs.getString("PKTABLE_NAME"));
                byName.computeIfAbsent(name, k -> new ArrayList<>())
                        .add(new String[]{String.valueOf(rs.getInt("KEY_SEQ")), rs.getString("FKCOLUMN_NAME"), rs.getString("PKCOLUMN_NAME")});
                target.put(name, rs.getString("PKTABLE_NAME"));
            }
        }
        List<ForeignKeyMeta> fks = new ArrayList<>();
        byName.forEach((name, parts) -> {
            parts.sort(Comparator.comparingInt(p -> Integer.parseInt(p[0])));
            fks.add(new ForeignKeyMeta(name, parts.stream().map(p -> p[1]).toList(), target.get(name), parts.stream().map(p -> p[2]).toList()));
        });
        return fks;
    }

    private List<IndexMeta> indexes(DatabaseMetaData meta, String catalog, String schema, String table) {
        Map<String, TreeMap<Integer, String>> cols = new LinkedHashMap<>();
        Map<String, Boolean> unique = new HashMap<>();
        try (ResultSet rs = meta.getIndexInfo(catalog, schema, table, false, true)) {
            while (rs.next()) {
                String name = rs.getString("INDEX_NAME");
                if (name == null || rs.getShort("TYPE") == DatabaseMetaData.tableIndexStatistic) {
                    continue;
                }
                cols.computeIfAbsent(name, k -> new TreeMap<>()).put((int) rs.getShort("ORDINAL_POSITION"), rs.getString("COLUMN_NAME"));
                unique.put(name, !rs.getBoolean("NON_UNIQUE"));
            }
        } catch (SQLException e) {
            log.debug("Index metadata unavailable for {}: {}", table, e.getMessage());
            return List.of();
        }
        List<IndexMeta> result = new ArrayList<>();
        cols.forEach((name, c) -> result.add(new IndexMeta(name, unique.get(name), new ArrayList<>(c.values()))));
        return result;
    }

    private Map<String, Long> rowEstimates(Connection connection, DataSourceEntity ds) {
        String sql = ds.getDbType() == DatabaseType.MYSQL
                ? "SELECT table_name, table_rows FROM information_schema.tables WHERE table_schema = ?"
                : "SELECT c.relname, GREATEST(c.reltuples, 0)::bigint FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace "
                + "WHERE n.nspname = ? AND c.relkind IN ('r','p','m','v')";
        Map<String, Long> result = new HashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, ds.effectiveSchema());
            ps.setQueryTimeout(10);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString(1), rs.getLong(2));
                }
            }
        } catch (SQLException e) {
            log.debug("Row estimates unavailable: {}", e.getMessage());
        }
        return result;
    }

    static String formatType(String typeName, int size, int digits) {
        if (typeName == null) {
            return "unknown";
        }
        String t = typeName.toLowerCase(Locale.ROOT);
        if ((t.equals("numeric") || t.equals("decimal")) && size > 0 && size < 1000) {
            return t + "(" + size + "," + digits + ")";
        }
        if ((t.equals("varchar") || t.equals("char") || t.equals("bpchar")) && size > 0 && size < 65536) {
            return t + "(" + size + ")";
        }
        return t;
    }

    private static String normalizeType(String type) {
        return type == null ? "TABLE" : type.toUpperCase(Locale.ROOT).contains("VIEW") ? "VIEW" : "TABLE";
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
