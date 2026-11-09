package com.queryai.dashboard;

import com.queryai.dashboard.DashboardDtos.*;
import com.queryai.sql.SqlDtos.ExecuteResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dashboards")
@Tag(name = "Dashboards")
public class DashboardController {
    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping
    public List<DashboardSummary> list(@RequestParam Long workspaceId) {
        return service.list(workspaceId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DashboardSummary create(@Valid @RequestBody DashboardRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    public DashboardDetail get(@PathVariable Long id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public DashboardSummary update(@PathVariable Long id, @Valid @RequestBody DashboardRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PutMapping("/{id}/layout")
    @Operation(summary = "Persist widget positions and sizes")
    public DashboardDetail layout(@PathVariable Long id, @Valid @RequestBody LayoutRequest request) {
        return service.saveLayout(id, request);
    }

    @PostMapping("/{id}/widgets")
    @ResponseStatus(HttpStatus.CREATED)
    public DashboardDetail addWidget(@PathVariable Long id, @Valid @RequestBody WidgetRequest request) {
        return service.addWidget(id, request);
    }

    @PutMapping("/{id}/widgets/{widgetId}")
    public DashboardDetail updateWidget(@PathVariable Long id, @PathVariable Long widgetId, @Valid @RequestBody WidgetRequest request) {
        return service.updateWidget(id, widgetId, request);
    }

    @DeleteMapping("/{id}/widgets/{widgetId}")
    public DashboardDetail deleteWidget(@PathVariable Long id, @PathVariable Long widgetId) {
        return service.deleteWidget(id, widgetId);
    }

    @PostMapping("/{id}/widgets/{widgetId}/data")
    @Operation(summary = "Run the widget's saved query and return live results")
    public ExecuteResponse widgetData(@PathVariable Long id, @PathVariable Long widgetId) {
        return service.widgetData(id, widgetId);
    }
}
