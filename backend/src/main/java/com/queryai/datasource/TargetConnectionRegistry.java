package com.queryai.datasource;

import com.queryai.common.ApiException;
import com.queryai.config.QueryAiProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns one small, read-only Hikari pool per target data source. Pools are rebuilt automatically when the data source
 * definition changes (detected via its update timestamp) and closed when the data source is deleted.
 */
@Component
public class TargetConnectionRegistry {
    private static final Logger log = LoggerFactory.getLogger(TargetConnectionRegistry.class);

    private final Map<Long, PoolHolder> pools = new ConcurrentHashMap<>();
    private final CredentialCipher cipher;
    private final HostGuard hostGuard;
    private final QueryAiProperties.DataSourceSettings settings;

    public TargetConnectionRegistry(CredentialCipher cipher, HostGuard hostGuard, QueryAiProperties properties) {
        this.cipher = cipher;
        this.hostGuard = hostGuard;
        this.settings = properties.datasource();
    }

    public ConnectionSettings settingsFor(DataSourceEntity ds) {
        return new ConnectionSettings(ds.getDbType(), ds.getHost(), ds.getPort(), ds.getDatabaseName(), ds.getSchemaName(),
                ds.getUsername(), cipher.decrypt(ds.getPasswordEncrypted()), ds.getSslMode());
    }

    public DataSource dataSource(DataSourceEntity ds) {
        String fingerprint = String.valueOf(ds.getUpdatedAt());
        PoolHolder holder = pools.compute(ds.getId(), (id, existing) -> {
            if (existing != null && existing.fingerprint.equals(fingerprint) && !existing.pool.isClosed()) {
                return existing;
            }
            if (existing != null) {
                existing.pool.close();
            }
            return new PoolHolder(createPool(ds), fingerprint);
        });
        return holder.pool;
    }

    private HikariDataSource createPool(DataSourceEntity ds) {
        String blocked = hostGuard.check(ds.getHost());
        if (blocked != null) {
            throw ApiException.forbidden(blocked);
        }
        ConnectionSettings cs = settingsFor(ds);
        HikariConfig config = new HikariConfig();
        config.setPoolName("target-" + ds.getId());
        config.setDriverClassName(cs.type().driverClass());
        config.setJdbcUrl(cs.jdbcUrl(settings.connectTimeoutSeconds()));
        config.setUsername(cs.username());
        config.setPassword(cs.password());
        config.setReadOnly(true);
        config.setAutoCommit(false);
        config.setMaximumPoolSize(Math.max(1, settings.poolSize()));
        config.setMinimumIdle(0);
        config.setIdleTimeout(120_000);
        config.setMaxLifetime(900_000);
        config.setConnectionTimeout(settings.connectTimeoutSeconds() * 1000L);
        config.setInitializationFailTimeout(-1); // do not fail fast; errors surface on first use with a clear message
        return new HikariDataSource(config);
    }

    /** Opens a one-off connection (used by connection tests for unsaved settings). Caller must close it. */
    public Connection openDirect(ConnectionSettings cs) throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", cs.username());
        props.setProperty("password", cs.password() == null ? "" : cs.password());
        try {
            Class.forName(cs.type().driverClass());
        } catch (ClassNotFoundException e) {
            throw new SQLException("JDBC driver not available: " + cs.type().driverClass(), e);
        }
        DriverManager.setLoginTimeout(settings.connectTimeoutSeconds());
        Connection connection = DriverManager.getConnection(cs.jdbcUrl(settings.connectTimeoutSeconds()), props);
        connection.setReadOnly(true);
        return connection;
    }

    public void evict(Long dataSourceId) {
        PoolHolder holder = pools.remove(dataSourceId);
        if (holder != null) {
            holder.pool.close();
            log.info("Closed connection pool for data source {}", dataSourceId);
        }
    }

    @PreDestroy
    public void shutdown() {
        pools.values().forEach(h -> h.pool.close());
        pools.clear();
    }

    private record PoolHolder(HikariDataSource pool, String fingerprint) {
    }
}
