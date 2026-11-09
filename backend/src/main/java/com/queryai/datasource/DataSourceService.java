package com.queryai.datasource;

import com.queryai.audit.AuditAction;
import com.queryai.audit.AuditService;
import com.queryai.common.ApiException;
import com.queryai.config.QueryAiProperties;
import com.queryai.datasource.DataSourceDtos.*;
import com.queryai.datasource.DatabaseType.SslMode;
import com.queryai.security.CurrentUser;
import com.queryai.user.UserRepository;
import com.queryai.workspace.WorkspaceAccessService;
import com.queryai.workspace.WorkspaceMemberRepository;
import com.queryai.workspace.WorkspaceRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Service
public class DataSourceService {
    private final DataSourceRepository repository;
    private final DataSourceGrantRepository grants;
    private final DataSourceAccess access;
    private final WorkspaceAccessService workspaceAccess;
    private final WorkspaceMemberRepository members;
    private final UserRepository users;
    private final CredentialCipher cipher;
    private final TargetConnectionRegistry registry;
    private final ConnectionTester tester;
    private final CurrentUser currentUser;
    private final AuditService audit;
    private final QueryAiProperties.DataSourceSettings settings;

    public DataSourceService(DataSourceRepository repository, DataSourceGrantRepository grants, DataSourceAccess access,
                             WorkspaceAccessService workspaceAccess, WorkspaceMemberRepository members, UserRepository users,
                             CredentialCipher cipher, TargetConnectionRegistry registry, ConnectionTester tester,
                             CurrentUser currentUser, AuditService audit, QueryAiProperties properties) {
        this.repository = repository;
        this.grants = grants;
        this.access = access;
        this.workspaceAccess = workspaceAccess;
        this.members = members;
        this.users = users;
        this.cipher = cipher;
        this.registry = registry;
        this.tester = tester;
        this.currentUser = currentUser;
        this.audit = audit;
        this.settings = properties.datasource();
    }

    @Transactional(readOnly = true)
    public List<DataSourceDto> list(Long workspaceId) {
        WorkspaceRole role = workspaceAccess.require(workspaceId, WorkspaceRole.VIEWER);
        return repository.findByWorkspaceIdOrderByName(workspaceId).stream().map(ds -> toDto(ds, role)).toList();
    }

    @Transactional(readOnly = true)
    public DataSourceDto get(Long id) {
        DataSourceEntity ds = access.requireVisible(id);
        return toDto(ds, workspaceAccess.roleIn(ds.getWorkspaceId()));
    }

    @Transactional
    public DataSourceDto create(DataSourceRequest request) {
        workspaceAccess.require(request.workspaceId(), WorkspaceRole.ADMIN);
        if (request.password() == null || request.password().isEmpty()) {
            throw ApiException.badRequest("Password is required for a new connection");
        }
        if (repository.existsByWorkspaceIdAndNameIgnoreCase(request.workspaceId(), request.name().trim())) {
            throw ApiException.conflict("A data source named '" + request.name() + "' already exists in this workspace");
        }
        DataSourceEntity ds = new DataSourceEntity();
        ds.setWorkspaceId(request.workspaceId());
        ds.setCreatedBy(currentUser.id());
        apply(ds, request);
        ds.setPasswordEncrypted(cipher.encrypt(request.password()));
        repository.save(ds);
        audit.record(AuditAction.DATASOURCE_CREATED, ds.getWorkspaceId(), "DATA_SOURCE", ds.getId(),
                Map.of("name", ds.getName(), "type", ds.getDbType().name(), "host", ds.getHost()));
        return toDto(ds, WorkspaceRole.ADMIN);
    }

    @Transactional
    public DataSourceDto update(Long id, DataSourceRequest request) {
        DataSourceEntity ds = access.requireManage(id);
        if (!ds.getWorkspaceId().equals(request.workspaceId())) {
            throw ApiException.badRequest("A data source cannot be moved between workspaces");
        }
        if (!ds.getName().equalsIgnoreCase(request.name().trim())
                && repository.existsByWorkspaceIdAndNameIgnoreCase(ds.getWorkspaceId(), request.name().trim())) {
            throw ApiException.conflict("A data source named '" + request.name() + "' already exists in this workspace");
        }
        apply(ds, request);
        if (request.password() != null && !request.password().isEmpty()) {
            ds.setPasswordEncrypted(cipher.encrypt(request.password()));
        }
        ds.setStatus(DataSourceEntity.Status.UNTESTED);
        ds.setUpdatedAt(Instant.now()); // forces the connection pool to be rebuilt with the new settings
        registry.evict(ds.getId());
        audit.record(AuditAction.DATASOURCE_UPDATED, ds.getWorkspaceId(), "DATA_SOURCE", ds.getId(), Map.of("name", ds.getName()));
        return toDto(ds, WorkspaceRole.ADMIN);
    }

    @Transactional
    public void delete(Long id) {
        DataSourceEntity ds = access.requireManage(id);
        registry.evict(id);
        audit.record(AuditAction.DATASOURCE_DELETED, ds.getWorkspaceId(), "DATA_SOURCE", id, Map.of("name", ds.getName()));
        repository.delete(ds);
    }

    /** Tests a saved data source with its stored credentials and records the outcome. */
    @Transactional
    public ConnectionTestResult testSaved(Long id) {
        DataSourceEntity ds = access.requireVisible(id);
        ConnectionTestResult result = tester.test(registry.settingsFor(ds));
        ds.setLastTestedAt(Instant.now());
        ds.setStatus(result.success() ? DataSourceEntity.Status.CONNECTED : DataSourceEntity.Status.FAILED);
        ds.setLastError(result.success() ? null : summarize(result));
        if (result.serverVersion() != null) {
            ds.setServerVersion(result.serverVersion());
        }
        audit.record(AuditAction.DATASOURCE_TESTED, ds.getWorkspaceId(), "DATA_SOURCE", id, Map.of("success", result.success()));
        return result;
    }

    /** Tests settings from the connection form before (or while) saving them. */
    @Transactional(readOnly = true)
    public ConnectionTestResult testAdhoc(TestConnectionRequest request) {
        String password = request.password();
        if (request.dataSourceId() != null) {
            DataSourceEntity existing = access.requireManage(request.dataSourceId());
            if (password == null || password.isEmpty()) {
                password = cipher.decrypt(existing.getPasswordEncrypted());
            }
        } else if (request.workspaceId() != null) {
            workspaceAccess.require(request.workspaceId(), WorkspaceRole.ADMIN);
        } else {
            throw ApiException.badRequest("workspaceId or dataSourceId is required");
        }
        ConnectionSettings cs = new ConnectionSettings(request.dbType(), request.host().trim(), request.port(),
                request.databaseName().trim(), blankToNull(request.schemaName()), request.username().trim(),
                password == null ? "" : password, request.sslMode() == null ? SslMode.DISABLE : request.sslMode());
        return tester.test(cs);
    }

    @Transactional(readOnly = true)
    public GrantsDto getGrants(Long id) {
        DataSourceEntity ds = access.requireManage(id);
        List<GrantedUser> granted = grants.findByDataSourceId(id).stream()
                .map(g -> users.findById(g.getUserId()).map(u -> new GrantedUser(u.getId(), u.getEmail(), u.getFullName())).orElse(null))
                .filter(java.util.Objects::nonNull).toList();
        return new GrantsDto(ds.isRestrictedAccess(), granted);
    }

    @Transactional
    public GrantsDto updateGrants(Long id, UpdateGrantsRequest request) {
        DataSourceEntity ds = access.requireManage(id);
        ds.setRestrictedAccess(request.restrictedAccess());
        grants.deleteByDataSourceId(id);
        grants.flush();
        for (Long userId : new HashSet<>(request.userIds())) {
            if (members.findMembership(ds.getWorkspaceId(), userId).isEmpty()) {
                throw ApiException.badRequest("User " + userId + " is not a member of this workspace");
            }
            DataSourceGrant grant = new DataSourceGrant();
            grant.setDataSourceId(id);
            grant.setUserId(userId);
            grant.setGrantedBy(currentUser.id());
            grants.save(grant);
        }
        audit.record(AuditAction.DATASOURCE_GRANTS_UPDATED, ds.getWorkspaceId(), "DATA_SOURCE", id,
                Map.of("restricted", request.restrictedAccess(), "users", request.userIds()));
        return getGrants(id);
    }

    private void apply(DataSourceEntity ds, DataSourceRequest r) {
        ds.setName(r.name().trim());
        ds.setDbType(r.dbType());
        ds.setHost(r.host().trim());
        ds.setPort(r.port());
        ds.setDatabaseName(r.databaseName().trim());
        ds.setSchemaName(r.dbType() == DatabaseType.MYSQL ? null : blankToNull(r.schemaName()));
        ds.setUsername(r.username().trim());
        ds.setSslMode(r.sslMode() == null ? SslMode.DISABLE : r.sslMode());
        ds.setRestrictedAccess(r.restrictedAccess());
        int maxRows = r.maxRows() == null ? settings.defaultMaxRows() : r.maxRows();
        ds.setMaxRows(Math.min(maxRows, settings.hardMaxRows()));
        ds.setQueryTimeoutSec(r.queryTimeoutSec() == null ? 30 : r.queryTimeoutSec());
    }

    public DataSourceDto toDto(DataSourceEntity ds, WorkspaceRole role) {
        return new DataSourceDto(ds.getId(), ds.getWorkspaceId(), ds.getName(), ds.getDbType(), ds.getHost(), ds.getPort(),
                ds.getDatabaseName(), ds.getSchemaName(), ds.effectiveSchema(), ds.getUsername(),
                ds.getPasswordEncrypted() != null, ds.getSslMode(), ds.isRestrictedAccess(), ds.getMaxRows(),
                ds.getQueryTimeoutSec(), ds.getStatus(), ds.getLastTestedAt(), ds.getLastError(), ds.getServerVersion(),
                role.atLeast(WorkspaceRole.ANALYST) && access.canQuery(ds, role), role.atLeast(WorkspaceRole.ADMIN),
                ds.getCreatedAt(), ds.getUpdatedAt(), ds.isPiiMaskingEnabled());
    }

    private static String summarize(ConnectionTestResult result) {
        return result.steps().stream().filter(s -> "FAILED".equals(s.status())).findFirst()
                .map(s -> s.name() + ": " + s.message()).orElse(result.hint());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
