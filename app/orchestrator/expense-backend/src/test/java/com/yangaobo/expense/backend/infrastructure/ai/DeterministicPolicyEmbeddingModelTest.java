package com.yangaobo.expense.backend.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeterministicPolicyEmbeddingModelTest {

    private final DeterministicPolicyEmbeddingModel model =
            new DeterministicPolicyEmbeddingModel();

    @Test
    void shouldCreateStableNormalizedVector() {
        float[] first = model.embed("住宿费上限六百元");
        float[] second = model.embed("住宿费上限六百元");

        assertThat(first).hasSize(1024).containsExactly(second);
        double norm = 0;
        for (float value : first) {
            norm += value * value;
        }
        assertThat(Math.sqrt(norm)).isCloseTo(1.0, within(0.0001));
    }

    private static org.assertj.core.data.Offset<Double> within(double value) {
        return org.assertj.core.data.Offset.offset(value);
    }
}
