package com.queryai.datasource;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DataSourceRepository extends JpaRepository<DataSourceEntity, Long> {
    List<DataSourceEntity> findByWorkspaceIdOrderByName(Long workspaceId);

    boolean existsByWorkspaceIdAndNameIgnoreCase(Long workspaceId, String name);

    long countByWorkspaceId(Long workspaceId);
}
