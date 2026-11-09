package com.queryai.savedquery;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavedQueryVersionRepository extends JpaRepository<SavedQueryVersion, Long> {
    List<SavedQueryVersion> findBySavedQueryIdOrderByVersionDesc(Long savedQueryId);

    Optional<SavedQueryVersion> findBySavedQueryIdAndVersion(Long savedQueryId, int version);
}
