package com.queryai.pii;

import com.queryai.audit.AuditAction;
import com.queryai.audit.AuditService;
import com.queryai.common.ApiException;
import com.queryai.datasource.DataSourceAccess;
import com.queryai.datasource.DataSourceEntity;
import com.queryai.datasource.DataSourceRepository;
import com.queryai.schema.SchemaModel.ColumnMeta;
import com.queryai.schema.SchemaModel.TableMeta;
import com.queryai.schema.SchemaSnapshotRepository;
import com.queryai.security.CurrentUser;
import com.queryai.sql.QueryResult;
import com.queryai.sql.QueryResult.Category;
import com.queryai.sql.QueryResult.ResultColumn;
import com.queryai.workspace.WorkspaceAccessService;
import com.queryai.workspace.WorkspaceRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Personal-data protection for query results. Columns are classified automatically ({@link PiiDetector}) and admins
 * can override any column. Results are masked for workspace members below ADMIN; admins see raw values unless they
 * preview the analyst view (request header {@code X-QueryAI-Pii-Preview: masked}).
 */
@Service
public class PiiService {
    public static final String PREVIEW_HEADER = "X-QueryAI-Pii-Preview";
    private static final Pattern IDENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern STRING_LITERAL = Pattern.compile("'(?:[^']|'')*'");

    private final PiiPolicyRepository policies;
    private final SchemaSnapshotRepository snapshots;
    private final WorkspaceAccessService workspaceAccess;
    private final DataSourceAccess access;
    private final DataSourceRepository dataSources;
    private final CurrentUser currentUser;
    private final AuditService audit;

    public PiiService(PiiPolicyRepository policies, SchemaSnapshotRepository snapshots, WorkspaceAccessService workspaceAccess,
                      DataSourceAccess access, DataSourceRepository dataSources, CurrentUser currentUser, AuditService audit) {
        this.policies = policies;
        this.snapshots = snapshots;
        this.workspaceAccess = workspaceAccess;
        this.access = access;
        this.dataSources = dataSources;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    /** Effective classification of one column. */
    public record PiiColumn(String table, String column, PiiType type, boolean masked, String source) {
    }

    public record PiiColumnDto(String table, String column, PiiType piiType, String label, boolean masked, String source) {
    }

    public record PiiOverview(Long dataSourceId, boolean maskingEnabled, boolean youSeeRawValues, List<PiiColumnDto> columns) {
    }

    public record PiiPolicyRequest(@NotBlank @Size(max = 128) String table, @NotBlank @Size(max = 128) String column,
                                   PiiType piiType, boolean masked) {
    }

    public record PiiSettingsRequest(boolean enabled) {
    }

    static String key(String table, String column) {
        return table.toLowerCase(Locale.ROOT) + "." + column.toLowerCase(Locale.ROOT);
    }

    /** Auto-detected columns merged with admin overrides, keyed by {@code table.column} (lower case). */
    public Map<String, PiiColumn> effective(Long dataSourceId, List<TableMeta> tables) {
        Map<String, PiiPolicy> overrides = new HashMap<>();
        for (PiiPolicy p : policies.findByDataSourceId(dataSourceId)) {
            overrides.put(key(p.getTableName(), p.getColumnName()), p);
        }
        Map<String, PiiColumn> out = new LinkedHashMap<>();
        for (TableMeta t : tables) {
            for (ColumnMeta c : t.columns()) {
                String k = key(t.name(), c.name());
                PiiPolicy o = overrides.get(k);
                if (o != null) {
                    out.put(k, new PiiColumn(t.name(), c.name(), o.getPiiType(), o.isMasked(), "manual"));
                } else {
                    PiiDetector.detect(t.name(), c.name())
                            .ifPresent(type -> out.put(k, new PiiColumn(t.name(), c.name(), type, true, "auto")));
                }
            }
        }
        return out;
    }

    @Transactional(readOnly = true)
    public PiiOverview overview(Long dataSourceId) {
        DataSourceEntity ds = access.requireVisible(dataSourceId);
        List<PiiColumnDto> columns = effective(ds.getId(), tables(ds)).values().stream()
                .map(c -> new PiiColumnDto(c.table(), c.column(), c.type(), c.type().label(), c.masked(), c.source())).toList();
        return new PiiOverview(ds.getId(), ds.isPiiMaskingEnabled(), !shouldMask(ds), columns);
    }

    @Transactional
    public PiiOverview updateColumn(Long dataSourceId, PiiPolicyRequest request) {
        DataSourceEntity ds = access.requireManage(dataSourceId);
        boolean exists = tables(ds).stream().anyMatch(t -> t.name().equals(request.table())
                && t.columns().stream().anyMatch(c -> c.name().equals(request.column())));
        if (!exists) {
            throw ApiException.notFound("Column '" + request.table() + "." + request.column() + "'");
        }
        PiiPolicy p = policies.findByDataSourceIdAndTableNameAndColumnName(ds.getId(), request.table(), request.column())
                .orElseGet(PiiPolicy::new);
        p.setDataSourceId(ds.getId());
        p.setTableName(request.table());
        p.setColumnName(request.column());
        p.setPiiType(request.piiType() != null ? request.piiType()
                : PiiDetector.detect(request.table(), request.column()).orElse(PiiType.OTHER));
        p.setMasked(request.masked());
        p.setUpdatedBy(currentUser.id());
        policies.save(p);
        audit.record(AuditAction.PII_POLICY_UPDATED, ds.getWorkspaceId(), "DATA_SOURCE", ds.getId(),
                Map.of("column", request.table() + "." + request.column(), "masked", request.masked(), "type", p.getPiiType().name()));
        return overview(dataSourceId);
    }

    @Transactional
    public PiiOverview updateSettings(Long dataSourceId, PiiSettingsRequest request) {
        DataSourceEntity ds = access.requireManage(dataSourceId);
        ds.setPiiMaskingEnabled(request.enabled());
        dataSources.save(ds);
        audit.record(AuditAction.PII_POLICY_UPDATED, ds.getWorkspaceId(), "DATA_SOURCE", ds.getId(),
                Map.of("maskingEnabled", request.enabled()));
        return overview(dataSourceId);
    }

    /** True when the current caller must receive masked values for this data source. */
    public boolean shouldMask(DataSourceEntity ds) {
        if (!ds.isPiiMaskingEnabled()) {
            return false;
        }
        if (previewRequested()) {
            return true;
        }
        WorkspaceRole role = workspaceAccess.roleIn(ds.getWorkspaceId());
        return !role.atLeast(WorkspaceRole.ADMIN);
    }

    /**
     * Masks personal data in a result for the current caller. {@code referencedTables} narrows the candidate PII
     * columns (empty = all tables); {@code sql} is used to catch PII columns that were renamed with an alias or folded
     * into an expression.
     */
    @Transactional(readOnly = true)
    public QueryResult protect(DataSourceEntity ds, QueryResult result, Collection<String> referencedTables, String sql) {
        if (result == null || !shouldMask(ds)) {
            return result;
        }
        Set<String> refs = new HashSet<>();
        if (referencedTables != null) {
            referencedTables.forEach(t -> refs.add(t.toLowerCase(Locale.ROOT)));
        }
        List<TableMeta> tables = tables(ds);
        Map<String, PiiColumn> effective = effective(ds.getId(), tables);
        Map<String, PiiType> piiByName = new HashMap<>();
        Set<String> plainNames = new HashSet<>();
        for (TableMeta t : tables) {
            if (!refs.isEmpty() && !refs.contains(t.name().toLowerCase(Locale.ROOT))) {
                continue;
            }
            for (ColumnMeta c : t.columns()) {
                PiiColumn p = effective.get(key(t.name(), c.name()));
                if (p != null && p.masked()) {
                    piiByName.put(c.name().toLowerCase(Locale.ROOT), p.type());
                } else {
                    plainNames.add(c.name().toLowerCase(Locale.ROOT));
                }
            }
        }
        Map<String, PiiType> types = new HashMap<>(piiByName);
        if (sql != null && !piiByName.isEmpty()) {
            String code = STRING_LITERAL.matcher(sql).replaceAll("''").replace("\"", "").replace("`", "");
            Set<String> idents = new HashSet<>();
            Matcher m = IDENT.matcher(code);
            while (m.find()) {
                idents.add(m.group().toLowerCase(Locale.ROOT));
            }
            boolean usesPii = piiByName.keySet().stream().anyMatch(idents::contains);
            if (usesPii) {
                // aliases: "email AS contact" / "c.email contact"
                for (Map.Entry<String, PiiType> e : piiByName.entrySet()) {
                    Matcher alias = Pattern.compile("(?i)\\b(?:\\w+\\.)?" + Pattern.quote(e.getKey()) + "\\s+(?:as\\s+)?([A-Za-z_][A-Za-z0-9_]*)")
                            .matcher(code);
                    while (alias.find()) {
                        String a = alias.group(1).toLowerCase(Locale.ROOT);
                        if (!isKeyword(a)) {
                            types.putIfAbsent(a, e.getValue());
                        }
                    }
                }
                // expressions over PII (CONCAT(first_name, ' ', last_name) AS customer) produce new text columns
                for (ResultColumn c : result.columns()) {
                    String label = c.name().toLowerCase(Locale.ROOT);
                    if (!types.containsKey(label) && !plainNames.contains(label)
                            && (c.category() == Category.TEXT || c.category() == Category.OTHER)) {
                        types.put(label, PiiType.OTHER);
                    }
                }
            }
        }
        return PiiMasker.apply(result, types, true);
    }

    private static boolean isKeyword(String word) {
        return switch (word) {
            case "from", "where", "and", "or", "group", "order", "by", "having", "limit", "offset", "join", "on", "as",
                 "is", "not", "null", "like", "ilike", "in", "between", "asc", "desc", "union", "case", "when", "then",
                 "else", "end", "left", "right", "inner", "outer", "full", "cross", "using", "fetch", "select", "distinct",
                 "nulls", "first", "last", "over", "partition", "filter", "within", "collate" -> true;
            default -> false;
        };
    }

    private List<TableMeta> tables(DataSourceEntity ds) {
        return snapshots.findByDataSourceId(ds.getId()).map(s -> s.getSnapshot().tables()).orElse(List.of());
    }

    private static boolean previewRequested() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            return "masked".equalsIgnoreCase(request.getHeader(PREVIEW_HEADER));
        }
        return false;
    }
}
