package com.yangaobo.expense.backend.infrastructure.ai;

import com.yangaobo.expense.backend.application.policy.PolicyEmbeddingModel;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "expense.ai.embedding",
        name = "provider",
        havingValue = "deterministic",
        matchIfMissing = false)
public class DeterministicPolicyEmbeddingModel implements PolicyEmbeddingModel {

    static final int DIMENSIONS = 1024;

    @Override
    public float[] embed(String text) {
        float[] vector = new float[DIMENSIONS];
        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        int[] codePoints = normalized.codePoints().toArray();
        if (codePoints.length == 0) {
            return vector;
        }
        for (int index = 0; index < codePoints.length; index++) {
            int current = codePoints[index];
            vector[Math.floorMod(current * 31, DIMENSIONS)] += 0.35f;
            if (index + 1 < codePoints.length) {
                int next = codePoints[index + 1];
                int bucket = Math.floorMod((current * 65_537) ^ (next * 257), DIMENSIONS);
                vector[bucket] += 1.0f;
            }
        }
        normalize(vector);
        return vector;
    }

    @Override
    public String modelName() {
        return "deterministic-hash-embedding-v1";
    }

    private static void normalize(float[] vector) {
        double sum = 0;
        for (float value : vector) {
            sum += value * value;
        }
        if (sum == 0) {
            return;
        }
        float norm = (float) Math.sqrt(sum);
        for (int index = 0; index < vector.length; index++) {
            vector[index] /= norm;
        }
    }
}
