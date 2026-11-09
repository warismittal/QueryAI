package com.queryai.schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class SchemaDtos {
    private SchemaDtos() {
    }

    public record SchemaView(Long dataSourceId, String dbType, String schema, Instant refreshedAt, int tableCount,
                             List<TableView> tables, List<RelationshipView> relationships) {
    }

    public record TableView(String name, String type, String description, Long rowEstimate, List<ColumnView> columns,
                            List<String> primaryKey, List<SchemaModel.ForeignKeyMeta> foreignKeys,
                            List<SchemaModel.IndexMeta> indexes) {
    }

    /** {@code pii}: detected or configured personal-data type (null when none); {@code masked}: hidden from non-admins. */
    public record ColumnView(String name, String dataType, boolean nullable, String defaultValue, String description,
                             boolean primaryKey, String references, String pii, boolean masked) {
    }

    public record RelationshipView(String fromTable, List<String> fromColumns, String toTable, List<String> toColumns,
                                   String name) {
    }

    public record SchemaSearchResponse(String query, String engine, String model, List<SchemaSearchMatch> matches) {
    }

    public record SchemaSearchMatch(String table, double score, String description) {
    }

    public record AnnotationRequest(@NotBlank @Size(max = 128) String tableName, @Size(max = 128) String columnName,
                                    @Size(max = 1000) String description) {
    }

    public record GlossaryTermDto(Long id, Long dataSourceId, String term, String definition, String sqlHint,
                                  Instant updatedAt) {
        static GlossaryTermDto from(GlossaryTerm t) {
            return new GlossaryTermDto(t.getId(), t.getDataSourceId(), t.getTerm(), t.getDefinition(), t.getSqlHint(), t.getUpdatedAt());
        }
    }

    public record GlossaryTermRequest(@NotBlank @Size(max = 120) String term, @NotBlank @Size(max = 2000) String definition,
                                      @Size(max = 2000) String sqlHint) {
    }
}
