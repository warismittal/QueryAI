package com.queryai.evaluation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** A benchmark question with a hand-written reference SQL ("golden query"). */
@Entity
@Table(name = "evaluation_cases")
@Getter
@Setter
@NoArgsConstructor
public class EvaluationCase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_source_id", nullable = false)
    private Long dataSourceId;

    @Column(nullable = false)
    private String question;

    @Column(name = "expected_sql", nullable = false)
    private String expectedSql;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
