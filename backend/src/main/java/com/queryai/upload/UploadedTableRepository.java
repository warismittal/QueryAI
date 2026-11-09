package com.queryai.upload;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UploadedTableRepository extends JpaRepository<UploadedTable, Long> {
    List<UploadedTable> findByWorkspaceIdOrderByCreatedAtDesc(Long workspaceId);

    Optional<UploadedTable> findByDataSourceIdAndTableName(Long dataSourceId, String tableName);
}
