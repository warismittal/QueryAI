package com.queryai.upload;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** A table QueryAI created in the warehouse from an uploaded file. */
@Entity
@Table(name = "uploaded_tables")
@Getter
@Setter
@NoArgsConstructor
public class UploadedTable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "data_source_id", nullable = false)
    private Long dataSourceId;

    @Column(name = "table_name", nullable = false)
    private String tableName;

    @Column(name = "original_file", nullable = false)
    private String originalFile;

    @Column(name = "row_count", nullable = false)
    private long rowCount;

    @Column(name = "column_count", nullable = false)
    private int columnCount;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
