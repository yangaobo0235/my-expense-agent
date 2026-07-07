import { expect, test } from '@playwright/test';

const caseId = '11111111-1111-4111-8111-111111111111';
const reviewTaskId = '22222222-2222-4222-8222-222222222222';
const documentId = '33333333-3333-4333-8333-333333333333';
const failedCaseId = '44444444-4444-4444-8444-444444444444';

const waitingHumanCase = {
  id: caseId,
  caseNumber: 'EF-20260622-0001',
  applicantName: '张三',
  departmentCode: 'RD',
  title: '上海差旅报销',
  claimedAmount: 1280.5,
  currency: 'CNY',
  status: 'WAITING_HUMAN',
  riskLevel: 'MEDIUM',
  riskScore: 52,
  version: 3,
  createdAt: '2026-06-22T09:00:00+08:00',
  updatedAt: '2026-06-22T10:30:00+08:00',
};

const approvedCase = {
  ...waitingHumanCase,
  status: 'APPROVED',
  riskLevel: 'LOW',
  riskScore: 18,
  version: 4,
  updatedAt: '2026-06-22T10:45:00+08:00',
};

const failedCase = {
  ...waitingHumanCase,
  id: failedCaseId,
  caseNumber: 'EF-20260622-0002',
  title: '失败恢复测试案例',
  status: 'FAILED',
  riskLevel: undefined,
  riskScore: undefined,
  version: 2,
};

const recoveredCase = {
  ...failedCase,
  status: 'WAITING_HUMAN',
  riskLevel: 'HIGH',
  riskScore: 76,
  version: 3,
  updatedAt: '2026-06-22T11:05:00+08:00',
};

test.beforeEach(async ({ page }) => {
  let approved = false;
  let settled = false;

  await page.route('**/api/v1/expense-cases?**', async (route) => {
    await route.fulfill({
      json: {
        items: [approved ? approvedCase : waitingHumanCase],
        page: 0,
        size: 20,
        total: 1,
      },
    });
  });

  await page.route(`**/api/v1/expense-cases/${caseId}`, async (route) => {
    await route.fulfill({ json: approved ? approvedCase : waitingHumanCase });
  });

  await page.route('**/api/v1/expense-cases', async (route) => {
    if (route.request().method() !== 'POST') return route.fallback();
    await route.fulfill({ status: 201, json: { ...waitingHumanCase, status: 'UPLOADED' } });
  });

  await page.route(`**/api/v1/expense-cases/${caseId}/documents`, async (route) => {
    if (route.request().method() === 'POST') {
      await route.fulfill({
        status: 201,
        json: {
          id: documentId,
          originalFilename: 'invoice.png',
        },
      });
      return;
    }
    await route.fulfill({
      json: [
        {
          id: documentId,
          originalFilename: 'invoice.png',
          contentType: 'image/png',
          fileSize: 12345,
          sha256: 'fixture-document-sha256',
          previewUrl:
            'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAEklEQVR4nGP4z8AAAAMBAQDJ/pLvAAAAAElFTkSuQmCC',
          previewExpiresAt: '2026-06-22T11:00:00+08:00',
          extraction: {
            result: {
              documentType: 'VAT_INVOICE',
              invoiceNumber: 'INV-20260622-001',
              sellerName: '上海差旅服务有限公司',
              buyerName: 'ExpenseFlow 科技',
              issueDate: '2026-06-21',
              totalAmount: 1280.5,
              currency: 'CNY',
              confidence: 0.93,
              warnings: [],
              items: [
                {
                  description: '酒店住宿',
                  quantity: 1,
                  unitPrice: 1280.5,
                  amount: 1280.5,
                },
              ],
            },
            validationErrors: [],
            modelName: 'deterministic-text-extractor',
            promptVersion: 'offline-v1',
          },
          createdAt: '2026-06-22T09:05:00+08:00',
        },
      ],
    });
  });

  await page.route(`**/api/v1/expense-cases/${caseId}/analyze`, async (route) => {
    await route.fulfill({ json: { caseId, status: 'EXTRACTED' } });
  });

  await page.route(`**/api/v1/expense-cases/${caseId}/events`, async (route) => {
    await route.fulfill({
      contentType: 'text/event-stream',
      body: [
        'id: event-1',
        `data: ${JSON.stringify({
          eventId: 'event-1',
          caseId,
          type: 'DOCUMENT_EXTRACTED',
          sequence: 1,
          occurredAt: '2026-06-22T09:10:00+08:00',
          payload: { documentId },
        })}`,
        '',
        '',
      ].join('\n'),
    });
  });

  await page.route(`**/api/v1/expense-cases/${caseId}/evidence`, async (route) => {
    await route.fulfill({
      json: {
        run: {
          id: 'run-1',
          requestId: 'request-1',
          status: 'SUCCEEDED',
          startedAt: '2026-06-22T09:20:00+08:00',
          completedAt: '2026-06-22T09:20:20+08:00',
          traceId: 'trace-fixture-001',
        },
        steps: [
          {
            id: 'step-plan',
            name: 'AGENT_PLAN',
            attempt: 1,
            status: 'SUCCEEDED',
            durationMs: 4,
            evidence: {
              planVersion: 'expenseflow-multi-agent-v1',
              agents: [
                {
                  sequence: 1,
                  role: 'RECEIPT_EXTRACTION_AGENT',
                  name: '票据提取 Agent',
                  responsibility: '结构化票据字段。',
                  allowedTools: [],
                  writeOperationAllowed: false,
                  failurePolicy: 'REQUIRE_HUMAN_REVIEW',
                  maxAttempts: 1,
                  handoffTarget: '审核员人工补录票据字段',
                },
                {
                  sequence: 3,
                  role: 'POLICY_RAG_AGENT',
                  name: '制度检索 Agent',
                  responsibility: '检索制度片段。',
                  allowedTools: ['calculate_allowed_amount'],
                  writeOperationAllowed: false,
                  failurePolicy: 'RETRY_THEN_HUMAN_REVIEW',
                  maxAttempts: 2,
                  handoffTarget: '审核员选择制度版本并补充引用',
                },
              ],
            },
          },
          {
            id: 'step-1',
            name: 'POLICY_RETRIEVAL',
            attempt: 1,
            status: 'SUCCEEDED',
            durationMs: 85,
            evidence: { category: '住宿费', region: 'CN' },
          },
          {
            id: 'step-2',
            name: 'RISK_ASSESSMENT',
            attempt: 1,
            status: 'SUCCEEDED',
            durationMs: 12,
            evidence: { score: 52 },
          },
        ],
        policyFindings: [
          {
            policyId: 'policy-1',
            policyCode: 'HOTEL-CN-V1',
            policyName: '住宿费制度',
            version: 'v1',
            section: '华东地区住宿标准',
            chunkId: 'chunk-1',
            content: '普通员工单晚住宿报销上限为 800 元，超出部分需要人工复核。',
            score: 0.91,
          },
        ],
        risk: {
          score: 52,
          level: 'MEDIUM',
          requiresHumanReview: true,
          signals: [
            {
              code: 'POLICY_LIMIT_EXCEEDED',
              score: 35,
              message: '票面金额超过住宿费制度额度。',
              evidence: { limit: '800', amount: '1280.50' },
            },
          ],
        },
        toolCalls: settled
          ? [
              {
                id: 'tool-reimbursement-1',
                toolName: 'submit_reimbursement',
                writeOperation: true,
                status: 'SUCCEEDED',
                output: { reimbursementId: '55555555-5555-4555-8555-555555555555' },
                durationMs: 38,
                approvalReference: 'decision:review-request-1',
                createdAt: '2026-06-22T10:46:00+08:00',
                completedAt: '2026-06-22T10:46:01+08:00',
              },
              {
                id: 'tool-payment-1',
                toolName: 'submit_payment',
                writeOperation: true,
                status: 'SUCCEEDED',
                output: { paymentId: '66666666-6666-4666-8666-666666666666', status: 'SUBMITTED' },
                durationMs: 42,
                approvalReference: 'decision:review-request-1',
                createdAt: '2026-06-22T10:46:01+08:00',
                completedAt: '2026-06-22T10:46:02+08:00',
              },
            ]
          : [],
      },
    });
  });

  await page.route('**/api/v1/review-tasks', async (route) => {
    await route.fulfill({
      json: [
        {
          id: reviewTaskId,
          caseId,
          status: 'OPEN',
          reasonCodes: ['POLICY_LIMIT_EXCEEDED', 'DUPLICATE_DOCUMENT'],
          version: 1,
          createdAt: '2026-06-22T10:31:00+08:00',
          updatedAt: '2026-06-22T10:31:00+08:00',
        },
      ],
    });
  });

  await page.route(`**/api/v1/review-tasks/${reviewTaskId}/approve`, async (route) => {
    approved = true;
    await route.fulfill({ json: approvedCase });
  });

  await page.route(`**/api/v1/expense-cases/${caseId}/settlement`, async (route) => {
    settled = true;
    await route.fulfill({
      json: {
        caseId,
        reimbursementId: '55555555-5555-4555-8555-555555555555',
        paymentId: '66666666-6666-4666-8666-666666666666',
        amount: 1280.5,
        currency: 'CNY',
        status: 'SUBMITTED',
      },
    });
  });

  await page.route('**/api/v1/evaluation/risk-report', async (route) => {
    await route.fulfill({
      json: {
        datasetVersion: 'risk-golden-v1',
        datasetSha256: 'fixture-dataset-sha256',
        engineVersion: 'deterministic-risk-v1',
        generatedAt: '2026-06-22T10:40:00+08:00',
        caseCount: 20,
        categoryCounts: {
          NORMAL: 8,
          POLICY_LIMIT: 6,
          DUPLICATE: 6,
        },
        metrics: {
          precision: 0.95,
          recall: 0.9,
          f1: 0.925,
          riskLevelAccuracy: 0.9,
          humanReviewAccuracy: 0.95,
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
      },
    });
  });

  await page.route('**/api/v1/observability/runs?**', async (route) => {
    await route.fulfill({
      json: [
        {
          runId: 'run-1',
          caseId,
          requestId: 'request-1',
          status: 'SUCCEEDED',
          startedAt: '2026-06-22T09:20:00+08:00',
          completedAt: '2026-06-22T09:20:20+08:00',
          traceId: 'trace-fixture-001',
          stepCount: 6,
          succeededStepCount: 6,
          failedStepCount: 0,
          durationMs: 20000,
          agentPlanRecorded: true,
        },
      ],
    });
  });
});

test('navigates reviewer core pages with fixture backend contracts', async ({ page }) => {
  await page.goto('/cases');

  await expect(page.getByText('ExpenseFlow')).toBeVisible();
  await expect(page.getByRole('heading', { name: '费用案例' })).toBeVisible();
  await expect(page.getByText('EF-20260622-0001')).toBeVisible();
  await expect(page.getByText('张三')).toBeVisible();

  await page.getByText('新建上传').click();
  await expect(page.getByRole('heading', { name: '新建费用案例' })).toBeVisible();
  await expect(page.getByText('安全上传')).toBeVisible();

  await page.getByText('人工审核', { exact: true }).click();
  await expect(page.getByRole('heading', { name: '人工审核队列' })).toBeVisible();
  await expect(page.getByText('POLICY_LIMIT_EXCEEDED')).toBeVisible();

  await page.getByText('评测报告').click();
  await expect(page.getByRole('heading', { name: '离线评测报告' })).toBeVisible();
  await expect(page.getByText('risk-golden-v1')).toBeVisible();
  await expect(page.getByText('Agent 治理门禁')).toBeVisible();
  await expect(page.getByText('写 Tool 隔离通过')).toBeVisible();
  await expect(page.getByText('当前基线没有回归失败案例')).toBeVisible();

  await page.getByText('可观测性').click();
  await expect(page.getByRole('heading', { name: '可观测性' })).toBeVisible();
  await expect(page.getByText('trace-fixture-001')).toBeVisible();
  await expect(page.getByText('6/6 成功')).toBeVisible();
  await expect(page.getByText('已记录 Agent Plan')).toBeVisible();
});

test('covers upload, evidence detail, review decision and evaluation with fixture contracts', async ({
  page,
}) => {
  await page.goto('/cases/new');

  await page.getByLabel('申请人').fill('张三');
  await page.getByLabel('部门编码').fill('RD');
  await page.getByLabel('费用标题').fill('上海差旅报销');
  await page.getByLabel('申报金额').fill('1280.50');

  await page
    .locator('input[type="file"]')
    .setInputFiles({
      name: 'invoice.png',
      mimeType: 'image/png',
      buffer: Buffer.from(
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAEklEQVR4nGP4z8AAAAMBAQDJ/pLvAAAAAElFTkSuQmCC',
        'base64',
      ),
    });

  await page.getByRole('button', { name: '创建、上传并提取' }).click();

  await expect(page.getByRole('heading', { name: '上海差旅报销' })).toBeVisible();
  await expect(page.getByText('EF-20260622-0001')).toBeVisible();
  await expect(page.getByText('DOCUMENT_EXTRACTED')).toBeVisible();
  await expect(page.getByText('INV-20260622-001')).toBeVisible();
  await expect(page.getByText('上海差旅服务有限公司')).toBeVisible();
  await expect(page.getByText('制度、风险与 Tool 证据链')).toBeVisible();
  await expect(page.getByText('多 Agent 编排计划')).toBeVisible();
  await expect(page.getByRole('button', { name: /住宿费制度 vv1/ })).toBeVisible();
  await expect(page.getByText('POLICY_LIMIT_EXCEEDED')).toBeVisible();

  await page.getByText('人工审核', { exact: true }).click();
  await expect(page.getByRole('heading', { name: '人工审核队列' })).toBeVisible();
  await page.getByRole('button', { name: /处\s*理/ }).click();
  await page.getByLabel('审核说明').fill('已核验证据链，批准按制度处理。');
  await page.getByRole('button', { name: '确认提交' }).click();
  await expect(page.getByText('审核动作已提交')).toBeVisible();

  await page.getByText('费用案例').click();
  await page.getByText('EF-20260622-0001').click();
  await expect(page.getByText('已批准')).toBeVisible();
  await page.getByRole('button', { name: '发起结算' }).click();
  await expect(page.getByText(/结算完成/)).toBeVisible();
  await expect(page.getByText('结算已提交')).toBeVisible();
  await expect(page.getByRole('button', { name: '发起结算' })).not.toBeVisible();
  await expect(page.getByText('submit_reimbursement')).toBeVisible();
  await expect(page.getByText('submit_payment')).toBeVisible();

  await page.getByText('评测报告').click();
  await expect(page.getByText('精确率')).toBeVisible();
  await expect(page.getByText('高风险漏报率')).toBeVisible();
  await expect(page.getByText('审批写入幂等重试已保护')).toBeVisible();
});

test('resumes a failed workflow and refreshes recovered evidence', async ({ page }) => {
  let recovered = false;

  await page.route(`**/api/v1/expense-cases/${failedCaseId}`, async (route) => {
    await route.fulfill({ json: recovered ? recoveredCase : failedCase });
  });

  await page.route(`**/api/v1/expense-cases/${failedCaseId}/documents`, async (route) => {
    await route.fulfill({
      json: [
        {
          id: documentId,
          originalFilename: 'failed-invoice.png',
          contentType: 'image/png',
          fileSize: 12345,
          sha256: 'fixture-failed-document-sha256',
          previewUrl:
            'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAEklEQVR4nGP4z8AAAAMBAQDJ/pLvAAAAAElFTkSuQmCC',
          previewExpiresAt: '2026-06-22T12:00:00+08:00',
          extraction: {
            result: {
              documentType: 'VAT_INVOICE',
              invoiceNumber: 'INV-FAILED-001',
              sellerName: '恢复测试供应商',
              buyerName: 'ExpenseFlow 科技',
              issueDate: '2026-06-21',
              totalAmount: 2600,
              currency: 'CNY',
              confidence: 0.68,
              warnings: ['置信度偏低，恢复后必须进入人工复核。'],
              items: [{ description: '业务招待餐饮', quantity: 1, unitPrice: 2600, amount: 2600 }],
            },
            validationErrors: ['票据置信度低于自动通过阈值'],
            modelName: 'deterministic-text-extractor',
            promptVersion: 'offline-v1',
          },
          createdAt: '2026-06-22T10:50:00+08:00',
        },
      ],
    });
  });

  await page.route(`**/api/v1/expense-cases/${failedCaseId}/events`, async (route) => {
    await route.fulfill({
      contentType: 'text/event-stream',
      body: [
        'id: failed-event-1',
        `data: ${JSON.stringify({
          eventId: 'failed-event-1',
          caseId: failedCaseId,
          type: recovered ? 'WORKFLOW_RESUMED' : 'WORKFLOW_FAILED',
          sequence: 1,
          occurredAt: '2026-06-22T11:00:00+08:00',
          payload: {},
        })}`,
        '',
        '',
      ].join('\n'),
    });
  });

  await page.route(`**/api/v1/expense-cases/${failedCaseId}/evidence`, async (route) => {
    await route.fulfill({
      json: recovered
        ? {
            run: {
              id: 'run-recovered',
              requestId: 'resume-request-1',
              status: 'SUCCEEDED',
              startedAt: '2026-06-22T11:00:00+08:00',
              completedAt: '2026-06-22T11:00:25+08:00',
              traceId: 'trace-recovered-001',
            },
            steps: [
              {
                id: 'step-reuse',
                name: 'MCP_EMPLOYEE_CONTEXT',
                attempt: 1,
                status: 'SUCCEEDED',
                durationMs: 0,
                evidence: { reused: true },
              },
              {
                id: 'step-risk',
                name: 'RISK_ASSESSMENT',
                attempt: 2,
                status: 'SUCCEEDED',
                durationMs: 19,
                evidence: { score: 76, resumed: true },
              },
            ],
            policyFindings: [
              {
                policyId: 'policy-business-meal',
                policyCode: 'MEAL-CN-V1',
                policyName: '业务招待费制度',
                version: 'v1',
                section: '高额招待审批',
                chunkId: 'chunk-recovered',
                content: '单笔业务招待超过 2000 元必须进入人工复核并保留审批证据。',
                score: 0.94,
              },
            ],
            risk: {
              score: 76,
              level: 'HIGH',
              requiresHumanReview: true,
              signals: [
                {
                  code: 'LOW_EXTRACTION_CONFIDENCE',
                  score: 20,
                  message: '票据字段置信度低，需要人工复核。',
                  evidence: { confidence: '0.68' },
                },
                {
                  code: 'POLICY_LIMIT_EXCEEDED',
                  score: 35,
                  message: '业务招待金额超过制度阈值。',
                  evidence: { limit: '2000', amount: '2600' },
                },
              ],
            },
            toolCalls: [],
          }
        : {
            run: {
              id: 'run-failed',
              requestId: 'resume-request-1',
              status: 'FAILED',
              startedAt: '2026-06-22T10:58:00+08:00',
              completedAt: '2026-06-22T10:58:09+08:00',
              errorCode: 'DEPENDENCY_UNAVAILABLE',
              errorMessage: 'audit-history MCP 暂时不可用，等待恢复后可重试。',
              traceId: 'trace-failed-001',
            },
            steps: [
              {
                id: 'step-failed',
                name: 'MCP_DUPLICATE_CHECK',
                attempt: 1,
                status: 'FAILED',
                durationMs: 3000,
                errorCode: 'DEPENDENCY_UNAVAILABLE',
                errorMessage: 'audit-history MCP timeout',
                evidence: { retryable: true },
              },
            ],
            policyFindings: [],
            risk: undefined,
            toolCalls: [],
          },
    });
  });

  await page.route(`**/api/v1/expense-cases/${failedCaseId}/workflow`, async (route) => {
    recovered = true;
    await route.fulfill({ json: { caseId: failedCaseId, status: 'WAITING_HUMAN' } });
  });

  await page.goto(`/cases/${failedCaseId}`);

  await expect(page.getByRole('heading', { name: '失败恢复测试案例' })).toBeVisible();
  await expect(page.getByText('处理失败')).toBeVisible();
  await expect(page.getByText('DEPENDENCY_UNAVAILABLE')).toBeVisible();
  await expect(page.getByText('audit-history MCP 暂时不可用，等待恢复后可重试。')).toBeVisible();

  await page.getByRole('button', { name: '启动完整审核' }).click();
  await page.getByLabel('费用类别').click();
  await page.getByText('业务招待费', { exact: true }).click();
  const policyLimitExceeded = page.locator('input[value="policyLimitExceeded"]');
  const missingRequiredDocument = page.locator('input[value="missingRequiredDocument"]');
  await page.getByText('制度额度超标', { exact: true }).click();
  await page.getByText('缺少必要凭证', { exact: true }).click();
  await expect(policyLimitExceeded).toBeChecked();
  await expect(missingRequiredDocument).toBeChecked();
  await page.getByRole('button', { name: '开始审核' }).click();

  await expect(page.getByText('完整审核工作流执行完成')).toBeVisible();
  await expect(page.getByText('等待人工审核')).toBeVisible();
  await expect(page.getByText('trace-recovered-001')).toBeVisible();
  await expect(page.getByText('LOW_EXTRACTION_CONFIDENCE')).toBeVisible();
  await expect(page.getByRole('button', { name: /业务招待费制度 vv1/ })).toBeVisible();
});

test('shows forbidden page for reviewer-only route without reviewer role', async ({ page }) => {
  await page.addInitScript(() => {
    window.localStorage.setItem(
      'expense-e2e-user',
      JSON.stringify({
        subject: 'employee-only',
        displayName: '普通员工',
        roles: ['EMPLOYEE'],
      }),
    );
  });

  await page.goto('/reviews');

  await expect(page.getByText('无权访问')).toBeVisible();
  await expect(page.getByText('当前角色不能访问此页面。')).toBeVisible();
  await expect(page.getByText('人工审核队列')).not.toBeVisible();
});

test('redirects to login when API returns 401', async ({ page }) => {
  await page.route('**/api/v1/expense-cases?**', async (route) => {
    await route.fulfill({
      status: 401,
      json: {
        code: 'AUTHENTICATION_REQUIRED',
        message: '登录已过期，请重新认证。',
        requestId: 'request-401',
      },
    });
  });

  await page.goto('/cases');

  await expect(page).toHaveURL(/\/login\?reason=session-expired/);
  await expect(page.getByRole('button', { name: '使用 Keycloak 安全登录' })).toBeVisible();
});

test('surfaces review conflict when idempotent decision is already handled', async ({ page }) => {
  await page.route(`**/api/v1/review-tasks/${reviewTaskId}/approve`, async (route) => {
    await route.fulfill({
      status: 409,
      json: {
        code: 'REVIEW_TASK_ALREADY_HANDLED',
        message: '该审核任务已被其他审核人处理，请刷新队列。',
        requestId: 'request-409',
      },
    });
  });

  await page.goto('/reviews');

  await expect(page.getByRole('heading', { name: '人工审核队列' })).toBeVisible();
  await page.getByRole('button', { name: /处\s*理/ }).click();
  await page.getByLabel('审核说明').fill('尝试提交已处理任务。');
  await page.getByRole('button', { name: '确认提交' }).click();

  await expect(page.getByText('该审核任务已被其他审核人处理，请刷新队列。')).toBeVisible();
});

test('recovers case event stream when server rejects stale Last-Event-ID', async ({ page }) => {
  let eventCalls = 0;
  await page.addInitScript(([id]) => {
    window.sessionStorage.setItem(`expense-event:${id}`, 'stale-event-id');
  }, [caseId]);

  await page.route(`**/api/v1/expense-cases/${caseId}/events`, async (route) => {
    eventCalls += 1;
    if (eventCalls === 1) {
      await route.fulfill({
        status: 422,
        json: {
          code: 'EVENT_CURSOR_EXPIRED',
          message: '事件游标已过期，请重新拉取快照。',
          requestId: 'request-sse-reset',
        },
      });
      return;
    }
    await route.fulfill({
      contentType: 'text/event-stream',
      body: [
        'id: event-after-reset',
        `data: ${JSON.stringify({
          eventId: 'event-after-reset',
          caseId,
          type: 'EVENT_STREAM_RESET_RECOVERED',
          sequence: 1,
          occurredAt: '2026-06-22T12:00:00+08:00',
          payload: { recovered: true },
        })}`,
        '',
        '',
      ].join('\n'),
    });
  });

  await page.goto(`/cases/${caseId}`);

  await expect(page.getByText('EVENT_STREAM_RESET_RECOVERED')).toBeVisible();
  await expect(
    page.evaluate((id) => sessionStorage.getItem(`expense-event:${id}`), caseId),
  ).resolves.toBe('event-after-reset');
});

test('renders empty case list state from backend pagination contract', async ({ page }) => {
  await page.route('**/api/v1/expense-cases?**', async (route) => {
    await route.fulfill({
      json: {
        items: [],
        page: 0,
        size: 20,
        total: 0,
      },
    });
  });

  await page.goto('/cases');

  await expect(page.getByRole('heading', { name: '费用案例' })).toBeVisible();
  await expect(page.getByText('暂无费用案例')).toBeVisible();
  await expect(page.getByText('EF-20260622-0001')).not.toBeVisible();
});

test('shows backend-driven error states for evaluation and observability pages', async ({
  page,
}) => {
  await page.route('**/api/v1/evaluation/risk-report', async (route) => {
    await route.fulfill({
      status: 500,
      json: {
        code: 'EVALUATION_REPORT_UNAVAILABLE',
        message: '评测报告生成失败，请检查数据集。',
        requestId: 'request-evaluation-500',
      },
    });
  });

  await page.route('**/api/v1/observability/runs?**', async (route) => {
    await route.fulfill({
      status: 500,
      json: {
        code: 'OBSERVABILITY_INDEX_UNAVAILABLE',
        message: 'Trace 索引暂不可用。',
        requestId: 'request-observability-500',
      },
    });
  });

  await page.goto('/evaluation');

  await expect(page.getByText('评测报告加载失败')).toBeVisible();
  await expect(page.getByText('请确认后端从项目根目录启动')).toBeVisible();

  await page.getByText('可观测性').click();

  await expect(page.getByRole('heading', { name: '可观测性' })).toBeVisible();
  await expect(page.getByText('运行索引加载失败')).toBeVisible();
});

test('keeps upload form recoverable after backend upload failure and allows retry', async ({
  page,
}) => {
  let uploadAttempts = 0;

  await page.route(`**/api/v1/expense-cases/${caseId}/documents`, async (route) => {
    if (route.request().method() === 'POST') {
      uploadAttempts += 1;
      if (uploadAttempts === 1) {
        await route.fulfill({
          status: 503,
          json: {
            code: 'DEPENDENCY_UNAVAILABLE',
            message: 'MinIO 暂时不可用，请稍后重试。',
            requestId: 'request-upload-503',
          },
        });
        return;
      }
      await route.fulfill({
        status: 201,
        json: {
          id: documentId,
          originalFilename: 'invoice.png',
        },
      });
      return;
    }
    await route.fallback();
  });

  await page.goto('/cases/new');

  await page.getByLabel('申请人').fill('张三');
  await page.getByLabel('部门编码').fill('RD');
  await page.getByLabel('费用标题').fill('上海差旅报销');
  await page.getByLabel('申报金额').fill('1280.50');
  await page
    .locator('input[type="file"]')
    .setInputFiles({
      name: 'invoice.png',
      mimeType: 'image/png',
      buffer: Buffer.from(
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAEklEQVR4nGP4z8AAAAMBAQDJ/pLvAAAAAElFTkSuQmCC',
        'base64',
      ),
    });

  await page.getByRole('button', { name: '创建、上传并提取' }).click();

  await expect(page.getByText('创建或提取失败')).toBeVisible();
  await expect(page.getByText('MinIO 暂时不可用，请稍后重试。')).toBeVisible();
  await expect(page.getByRole('heading', { name: '新建费用案例' })).toBeVisible();
  await expect(page.getByRole('button', { name: '创建、上传并提取' })).toBeEnabled();

  await page.getByRole('button', { name: '创建、上传并提取' }).click();

  await expect(page.getByRole('heading', { name: '上海差旅报销' })).toBeVisible();
  await expect(page.getByText('DOCUMENT_EXTRACTED')).toBeVisible();
  expect(uploadAttempts).toBe(2);
});
