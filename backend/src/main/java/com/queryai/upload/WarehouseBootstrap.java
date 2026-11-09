package com.queryai.upload;

import com.queryai.config.QueryAiProperties.Warehouse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Prepares the warehouse on start-up: makes sure the read-only role exists and, on single-database hosting
 * ({@code queryai.warehouse.bootstrap-demo=true}, e.g. Render's free PostgreSQL), loads the bundled demo e-commerce
 * dataset into its own schema once. Failures are logged, never fatal: QueryAI still starts and explains what is missing.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WarehouseBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(WarehouseBootstrap.class);

    private final WarehouseService warehouse;

    public WarehouseBootstrap(WarehouseService warehouse) {
        this.warehouse = warehouse;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!warehouse.enabled()) {
            return;
        }
        Warehouse cfg = warehouse.config();
        try (Connection con = warehouse.adminConnection()) {
            ensureReaderRole(con, cfg);
            if (cfg.bootstrapDemo()) {
                loadDemo(con, cfg.demoSchema() == null || cfg.demoSchema().isBlank() ? "commerce" : cfg.demoSchema());
            }
        } catch (SQLException | RuntimeException e) {
            log.error("Warehouse is not reachable ({}); CSV uploads will fail until it is", e.getMessage());
        }
    }

    private void ensureReaderRole(Connection con, Warehouse cfg) {
        String reader = warehouse.reader();
        try (PreparedStatement ps = con.prepareStatement("SELECT 1 FROM pg_roles WHERE rolname = ?")) {
            ps.setString(1, reader);
            boolean exists;
            try (ResultSet rs = ps.executeQuery()) {
                exists = rs.next();
            }
            try (Statement st = con.createStatement()) {
                String password = WarehouseService.literal(cfg.readerPassword());
                if (!exists) {
                    st.execute("CREATE ROLE " + WarehouseService.q(reader) + " LOGIN PASSWORD " + password);
                    log.info("Created read-only warehouse role '{}'", reader);
                }
                if (cfg.bootstrapDemo()) {
                    // keep the role's password in sync with the configured secret on managed hosting
                    st.execute("ALTER ROLE " + WarehouseService.q(reader) + " WITH LOGIN PASSWORD " + password);
                }
                st.execute("ALTER ROLE " + WarehouseService.q(reader) + " SET default_transaction_read_only = on");
                String db;
                try (ResultSet rs = st.executeQuery("SELECT current_database()")) {
                    rs.next();
                    db = rs.getString(1);
                }
                st.execute("GRANT CONNECT ON DATABASE " + WarehouseService.q(db) + " TO " + WarehouseService.q(reader));
            }
        } catch (SQLException e) {
            log.warn("Could not create or update the read-only role '{}' ({}). Create it manually: "
                    + "CREATE ROLE {} LOGIN PASSWORD '...'", reader, e.getMessage(), reader);
        }
    }

    private void loadDemo(Connection con, String schema) throws SQLException {
        WarehouseService.requireSafe(schema);
        boolean present;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT 1 FROM information_schema.tables WHERE table_schema = ? AND table_name = 'product_reviews'")) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                present = rs.next();
            }
        }
        if (!present) {
            long started = System.currentTimeMillis();
            boolean auto = con.getAutoCommit();
            con.setAutoCommit(false);
            try (Statement st = con.createStatement()) {
                st.execute("CREATE SCHEMA IF NOT EXISTS " + WarehouseService.q(schema));
                st.execute("SET LOCAL search_path TO " + WarehouseService.q(schema));
                for (String file : List.of("demo/postgres-schema.sql", "demo/postgres-data.sql")) {
                    for (String sql : statements(read(file))) {
                        st.execute(sql);
                    }
                }
                con.commit();
                log.info("Loaded the sample e-commerce dataset into schema '{}' in {} ms", schema, System.currentTimeMillis() - started);
            } catch (SQLException | IOException e) {
                con.rollback();
                log.error("Could not load the sample dataset into schema '{}': {}", schema, e.getMessage());
                return;
            } finally {
                con.setAutoCommit(auto);
            }
        }
        try (Statement st = con.createStatement()) {
            warehouse.grantRead(st, schema);
        } catch (SQLException e) {
            log.warn("Could not grant read access on schema '{}' to the reader role: {}", schema, e.getMessage());
        }
    }

    private static String read(String path) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Splits a generated SQL script on statement-ending semicolons; drops transaction control and ANALYZE. */
    static List<String> statements(String script) {
        List<String> out = new ArrayList<>();
        for (String part : script.split(";\\s*\\R")) {
            String sql = part.strip();
            String withoutComments = sql.replaceAll("(?m)^--.*$", "").strip();
            if (withoutComments.isEmpty()) {
                continue;
            }
            String upper = withoutComments.toUpperCase();
            if (upper.equals("BEGIN") || upper.equals("COMMIT") || upper.equals("ANALYZE") || upper.startsWith("SET ")) {
                continue;
            }
            out.add(withoutComments.endsWith(";") ? withoutComments.substring(0, withoutComments.length() - 1) : withoutComments);
        }
        return out;
    }
}
