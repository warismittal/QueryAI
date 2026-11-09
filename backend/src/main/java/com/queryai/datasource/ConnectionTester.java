package com.queryai.datasource;

import com.queryai.config.QueryAiProperties;
import com.queryai.datasource.DataSourceDtos.ConnectionTestResult;
import com.queryai.datasource.DataSourceDtos.DiagnosticStep;
import org.springframework.stereotype.Component;

import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Step-by-step connection diagnostics: host policy, DNS, TCP reachability, authentication, a probe query and metadata
 * access. Each step reports its own outcome so users can see exactly where a connection fails.
 */
@Component
public class ConnectionTester {
    private final HostGuard hostGuard;
    private final TargetConnectionRegistry registry;
    private final int timeoutSeconds;

    public ConnectionTester(HostGuard hostGuard, TargetConnectionRegistry registry, QueryAiProperties properties) {
        this.hostGuard = hostGuard;
        this.registry = registry;
        this.timeoutSeconds = properties.datasource().connectTimeoutSeconds();
    }

    public ConnectionTestResult test(ConnectionSettings cs) {
        List<DiagnosticStep> steps = new ArrayList<>();
        long overall = System.currentTimeMillis();

        long t = System.currentTimeMillis();
        String blocked = hostGuard.check(cs.host());
        if (blocked != null) {
            steps.add(new DiagnosticStep("Host policy", "FAILED", blocked, elapsed(t)));
            return fail(steps, "Use a different host. Link-local and administrator-blocked hosts are refused.");
        }
        steps.add(new DiagnosticStep("Host policy", "OK", "Host is allowed", elapsed(t)));

        t = System.currentTimeMillis();
        try {
            InetAddress address = InetAddress.getByName(cs.host());
            steps.add(new DiagnosticStep("DNS resolution", "OK", cs.host() + " → " + address.getHostAddress(), elapsed(t)));
        } catch (UnknownHostException e) {
            steps.add(new DiagnosticStep("DNS resolution", "FAILED", "Could not resolve host " + cs.host(), elapsed(t)));
            return fail(steps, "Check the hostname for typos. Inside Docker, use the service name (e.g. 'postgres') rather than 'localhost'.");
        }

        t = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(cs.host(), cs.port()), timeoutSeconds * 1000);
            steps.add(new DiagnosticStep("Network reachability", "OK", "TCP port " + cs.port() + " is open", elapsed(t)));
        } catch (SocketTimeoutException e) {
            steps.add(new DiagnosticStep("Network reachability", "FAILED", "Timed out connecting to port " + cs.port(), elapsed(t)));
            return fail(steps, "A firewall or security group is probably blocking the port, or the server is not listening on this interface.");
        } catch (ConnectException e) {
            steps.add(new DiagnosticStep("Network reachability", "FAILED", "Connection refused on port " + cs.port(), elapsed(t)));
            return fail(steps, "The host is reachable but nothing is listening on port " + cs.port() + ". Default port for "
                    + cs.type().displayName() + " is " + cs.type().defaultPort() + ".");
        } catch (Exception e) {
            steps.add(new DiagnosticStep("Network reachability", "FAILED", e.getMessage(), elapsed(t)));
            return fail(steps, "The server could not be reached.");
        }

        t = System.currentTimeMillis();
        try (Connection connection = registry.openDirect(cs)) {
            steps.add(new DiagnosticStep("Authentication", "OK", "Logged in as " + cs.username(), elapsed(t)));

            t = System.currentTimeMillis();
            try (Statement st = connection.createStatement()) {
                st.setQueryTimeout(timeoutSeconds);
                try (ResultSet rs = st.executeQuery("SELECT 1")) {
                    rs.next();
                }
            }
            steps.add(new DiagnosticStep("Probe query", "OK", "SELECT 1 succeeded", elapsed(t)));

            t = System.currentTimeMillis();
            DatabaseMetaData meta = connection.getMetaData();
            String version = meta.getDatabaseProductName() + " " + meta.getDatabaseProductVersion();
            List<String> schemas = listSchemas(cs, meta);
            String schema = cs.effectiveSchema();
            int tableCount = countTables(cs, meta, schema);
            if (cs.type() == DatabaseType.POSTGRESQL && !schemas.contains(schema)) {
                steps.add(new DiagnosticStep("Schema access", "FAILED", "Schema '" + schema + "' not found or not visible", elapsed(t)));
                return new ConnectionTestResult(false, version, elapsed(overall), 0, schemas, steps,
                        "Pick one of the available schemas: " + String.join(", ", schemas));
            }
            steps.add(new DiagnosticStep("Schema access", tableCount > 0 ? "OK" : "WARNING",
                    tableCount + " tables/views visible in '" + schema + "'", elapsed(t)));
            String hint = tableCount == 0 ? "Connected, but no tables are visible. Check the schema name and that the user has SELECT privileges." : null;
            return new ConnectionTestResult(true, version, elapsed(overall), tableCount, schemas, steps, hint);
        } catch (SQLException e) {
            steps.add(new DiagnosticStep("Authentication", "FAILED", sanitize(e.getMessage()), elapsed(t)));
            return fail(steps, hintFor(cs, e));
        }
    }

    private List<String> listSchemas(ConnectionSettings cs, DatabaseMetaData meta) throws SQLException {
        List<String> schemas = new ArrayList<>();
        if (cs.type() == DatabaseType.MYSQL) {
            schemas.add(cs.database());
            return schemas;
        }
        try (ResultSet rs = meta.getSchemas()) {
            while (rs.next()) {
                String s = rs.getString("TABLE_SCHEM");
                if (!s.startsWith("pg_") && !s.equals("information_schema")) {
                    schemas.add(s);
                }
            }
        }
        return schemas;
    }

    private int countTables(ConnectionSettings cs, DatabaseMetaData meta, String schema) throws SQLException {
        String catalog = cs.type() == DatabaseType.MYSQL ? cs.database() : null;
        String schemaPattern = cs.type() == DatabaseType.MYSQL ? null : schema;
        int count = 0;
        try (ResultSet rs = meta.getTables(catalog, schemaPattern, "%", new String[]{"TABLE", "VIEW"})) {
            while (rs.next()) {
                count++;
            }
        }
        return count;
    }

    static String hintFor(ConnectionSettings cs, SQLException e) {
        String state = e.getSQLState() == null ? "" : e.getSQLState();
        int code = e.getErrorCode();
        if ("28P01".equals(state) || "28000".equals(state) || code == 1045) {
            return "Authentication failed. Verify the username and password, and that the user may connect from QueryAI's network address.";
        }
        if ("3D000".equals(state) || code == 1049) {
            return "The database '" + cs.database() + "' does not exist on this server.";
        }
        if (state.startsWith("08")) {
            return "The connection was rejected. If the server requires TLS, set SSL mode to REQUIRE.";
        }
        return "The database rejected the connection: " + sanitize(e.getMessage());
    }

    private static String sanitize(String message) {
        if (message == null) {
            return "Unknown error";
        }
        return message.length() > 400 ? message.substring(0, 400) + "…" : message;
    }

    private static ConnectionTestResult fail(List<DiagnosticStep> steps, String hint) {
        long total = steps.stream().mapToLong(DiagnosticStep::durationMs).sum();
        return new ConnectionTestResult(false, null, total, 0, List.of(), steps, hint);
    }

    private static long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }
}
