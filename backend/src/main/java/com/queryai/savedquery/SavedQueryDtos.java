package com.queryai.savedquery;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class SavedQueryDtos {
    private SavedQueryDtos() {
    }

    public record SavedQueryDto(Long id, Long workspaceId, Long dataSourceId, String dataSourceName, Long ownerId,
                                String ownerName, String name, String description, String folder, String question,
                                String sql, String chartType, Visibility visibility, boolean template, int currentVersion,
                                List<String> parameters, boolean canEdit, Instant createdAt, Instant updatedAt) {
    }

    public record CreateSavedQueryRequest(@NotNull Long workspaceId, @NotNull Long dataSourceId,
                                          @NotBlank @Size(max = 160) String name, @Size(max = 2000) String description,
                                          @Size(max = 120) String folder, @Size(max = 4000) String question,
                                          @NotBlank @Size(max = 100_000) String sql, @Size(max = 20) String chartType,
                                          Visibility visibility, boolean template) {
    }

    public record UpdateSavedQueryRequest(@NotBlank @Size(max = 160) String name, @Size(max = 2000) String description,
                                          @Size(max = 120) String folder, @NotBlank @Size(max = 100_000) String sql,
                                          @Size(max = 20) String chartType, @NotNull Visibility visibility, boolean template,
                                          @Size(max = 500) String versionNote) {
    }

    public record VersionDto(int version, String sql, String note, Long createdBy, Instant createdAt) {
    }

    public record RunSavedQueryRequest(Map<String, Object> parameters) {
    }
}
