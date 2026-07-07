package com.yangaobo.expense.backend.application.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yangaobo.expense.common.error.ExpenseFlowException;
import org.junit.jupiter.api.Test;

class PolicyDocumentChunkerTest {

    private final PolicyDocumentChunker chunker = new PolicyDocumentChunker();

    @Test
    void shouldPreserveMarkdownSectionAsCitationMetadata() {
        var chunks =
                chunker.chunk(
                        """
                        # 住宿费制度

                        ## 金额上限

                        一线城市每晚不得超过六百元。

                        ## 必需凭证

                        必须提供住宿发票和酒店明细。
                        """);

        assertThat(chunks)
                .extracting(PolicyChunkDraft::section)
                .containsExactly("金额上限", "必需凭证");
        assertThat(chunks.getFirst().content()).contains("六百元");
        assertThat(chunks.getFirst().tokenCount()).isPositive();
    }

    @Test
    void shouldRejectEmptyPolicy() {
        assertThatThrownBy(() -> chunker.chunk(" \n "))
                .isInstanceOf(ExpenseFlowException.class)
                .hasMessageContaining("不能为空");
    }
}
