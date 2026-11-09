package com.queryai.conversations;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {
    List<ConversationMessage> findByConversationIdOrderByCreatedAtAscIdAsc(Long conversationId);

    long countByConversationId(Long conversationId);
}
