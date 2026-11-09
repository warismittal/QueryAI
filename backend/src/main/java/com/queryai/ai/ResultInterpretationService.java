package com.queryai.ai;

import com.queryai.ai.AiDtos.InterpretationOutput;
import com.queryai.common.ApiException;
import com.queryai.config.QueryAiProperties;
import com.queryai.sql.QueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Stage 6: explains real query results in plain language. If the LLM is unavailable the service falls back to a
 * factual, clearly labelled system summary rather than inventing an explanation.
 */
@Service
public class ResultInterpretationService {
    private static final Logger log = LoggerFactory.getLogger(ResultInterpretationService.class);
    private static final int SAMPLE_ROWS = 30;

    private final LlmClient llm;
    private final boolean sendSamples;

    public ResultInterpretationService(LlmClient llm, QueryAiProperties properties) {
        this.llm = llm;
        this.sendSamples = properties.ai().sendResultSamples();
    }

    public record Interpretation(String summary, List<String> insights, List<String> followUps, String generatedBy) {
    }

    public Interpretation interpret(String question, String sql, QueryResult result) {
        if (!llm.isConfigured()) {
            return fallback(result);
        }
        try {
            InterpretationOutput out = llm.structured(PromptTemplates.interpretationSystem(),
                    PromptTemplates.interpretationUser(question == null ? "(manual SQL)" : question, sql,
                            result.columns().stream().map(c -> c.name() + " (" + c.category() + ")").collect(Collectors.joining(", ")),
                            result.rowCount(), result.truncated(), sendSamples ? sample(result) : null),
                    InterpretationOutput.class).value();
            return new Interpretation(out.summary(), safe(out.insights(), 4), safe(out.followUps(), 3), "ai");
        } catch (ApiException e) {
            log.info("Interpretation unavailable, using system summary: {}", e.getMessage());
            return fallback(result);
        }
    }

    public static Interpretation fallback(QueryResult result) {
        String cols = result.columns().stream().map(QueryResult.ResultColumn::name).collect(Collectors.joining(", "));
        String summary = result.rowCount() == 0
                ? "The query ran successfully but returned no rows."
                : "The query returned " + result.rowCount() + (result.truncated() ? "+ rows (truncated at the row limit)" : " row" + (result.rowCount() == 1 ? "" : "s"))
                + " with columns: " + cols + ".";
        return new Interpretation(summary, List.of(), List.of(), "system");
    }

    private static String sample(QueryResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append(result.columns().stream().map(QueryResult.ResultColumn::name).collect(Collectors.joining("\t"))).append('\n');
        result.rows().stream().limit(SAMPLE_ROWS).forEach(row -> sb.append(row.stream().map(v -> {
            String s = String.valueOf(v);
            return s.length() > 80 ? s.substring(0, 80) + "…" : s;
        }).collect(Collectors.joining("\t"))).append('\n'));
        if (result.rowCount() > SAMPLE_ROWS) {
            sb.append("… (").append(result.rowCount() - SAMPLE_ROWS).append(" more rows not shown)\n");
        }
        return sb.toString();
    }

    private static List<String> safe(List<String> values, int max) {
        return values == null ? List.of() : values.stream().filter(v -> v != null && !v.isBlank()).limit(max).toList();
    }
}
