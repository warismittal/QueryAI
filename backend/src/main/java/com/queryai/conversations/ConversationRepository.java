package com.queryai.conversations;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @Query("""
            select c from Conversation c
            where c.userId = :userId and c.workspaceId = :workspaceId
              and (:dataSourceId is null or c.dataSourceId = :dataSourceId)
            order by c.updatedAt desc
            """)
    List<Conversation> findRecent(Long userId, Long workspaceId, Long dataSourceId, Pageable pageable);
}
