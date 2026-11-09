package com.queryai.schema;

import com.queryai.schema.SchemaModel.SchemaSnapshot;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "schema_snapshots")
@Getter
@Setter
@NoArgsConstructor
public class SchemaSnapshotEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_source_id", nullable = false, unique = true)
    private Long dataSourceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private SchemaSnapshot snapshot;

    @Column(name = "table_count", nullable = false)
    private int tableCount;

    @Column(name = "refreshed_at", nullable = false)
    private Instant refreshedAt;
}
