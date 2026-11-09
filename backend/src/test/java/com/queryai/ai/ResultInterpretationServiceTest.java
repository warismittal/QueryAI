package com.queryai.ai;

import com.queryai.ai.AiDtos.InterpretationOutput;
import com.queryai.ai.LlmClient.LlmResponse;
import com.queryai.common.ApiException;
import com.queryai.config.QueryAiProperties;
import com.queryai.sql.QueryResult;
import com.queryai.sql.QueryResult.Category;
import com.queryai.sql.QueryResult.ResultColumn;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ResultInterpretationServiceTest {
    private final LlmClient llm = mock(LlmClient.class);
    private final QueryAiProperties props = new QueryAiProperties(null, null,
            new QueryAiProperties.Ai("openai", true, 12, 6, 1), null, null, null, null);
    private final ResultInterpretationService service = new ResultInterpretationService(llm, props);
    private final QueryResult result = new QueryResult(List.of(new ResultColumn("channel", "varchar", Category.TEXT),
            new ResultColumn("orders", "int8", Category.NUMBER)), List.of(List.of("web", 10), List.of("mobile", 4)), 2, false, 3, 5000);

    @Test
    void usesFactualSystemSummaryWhenAiIsNotConfigured() {
        when(llm.isConfigured()).thenReturn(false);
        var i = service.interpret("orders by channel", "SELECT ...", result);
        assertThat(i.generatedBy()).isEqualTo("system");
        assertThat(i.summary()).contains("2 rows").contains("channel, orders");
        verify(llm, never()).structured(anyString(), anyString(), any());
    }

    @Test
    void sendsRealRowsToTheModel() {
        when(llm.isConfigured()).thenReturn(true);
        when(llm.structured(anyString(), anyString(), eq(InterpretationOutput.class)))
                .thenReturn(new LlmResponse<>(new InterpretationOutput("Web leads with 10 orders.", List.of("web: 10"), List.of()), "m", 1));
        var i = service.interpret("orders by channel", "SELECT ...", result);
        assertThat(i.generatedBy()).isEqualTo("ai");
        verify(llm).structured(anyString(), org.mockito.ArgumentMatchers.contains("web\t10"), eq(InterpretationOutput.class));
    }

    @Test
    void fallsBackWhenTheProviderFails() {
        when(llm.isConfigured()).thenReturn(true);
        when(llm.structured(anyString(), anyString(), eq(InterpretationOutput.class)))
                .thenThrow(ApiException.badGateway("LLM_UNAVAILABLE", "down", null));
        assertThat(service.interpret("q", "SELECT 1", result).generatedBy()).isEqualTo("system");
    }
}
