package com.queryai.upload;

import com.queryai.audit.AuditAction;
import com.queryai.audit.AuditService;
import com.queryai.common.ApiException;
import com.queryai.datasource.CredentialCipher;
import com.queryai.datasource.DataSourceEntity;
import com.queryai.datasource.DataSourceRepository;
import com.queryai.datasource.DatabaseType;
import com.queryai.schema.SchemaService;
import com.queryai.security.CurrentUser;
import com.queryai.upload.ColumnTypeInference.SqlType;
import com.queryai.upload.CsvParser.CsvData;
import com.queryai.upload.CsvParser.CsvException;
import com.queryai.upload.WarehouseService.LoadedColumn;
import com.queryai.upload.WarehouseService.Location;
import com.queryai.workspace.WorkspaceAccessService;
import com.queryai.workspace.WorkspaceRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * CSV upload: parse, infer column types, load into the workspace's warehouse schema and expose it through a read-only
 * "Uploaded files" data source, so uploaded data can be explored, asked about and charted like any other database.
 */
@Service
public class UploadService {
    public static final String SOURCE_NAME = "Uploaded files";
    private static final int MAX_COLUMNS = 300;

    private final WarehouseService warehouse;
    private final WorkspaceAccessService workspaceAccess;
    private final DataSourceRepository dataSources;
    private final UploadedTableRepository uploads;
    private final CredentialCipher cipher;
    private final SchemaService schemaService;
    private final CurrentUser currentUser;
    private final AuditService audit;

    public UploadService(WarehouseService warehouse, WorkspaceAccessService workspaceAccess, DataSourceRepository dataSources,
                         UploadedTableRepository uploads, CredentialCipher cipher, SchemaService schemaService,
                         CurrentUser currentUser, AuditService audit) {
        this.warehouse = warehouse;
        this.workspaceAccess = workspaceAccess;
        this.dataSources = dataSources;
        this.uploads = uploads;
        this.cipher = cipher;
        this.schemaService = schemaService;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    public record UploadColumn(String name, String header, String type) {
    }

    public record UploadResult(Long id, Long dataSourceId, String dataSourceName, String schema, String table, long rowCount,
                               List<UploadColumn> columns, boolean replaced) {
    }

    public record UploadedTableDto(Long id, Long dataSourceId, String table, String originalFile, long rowCount,
                                   int columnCount, long sizeBytes, Instant createdAt) {
        static UploadedTableDto from(UploadedTable t) {
            return new UploadedTableDto(t.getId(), t.getDataSourceId(), t.getTableName(), t.getOriginalFile(), t.getRowCount(),
                    t.getColumnCount(), t.getSizeBytes(), t.getCreatedAt());
        }
    }

    public UploadResult upload(MultipartFile file, Long workspaceId, String requestedTable, boolean replace) {
        warehouse.requireEnabled();
        workspaceAccess.require(workspaceId, WorkspaceRole.ANALYST);
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Choose a non-empty CSV file");
        }
        if (file.getSize() > warehouse.config().maxUploadBytes()) {
            throw ApiException.badRequest("The file is larger than the " + (warehouse.config().maxUploadBytes() / (1024 * 1024)) + " MB limit");
        }
        String fileName = file.getOriginalFilename() == null ? "upload.csv" : file.getOriginalFilename();
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".csv") || lower.endsWith(".tsv") || lower.endsWith(".txt"))) {
            throw ApiException.badRequest("Only .csv, .tsv and .txt files are supported");
        }

        CsvData data;
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            data = CsvParser.parse(reader, lower.endsWith(".tsv") ? Character.valueOf('\t') : null,
                    warehouse.config().maxUploadRows(), MAX_COLUMNS);
        } catch (CsvException e) {
            throw ApiException.badRequest(e.getMessage());
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read the file: " + e.getMessage());
        }
        if (data.rows().isEmpty()) {
            throw ApiException.badRequest("The file has a header but no data rows");
        }

        List<String> names = Identifiers.columns(data.header());
        List<LoadedColumn> columns = new ArrayList<>(names.size());
        for (int i = 0; i < names.size(); i++) {
            SqlType type = ColumnTypeInference.infer(data.rows(), i);
            columns.add(new LoadedColumn(names.get(i), data.header().get(i).trim(), type));
        }
        String table = requestedTable != null && !requestedTable.isBlank()
                ? Identifiers.sanitize(requestedTable, "uploaded_data") : Identifiers.tableFromFileName(fileName);
        String schema = WarehouseService.workspaceSchema(workspaceId);
        String comment = "Uploaded from " + fileName.replaceAll("[^\\w .()-]", "_") + " on " + LocalDate.now() + " ("
                + data.rows().size() + " rows)";

        long rows = warehouse.createAndLoad(schema, table, columns, data.rows(), comment, replace);

        DataSourceEntity ds = ensureUploadSource(workspaceId, schema);
        UploadedTable record = uploads.findByDataSourceIdAndTableName(ds.getId(), table).orElseGet(UploadedTable::new);
        boolean replaced = record.getId() != null;
        record.setWorkspaceId(workspaceId);
        record.setDataSourceId(ds.getId());
        record.setTableName(table);
        record.setOriginalFile(fileName.length() > 255 ? fileName.substring(0, 255) : fileName);
        record.setRowCount(rows);
        record.setColumnCount(columns.size());
        record.setSizeBytes(file.getSize());
        record.setUploadedBy(currentUser.id());
        uploads.save(record);

        schemaService.refresh(ds.getId()); // the new table becomes visible to the explorer, the AI and SQL validation
        audit.record(AuditAction.DATA_UPLOADED, workspaceId, "DATA_SOURCE", ds.getId(),
                Map.of("table", table, "rows", rows, "file", record.getOriginalFile()));
        return new UploadResult(record.getId(), ds.getId(), ds.getName(), schema, table, rows,
                columns.stream().map(c -> new UploadColumn(c.name(), c.sourceHeader(), c.type().name())).toList(), replaced);
    }

    @Transactional(readOnly = true)
    public List<UploadedTableDto> list(Long workspaceId) {
        workspaceAccess.require(workspaceId, WorkspaceRole.VIEWER);
        return uploads.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId).stream().map(UploadedTableDto::from).toList();
    }

    public void delete(Long id) {
        warehouse.requireEnabled();
        UploadedTable t = uploads.findById(id).orElseThrow(() -> ApiException.notFound("Uploaded table"));
        WorkspaceRole role = workspaceAccess.require(t.getWorkspaceId(), WorkspaceRole.ANALYST);
        if (!role.atLeast(WorkspaceRole.ADMIN) && !currentUser.id().equals(t.getUploadedBy())) {
            throw ApiException.forbidden("Only the uploader or a workspace admin can delete this table");
        }
        warehouse.dropTable(WarehouseService.workspaceSchema(t.getWorkspaceId()), t.getTableName());
        uploads.delete(t);
        schemaService.refresh(t.getDataSourceId());
        audit.record(AuditAction.DATA_UPLOAD_DELETED, t.getWorkspaceId(), "DATA_SOURCE", t.getDataSourceId(),
                Map.of("table", t.getTableName()));
    }

    /** The workspace's read-only data source over its upload schema, created on first upload. */
    DataSourceEntity ensureUploadSource(Long workspaceId, String schema) {
        Location loc = warehouse.location();
        for (DataSourceEntity ds : dataSources.findByWorkspaceIdOrderByName(workspaceId)) {
            if (ds.getDbType() == DatabaseType.POSTGRESQL && schema.equals(ds.getSchemaName())
                    && loc.database().equals(ds.getDatabaseName()) && loc.host().equals(ds.getHost())) {
                return ds;
            }
        }
        String name = SOURCE_NAME;
        int k = 2;
        while (dataSources.existsByWorkspaceIdAndNameIgnoreCase(workspaceId, name)) {
            name = SOURCE_NAME + " " + k++;
        }
        DataSourceEntity ds = new DataSourceEntity();
        ds.setWorkspaceId(workspaceId);
        ds.setName(name);
        ds.setDbType(DatabaseType.POSTGRESQL);
        ds.setHost(loc.host());
        ds.setPort(loc.port());
        ds.setDatabaseName(loc.database());
        ds.setSchemaName(schema);
        ds.setUsername(warehouse.config().readerUsername());
        ds.setPasswordEncrypted(cipher.encrypt(warehouse.config().readerPassword()));
        ds.setCreatedBy(currentUser.id());
        return dataSources.save(ds);
    }
}
