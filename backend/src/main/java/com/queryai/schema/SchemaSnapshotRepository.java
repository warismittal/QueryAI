package com.queryai.schema;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SchemaSnapshotRepository extends JpaRepository<SchemaSnapshotEntity, Long> {
    Optional<SchemaSnapshotEntity> findByDataSourceId(Long dataSourceId);
}
