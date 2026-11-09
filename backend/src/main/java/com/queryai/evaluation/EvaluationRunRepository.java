package com.queryai.evaluation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvaluationRunRepository extends JpaRepository<EvaluationRun, Long> {
    List<EvaluationRun> findTop10ByDataSourceIdOrderByCreatedAtDesc(Long dataSourceId);
}
