package com.yangaobo.expense.backend.application.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yangaobo.expense.common.error.MyExpenseAgentException;
import org.junit.jupiter.api.Test;

class PolicyDocumentChunkerTest {

    private final PolicyDocumentChunker chunker = new PolicyDocumentChunker();

    @Test
    void shouldPreserveMarkdownSectionAsCitationMetadata() {
        var chunks =
                chunker.chunk(
                        """
                        # 学生竞赛差旅经费管理办法

                        ## 竞赛住宿标准

                        学生参加校级认定竞赛，住宿费每晚不得超过三百五十元。

                        ## 必需材料

                        必须提供竞赛通知、住宿发票和指导老师确认记录。
                        """);

        assertThat(chunks)
                .extracting(PolicyChunkDraft::section)
                .containsExactly("竞赛住宿标准", "必需材料");
        assertThat(chunks.getFirst().content()).contains("三百五十元");
        assertThat(chunks.getFirst().tokenCount()).isPositive();
    }

    @Test
    void shouldRejectEmptyPolicy() {
        assertThatThrownBy(() -> chunker.chunk(" \n "))
                .isInstanceOf(MyExpenseAgentException.class)
                .hasMessageContaining("不能为空");
    }
}
