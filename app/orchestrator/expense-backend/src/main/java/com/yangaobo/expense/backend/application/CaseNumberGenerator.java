package com.yangaobo.expense.backend.application;

import java.time.Instant;
import java.util.UUID;

public interface CaseNumberGenerator {

    String next(Instant now, UUID caseId);
}
