package com.yangaobo.expense.backend.application;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DefaultCaseNumberGenerator implements CaseNumberGenerator {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    @Override
    public String next(Instant now, UUID caseId) {
        String suffix = caseId.toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
        return "CF-" + DATE.format(now) + "-" + suffix;
    }
}
