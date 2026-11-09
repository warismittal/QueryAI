package com.queryai.datasource;

import com.queryai.common.ApiException;
import com.queryai.security.CurrentUser;
import com.queryai.workspace.WorkspaceAccessService;
import com.queryai.workspace.WorkspaceRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authorization for data sources: workspace role plus optional per-source grants when a source is restricted.
 */
@Service
public class DataSourceAccess {
    private final DataSourceRepository repository;
    private final DataSourceGrantRepository grants;
    private final WorkspaceAccessService workspaceAccess;
    private final CurrentUser currentUser;

    public DataSourceAccess(DataSourceRepository repository, DataSourceGrantRepository grants,
                            WorkspaceAccessService workspaceAccess, CurrentUser currentUser) {
        this.repository = repository;
        this.grants = grants;
        this.workspaceAccess = workspaceAccess;
        this.currentUser = currentUser;
    }

    public DataSourceEntity load(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Data source"));
    }

    /** Any workspace member may see that the data source exists and read its schema metadata. */
    @Transactional(readOnly = true)
    public DataSourceEntity requireVisible(Long id) {
        DataSourceEntity ds = load(id);
        workspaceAccess.requireMember(ds.getWorkspaceId());
        return ds;
    }

    /** Requires {@code minimum} workspace role and, for restricted sources, an explicit grant (admins are exempt). */
    @Transactional(readOnly = true)
    public DataSourceEntity requireQueryable(Long id, WorkspaceRole minimum) {
        DataSourceEntity ds = load(id);
        WorkspaceRole role = workspaceAccess.require(ds.getWorkspaceId(), minimum);
        if (!canQuery(ds, role)) {
            throw ApiException.forbidden("You have not been granted access to query '" + ds.getName() + "'");
        }
        return ds;
    }

    @Transactional(readOnly = true)
    public DataSourceEntity requireManage(Long id) {
        DataSourceEntity ds = load(id);
        workspaceAccess.require(ds.getWorkspaceId(), WorkspaceRole.ADMIN);
        return ds;
    }

    public boolean canQuery(DataSourceEntity ds, WorkspaceRole role) {
        if (!ds.isRestrictedAccess() || role.atLeast(WorkspaceRole.ADMIN)) {
            return true;
        }
        return grants.existsByDataSourceIdAndUserId(ds.getId(), currentUser.id());
    }
}
