package com.queryai.history;

import com.queryai.common.PageResponse;
import com.queryai.history.HistoryService.FeedbackRequest;
import com.queryai.history.HistoryService.QueryRunDto;
import com.queryai.sql.SqlDtos.ExecuteResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/history")
@Tag(name = "Query history & feedback")
public class HistoryController {
    private final HistoryService service;

    public HistoryController(HistoryService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Search query history with filters and pagination")
    public PageResponse<QueryRunDto> search(@RequestParam Long workspaceId,
                                            @RequestParam(defaultValue = "false") boolean mine,
                                            @RequestParam(required = false) Long dataSourceId,
                                            @RequestParam(required = false) ExecutionStatus status,
                                            @RequestParam(required = false) QuerySource source,
                                            @RequestParam(required = false) String q,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return service.search(workspaceId, mine, dataSourceId, status, source, q, page, size);
    }

    @GetMapping("/{id}")
    public QueryRunDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/{id}/feedback")
    @Operation(summary = "Thumbs up/down with an optional comment (one per user, last one wins)")
    public QueryRunDto feedback(@PathVariable Long id, @Valid @RequestBody FeedbackRequest request) {
        return service.giveFeedback(id, request);
    }

    @PostMapping("/{id}/rerun")
    public ExecuteResponse rerun(@PathVariable Long id) {
        return service.rerun(id);
    }
}
