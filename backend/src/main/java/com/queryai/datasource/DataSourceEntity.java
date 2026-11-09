package com.queryai.datasource;

import com.queryai.datasource.DatabaseType.SslMode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/** Connection definition for a target business database. The password is stored AES-GCM encrypted. */
@Entity
@Table(name = "data_sources")
@Getter
@Setter
@NoArgsConstructor
public class DataSourceEntity {
    public enum Status { UNTESTED, CONNECTED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "db_type", nullable = false)
    private DatabaseType dbType;

    @Column(nullable = false)
    private String host;

    @Column(nullable = false)
    private int port;

    @Column(name = "database_name", nullable = false)
    private String databaseName;

    @Column(name = "schema_name")
    private String schemaName;

    @Column(nullable = false)
    private String username;

    @Column(name = "password_encrypted", nullable = false)
    private String passwordEncrypted;

    @Enumerated(EnumType.STRING)
    @Column(name = "ssl_mode", nullable = false)
    private SslMode sslMode = SslMode.DISABLE;

    @Column(name = "restricted_access", nullable = false)
    private boolean restrictedAccess;

    @Column(name = "max_rows", nullable = false)
    private int maxRows = 5000;

    @Column(name = "query_timeout_sec", nullable = false)
    private int queryTimeoutSec = 30;

    /** When true, personal data columns are masked for everyone below workspace ADMIN. */
    @Column(name = "pii_masking_enabled", nullable = false)
    private boolean piiMaskingEnabled = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.UNTESTED;

    @Column(name = "last_tested_at")
    private Instant lastTestedAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "server_version")
    private String serverVersion;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Effective schema: PostgreSQL defaults to {@code public}; MySQL uses the database itself. */
    public String effectiveSchema() {
        if (dbType == DatabaseType.MYSQL) {
            return databaseName;
        }
        return schemaName == null || schemaName.isBlank() ? "public" : schemaName;
    }
}
