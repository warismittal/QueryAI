package com.queryai.schema;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GlossaryTermRepository extends JpaRepository<GlossaryTerm, Long> {
    List<GlossaryTerm> findByDataSourceIdOrderByTerm(Long dataSourceId);

    boolean existsByDataSourceIdAndTermIgnoreCase(Long dataSourceId, String term);
}
