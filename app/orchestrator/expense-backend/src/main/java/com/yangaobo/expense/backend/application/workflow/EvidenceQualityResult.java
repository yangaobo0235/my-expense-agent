package com.yangaobo.expense.backend.application.workflow;

import java.io.Serializable;
import java.util.List;

public record EvidenceQualityResult(
        EvidenceQuality quality,
        List<String> missingMaterials,
        List<String> dependencyFailures) implements Serializable {

    public EvidenceQualityResult {
        quality = quality == null ? EvidenceQuality.COMPLETE : quality;
        missingMaterials = missingMaterials == null ? List.of() : List.copyOf(missingMaterials);
        dependencyFailures = dependencyFailures == null ? List.of() : List.copyOf(dependencyFailures);
    }
}
