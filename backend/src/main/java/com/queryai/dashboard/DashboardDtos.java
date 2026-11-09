package com.queryai.dashboard;

import com.queryai.dashboard.DashboardWidget.ChartType;
import com.queryai.savedquery.Visibility;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;

public final class DashboardDtos {
    private DashboardDtos() {
    }

    public record DashboardSummary(Long id, Long workspaceId, Long ownerId, String ownerName, String name,
                                   String description, Visibility visibility, long widgetCount, boolean canEdit,
                                   Instant updatedAt) {
    }

    public record DashboardDetail(DashboardSummary dashboard, List<WidgetDto> widgets) {
    }

    public record WidgetDto(Long id, Long savedQueryId, String savedQueryName, String title, ChartType chartType,
                            String xColumn, List<String> yColumns, int x, int y, int w, int h) {
    }

    public record DashboardRequest(@NotNull Long workspaceId, @NotBlank @Size(max = 160) String name,
                                   @Size(max = 2000) String description, Visibility visibility) {
    }

    public record WidgetRequest(@NotNull Long savedQueryId, @NotBlank @Size(max = 160) String title,
                                @NotNull ChartType chartType, @Size(max = 128) String xColumn, List<@Size(max = 128) String> yColumns,
                                @Min(0) @Max(100) Integer x, @Min(0) @Max(1000) Integer y,
                                @Min(1) @Max(12) Integer w, @Min(1) @Max(24) Integer h) {
    }

    public record LayoutItem(@NotNull Long id, @Min(0) @Max(100) int x, @Min(0) @Max(1000) int y,
                             @Min(1) @Max(12) int w, @Min(1) @Max(24) int h) {
    }

    public record LayoutRequest(@NotNull @Valid List<LayoutItem> items) {
    }
}
