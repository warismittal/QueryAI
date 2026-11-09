package com.queryai.ai;

import com.queryai.ai.AiDtos.SqlGenerationOutput;
import com.queryai.ai.LlmClient.LlmResponse;
import com.queryai.datasource.DatabaseType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;

/** Stage 3: turns question + authorised schema + glossary + conversation context into dialect-specific SQL. */
@Service
public class SqlGenerationService {
    private final LlmClient llm;
    private final Clock clock;

    @Autowired
    public SqlGenerationService(LlmClient llm) {
        this(llm, Clock.systemUTC());
    }

    SqlGenerationService(LlmClient llm, Clock clock) {
        this.llm = llm;
        this.clock = clock;
    }

    public record GenerationContext(DatabaseType dialect, String schemaText, String glossaryText, String conversation,
                                    String intentSummary, String question) {
        String userPrompt() {
            return PromptTemplates.sqlUser(dialect, schemaText, glossaryText, conversation, intentSummary, question);
        }
    }

    public LlmResponse<SqlGenerationOutput> generate(GenerationContext ctx) {
        return clean(llm.structured(PromptTemplates.sqlSystem(ctx.dialect(), LocalDate.now(clock)), ctx.userPrompt(),
                SqlGenerationOutput.class));
    }

    /** Asks the model to fix SQL that failed validation, giving it the exact validation errors. */
    public LlmResponse<SqlGenerationOutput> repair(GenerationContext ctx, String failedSql, String errors) {
        return clean(llm.structured(PromptTemplates.sqlSystem(ctx.dialect(), LocalDate.now(clock)),
                PromptTemplates.repairUser(ctx.userPrompt(), failedSql, errors), SqlGenerationOutput.class));
    }

    private static LlmResponse<SqlGenerationOutput> clean(LlmResponse<SqlGenerationOutput> response) {
        SqlGenerationOutput o = response.value();
        String sql = stripFences(o.sql());
        return new LlmResponse<>(new SqlGenerationOutput(sql, o.explanation(), o.assumptions(), o.needsClarification(),
                o.clarificationQuestion(), o.confidence()), response.model(), response.latencyMs());
    }

    static String stripFences(String sql) {
        if (sql == null) {
            return "";
        }
        String s = sql.trim();
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            s = firstNewline < 0 ? "" : s.substring(firstNewline + 1);
            int fence = s.lastIndexOf("```");
            if (fence >= 0) {
                s = s.substring(0, fence);
            }
        }
        s = s.trim();
        while (s.endsWith(";")) {
            s = s.substring(0, s.length() - 1).trim();
        }
        return s;
    }
}
