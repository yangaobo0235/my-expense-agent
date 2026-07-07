package com.yangaobo.expense.backend.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.expense.backend.application.document.DocumentContentInspector;
import com.yangaobo.expense.backend.application.extraction.DocumentInputKind;
import com.yangaobo.expense.backend.application.extraction.ExpenseExtractionJsonSchema;
import com.yangaobo.expense.backend.application.extraction.PreparedDocument;
import com.yangaobo.expense.backend.application.governance.DependencyCircuitBreaker;
import com.yangaobo.expense.backend.application.prompt.PromptRenderService;
import com.yangaobo.expense.backend.application.prompt.RenderedPrompt;
import java.math.BigDecimal;
import java.time.Clock;
import com.yangaobo.expense.common.error.ExpenseFlowException;
import org.junit.jupiter.api.Test;

class LlmExpenseDocumentExtractorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldFailFastWhenApiKeyIsMissing() {
        ExpenseExtractionProperties properties = new ExpenseExtractionProperties();
        properties.setApiKey("");
        LlmExpenseDocumentExtractor extractor =
                new LlmExpenseDocumentExtractor(
                        properties,
                        new ExpenseExtractionJsonSchema(),
                        mock(PromptRenderService.class),
                        new DependencyCircuitBreaker(Clock.systemUTC()),
                        new ObjectMapper(),
                        new DocumentContentInspector());

        assertThatThrownBy(
                        () ->
                                extractor.extract(
                                        new PreparedDocument(
                                                DocumentInputKind.TEXT,
                                                "sellerName: Hotel\ntotalAmount: 100\ncurrency: CNY",
                                                new byte[0],
                                                "text/plain",
                                                1)))
                .isInstanceOf(ExpenseFlowException.class)
                .hasMessageContaining("LLM API key is not configured");
    }

    @Test
    void shouldNormalizeCommonChineseReceiptFields() throws Exception {
        LlmExpenseDocumentExtractor extractor = extractorWithObjectMapper(objectMapper);

        JsonNode normalized =
                extractor.normalizeExtractionJson(
                        objectMapper.readTree(
                                """
                                {
                                  "result": {
                                    "票据类型": "酒店住宿费电子收据",
                                    "收据编号": "RCPT-20260620-001",
                                    "商户名称": "测试酒店",
                                    "申请人": "费用员工",
                                    "开票日期": "2026-06-20",
                                    "合计金额": "¥1,280.50",
                                    "币种": "人民币",
                                    "项目明细": [
                                      {"项目名称": "住宿费", "数量": "2", "单价": "640.25", "金额": "1280.50"}
                                    ]
                                  }
                                }
                                """));

        assertThat(normalized.path("sellerName").asText()).isEqualTo("测试酒店");
        assertThat(normalized.path("currency").asText()).isEqualTo("CNY");
        assertThat(normalized.path("totalAmount").decimalValue()).isEqualByComparingTo("1280.50");
        assertThat(normalized.path("items").get(0).path("description").asText()).isEqualTo("住宿费");
        assertThat(normalized.path("items").get(0).path("amount").decimalValue()).isEqualByComparingTo("1280.50");
    }

    @Test
    void shouldPreferConfiguredExtractionModelOverPromptModel() {
        ExpenseExtractionProperties properties = new ExpenseExtractionProperties();
        properties.setApiKey("test-key");
        properties.setModelName("qwen-vl-plus");
        LlmExpenseDocumentExtractor extractor =
                new LlmExpenseDocumentExtractor(
                        properties,
                        new ExpenseExtractionJsonSchema(),
                        mock(PromptRenderService.class),
                        new DependencyCircuitBreaker(Clock.systemUTC()),
                        objectMapper,
                        new DocumentContentInspector());

        String selectedModel =
                extractor.extractionModelName(
                        new RenderedPrompt(
                                "receipt-extraction",
                                "receipt-extraction-v2",
                                "prompt",
                                "hash",
                                "qwen-plus",
                                BigDecimal.ZERO,
                                1024));

        assertThat(selectedModel).isEqualTo("qwen-vl-plus");
    }

    private static LlmExpenseDocumentExtractor extractorWithObjectMapper(ObjectMapper objectMapper) {
        ExpenseExtractionProperties properties = new ExpenseExtractionProperties();
        properties.setApiKey("test-key");
        return new LlmExpenseDocumentExtractor(
                properties,
                new ExpenseExtractionJsonSchema(),
                mock(PromptRenderService.class),
                new DependencyCircuitBreaker(Clock.systemUTC()),
                objectMapper,
                new DocumentContentInspector());
    }
}
