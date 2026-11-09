package com.queryai.dashboard;

import com.queryai.audit.AuditAction;
import com.queryai.audit.AuditService;
import com.queryai.common.ApiException;
import com.queryai.dashboard.DashboardDtos.*;
import com.queryai.history.QuerySource;
import com.queryai.savedquery.SavedQuery;
import com.queryai.savedquery.SavedQueryRepository;
import com.queryai.savedquery.SavedQueryService;
import com.queryai.savedquery.Visibility;
import com.queryai.security.CurrentUser;
import com.queryai.sql.SqlDtos.ExecuteResponse;
import com.queryai.user.User;
import com.queryai.user.UserRepository;
import com.queryai.workspace.WorkspaceAccessService;
import com.queryai.workspace.WorkspaceRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class DashboardService {
    private final DashboardRepository dashboards;
    private final DashboardWidgetRepository widgets;
    private final SavedQueryRepository savedQueries;
    private final SavedQueryService savedQueryService;
    private final WorkspaceAccessService workspaceAccess;
    private final UserRepository users;
    private final CurrentUser currentUser;
    private final AuditService audit;

    public DashboardService(DashboardRepository dashboards, DashboardWidgetRepository widgets, SavedQueryRepository savedQueries,
                            SavedQueryService savedQueryService, WorkspaceAccessService workspaceAccess, UserRepository users,
                            CurrentUser currentUser, AuditService audit) {
        this.dashboards = dashboards;
        this.widgets = widgets;
        this.savedQueries = savedQueries;
        this.savedQueryService = savedQueryService;
        this.workspaceAccess = workspaceAccess;
        this.users = users;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<DashboardSummary> list(Long workspaceId) {
        WorkspaceRole role = workspaceAccess.require(workspaceId, WorkspaceRole.VIEWER);
        return dashboards.findVisible(workspaceId, currentUser.id()).stream().map(d -> summary(d, role)).toList();
    }

    @Transactional(readOnly = true)
    public DashboardDetail get(Long id) {
        Dashboard d = loadVisible(id);
        return detail(d);
    }

    @Transactional
    public DashboardSummary create(DashboardRequest r) {
        WorkspaceRole role = workspaceAccess.require(r.workspaceId(), WorkspaceRole.ANALYST);
        Dashboard d = new Dashboard();
        d.setWorkspaceId(r.workspaceId());
        d.setOwnerId(currentUser.id());
        d.setName(r.name().trim());
        d.setDescription(r.description());
        d.setVisibility(r.visibility() == null ? Visibility.PRIVATE : r.visibility());
        dashboards.save(d);
        audit.record(AuditAction.DASHBOARD_CREATED, d.getWorkspaceId(), "DASHBOARD", d.getId(), Map.of("name", d.getName()));
        return summary(d, role);
    }

    @Transactional
    public DashboardSummary update(Long id, DashboardRequest r) {
        Dashboard d = loadEditable(id);
        if (r.visibility() == Visibility.WORKSPACE) {
            ensureWidgetsShareable(d.getId());
        }
        d.setName(r.name().trim());
        d.setDescription(r.description());
        d.setVisibility(r.visibility() == null ? d.getVisibility() : r.visibility());
        audit.record(AuditAction.DASHBOARD_UPDATED, d.getWorkspaceId(), "DASHBOARD", id,
                Map.of("name", d.getName(), "visibility", d.getVisibility().name()));
        return summary(d, workspaceAccess.roleIn(d.getWorkspaceId()));
    }

    @Transactional
    public void delete(Long id) {
        Dashboard d = loadEditable(id);
        audit.record(AuditAction.DASHBOARD_DELETED, d.getWorkspaceId(), "DASHBOARD", id, Map.of("name", d.getName()));
        dashboards.delete(d);
    }

    @Transactional
    public DashboardDetail addWidget(Long dashboardId, WidgetRequest r) {
        Dashboard d = loadEditable(dashboardId);
        SavedQuery q = usableQuery(d, r.savedQueryId());
        DashboardWidget w = new DashboardWidget();
        w.setDashboardId(d.getId());
        w.setSavedQueryId(q.getId());
        applyWidget(w, r);
        if (r.y() == null) {
            // place new widgets below existing ones
            int bottom = widgets.findByDashboardIdOrderByPosYAscPosXAsc(d.getId()).stream()
                    .mapToInt(x -> x.getPosY() + x.getHeight()).max().orElse(0);
            w.setPosY(bottom);
        }
        widgets.save(w);
        d.setUpdatedAt(java.time.Instant.now());
        audit.record(AuditAction.DASHBOARD_UPDATED, d.getWorkspaceId(), "DASHBOARD", d.getId(), Map.of("widgetAdded", w.getTitle()));
        return detail(d);
    }

    @Transactional
    public DashboardDetail updateWidget(Long dashboardId, Long widgetId, WidgetRequest r) {
        Dashboard d = loadEditable(dashboardId);
        DashboardWidget w = widget(d, widgetId);
        if (!w.getSavedQueryId().equals(r.savedQueryId())) {
            w.setSavedQueryId(usableQuery(d, r.savedQueryId()).getId());
        }
        applyWidget(w, r);
        d.setUpdatedAt(java.time.Instant.now());
        return detail(d);
    }

    @Transactional
    public DashboardDetail deleteWidget(Long dashboardId, Long widgetId) {
        Dashboard d = loadEditable(dashboardId);
        widgets.delete(widget(d, widgetId));
        d.setUpdatedAt(java.time.Instant.now());
        return detail(d);
    }

    @Transactional
    public DashboardDetail saveLayout(Long dashboardId, LayoutRequest request) {
        Dashboard d = loadEditable(dashboardId);
        Map<Long, DashboardWidget> byId = new HashMap<>();
        widgets.findByDashboardIdOrderByPosYAscPosXAsc(d.getId()).forEach(w -> byId.put(w.getId(), w));
        for (LayoutItem item : request.items()) {
            DashboardWidget w = byId.get(item.id());
            if (w == null) {
                throw ApiException.badRequest("Widget " + item.id() + " does not belong to this dashboard");
            }
            w.setPosX(item.x());
            w.setPosY(item.y());
            w.setWidth(item.w());
            w.setHeight(item.h());
        }
        d.setUpdatedAt(java.time.Instant.now());
        audit.record(AuditAction.DASHBOARD_UPDATED, d.getWorkspaceId(), "DASHBOARD", d.getId(), Map.of("layout", request.items().size()));
        return detail(d);
    }

    /** Executes the widget's saved query live against the database. */
    public ExecuteResponse widgetData(Long dashboardId, Long widgetId) {
        DashboardWidget w = loadWidgetTx(dashboardId, widgetId);
        return savedQueryService.run(w.getSavedQueryId(), null, QuerySource.DASHBOARD);
    }

    @Transactional(readOnly = true)
    public DashboardWidget loadWidgetTx(Long dashboardId, Long widgetId) {
        Dashboard d = loadVisible(dashboardId);
        return widget(d, widgetId);
    }

    private SavedQuery usableQuery(Dashboard d, Long savedQueryId) {
        SavedQuery q = savedQueries.findById(savedQueryId).orElseThrow(() -> ApiException.notFound("Saved query"));
        if (!q.getWorkspaceId().equals(d.getWorkspaceId())) {
            throw ApiException.badRequest("The saved query belongs to a different workspace");
        }
        if (q.getVisibility() == Visibility.PRIVATE && !Objects.equals(q.getOwnerId(), currentUser.id())) {
            throw ApiException.notFound("Saved query");
        }
        if (d.getVisibility() == Visibility.WORKSPACE && q.getVisibility() == Visibility.PRIVATE) {
            throw ApiException.badRequest("'" + q.getName() + "' is private. Share it with the workspace before adding it to a shared dashboard.");
        }
        return q;
    }

    private void ensureWidgetsShareable(Long dashboardId) {
        for (DashboardWidget w : widgets.findByDashboardIdOrderByPosYAscPosXAsc(dashboardId)) {
            savedQueries.findById(w.getSavedQueryId()).ifPresent(q -> {
                if (q.getVisibility() == Visibility.PRIVATE) {
                    throw ApiException.badRequest("Widget '" + w.getTitle() + "' uses the private query '" + q.getName()
                            + "'. Share that query with the workspace before sharing this dashboard.");
                }
            });
        }
    }

    private static void applyWidget(DashboardWidget w, WidgetRequest r) {
        w.setTitle(r.title().trim());
        w.setChartType(r.chartType());
        w.setXColumn(r.xColumn() == null || r.xColumn().isBlank() ? null : r.xColumn());
        w.setYColumns(r.yColumns() == null || r.yColumns().isEmpty() ? null : String.join(",", r.yColumns()));
        if (r.x() != null) {
            w.setPosX(r.x());
        }
        if (r.y() != null) {
            w.setPosY(r.y());
        }
        if (r.w() != null) {
            w.setWidth(r.w());
        }
        if (r.h() != null) {
            w.setHeight(r.h());
        }
    }

    private DashboardWidget widget(Dashboard d, Long widgetId) {
        DashboardWidget w = widgets.findById(widgetId).orElseThrow(() -> ApiException.notFound("Widget"));
        if (!w.getDashboardId().equals(d.getId())) {
            throw ApiException.notFound("Widget");
        }
        return w;
    }

    private Dashboard loadVisible(Long id) {
        Dashboard d = dashboards.findById(id).orElseThrow(() -> ApiException.notFound("Dashboard"));
        workspaceAccess.requireMember(d.getWorkspaceId());
        if (d.getVisibility() == Visibility.PRIVATE && !Objects.equals(d.getOwnerId(), currentUser.id())) {
            throw ApiException.notFound("Dashboard");
        }
        return d;
    }

    private Dashboard loadEditable(Long id) {
        Dashboard d = loadVisible(id);
        WorkspaceRole role = workspaceAccess.roleIn(d.getWorkspaceId());
        if (!Objects.equals(d.getOwnerId(), currentUser.id()) && !role.atLeast(WorkspaceRole.ADMIN)) {
            throw ApiException.forbidden("Only the owner or a workspace admin can modify this dashboard");
        }
        return d;
    }

    private DashboardDetail detail(Dashboard d) {
        Map<Long, String> names = new HashMap<>();
        List<DashboardWidget> list = widgets.findByDashboardIdOrderByPosYAscPosXAsc(d.getId());
        savedQueries.findAllById(list.stream().map(DashboardWidget::getSavedQueryId).toList()).forEach(q -> names.put(q.getId(), q.getName()));
        List<WidgetDto> dtos = list.stream().map(w -> new WidgetDto(w.getId(), w.getSavedQueryId(), names.get(w.getSavedQueryId()),
                w.getTitle(), w.getChartType(), w.getXColumn(),
                w.getYColumns() == null ? List.of() : Arrays.asList(w.getYColumns().split(",")),
                w.getPosX(), w.getPosY(), w.getWidth(), w.getHeight())).toList();
        return new DashboardDetail(summary(d, workspaceAccess.roleIn(d.getWorkspaceId())), dtos);
    }

    private DashboardSummary summary(Dashboard d, WorkspaceRole role) {
        String owner = d.getOwnerId() == null ? null : users.findById(d.getOwnerId()).map(User::getFullName).orElse(null);
        boolean canEdit = Objects.equals(d.getOwnerId(), currentUser.id()) || role.atLeast(WorkspaceRole.ADMIN);
        return new DashboardSummary(d.getId(), d.getWorkspaceId(), d.getOwnerId(), owner, d.getName(), d.getDescription(),
                d.getVisibility(), widgets.countByDashboardId(d.getId()), canEdit, d.getUpdatedAt());
    }
}
