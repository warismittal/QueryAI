package com.queryai.datasource;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DataSourceGrantRepository extends JpaRepository<DataSourceGrant, Long> {
    boolean existsByDataSourceIdAndUserId(Long dataSourceId, Long userId);

    List<DataSourceGrant> findByDataSourceId(Long dataSourceId);

    @Modifying
    @Query("delete from DataSourceGrant g where g.dataSourceId = :dataSourceId")
    void deleteByDataSourceId(Long dataSourceId);
}
