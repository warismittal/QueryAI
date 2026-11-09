package com.queryai.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Structured outputs requested from the LLM. Each is validated with Bean Validation before use. */
public final class AiDtos {
    private AiDtos() {
    }

    public enum IntentType { AGGREGATION, RANKING, TREND, COMPARISON, LOOKUP, DISTRIBUTION, OTHER }

    public record IntentAnalysis(
            @NotNull @JsonPropertyDescription("Primary analytical intent") IntentType intentType,
            @NotBlank @Size(max = 500) @JsonPropertyDescription("One sentence restating what the user wants") String summary,
            @JsonPropertyDescription("Measures or calculations requested, e.g. 'total spending', 'percentage change'") List<String> metrics,
            @JsonPropertyDescription("Grouping dimensions, e.g. 'customer', 'month'") List<String> dimensions,
            @JsonPropertyDescription("Filters or conditions, e.g. 'declined by more than 15%'") List<String> filters,
            @JsonPropertyDescription("Time range mentioned, or null") String timeRange,
            @JsonPropertyDescription("Domain nouns useful for finding tables and columns, singular, lower case") List<String> keywords,
            @JsonPropertyDescription("True only if the question cannot be answered without more information") boolean needsClarification,
            @JsonPropertyDescription("Question to ask the user when needsClarification is true") String clarificationQuestion) {
    }

    public record SqlGenerationOutput(
            @JsonPropertyDescription("A single read-only SELECT statement in the requested dialect, or empty string when clarification is needed") String sql,
            @NotBlank @Size(max = 2000) @JsonPropertyDescription("Plain-English explanation of how the query answers the question") String explanation,
            @JsonPropertyDescription("Assumptions made, e.g. how 'this year' or 'revenue' was interpreted") List<String> assumptions,
            @JsonPropertyDescription("True only if the question is ambiguous or unanswerable with the given schema") boolean needsClarification,
            @JsonPropertyDescription("Specific question to ask the user when needsClarification is true") String clarificationQuestion,
            @DecimalMin("0.0") @DecimalMax("1.0") @JsonPropertyDescription("Confidence from 0 to 1 that the SQL answers the question") double confidence) {
    }

    public record InterpretationOutput(
            @NotBlank @Size(max = 3000) @JsonPropertyDescription("2-4 sentence answer to the question grounded ONLY in the provided rows") String summary,
            @JsonPropertyDescription("Up to 4 short, specific insights with numbers taken from the rows") List<String> insights,
            @JsonPropertyDescription("Up to 3 natural follow-up questions the user could ask next") List<String> followUps) {
    }
}
