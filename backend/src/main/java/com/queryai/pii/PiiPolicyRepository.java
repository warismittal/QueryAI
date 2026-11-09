package com.queryai.pii;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PiiPolicyRepository extends JpaRepository<PiiPolicy, Long> {
    List<PiiPolicy> findByDataSourceId(Long dataSourceId);

    Optional<PiiPolicy> findByDataSourceIdAndTableNameAndColumnName(Long dataSourceId, String tableName, String columnName);
}
