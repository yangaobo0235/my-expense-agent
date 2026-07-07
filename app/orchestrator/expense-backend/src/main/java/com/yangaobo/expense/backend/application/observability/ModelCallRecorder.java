package com.yangaobo.expense.backend.application.observability;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.UUID;
import com.yangaobo.expense.backend.application.governance.SensitiveDataMasker;
import org.springframework.stereotype.Service;

@Service
public class ModelCallRecorder {

    private final ModelCallRepository repository;
    private final Clock clock;
    private final SensitiveDataMasker masker;

    public ModelCallRecorder(
            ModelCallRepository repository,
            Clock clock,
            SensitiveDataMasker masker) {
        this.repository = repository;
        this.clock = clock;
        this.masker = masker;
    }

    public void succeeded(
            UUID caseId,
            UUID runId,
            String stepName,
            String modelName,
            String promptVersion,
            String prompt,
            String input,
            String output,
            int promptTokens,
            int completionTokens,
            long latencyMs,
            int retryCount) {
        repository.save(
                new ModelCallRepository.ModelCallRecord(
                        UUID.randomUUID(),
                        caseId,
                        runId,
                        stepName,
                        modelName,
                        promptVersion,
                        sha256(masker.mask(prompt)),
                        sha256(masker.mask(input)),
                        sha256(masker.mask(output)),
                        Math.max(0, promptTokens),
                        Math.max(0, completionTokens),
                        Math.max(0, promptTokens + completionTokens),
                        Math.max(0, latencyMs),
                        Math.max(0, retryCount),
                        "SUCCEEDED",
                        null,
                        clock.instant()));
    }

    public void failed(
            UUID caseId,
            UUID runId,
            String stepName,
            String modelName,
            String promptVersion,
            String prompt,
            String input,
            long latencyMs,
            int retryCount,
            String errorCode) {
        repository.save(
                new ModelCallRepository.ModelCallRecord(
                        UUID.randomUUID(),
                        caseId,
                        runId,
                        stepName,
                        modelName,
                        promptVersion,
                        sha256(masker.mask(prompt)),
                        sha256(masker.mask(input)),
                        null,
                        0,
                        0,
                        0,
                        Math.max(0, latencyMs),
                        Math.max(0, retryCount),
                        "FAILED",
                        errorCode,
                        clock.instant()));
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            (value == null ? "" : value)
                                                    .getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256 算法", exception);
        }
    }
}
