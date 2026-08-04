import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { EvidencePanel } from './EvidencePanel';

describe('EvidencePanel', () => {
  it('renders policy citations, risks and workflow evidence', () => {
    render(
      <EvidencePanel
        evidence={{
          run: {
            id: 'run-1',
            requestId: 'request-1',
            status: 'SUCCEEDED',
            startedAt: '2026-06-22T00:00:00Z',
          },
          steps: [
            {
              id: 'step-plan',
              name: 'EXECUTION_POLICY',
              attempt: 1,
              status: 'SUCCEEDED',
              durationMs: 5,
              evidence: {
                planVersion: 'my-expense-governed-policy-v1',
                steps: [
                  {
                    sequence: 3,
                    capability: 'POLICY_RAG_AGENT',
                    name: '适用制度检索',
                    responsibility: '检索制度片段并保留引用。',
                    allowedTools: ['calculate_allowed_amount'],
                    writeOperationAllowed: false,
                    failurePolicy: 'RETRY_THEN_HUMAN_REVIEW',
                    maxAttempts: 2,
                    handoffTarget: '审核员选择制度版本并补充引用',
                  },
                  {
                    sequence: 6,
                    capability: 'APPROVED_SETTLEMENT_AGENT',
                    name: '审批后受控写入',
                    responsibility: '审批通过后调用写 Tool。',
                    allowedTools: ['submit_fund_posting'],
                    writeOperationAllowed: true,
                    failurePolicy: 'IDEMPOTENT_WRITE_RETRY',
                    maxAttempts: 3,
                    handoffTarget: '学院财务复核入账状态并人工补偿',
                  },
                ],
              },
            },
            {
              id: 'step-1',
              name: 'MCP_DUPLICATE_CHECK',
              attempt: 1,
              status: 'SUCCEEDED',
              durationMs: 42,
              evidence: { duplicate: true },
            },
          ],
          policyFindings: [{
            policyId: 'policy-1',
            policyCode: 'COMPETITION-TRAVEL-CN',
            policyName: '学生竞赛差旅经费管理办法',
            version: '1.0',
            section: '金额上限',
            chunkId: 'chunk-1',
            content: '学生竞赛住宿费每日上限三百五十元。',
            score: 0.91,
          }],
          risk: {
            score: 30,
            level: 'MEDIUM',
            requiresHumanReview: true,
            signals: [{
              code: 'DUPLICATE_DOCUMENT',
              score: 30,
              message: '检测到重复凭证',
              evidence: {},
            }],
          },
          toolCalls: [],
        }}
      />,
    );

    expect(screen.getByText('受治理执行策略')).toBeInTheDocument();
    fireEvent.click(screen.getByText('查看执行策略'));
    expect(screen.getByText('学院财务复核入账状态并人工补偿')).toBeInTheDocument();
    expect(screen.getByText('历史重复检测')).toBeInTheDocument();
    expect(screen.getByText('学生竞赛差旅经费管理办法 v1.0 · 金额上限')).toBeInTheDocument();
    expect(screen.getByText('检测到重复凭证')).toBeInTheDocument();
    expect(screen.getByText('需要人工复核')).toBeInTheDocument();
  });
});
