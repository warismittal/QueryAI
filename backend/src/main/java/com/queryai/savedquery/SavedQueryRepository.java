package com.queryai.savedquery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SavedQueryRepository extends JpaRepository<SavedQuery, Long> {

    @Query("""
            select s from SavedQuery s
            where s.workspaceId = :workspaceId
              and (s.ownerId = :userId or s.visibility = com.queryai.savedquery.Visibility.WORKSPACE)
              and (:folder is null or s.folder = :folder)
              and (:templatesOnly = false or s.template = true)
              and (:q is null or lower(s.name) like lower(concat('%', cast(:q as string), '%'))
                   or lower(coalesce(s.description, '')) like lower(concat('%', cast(:q as string), '%')))
            order by s.updatedAt desc
            """)
    List<SavedQuery> findVisible(@Param("workspaceId") Long workspaceId, @Param("userId") Long userId,
                                 @Param("folder") String folder, @Param("templatesOnly") boolean templatesOnly,
                                 @Param("q") String q);

    long countByWorkspaceId(Long workspaceId);
}
