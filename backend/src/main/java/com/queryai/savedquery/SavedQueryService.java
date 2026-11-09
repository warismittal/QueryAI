package com.queryai.savedquery;

import com.queryai.audit.AuditAction;
import com.queryai.audit.AuditService;
import com.queryai.common.ApiException;
import com.queryai.datasource.DataSourceAccess;
import com.queryai.datasource.DataSourceEntity;
import com.queryai.datasource.DataSourceRepository;
import com.queryai.history.QuerySource;
import com.queryai.history.QueryRun;
import com.queryai.savedquery.SavedQueryDtos.*;
import com.queryai.security.CurrentUser;
import com.queryai.sql.SqlDtos.ExecuteResponse;
import com.queryai.sql.SqlSafetyValidator;
import com.queryai.sql.SqlService;
import com.queryai.sql.SqlValidationService;
import com.queryai.user.User;
import com.queryai.user.UserRepository;
import com.queryai.workspace.WorkspaceAccessService;
import com.queryai.workspace.WorkspaceRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class SavedQueryService {
    private final SavedQueryRepository repository;
    private final SavedQueryVersionRepository versions;
    private final WorkspaceAccessService workspaceAccess;
    private final DataSourceAccess dataSourceAccess;
    private final DataSourceRepository dataSources;
    private final UserRepository users;
    private final SqlValidationService validation;
    private final SqlService sqlService;
    private final CurrentUser currentUser;
    private final AuditService audit;

    public SavedQueryService(SavedQueryRepository repository, SavedQueryVersionRepository versions,
                             WorkspaceAccessService workspaceAccess, DataSourceAccess dataSourceAccess,
                             DataSourceRepository dataSources, UserRepository users, SqlValidationService validation,
                             SqlService sqlService, CurrentUser currentUser, AuditService audit) {
        this.repository = repository;
        this.versions = versions;
        this.workspaceAccess = workspaceAccess;
        this.dataSourceAccess = dataSourceAccess;
        this.dataSources = dataSources;
        this.users = users;
        this.validation = validation;
        this.sqlService = sqlService;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<SavedQueryDto> list(Long workspaceId, String folder, boolean templatesOnly, String q) {
        WorkspaceRole role = workspaceAccess.require(workspaceId, WorkspaceRole.VIEWER);
        return repository.findVisible(workspaceId, currentUser.id(), blankToNull(folder), templatesOnly, blankToNull(q))
                .stream().map(s -> toDto(s, role)).toList();
    }

    @Transactional(readOnly = true)
    public SavedQueryDto get(Long id) {
        SavedQuery s = loadVisible(id);
        return toDto(s, workspaceAccess.roleIn(s.getWorkspaceId()));
    }

    @Transactional
    public SavedQueryDto create(CreateSavedQueryRequest r) {
        WorkspaceRole role = workspaceAccess.require(r.workspaceId(), WorkspaceRole.ANALYST);
        DataSourceEntity ds = dataSourceAccess.requireQueryable(r.dataSourceId(), WorkspaceRole.ANALYST);
        if (!ds.getWorkspaceId().equals(r.workspaceId())) {
            throw ApiException.badRequest("The data source belongs to a different workspace");
        }
        String sql = ensureSafe(ds, r.sql());
        SavedQuery s = new SavedQuery();
        s.setWorkspaceId(r.workspaceId());
        s.setDataSourceId(ds.getId());
        s.setOwnerId(currentUser.id());
        s.setName(r.name().trim());
        s.setDescription(r.description());
        s.setFolder(blankToNull(r.folder()));
        s.setQuestion(r.question());
        s.setSqlText(sql);
        s.setChartType(r.chartType());
        s.setVisibility(r.visibility() == null ? Visibility.PRIVATE : r.visibility());
        s.setTemplate(r.template());
        s.setCurrentVersion(1);
        repository.save(s);
        addVersion(s, "Initial version");
        audit.record(AuditAction.SAVED_QUERY_CREATED, s.getWorkspaceId(), "SAVED_QUERY", s.getId(), Map.of("name", s.getName()));
        return toDto(s, role);
    }

    @Transactional
    public SavedQueryDto update(Long id, UpdateSavedQueryRequest r) {
        SavedQuery s = loadEditable(id);
        DataSourceEntity ds = dataSourceAccess.load(s.getDataSourceId());
        String sql = ensureSafe(ds, r.sql());
        boolean sqlChanged = !sql.equals(s.getSqlText());
        s.setName(r.name().trim());
        s.setDescription(r.description());
        s.setFolder(blankToNull(r.folder()));
        s.setChartType(r.chartType());
        s.setVisibility(r.visibility());
        s.setTemplate(r.template());
        if (sqlChanged) {
            s.setSqlText(sql);
            s.setCurrentVersion(s.getCurrentVersion() + 1);
            addVersion(s, r.versionNote());
        }
        audit.record(AuditAction.SAVED_QUERY_UPDATED, s.getWorkspaceId(), "SAVED_QUERY", s.getId(),
                Map.of("name", s.getName(), "sqlChanged", sqlChanged, "visibility", s.getVisibility().name()));
        return toDto(s, workspaceAccess.roleIn(s.getWorkspaceId()));
    }

    @Transactional
    public SavedQueryDto duplicate(Long id) {
        SavedQuery source = loadVisible(id);
        WorkspaceRole role = workspaceAccess.require(source.getWorkspaceId(), WorkspaceRole.ANALYST);
        SavedQuery copy = new SavedQuery();
        copy.setWorkspaceId(source.getWorkspaceId());
        copy.setDataSourceId(source.getDataSourceId());
        copy.setOwnerId(currentUser.id());
        copy.setName(("Copy of " + source.getName()).substring(0, Math.min(160, 8 + source.getName().length())));
        copy.setDescription(source.getDescription());
        copy.setFolder(source.getFolder());
        copy.setQuestion(source.getQuestion());
        copy.setSqlText(source.getSqlText());
        copy.setChartType(source.getChartType());
        copy.setVisibility(Visibility.PRIVATE);
        copy.setTemplate(false);
        repository.save(copy);
        addVersion(copy, "Duplicated from '" + source.getName() + "' v" + source.getCurrentVersion());
        audit.record(AuditAction.SAVED_QUERY_CREATED, copy.getWorkspaceId(), "SAVED_QUERY", copy.getId(), Map.of("duplicateOf", id));
        return toDto(copy, role);
    }

    @Transactional
    public void delete(Long id) {
        SavedQuery s = loadEditable(id);
        audit.record(AuditAction.SAVED_QUERY_DELETED, s.getWorkspaceId(), "SAVED_QUERY", id, Map.of("name", s.getName()));
        repository.delete(s);
    }

    @Transactional(readOnly = true)
    public List<VersionDto> versions(Long id) {
        loadVisible(id);
        return versions.findBySavedQueryIdOrderByVersionDesc(id).stream()
                .map(v -> new VersionDto(v.getVersion(), v.getSqlText(), v.getNote(), v.getCreatedBy(), v.getCreatedAt())).toList();
    }

    @Transactional
    public SavedQueryDto restore(Long id, int version) {
        SavedQuery s = loadEditable(id);
        SavedQueryVersion v = versions.findBySavedQueryIdAndVersion(id, version).orElseThrow(() -> ApiException.notFound("Version " + version));
        s.setSqlText(v.getSqlText());
        s.setCurrentVersion(s.getCurrentVersion() + 1);
        addVersion(s, "Restored from v" + version);
        audit.record(AuditAction.SAVED_QUERY_UPDATED, s.getWorkspaceId(), "SAVED_QUERY", id, Map.of("restoredVersion", version));
        return toDto(s, workspaceAccess.roleIn(s.getWorkspaceId()));
    }

    /** Runs a saved query. Viewers may run shared queries; restricted data sources still require a grant. */
    public ExecuteResponse run(Long id, Map<String, Object> parameters, QuerySource source) {
        SavedQuery s = loadVisibleTx(id);
        DataSourceEntity ds = dataSourceAccess.requireQueryable(s.getDataSourceId(), WorkspaceRole.VIEWER);
        QueryRun run = sqlService.newRun(ds, source);
        run.setQuestion(s.getQuestion() != null ? s.getQuestion() : "Saved query: " + s.getName());
        return sqlService.runValidated(ds, s.getSqlText(), parameters, run, null);
    }

    @Transactional(readOnly = true)
    public SavedQuery loadVisibleTx(Long id) {
        return loadVisible(id);
    }

    SavedQuery loadVisible(Long id) {
        SavedQuery s = repository.findById(id).orElseThrow(() -> ApiException.notFound("Saved query"));
        workspaceAccess.requireMember(s.getWorkspaceId());
        if (s.getVisibility() == Visibility.PRIVATE && !Objects.equals(s.getOwnerId(), currentUser.id())) {
            throw ApiException.notFound("Saved query");
        }
        return s;
    }

    private SavedQuery loadEditable(Long id) {
        SavedQuery s = loadVisible(id);
        WorkspaceRole role = workspaceAccess.roleIn(s.getWorkspaceId());
        if (!Objects.equals(s.getOwnerId(), currentUser.id()) && !role.atLeast(WorkspaceRole.ADMIN)) {
            throw ApiException.forbidden("Only the owner or a workspace admin can modify this saved query");
        }
        return s;
    }

    private String ensureSafe(DataSourceEntity ds, String sql) {
        SqlSafetyValidator.Report report = validation.validateStatic(ds, sql);
        if (!report.valid()) {
            String errors = report.issues().stream().filter(i -> i.severity() == SqlSafetyValidator.Severity.ERROR)
                    .map(SqlSafetyValidator.Issue::message).reduce((a, b) -> a + "; " + b).orElse("invalid SQL");
            throw ApiException.badRequest("This SQL cannot be saved: " + errors);
        }
        return report.normalizedSql();
    }

    private void addVersion(SavedQuery s, String note) {
        SavedQueryVersion v = new SavedQueryVersion();
        v.setSavedQueryId(s.getId());
        v.setVersion(s.getCurrentVersion());
        v.setSqlText(s.getSqlText());
        v.setNote(note == null || note.isBlank() ? null : note.trim());
        v.setCreatedBy(currentUser.id());
        versions.save(v);
    }

    SavedQueryDto toDto(SavedQuery s, WorkspaceRole role) {
        String owner = s.getOwnerId() == null ? null : users.findById(s.getOwnerId()).map(User::getFullName).orElse(null);
        String dsName = dataSources.findById(s.getDataSourceId()).map(DataSourceEntity::getName).orElse(null);
        DataSourceEntity ds = dataSources.findById(s.getDataSourceId()).orElse(null);
        List<String> params = ds == null ? List.of() : new SqlSafetyValidator().validate(s.getSqlText(), ds.getDbType(), null, null).parameters();
        boolean canEdit = Objects.equals(s.getOwnerId(), currentUser.id()) || (role != null && role.atLeast(WorkspaceRole.ADMIN));
        return new SavedQueryDto(s.getId(), s.getWorkspaceId(), s.getDataSourceId(), dsName, s.getOwnerId(), owner, s.getName(),
                s.getDescription(), s.getFolder(), s.getQuestion(), s.getSqlText(), s.getChartType(), s.getVisibility(),
                s.isTemplate(), s.getCurrentVersion(), params, canEdit, s.getCreatedAt(), s.getUpdatedAt());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
