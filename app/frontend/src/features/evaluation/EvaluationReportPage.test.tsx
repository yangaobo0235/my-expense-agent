import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { EvaluationReportPage } from './EvaluationReportPage';

vi.mock('../../api/expense-api', () => ({
  getExtractionEvaluationReport: vi.fn().mockResolvedValue({
    datasetVersion: 'extraction-golden-v1',
    generatedAt: '2026-06-22T00:00:00Z',
    caseCount: 30,
    categoryCounts: { 标准票据: 20, 需要修正: 6, 人工接管: 4 },
    metrics: {
      jsonValidRate: 1,
      schemaPassRate: 1,
      invoiceNumberExactMatch: 1,
      amountExactMatch: 1,
      dateAccuracy: 1,
      currencyAccuracy: 1,
      itemPrecision: 1,
      itemRecall: 1,
      itemF1: 1,
      repairSuccessRate: 1,
      humanHandoffRate: 0.1333,
      p50LatencyMs: 80,
      p95LatencyMs: 120,
      averageTokenUsage: 320,
    },
    gatePassed: true,
    failures: [],
  }),
  getRiskEvaluationReport: vi.fn().mockResolvedValue({
    datasetVersion: 'risk-golden-v3',
    datasetSha256: 'a'.repeat(64),
    engineVersion: 'deterministic-risk-v1',
    generatedAt: '2026-06-22T00:00:00Z',
    caseCount: 300,
    categoryCounts: {},
    metrics: {
      riskLevelAccuracy: 0.9067,
      routingAccuracy: 0.9333,
      highRiskRecall: 0.8769,
    },
    failures: [],
  }),
}));

describe('EvaluationReportPage', () => {
  it('renders versioned metrics and empty failure state', async () => {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={client}>
        <EvaluationReportPage />
      </QueryClientProvider>,
    );

    expect(await screen.findByText('关键字段回归通过')).toBeInTheDocument();
    expect(screen.getByText('extraction-golden-v1')).toBeInTheDocument();
    expect(screen.getByText('30')).toBeInTheDocument();
    fireEvent.click(screen.getByText('风险分流'));
    expect(await screen.findByText('分级准确率')).toBeInTheDocument();
    expect(screen.getByText('risk-golden-v3')).toBeInTheDocument();
    expect(document.body).toHaveTextContent('300条');
    expect(document.body).toHaveTextContent('90.7%');
    expect(document.body).toHaveTextContent('93.3%');
    expect(document.body).toHaveTextContent('87.7%');
  });
});
