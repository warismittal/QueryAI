package com.queryai.schema;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ColumnAnnotationRepository extends JpaRepository<ColumnAnnotation, Long> {
    List<ColumnAnnotation> findByDataSourceId(Long dataSourceId);

    Optional<ColumnAnnotation> findByDataSourceIdAndTableNameAndColumnName(Long dataSourceId, String tableName, String columnName);
}
