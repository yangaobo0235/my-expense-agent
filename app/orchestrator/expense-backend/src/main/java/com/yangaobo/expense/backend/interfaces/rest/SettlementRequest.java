package com.yangaobo.expense.backend.interfaces.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SettlementRequest(
        @NotBlank @Size(max = 128) String requestId) {}
