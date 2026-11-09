package com.queryai.schema;

import com.queryai.ai.embedding.SemanticSchemaIndex;
import com.queryai.ai.embedding.SemanticSchemaIndex.SemanticResult;
import com.queryai.audit.AuditAction;
import com.queryai.audit.AuditService;
import com.queryai.common.ApiException;
import com.queryai.datasource.DataSourceAccess;
import com.queryai.datasource.DataSourceEntity;
import com.queryai.datasource.DataSourceRepository;
import com.queryai.datasource.TargetConnectionRegistry;
import com.queryai.pii.PiiService;
import com.queryai.pii.PiiService.PiiColumn;
import com.queryai.schema.SchemaDtos.*;
import com.queryai.schema.SchemaModel.*;
import com.queryai.security.CurrentUser;
import com.queryai.sql.QueryResult;
import com.queryai.sql.SqlExecutionService;
import com.queryai.workspace.WorkspaceRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SchemaService {
    private final SchemaSnapshotRepository snapshots;
    private final ColumnAnnotationRepository annotations;
    private final SchemaIntrospector introspector;
    private final TargetConnectionRegistry registry;
    private final DataSourceAccess access;
    private final DataSourceRepository dataSources;
    private final SqlExecutionService executor;
    private final CurrentUser currentUser;
    private final AuditService audit;
    private final PiiService pii;
    private final SemanticSchemaIndex semanticIndex;

    public SchemaService(SchemaSnapshotRepository snapshots, ColumnAnnotationRepository annotations,
                         SchemaIntrospector introspector, TargetConnectionRegistry registry, DataSourceAccess access,
                         DataSourceRepository dataSources, SqlExecutionService executor, CurrentUser currentUser,
                         AuditService audit, PiiService pii, SemanticSchemaIndex semanticIndex) {
        this.pii = pii;
        this.semanticIndex = semanticIndex;
        this.snapshots = snapshots;
        this.annotations = annotations;
        this.introspector = introspector;
        this.registry = registry;
        this.access = access;
        this.dataSources = dataSources;
        this.executor = executor;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @Transactional
    public SchemaView getSchema(Long dataSourceId) {
        DataSourceEntity ds = access.requireVisible(dataSourceId);
        return toView(ds, ensureSnapshot(ds));
    }

    @Transactional
    public SchemaView refresh(Long dataSourceId) {
        DataSourceEntity ds = access.requireQueryable(dataSourceId, WorkspaceRole.ANALYST);
        SchemaSnapshotEntity entity = capture(ds);
        audit.record(AuditAction.SCHEMA_REFRESHED, ds.getWorkspaceId(), "DATA_SOURCE", ds.getId(), Map.of("tables", entity.getTableCount()));
        return toView(ds, entity);
    }

    /** Returns the stored snapshot, capturing it from the database on first use. */
    @Transactional
    public SchemaSnapshotEntity ensureSnapshot(DataSourceEntity ds) {
        return snapshots.findByDataSourceId(ds.getId()).orElseGet(() -> capture(ds));
    }

    private SchemaSnapshotEntity capture(DataSourceEntity ds) {
        SchemaSnapshot snapshot;
        try {
            snapshot = introspector.introspect(registry.dataSource(ds), ds);
        } catch (SQLException | RuntimeException e) {
            ds.setStatus(DataSourceEntity.Status.FAILED);
            ds.setLastError("Schema discovery failed: " + SqlExecutionService.databaseMessage(e));
            dataSources.save(ds);
            if (e instanceof ApiException api) {
                throw api;
            }
            // connection-pool timeouts hide the real reason (wrong host, password, port ...): surface the root cause
            String cause = SqlExecutionService.databaseMessage(e);
            String hint = cause.contains("Connection is not available") || cause.contains("refused") || cause.contains("UnknownHost")
                    ? " — check the host and port (inside Docker use the service name, e.g. 'postgres', or 'host.docker.internal' for your computer) and use Test on the Connections page."
                    : "";
            throw ApiException.badGateway("SCHEMA_DISCOVERY_FAILED", "Could not connect to '" + ds.getName() + "': " + cause + hint, e);
        }
        SchemaSnapshotEntity entity = snapshots.findByDataSourceId(ds.getId()).orElseGet(SchemaSnapshotEntity::new);
        entity.setDataSourceId(ds.getId());
        entity.setSnapshot(snapshot);
        entity.setTableCount(snapshot.tables().size());
        entity.setRefreshedAt(Instant.now());
        ds.setStatus(DataSourceEntity.Status.CONNECTED);
        ds.setLastError(null);
        dataSources.save(ds);
        return snapshots.save(entity);
    }

    /** Lower-cased names of tables visible through this data source (used for SQL authorisation). */
    @Transactional
    public Set<String> allowedTables(DataSourceEntity ds) {
        return ensureSnapshot(ds).getSnapshot().tables().stream()
                .map(t -> t.name().toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }

    @Transactional
    public QueryResult preview(Long dataSourceId, String table) {
        DataSourceEntity ds = access.requireQueryable(dataSourceId, WorkspaceRole.ANALYST);
        TableMeta meta = ensureSnapshot(ds).getSnapshot().tables().stream()
                .filter(t -> t.name().equals(table)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Table '" + table + "'"));
        // identifiers come from the snapshot (never from raw user input) and are quoted for the dialect
        String sql = "SELECT * FROM " + ds.getDbType().quoteIdentifier(ds.effectiveSchema()) + "."
                + ds.getDbType().quoteIdentifier(meta.name());
        QueryResult result = executor.execute(ds, sql, Map.of(), 25, Math.min(ds.getQueryTimeoutSec(), 30));
        return pii.protect(ds, result, List.of(meta.name()), sql);
    }

    /** Semantic table search (embeddings, pgvector cosine distance when available). Not read-only: the first call
     *  may capture the schema snapshot. */
    @Transactional
    public SchemaSearchResponse semanticSearch(Long dataSourceId, String query) {
        DataSourceEntity ds = access.requireVisible(dataSourceId);
        if (query == null || query.isBlank() || query.length() > 500) {
            throw ApiException.badRequest("Enter a search phrase of up to 500 characters");
        }
        SchemaView view = toView(ds, ensureSnapshot(ds));
        Map<String, String> descriptions = new HashMap<>();
        view.tables().forEach(t -> descriptions.put(t.name(), t.description()));
        SemanticResult result = semanticIndex.search(ds.getId(), view, query.trim(), Math.min(10, view.tables().size()));
        List<SchemaSearchMatch> matches = result.matches().stream()
                .map(m -> new SchemaSearchMatch(m.table(), Math.round(m.score() * 1000) / 1000.0, descriptions.get(m.table()))).toList();
        return new SchemaSearchResponse(query.trim(), result.engine(), result.model(), matches);
    }

    @Transactional
    public SchemaView annotate(Long dataSourceId, AnnotationRequest request) {
        DataSourceEntity ds = access.requireQueryable(dataSourceId, WorkspaceRole.ANALYST);
        String column = request.columnName() == null ? "" : request.columnName();
        Optional<ColumnAnnotation> existing = annotations.findByDataSourceIdAndTableNameAndColumnName(ds.getId(), request.tableName(), column);
        if (request.description() == null || request.description().isBlank()) {
            existing.ifPresent(annotations::delete);
        } else {
            ColumnAnnotation a = existing.orElseGet(ColumnAnnotation::new);
            a.setDataSourceId(ds.getId());
            a.setTableName(request.tableName());
            a.setColumnName(column);
            a.setDescription(request.description().trim());
            a.setUpdatedBy(currentUser.id());
            annotations.save(a);
        }
        audit.record(AuditAction.ANNOTATION_UPDATED, ds.getWorkspaceId(), "DATA_SOURCE", ds.getId(),
                Map.of("table", request.tableName(), "column", column));
        return toView(ds, ensureSnapshot(ds));
    }

    public SchemaView toView(DataSourceEntity ds, SchemaSnapshotEntity entity) {
        Map<String, String> notes = new HashMap<>();
        for (ColumnAnnotation a : annotations.findByDataSourceId(ds.getId())) {
            notes.put(a.getTableName() + "." + a.getColumnName(), a.getDescription());
        }
        List<TableView> tables = new ArrayList<>();
        List<RelationshipView> relationships = new ArrayList<>();
        Map<String, PiiColumn> piiColumns = pii.effective(ds.getId(), entity.getSnapshot().tables());
        for (TableMeta t : entity.getSnapshot().tables()) {
            Map<String, String> refs = new HashMap<>();
            for (ForeignKeyMeta fk : t.foreignKeys()) {
                for (int i = 0; i < fk.columns().size(); i++) {
                    refs.put(fk.columns().get(i), fk.referencedTable() + "." + fk.referencedColumns().get(i));
                }
                relationships.add(new RelationshipView(t.name(), fk.columns(), fk.referencedTable(), fk.referencedColumns(), fk.name()));
            }
            Set<String> pk = new HashSet<>(t.primaryKey());
            List<ColumnView> columns = t.columns().stream().map(c -> {
                PiiColumn p = piiColumns.get(t.name().toLowerCase(Locale.ROOT) + "." + c.name().toLowerCase(Locale.ROOT));
                return new ColumnView(c.name(), c.dataType(), c.nullable(), c.defaultValue(),
                        notes.getOrDefault(t.name() + "." + c.name(), c.comment()), pk.contains(c.name()), refs.get(c.name()),
                        p == null ? null : p.type().name(), p != null && p.masked());
            }).toList();
            tables.add(new TableView(t.name(), t.type(), notes.getOrDefault(t.name() + ".", t.comment()), t.rowEstimate(),
                    columns, t.primaryKey(), t.foreignKeys(), t.indexes()));
        }
        return new SchemaView(ds.getId(), ds.getDbType().name(), entity.getSnapshot().schema(), entity.getRefreshedAt(),
                entity.getTableCount(), tables, relationships);
    }
}
