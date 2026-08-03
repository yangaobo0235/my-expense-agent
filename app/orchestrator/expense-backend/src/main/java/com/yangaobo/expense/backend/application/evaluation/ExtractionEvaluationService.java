package com.yangaobo.expense.backend.application.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ExtractionEvaluationService {
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String datasetLocation;
    private final double minimumSchemaPassRate;
    private final double minimumAmountAccuracy;

    public ExtractionEvaluationService(
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${expense.evaluation.extraction-dataset:classpath:evaluation/cases/extraction-golden-v1.json}") String datasetLocation,
            @Value("${expense.evaluation.gates.extraction-schema-pass-rate:0.95}") double minimumSchemaPassRate,
            @Value("${expense.evaluation.gates.extraction-amount-accuracy:0.95}") double minimumAmountAccuracy) {
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.datasetLocation = datasetLocation;
        this.minimumSchemaPassRate = minimumSchemaPassRate;
        this.minimumAmountAccuracy = minimumAmountAccuracy;
    }

    public ExtractionEvaluationReport evaluate() {
        ExtractionEvaluationDataset dataset = EvaluationDatasetLoader.load(
                objectMapper, datasetLocation, "extraction", ExtractionEvaluationDataset.class,
                "无法读取票据抽取评测集");
        List<ExtractionEvaluationDataset.ExtractionCase> cases = dataset.cases();
        int count = cases.size();
        int json = 0, schema = 0, invoice = 0, amount = 0, date = 0, currency = 0;
        int itemTp = 0, itemFp = 0, itemFn = 0, repairs = 0, repairSuccess = 0, handoffs = 0;
        long tokens = 0;
        List<Long> latency = new ArrayList<>();
        List<ExtractionEvaluationReport.Failure> failures = new ArrayList<>();
        Map<String, Integer> categories = new LinkedHashMap<>();
        for (var item : cases) {
            json += item.jsonValid() ? 1 : 0;
            schema += item.schemaPassed() ? 1 : 0;
            invoice += Objects.equals(item.expectedInvoiceNumber(), item.actualInvoiceNumber()) ? 1 : 0;
            amount += equalAmount(item.expectedAmount(), item.actualAmount()) ? 1 : 0;
            date += Objects.equals(item.expectedDate(), item.actualDate()) ? 1 : 0;
            currency += Objects.equals(item.expectedCurrency(), item.actualCurrency()) ? 1 : 0;
            Set<String> expected = new LinkedHashSet<>(safe(item.expectedItems()));
            Set<String> actual = new LinkedHashSet<>(safe(item.actualItems()));
            itemTp += expected.stream().filter(actual::contains).count();
            itemFp += actual.stream().filter(value -> !expected.contains(value)).count();
            itemFn += expected.stream().filter(value -> !actual.contains(value)).count();
            repairs += item.repairUsed() ? 1 : 0;
            repairSuccess += item.repairUsed() && item.schemaPassed() && !item.humanHandoff() ? 1 : 0;
            handoffs += item.humanHandoff() ? 1 : 0;
            latency.add(item.latencyMs());
            tokens += item.tokenUsage();
            categories.merge(item.category(), 1, Integer::sum);
            List<String> mismatch = mismatch(item);
            if (!mismatch.isEmpty()) failures.add(new ExtractionEvaluationReport.Failure(item.id(), mismatch));
        }
        latency.sort(Long::compareTo);
        double precision = ratio(itemTp, itemTp + itemFp);
        double recall = ratio(itemTp, itemTp + itemFn);
        double f1 = precision + recall == 0 ? 0 : 2 * precision * recall / (precision + recall);
        ExtractionEvaluationReport.Metrics metrics = new ExtractionEvaluationReport.Metrics(
                ratio(json, count), ratio(schema, count), ratio(invoice, count), ratio(amount, count),
                ratio(date, count), ratio(currency, count), precision, recall, f1,
                ratio(repairSuccess, repairs), ratio(handoffs, count), percentile(latency, 0.50),
                percentile(latency, 0.95), count == 0 ? 0 : (double) tokens / count);
        return new ExtractionEvaluationReport(
                dataset.datasetVersion(), clock.instant(), count, Map.copyOf(categories), metrics,
                metrics.schemaPassRate() >= minimumSchemaPassRate
                        && metrics.amountExactMatch() >= minimumAmountAccuracy,
                List.copyOf(failures));
    }

    private static List<String> mismatch(ExtractionEvaluationDataset.ExtractionCase item) {
        List<String> fields = new ArrayList<>();
        if (!item.jsonValid()) fields.add("json");
        if (!item.schemaPassed()) fields.add("schema");
        if (!Objects.equals(item.expectedInvoiceNumber(), item.actualInvoiceNumber())) fields.add("invoiceNumber");
        if (!equalAmount(item.expectedAmount(), item.actualAmount())) fields.add("amount");
        if (!Objects.equals(item.expectedDate(), item.actualDate())) fields.add("date");
        if (!Objects.equals(item.expectedCurrency(), item.actualCurrency())) fields.add("currency");
        if (!new LinkedHashSet<>(safe(item.expectedItems())).equals(new LinkedHashSet<>(safe(item.actualItems())))) fields.add("items");
        return List.copyOf(fields);
    }
    private static boolean equalAmount(java.math.BigDecimal left, java.math.BigDecimal right) { return left != null && right != null && left.compareTo(right) == 0; }
    private static List<String> safe(List<String> values) { return values == null ? List.of() : values; }
    private static double ratio(long n, long d) { return d == 0 ? 0 : (double) n / d; }
    private static long percentile(List<Long> values, double p) { return values.isEmpty() ? 0 : values.get(Math.min(values.size() - 1, (int) Math.ceil(values.size() * p) - 1)); }
}
