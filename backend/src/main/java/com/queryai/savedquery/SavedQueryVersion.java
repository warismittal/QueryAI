package com.queryai.savedquery;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "saved_query_versions")
@Getter
@Setter
@NoArgsConstructor
public class SavedQueryVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "saved_query_id", nullable = false)
    private Long savedQueryId;

    @Column(nullable = false)
    private int version;

    @Column(name = "sql_text", nullable = false)
    private String sqlText;

    private String note;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
