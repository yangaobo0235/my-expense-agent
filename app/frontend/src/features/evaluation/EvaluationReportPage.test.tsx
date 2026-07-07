import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { EvaluationReportPage } from './EvaluationReportPage';

vi.mock('../../api/expense-api', () => ({
  getAgentSecurityEvaluationReport: vi.fn().mockResolvedValue({
    datasetVersion: 'agent-security-golden-v1',
    generatedAt: '2026-06-22T00:00:00Z',
    caseCount: 4,
    metrics: {
      blockedWriteToolCount: 4,
      unsafeWriteToolCallCount: 0,
      injectionDetectedCount: 4,
      humanHandoffCount: 4,
      securityPassRate: 1,
    },
    failures: [],
  }),
  getPolicyRagEvaluationReport: vi.fn().mockResolvedValue({
    datasetVersion: 'policy-rag-golden-v1',
    generatedAt: '2026-06-22T00:00:00Z',
    caseCount: 4,
    metrics: {
      recallAt5: 1,
      precisionAt5: 1,
      expectedPolicyHitRate: 1,
      expectedSectionHitRate: 1,
      noAnswerAccuracy: 1,
      injectionDefensePassed: true,
      averageSearchLatencyMs: 12,
    },
    failures: [],
  }),
  getRiskEvaluationReport: vi.fn().mockResolvedValue({
    datasetVersion: 'risk-golden-v1',
    datasetSha256: 'a'.repeat(64),
    engineVersion: 'deterministic-risk-v1',
    generatedAt: '2026-06-22T00:00:00Z',
    caseCount: 100,
    categoryCounts: {
      正常案例: 30,
      超标案例: 20,
      缺票案例: 10,
      重复票据: 10,
      金额不一致: 10,
      制度边界: 10,
      提示注入: 5,
      低质量图片: 5,
    },
    metrics: {
      precision: 1,
      recall: 1,
      f1: 1,
      riskLevelAccuracy: 1,
      humanReviewAccuracy: 1,
      highRiskMissRate: 0,
      humanReviewTriggerRate: 0.6,
    },
    agentGovernance: {
      planVersion: 'expenseflow-multi-agent-v1',
      totalAgents: 6,
      writeAgentCount: 1,
      idempotentWriteAgentCount: 1,
      writeToolIsolationPassed: true,
      settlementWriteRetryProtected: true,
      humanHandoffCoverage: 1,
      retryableAgentRate: 0.5,
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

    expect(await screen.findByText('risk-golden-v1')).toBeInTheDocument();
    expect(screen.getByText('黄金案例').closest('.ant-statistic')).toHaveTextContent('100条');
    expect(screen.getByText('低质量图片')).toBeInTheDocument();
    expect(screen.getByText('当前基线没有回归失败案例')).toBeInTheDocument();
    expect(screen.getByText('高风险漏报率')).toBeInTheDocument();
    expect(screen.getByText('Agent 治理门禁')).toBeInTheDocument();
    expect(screen.getByText('写 Tool 隔离通过')).toBeInTheDocument();
    expect(screen.getByText('审批写入幂等重试已保护')).toBeInTheDocument();
  });
});
