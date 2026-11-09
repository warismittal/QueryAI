package com.queryai.history;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface QueryFeedbackRepository extends JpaRepository<QueryFeedback, Long> {
    Optional<QueryFeedback> findByQueryRunIdAndUserId(Long queryRunId, Long userId);

    List<QueryFeedback> findByQueryRunIdInAndUserId(Collection<Long> queryRunIds, Long userId);

    @Query("select count(f) from QueryFeedback f where f.rating = :rating and f.queryRunId in (select r.id from QueryRun r where r.workspaceId = :workspaceId)")
    long countByWorkspaceAndRating(Long workspaceId, QueryFeedback.Rating rating);
}
