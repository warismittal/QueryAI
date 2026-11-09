package com.queryai.conversations;

import com.queryai.common.ApiException;
import com.queryai.common.RequestUtils;
import com.queryai.config.QueryAiProperties;
import com.queryai.conversations.ConversationMessage.Role;
import com.queryai.datasource.DataSourceEntity;
import com.queryai.security.CurrentUser;
import com.queryai.workspace.WorkspaceAccessService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Conversation management: persists chat turns and provides prior context for follow-up questions. */
@Service
public class ConversationService {
    private final ConversationRepository conversations;
    private final ConversationMessageRepository messages;
    private final CurrentUser currentUser;
    private final WorkspaceAccessService workspaceAccess;
    private final int maxTurns;

    public ConversationService(ConversationRepository conversations, ConversationMessageRepository messages,
                               CurrentUser currentUser, WorkspaceAccessService workspaceAccess, QueryAiProperties properties) {
        this.conversations = conversations;
        this.messages = messages;
        this.currentUser = currentUser;
        this.workspaceAccess = workspaceAccess;
        this.maxTurns = Math.max(1, properties.ai().maxConversationTurns());
    }

    public record ConversationDto(Long id, Long workspaceId, Long dataSourceId, String title, long messageCount,
                                  Instant createdAt, Instant updatedAt) {
    }

    public record MessageDto(Long id, Role role, String content, String sql, Long queryRunId, Map<String, Object> metadata,
                             Instant createdAt) {
        static MessageDto from(ConversationMessage m) {
            return new MessageDto(m.getId(), m.getRole(), m.getContent(), m.getSqlText(), m.getQueryRunId(), m.getMetadata(), m.getCreatedAt());
        }
    }

    public record ConversationDetail(ConversationDto conversation, List<MessageDto> messages) {
    }

    public record RenameRequest(@NotBlank @Size(max = 200) String title) {
    }

    /** Returns the caller's conversation (verifying ownership and data source) or starts a new one. */
    @Transactional
    public Conversation resolve(Long conversationId, DataSourceEntity ds, String question) {
        if (conversationId != null) {
            Conversation c = owned(conversationId);
            if (!c.getDataSourceId().equals(ds.getId())) {
                throw ApiException.badRequest("This conversation belongs to a different data source; start a new conversation");
            }
            return c;
        }
        Conversation c = new Conversation();
        c.setWorkspaceId(ds.getWorkspaceId());
        c.setDataSourceId(ds.getId());
        c.setUserId(currentUser.id());
        c.setTitle(RequestUtils.truncate(question.replaceAll("\\s+", " ").trim(), 120));
        return conversations.save(c);
    }

    @Transactional
    public ConversationMessage append(Long conversationId, Role role, String content, String sql, Long queryRunId,
                                      Map<String, Object> metadata) {
        ConversationMessage m = new ConversationMessage();
        m.setConversationId(conversationId);
        m.setRole(role);
        m.setContent(content == null || content.isBlank() ? "(no content)" : content);
        m.setSqlText(sql);
        m.setQueryRunId(queryRunId);
        m.setMetadata(metadata);
        messages.save(m);
        conversations.findById(conversationId).ifPresent(c -> c.setUpdatedAt(Instant.now()));
        return m;
    }

    /** Compact transcript of the last turns, used as LLM context for follow-up questions. */
    @Transactional(readOnly = true)
    public String transcript(Long conversationId) {
        if (conversationId == null) {
            return "(new conversation)";
        }
        List<ConversationMessage> all = messages.findByConversationIdOrderByCreatedAtAscIdAsc(conversationId);
        List<ConversationMessage> recent = all.subList(Math.max(0, all.size() - maxTurns * 2), all.size());
        if (recent.isEmpty()) {
            return "(new conversation)";
        }
        StringBuilder sb = new StringBuilder();
        for (ConversationMessage m : recent) {
            if (m.getRole() == Role.USER) {
                sb.append("User: ").append(RequestUtils.truncate(m.getContent(), 600)).append('\n');
            } else {
                sb.append("Assistant: ").append(RequestUtils.truncate(m.getContent(), 600)).append('\n');
                if (m.getSqlText() != null) {
                    sb.append("SQL used:\n").append(RequestUtils.truncate(m.getSqlText(), 2500)).append('\n');
                }
            }
        }
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public List<ConversationDto> list(Long workspaceId, Long dataSourceId) {
        workspaceAccess.requireMember(workspaceId);
        return conversations.findRecent(currentUser.id(), workspaceId, dataSourceId, PageRequest.of(0, 50)).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ConversationDetail get(Long id) {
        Conversation c = owned(id);
        return new ConversationDetail(toDto(c),
                messages.findByConversationIdOrderByCreatedAtAscIdAsc(id).stream().map(MessageDto::from).toList());
    }

    @Transactional
    public ConversationDto rename(Long id, RenameRequest request) {
        Conversation c = owned(id);
        c.setTitle(request.title().trim());
        return toDto(c);
    }

    @Transactional
    public void delete(Long id) {
        conversations.delete(owned(id));
    }

    private Conversation owned(Long id) {
        Conversation c = conversations.findById(id).orElseThrow(() -> ApiException.notFound("Conversation"));
        if (!c.getUserId().equals(currentUser.id())) {
            throw ApiException.notFound("Conversation");
        }
        return c;
    }

    private ConversationDto toDto(Conversation c) {
        return new ConversationDto(c.getId(), c.getWorkspaceId(), c.getDataSourceId(), c.getTitle(),
                messages.countByConversationId(c.getId()), c.getCreatedAt(), c.getUpdatedAt());
    }
}
