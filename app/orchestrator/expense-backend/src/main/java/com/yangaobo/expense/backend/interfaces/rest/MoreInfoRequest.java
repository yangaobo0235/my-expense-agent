package com.yangaobo.expense.backend.interfaces.rest;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record MoreInfoRequest(
        @NotEmpty List<String> requiredMaterials,
        List<String> reasonCodes) {}
