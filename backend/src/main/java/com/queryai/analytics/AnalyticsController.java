package com.queryai.analytics;

import com.queryai.ai.ChartRecommendationService;
import com.queryai.ai.ResultInterpretationService;
import com.queryai.analytics.AnalyticsDtos.*;
import com.queryai.analytics.OverviewService.Overview;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/analytics")
@Tag(name = "Natural-language analytics")
public class AnalyticsController {
    private final AnalysisOrchestrator orchestrator;
    private final ResultInterpretationService interpretation;
    private final ChartRecommendationService charts;
    private final OverviewService overviewService;

    public AnalyticsController(AnalysisOrchestrator orchestrator, ResultInterpretationService interpretation,
                               ChartRecommendationService charts, OverviewService overviewService) {
        this.orchestrator = orchestrator;
        this.interpretation = interpretation;
        this.charts = charts;
        this.overviewService = overviewService;
    }

    @PostMapping("/analyze")
    @Operation(summary = "Full pipeline: question → intent → schema → SQL → validation → execution → explanation → chart")
    public AnalyzeResponse analyze(@Valid @RequestBody AnalyzeRequest request) {
        return orchestrator.analyze(request);
    }

    @PostMapping("/generate")
    @Operation(summary = "Generate and validate SQL for a question without executing it")
    public AnalyzeResponse generate(@Valid @RequestBody AnalyzeRequest request) {
        return orchestrator.analyze(new AnalyzeRequest(request.dataSourceId(), request.question(), request.conversationId(),
                false, request.parameters()));
    }

    @PostMapping("/interpret")
    @Operation(summary = "Explain a result set the user has just executed")
    public InterpretResponse interpret(@Valid @RequestBody InterpretRequest request) {
        return new InterpretResponse(interpretation.interpret(request.question(), request.sql(), request.result()),
                charts.recommend(request.result()));
    }

    @GetMapping("/overview")
    @Operation(summary = "Measured workspace statistics for the home dashboard")
    public Overview overview(@RequestParam Long workspaceId) {
        return overviewService.overview(workspaceId);
    }
}
