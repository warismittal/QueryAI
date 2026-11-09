package com.queryai.schema;

import java.time.Instant;
import java.util.List;

/** Immutable schema metadata captured from a target database and stored as JSON. */
public final class SchemaModel {
    private SchemaModel() {
    }

    public record SchemaSnapshot(String dbType, String schema, Instant capturedAt, List<TableMeta> tables) {
    }

    public record TableMeta(String name, String type, String comment, Long rowEstimate, List<ColumnMeta> columns,
                            List<String> primaryKey, List<ForeignKeyMeta> foreignKeys, List<IndexMeta> indexes) {
    }

    public record ColumnMeta(String name, String dataType, boolean nullable, String defaultValue, String comment,
                             int ordinal) {
    }

    public record ForeignKeyMeta(String name, List<String> columns, String referencedTable, List<String> referencedColumns) {
    }

    public record IndexMeta(String name, boolean unique, List<String> columns) {
    }
}
