package com.queryai.ai;

import com.queryai.ai.AiDtos.IntentAnalysis;
import com.queryai.ai.LlmClient.LlmResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/** Stage 1 of the pipeline: understands what the user is asking for. */
@Service
public class IntentAnalysisService {
    private final LlmClient llm;

    public IntentAnalysisService(LlmClient llm) {
        this.llm = llm;
    }

    public LlmResponse<IntentAnalysis> analyse(String question, List<String> tableNames, String conversation) {
        return llm.structured(PromptTemplates.intentSystem(),
                PromptTemplates.intentUser(question, String.join(", ", tableNames), conversation), IntentAnalysis.class);
    }

    public static String describe(IntentAnalysis intent) {
        if (intent == null) {
            return "(not available)";
        }
        return "Intent: " + intent.intentType() + " — " + intent.summary()
                + "\nMetrics: " + join(intent.metrics())
                + "\nDimensions: " + join(intent.dimensions())
                + "\nFilters: " + join(intent.filters())
                + "\nTime range: " + (intent.timeRange() == null ? "none" : intent.timeRange());
    }

    private static String join(List<String> values) {
        return values == null || values.isEmpty() ? "none" : String.join("; ", values);
    }
}
