package com.queryai.history;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** One attempt to answer a question or run SQL. Persisted for history, analytics, feedback and auditing. */
@Entity
@Table(name = "query_runs")
@Getter
@Setter
@NoArgsConstructor
public class QueryRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "data_source_id")
    private Long dataSourceId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "conversation_id")
    private Long conversationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuerySource source;

    private String question;

    @Column(name = "generated_sql")
    private String generatedSql;

    @Column(name = "final_sql")
    private String finalSql;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", nullable = false)
    private ValidationStatus validationStatus = ValidationStatus.NOT_VALIDATED;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_status", nullable = false)
    private ExecutionStatus executionStatus = ExecutionStatus.NOT_EXECUTED;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(nullable = false)
    private boolean truncated;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "generation_ms")
    private Long generationMs;

    @Column(name = "error_message")
    private String errorMessage;

    private String explanation;

    @Column(name = "llm_model")
    private String llmModel;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
