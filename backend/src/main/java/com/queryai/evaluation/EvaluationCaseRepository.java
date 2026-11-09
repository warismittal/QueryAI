package com.queryai.evaluation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvaluationCaseRepository extends JpaRepository<EvaluationCase, Long> {
    List<EvaluationCase> findByDataSourceIdOrderByIdAsc(Long dataSourceId);

    long countByDataSourceId(Long dataSourceId);
}
