package com.queryai.evaluation;

import com.queryai.evaluation.EvaluationService.CaseDto;
import com.queryai.evaluation.EvaluationService.CaseRequest;
import com.queryai.evaluation.EvaluationService.RunDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/evaluation")
@Tag(name = "Text-to-SQL evaluation")
public class EvaluationController {
    private final EvaluationService service;

    public EvaluationController(EvaluationService service) {
        this.service = service;
    }

    @GetMapping("/cases")
    public List<CaseDto> cases(@RequestParam Long dataSourceId) {
        return service.listCases(dataSourceId);
    }

    @PostMapping("/cases")
    @ResponseStatus(HttpStatus.CREATED)
    public CaseDto create(@RequestParam Long dataSourceId, @Valid @RequestBody CaseRequest request) {
        return service.createCase(dataSourceId, request);
    }

    @DeleteMapping("/cases/{caseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestParam Long dataSourceId, @PathVariable Long caseId) {
        service.deleteCase(dataSourceId, caseId);
    }

    @GetMapping("/runs")
    public List<RunDto> runs(@RequestParam Long dataSourceId) {
        return service.listRuns(dataSourceId);
    }

    @PostMapping("/run")
    @Operation(summary = "Generate SQL for every benchmark case and compare real results with the reference query")
    public RunDto run(@RequestParam Long dataSourceId) {
        return service.run(dataSourceId);
    }
}
