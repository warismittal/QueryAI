package com.queryai.datasource;

import com.queryai.datasource.DatabaseType.SslMode;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;

public final class DataSourceDtos {
    private DataSourceDtos() {
    }

    /** Identifiers are restricted to a safe character set so they can never inject JDBC URL parameters. */
    public static final String IDENTIFIER = "^[A-Za-z0-9_$.\\-]{1,128}$";
    public static final String HOST = "^[A-Za-z0-9.\\-:\\[\\]]{1,255}$";

    public record DataSourceDto(Long id, Long workspaceId, String name, DatabaseType dbType, String host, int port,
                                String databaseName, String schemaName, String effectiveSchema, String username,
                                boolean hasPassword, SslMode sslMode, boolean restrictedAccess, int maxRows,
                                int queryTimeoutSec, DataSourceEntity.Status status, Instant lastTestedAt,
                                String lastError, String serverVersion, boolean canQuery, boolean canManage,
                                Instant createdAt, Instant updatedAt, boolean piiMaskingEnabled) {
    }

    public record DataSourceRequest(
            @NotNull Long workspaceId,
            @NotBlank @Size(max = 120) String name,
            @NotNull DatabaseType dbType,
            @NotBlank @Pattern(regexp = HOST, message = "Host contains invalid characters") String host,
            @Min(1) @Max(65535) int port,
            @NotBlank @Pattern(regexp = IDENTIFIER, message = "Database name contains invalid characters") String databaseName,
            @Pattern(regexp = "^$|" + IDENTIFIER, message = "Schema contains invalid characters") String schemaName,
            @NotBlank @Pattern(regexp = IDENTIFIER, message = "Username contains invalid characters") String username,
            /* Optional on update: blank keeps the stored password. */
            @Size(max = 512) String password,
            SslMode sslMode,
            boolean restrictedAccess,
            @Min(1) @Max(50000) Integer maxRows,
            @Min(1) @Max(600) Integer queryTimeoutSec) {
    }

    /** Ad-hoc test of unsaved settings. When {@code dataSourceId} is given and password is blank, the stored password is used. */
    public record TestConnectionRequest(
            Long workspaceId,
            Long dataSourceId,
            @NotNull DatabaseType dbType,
            @NotBlank @Pattern(regexp = HOST, message = "Host contains invalid characters") String host,
            @Min(1) @Max(65535) int port,
            @NotBlank @Pattern(regexp = IDENTIFIER, message = "Database name contains invalid characters") String databaseName,
            @Pattern(regexp = "^$|" + IDENTIFIER, message = "Schema contains invalid characters") String schemaName,
            @NotBlank @Pattern(regexp = IDENTIFIER, message = "Username contains invalid characters") String username,
            @Size(max = 512) String password,
            SslMode sslMode) {
    }

    public record DiagnosticStep(String name, String status, String message, long durationMs) {
    }

    public record ConnectionTestResult(boolean success, String serverVersion, long latencyMs, int tableCount,
                                       List<String> availableSchemas, List<DiagnosticStep> steps, String hint) {
    }

    public record GrantsDto(boolean restrictedAccess, List<GrantedUser> users) {
    }

    public record GrantedUser(Long userId, String email, String fullName) {
    }

    public record UpdateGrantsRequest(boolean restrictedAccess, @NotNull List<Long> userIds) {
    }
}
