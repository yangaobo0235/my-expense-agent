import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ObservabilityPage } from './ObservabilityPage';

vi.mock('../../api/expense-api', () => ({
  getModelCallSummary: vi.fn().mockResolvedValue({
    totalCalls: 1,
    successRate: 1,
    averageLatencyMs: 120,
    p95LatencyMs: 120,
    totalTokens: 80,
    callsByModel: { 'qwen-plus': 1 },
    failuresByStep: {},
  }),
  listModelCalls: vi.fn().mockResolvedValue([
    {
      id: 'model-call-1',
      caseId: 'case-1',
      stepName: 'DOCUMENT_EXTRACTION',
      modelName: 'qwen-plus',
      promptVersion: 'receipt-extraction-v2',
      promptHash: 'a'.repeat(64),
      inputHash: 'b'.repeat(64),
      outputHash: 'c'.repeat(64),
      promptTokens: 50,
      completionTokens: 30,
      totalTokens: 80,
      latencyMs: 120,
      retryCount: 0,
      status: 'SUCCEEDED',
      createdAt: '2026-06-22T00:00:00Z',
    },
  ]),
  listObservableRuns: vi.fn().mockResolvedValue([
    {
      runId: 'run-1',
      caseId: 'case-1',
      requestId: 'request-1',
      status: 'SUCCEEDED',
      startedAt: '2026-06-22T00:00:00Z',
      completedAt: '2026-06-22T00:00:02Z',
      traceId: '0123456789abcdef0123456789abcdef',
      stepCount: 6,
      succeededStepCount: 6,
      failedStepCount: 0,
      durationMs: 2000,
      agentPlanRecorded: true,
    },
  ]),
  grafanaTraceUrl: vi.fn((traceId: string) => `https://grafana.example/explore?trace=${traceId}`),
}));

describe('ObservabilityPage', () => {
  it('renders trace index and Tempo action', async () => {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={client}>
        <ObservabilityPage />
      </QueryClientProvider>,
    );

    expect(await screen.findByText('0123456789abcdef0123456789abcdef')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '在 Tempo 查看' })).toBeInTheDocument();
    expect(screen.getAllByText('SUCCEEDED').length).toBeGreaterThan(0);
    expect(screen.getByText('6/6 成功')).toBeInTheDocument();
    expect(screen.getByText('已记录 Agent Plan')).toBeInTheDocument();
  });
});
