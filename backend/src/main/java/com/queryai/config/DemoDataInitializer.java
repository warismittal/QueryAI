package com.queryai.config;

import com.queryai.dashboard.Dashboard;
import com.queryai.dashboard.DashboardRepository;
import com.queryai.dashboard.DashboardWidget;
import com.queryai.dashboard.DashboardWidgetRepository;
import com.queryai.datasource.CredentialCipher;
import com.queryai.datasource.DataSourceEntity;
import com.queryai.datasource.DataSourceRepository;
import com.queryai.datasource.DatabaseType;
import com.queryai.evaluation.EvaluationCase;
import com.queryai.evaluation.EvaluationCaseRepository;
import com.queryai.savedquery.SavedQuery;
import com.queryai.savedquery.SavedQueryRepository;
import com.queryai.savedquery.SavedQueryVersion;
import com.queryai.savedquery.SavedQueryVersionRepository;
import com.queryai.savedquery.Visibility;
import com.queryai.schema.GlossaryTerm;
import com.queryai.schema.GlossaryTermRepository;
import com.queryai.user.SystemRole;
import com.queryai.user.User;
import com.queryai.user.UserRepository;
import com.queryai.workspace.Workspace;
import com.queryai.workspace.WorkspaceMember;
import com.queryai.workspace.WorkspaceMemberRepository;
import com.queryai.workspace.WorkspaceRole;
import com.queryai.workspace.WorkspaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Optional first-run setup for local demos (queryai.demo.enabled=true): a demo account, a workspace connected to the
 * bundled e-commerce database(s), a business glossary, saved queries, a shared dashboard and evaluation cases.
 * The connection is real — every number shown in the UI comes from the demo database, not from this class.
 */
@Component
@ConditionalOnProperty(prefix = "queryai.demo", name = "enabled", havingValue = "true")
public class DemoDataInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private final QueryAiProperties properties;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final WorkspaceService workspaceService;
    private final DataSourceRepository dataSources;
    private final CredentialCipher cipher;
    private final GlossaryTermRepository glossary;
    private final SavedQueryRepository savedQueries;
    private final SavedQueryVersionRepository versions;
    private final DashboardRepository dashboards;
    private final DashboardWidgetRepository widgets;
    private final EvaluationCaseRepository evaluationCases;
    private final WorkspaceMemberRepository members;

    public DemoDataInitializer(QueryAiProperties properties, UserRepository users, PasswordEncoder passwordEncoder,
                               WorkspaceService workspaceService, DataSourceRepository dataSources, CredentialCipher cipher,
                               GlossaryTermRepository glossary, SavedQueryRepository savedQueries,
                               SavedQueryVersionRepository versions, DashboardRepository dashboards,
                               DashboardWidgetRepository widgets, EvaluationCaseRepository evaluationCases,
                               WorkspaceMemberRepository members) {
        this.members = members;
        this.properties = properties;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.workspaceService = workspaceService;
        this.dataSources = dataSources;
        this.cipher = cipher;
        this.glossary = glossary;
        this.savedQueries = savedQueries;
        this.versions = versions;
        this.dashboards = dashboards;
        this.widgets = widgets;
        this.evaluationCases = evaluationCases;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        QueryAiProperties.Demo demo = properties.demo();
        if (users.findByEmailIgnoreCase(demo.email()).isPresent()) {
            return;
        }
        User user = new User();
        user.setEmail(demo.email());
        user.setFullName(demo.publicMode() ? "Guest Analyst" : "Riya Sharma");
        user.setJobTitle("Analytics Lead");
        user.setPasswordHash(passwordEncoder.encode(demo.password()));
        // locally the demo account administers the installation so every screen can be explored;
        // on a public deployment it is a regular analyst in a workspace owned by a separate administrator
        user.setSystemRole(demo.publicMode() ? SystemRole.USER : SystemRole.ADMIN);
        users.save(user);

        Workspace ws;
        if (demo.publicMode()) {
            User owner = new User();
            owner.setEmail("owner@queryai.local");
            owner.setFullName("Workspace Owner");
            owner.setJobTitle("Administrator");
            byte[] secret = new byte[24];
            new java.security.SecureRandom().nextBytes(secret);
            owner.setPasswordHash(passwordEncoder.encode(java.util.Base64.getEncoder().encodeToString(secret)));
            owner.setSystemRole(SystemRole.ADMIN);
            users.save(owner);
            ws = workspaceService.createPersonalWorkspace(owner);
            WorkspaceMember member = new WorkspaceMember();
            member.setWorkspaceId(ws.getId());
            member.setUser(user);
            member.setRole(WorkspaceRole.ANALYST);
            members.save(member);
        } else {
            ws = workspaceService.createPersonalWorkspace(user);
        }
        ws.setName("Acme Commerce Analytics");
        ws.setDescription("Sales, customers, products and inventory for Acme Commerce");

        DataSourceEntity pg = createSource(ws, user, "E-commerce (PostgreSQL)", DatabaseType.POSTGRESQL, demo.postgres(),
                demo.postgres().schema() == null || demo.postgres().schema().isBlank() ? "public" : demo.postgres().schema());
        seedGlossary(pg, user);
        seedEvaluation(pg, user);
        seedSavedQueriesAndDashboard(ws, pg, user);

        if (demo.mysql() != null && demo.mysql().enabled()) {
            DataSourceEntity my = createSource(ws, user, "E-commerce (MySQL)", DatabaseType.MYSQL, demo.mysql(), null);
            seedGlossary(my, user);
        }
        log.info("Starter workspace ready: sign in as {} (workspace '{}')", demo.email(), ws.getName());
    }

    private DataSourceEntity createSource(Workspace ws, User user, String name, DatabaseType type, QueryAiProperties.Target t, String schema) {
        DataSourceEntity ds = new DataSourceEntity();
        ds.setWorkspaceId(ws.getId());
        ds.setName(name);
        ds.setDbType(type);
        ds.setHost(t.host());
        ds.setPort(t.port());
        ds.setDatabaseName(t.database());
        ds.setSchemaName(schema);
        ds.setUsername(t.username());
        ds.setPasswordEncrypted(cipher.encrypt(t.password()));
        ds.setCreatedBy(user.getId());
        return dataSources.save(ds);
    }

    private void seedGlossary(DataSourceEntity ds, User user) {
        term(ds, user, "revenue", "Money from completed sales: the sum of orders.total_amount, excluding cancelled and returned orders.",
                "SUM(o.total_amount) ... WHERE o.status NOT IN ('cancelled', 'returned')");
        term(ds, user, "spending", "What a customer spent: sum of their orders.total_amount excluding cancelled and returned orders.",
                "SUM(o.total_amount) grouped by o.customer_id, status NOT IN ('cancelled', 'returned')");
        term(ds, user, "average order value", "Revenue divided by the number of non-cancelled, non-returned orders (AOV).", null);
        term(ds, user, "active customer", "A customer with at least one non-cancelled order in the last 90 days.", null);
        term(ds, user, "gross margin", "Line revenue minus product cost: SUM(oi.line_total - oi.quantity * p.unit_cost); margin % divides by SUM(oi.line_total).",
                "JOIN order_items oi with products p ON p.id = oi.product_id");
        term(ds, user, "low stock", "Inventory rows where quantity_on_hand is below reorder_level.", "i.quantity_on_hand < i.reorder_level");
    }

    private void term(DataSourceEntity ds, User user, String term, String definition, String hint) {
        GlossaryTerm t = new GlossaryTerm();
        t.setDataSourceId(ds.getId());
        t.setTerm(term);
        t.setDefinition(definition);
        t.setSqlHint(hint);
        t.setCreatedBy(user.getId());
        glossary.save(t);
    }

    private void seedEvaluation(DataSourceEntity ds, User user) {
        evalCase(ds, user, "How many orders were placed in 2025?",
                "SELECT COUNT(*) AS orders FROM orders WHERE order_date >= DATE '2025-01-01' AND order_date < DATE '2026-01-01'");
        evalCase(ds, user, "What is the total revenue by sales channel?",
                "SELECT channel, SUM(total_amount) AS revenue FROM orders WHERE status NOT IN ('cancelled', 'returned') GROUP BY channel");
        evalCase(ds, user, "How many customers are in each segment?",
                "SELECT segment, COUNT(*) AS customers FROM customers GROUP BY segment");
        evalCase(ds, user, "How many products are below their reorder level in each warehouse?",
                "SELECT w.name, COUNT(*) AS low_stock_products FROM inventory i JOIN warehouses w ON w.id = i.warehouse_id "
                        + "WHERE i.quantity_on_hand < i.reorder_level GROUP BY w.name");
        evalCase(ds, user, "What is the average product rating per top-level category?",
                "SELECT COALESCE(parent.name, c.name) AS category, ROUND(AVG(r.rating), 2) AS avg_rating FROM product_reviews r "
                        + "JOIN products p ON p.id = r.product_id JOIN categories c ON c.id = p.category_id "
                        + "LEFT JOIN categories parent ON parent.id = c.parent_category_id GROUP BY COALESCE(parent.name, c.name)");
    }

    private void evalCase(DataSourceEntity ds, User user, String question, String sql) {
        EvaluationCase c = new EvaluationCase();
        c.setDataSourceId(ds.getId());
        c.setQuestion(question);
        c.setExpectedSql(sql);
        c.setCreatedBy(user.getId());
        evaluationCases.save(c);
    }

    private void seedSavedQueriesAndDashboard(Workspace ws, DataSourceEntity ds, User user) {
        SavedQuery monthly = saved(ws, ds, user, "Monthly revenue trend", "Executive", "LINE", false,
                "Revenue and order volume per month",
                """
                SELECT CAST(date_trunc('month', o.order_date) AS date) AS month,
                       ROUND(SUM(o.total_amount), 2) AS revenue,
                       COUNT(*) AS orders
                FROM orders o
                WHERE o.status NOT IN ('cancelled', 'returned')
                GROUP BY 1
                ORDER BY 1""");
        SavedQuery byCategory = saved(ws, ds, user, "Revenue by top-level category", "Executive", "BAR", false,
                "Line-item revenue rolled up to top-level categories",
                """
                SELECT COALESCE(parent.name, c.name) AS category,
                       ROUND(SUM(oi.line_total), 2) AS revenue
                FROM order_items oi
                JOIN orders o ON o.id = oi.order_id
                JOIN products p ON p.id = oi.product_id
                JOIN categories c ON c.id = p.category_id
                LEFT JOIN categories parent ON parent.id = c.parent_category_id
                WHERE o.status NOT IN ('cancelled', 'returned')
                GROUP BY 1
                ORDER BY revenue DESC""");
        SavedQuery channels = saved(ws, ds, user, "Orders by channel", "Executive", "PIE", false,
                "Share of orders per sales channel",
                """
                SELECT o.channel, COUNT(*) AS orders
                FROM orders o
                GROUP BY o.channel
                ORDER BY orders DESC""");
        SavedQuery aov = saved(ws, ds, user, "Average order value", "Executive", "METRIC", false,
                "AOV across all completed orders",
                """
                SELECT ROUND(SUM(o.total_amount) / NULLIF(COUNT(*), 0), 2) AS average_order_value,
                       COUNT(*) AS completed_orders
                FROM orders o
                WHERE o.status NOT IN ('cancelled', 'returned')""");
        saved(ws, ds, user, "Revenue between dates (template)", "Templates", "BAR", true,
                "Reusable template: fill in :start_date and :end_date",
                """
                SELECT o.channel, ROUND(SUM(o.total_amount), 2) AS revenue, COUNT(*) AS orders
                FROM orders o
                WHERE o.status NOT IN ('cancelled', 'returned')
                  AND o.order_date >= :start_date AND o.order_date < :end_date
                GROUP BY o.channel
                ORDER BY revenue DESC""");

        Dashboard d = new Dashboard();
        d.setWorkspaceId(ws.getId());
        d.setOwnerId(user.getId());
        d.setName("Executive overview");
        d.setDescription("Live KPIs from the e-commerce database");
        d.setVisibility(Visibility.WORKSPACE);
        dashboards.save(d);
        widget(d, aov, "Average order value", DashboardWidget.ChartType.METRIC, null, null, 0, 0, 4, 3);
        widget(d, channels, "Orders by channel", DashboardWidget.ChartType.PIE, "channel", "orders", 4, 0, 8, 3);
        widget(d, monthly, "Monthly revenue", DashboardWidget.ChartType.LINE, "month", "revenue", 0, 3, 12, 4);
        widget(d, byCategory, "Revenue by category", DashboardWidget.ChartType.BAR, "category", "revenue", 0, 7, 12, 4);
    }

    private SavedQuery saved(Workspace ws, DataSourceEntity ds, User user, String name, String folder, String chart,
                             boolean template, String description, String sql) {
        SavedQuery s = new SavedQuery();
        s.setWorkspaceId(ws.getId());
        s.setDataSourceId(ds.getId());
        s.setOwnerId(user.getId());
        s.setName(name);
        s.setFolder(folder);
        s.setChartType(chart);
        s.setTemplate(template);
        s.setDescription(description);
        s.setSqlText(sql.strip());
        s.setVisibility(Visibility.WORKSPACE);
        savedQueries.save(s);
        SavedQueryVersion v = new SavedQueryVersion();
        v.setSavedQueryId(s.getId());
        v.setVersion(1);
        v.setSqlText(s.getSqlText());
        v.setNote("Initial version");
        v.setCreatedBy(user.getId());
        versions.save(v);
        return s;
    }

    private void widget(Dashboard d, SavedQuery q, String title, DashboardWidget.ChartType type, String x, String y,
                        int posX, int posY, int w, int h) {
        DashboardWidget widget = new DashboardWidget();
        widget.setDashboardId(d.getId());
        widget.setSavedQueryId(q.getId());
        widget.setTitle(title);
        widget.setChartType(type);
        widget.setXColumn(x);
        widget.setYColumns(y);
        widget.setPosX(posX);
        widget.setPosY(posY);
        widget.setWidth(w);
        widget.setHeight(h);
        widgets.save(widget);
    }
}
