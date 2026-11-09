package com.queryai.dashboard;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DashboardWidgetRepository extends JpaRepository<DashboardWidget, Long> {
    List<DashboardWidget> findByDashboardIdOrderByPosYAscPosXAsc(Long dashboardId);

    long countByDashboardId(Long dashboardId);
}
