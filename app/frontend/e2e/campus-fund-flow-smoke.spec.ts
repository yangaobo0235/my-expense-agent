import { expect, test } from '@playwright/test';

test.describe.configure({ mode: 'serial' });

const caseId = '11111111-1111-4111-8111-111111111111';
const reviewTaskId = '22222222-2222-4222-8222-222222222222';
const documentId = '33333333-3333-4333-8333-333333333333';
const failedCaseId = '44444444-4444-4444-8444-444444444444';

const waitingHumanCase = {
  id: caseId,
  caseNumber: 'CF-20260622-0001',
  applicantName: '李明',
  projectCode: 'CS-SRTP',
  title: '蓝桥杯竞赛差旅报销',
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
  caseNumber: 'CF-20260622-0002',
  title: '实验耗材票据恢复测试',
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

  await page.addInitScript(() => {
    window.localStorage.setItem(
      'expense-e2e-user',
      JSON.stringify({
        subject: 'reviewer-fixture',
        displayName: '学院审核员',
        roles: ['STUDENT', 'COLLEGE_REVIEWER', 'FINANCE_ADMIN', 'AUDITOR'],
      }),
    );
  });

  await page.route('**/api/v1/fund-applications?**', async (route) => {
    await route.fulfill({
      json: {
        items: [approved ? { ...approvedCase, settlementStatus: settled ? 'SUBMITTED' : 'NOT_SUBMITTED' } : waitingHumanCase],
        page: 0,
        size: 20,
        total: 1,
      },
    });
  });

  await page.route(`**/api/v1/fund-applications/${caseId}`, async (route) => {
    await route.fulfill({
      json: approved
        ? { ...approvedCase, settlementStatus: settled ? 'SUBMITTED' : 'NOT_SUBMITTED' }
        : waitingHumanCase,
    });
  });

  await page.route('**/api/v1/fund-applications', async (route) => {
    if (route.request().method() !== 'POST') return route.fallback();
    await route.fulfill({ status: 201, json: { ...waitingHumanCase, status: 'UPLOADED' } });
  });

  await page.route(`**/api/v1/fund-applications/${caseId}/documents`, async (route) => {
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
              sellerName: '南京青奥酒店',
              buyerName: '江南大学',
              issueDate: '2026-06-21',
              totalAmount: 1280.5,
              currency: 'CNY',
              confidence: 0.93,
              warnings: [],
              items: [
                {
                  description: '竞赛住宿费',
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

  await page.route(`**/api/v1/fund-applications/${caseId}/analyze`, async (route) => {
    await route.fulfill({ json: { caseId, status: 'EXTRACTED' } });
  });

  await page.route(`**/api/v1/fund-applications/${caseId}/events`, async (route) => {
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

  await page.route(`**/api/v1/fund-applications/${caseId}/evidence`, async (route) => {
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
            name: 'EXECUTION_POLICY',
            attempt: 1,
            status: 'SUCCEEDED',
            durationMs: 4,
            evidence: {
              planVersion: 'my-expense-governed-policy-v1',
              steps: [
                {
                  sequence: 1,
                  capability: 'RECEIPT_EXTRACTION_AGENT',
                  name: '票据提取',
                  responsibility: '结构化票据字段。',
                  allowedTools: [],
                  writeOperationAllowed: false,
                  failurePolicy: 'REQUIRE_HUMAN_REVIEW',
                  maxAttempts: 1,
                  handoffTarget: '审核员人工补录票据字段',
                },
                {
                  sequence: 3,
                  capability: 'POLICY_RAG_AGENT',
                  name: '制度检索',
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
            evidence: { category: '竞赛差旅费', region: 'CN' },
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
            policyCode: 'COMPETITION-TRAVEL-v1',
            policyName: '学生竞赛差旅经费管理办法',
            version: 'v1',
            section: '竞赛住宿标准',
            chunkId: 'chunk-1',
            content: '学生参加校级认定竞赛，住宿费每晚上限 350 元，超出部分需要指导老师和学院复核。',
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
              message: '票面金额超过学生竞赛住宿标准。',
              evidence: { limit: '350', amount: '1280.50' },
            },
          ],
        },
        toolCalls: settled
          ? [
              {
                id: 'tool-budget-1',
                toolName: 'debit_project_budget',
                writeOperation: true,
                status: 'SUCCEEDED',
                output: { debitId: '44444444-4444-4444-8444-444444444444' },
                durationMs: 24,
                approvalReference: 'decision:review-request-1',
                createdAt: '2026-06-22T10:45:59+08:00',
                completedAt: '2026-06-22T10:46:00+08:00',
              },
              {
                id: 'tool-reimbursement-1',
                toolName: 'submit_fund_reimbursement',
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
                toolName: 'submit_fund_posting',
                writeOperation: true,
                status: 'SUCCEEDED',
                output: { postingId: '66666666-6666-4666-8666-666666666666', status: 'SUBMITTED' },
                durationMs: 42,
                approvalReference: 'decision:review-request-1',
                createdAt: '2026-06-22T10:46:01+08:00',
                completedAt: '2026-06-22T10:46:02+08:00',
              },
              {
                id: 'tool-history-1',
                toolName: 'record_fund_reimbursement_history',
                writeOperation: true,
                status: 'SUCCEEDED',
                output: { historyId: '77777777-7777-4777-8777-777777777777' },
                durationMs: 18,
                approvalReference: 'decision:review-request-1',
                createdAt: '2026-06-22T10:46:02+08:00',
                completedAt: '2026-06-22T10:46:03+08:00',
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

  await page.route(`**/api/v1/review-tasks/${reviewTaskId}`, async (route) => {
    await route.fulfill({
      json: {
        id: reviewTaskId,
        caseId,
        status: 'OPEN',
        assigneeRole: 'COLLEGE_REVIEWER',
        reasonCodes: ['POLICY_LIMIT_EXCEEDED', 'DUPLICATE_DOCUMENT'],
        requiredEvidence: ['ORIGINAL_DOCUMENT', 'POLICY_CITATION'],
        version: 1,
        createdAt: '2026-06-22T10:31:00+08:00',
        updatedAt: '2026-06-22T10:31:00+08:00',
      },
    });
  });

  await page.route(`**/api/v1/review-tasks/${reviewTaskId}/approve`, async (route) => {
    approved = true;
    await route.fulfill({ json: approvedCase });
  });

  await page.route(`**/api/v1/fund-applications/${caseId}/posting`, async (route) => {
    settled = true;
    await route.fulfill({
      json: {
        caseId,
        budgetDebitId: '44444444-4444-4444-8444-444444444444',
        reimbursementId: '55555555-5555-4555-8555-555555555555',
        postingId: '66666666-6666-4666-8666-666666666666',
        historyRecordIds: ['77777777-7777-4777-8777-777777777777'],
        amount: 1280.5,
        currency: 'CNY',
        status: 'SUBMITTED',
      },
    });
  });

  await page.route('**/api/v1/evaluations/risk/latest', async (route) => {
    await route.fulfill({
      json: {
        datasetVersion: 'risk-golden-v3',
        datasetSha256: 'fixture-dataset-sha256',
        engineVersion: 'deterministic-risk-v1',
        generatedAt: '2026-06-22T10:40:00+08:00',
        caseCount: 300,
        categoryCounts: {
          NORMAL: 100,
          POLICY_LIMIT: 100,
          DUPLICATE: 100,
        },
        metrics: {
          riskLevelAccuracy: 0.91,
          routingAccuracy: 0.93,
          highRiskRecall: 0.877,
        },
        failures: [],
      },
    });
  });

  await page.route('**/api/v1/evaluations/extraction/latest', async (route) => {
    await route.fulfill({
      json: {
        datasetVersion: 'extraction-golden-v1',
        generatedAt: '2026-06-22T10:40:00+08:00',
        caseCount: 30,
        metrics: {
          jsonValidRate: 1,
          schemaPassRate: 1,
          amountExactMatch: 1,
          dateAccuracy: 1,
          currencyAccuracy: 1,
        },
        gatePassed: true,
        failures: [],
      },
    });
  });
});

test('navigates reviewer core pages with fixture backend contracts', async ({ page }) => {
  await page.goto('/cases');

  await expect(page.getByText('财务管理信息平台')).toBeVisible();
  await expect(page.getByRole('heading', { name: '经费申请' })).toBeVisible();
  await expect(page.getByText('CF-20260622-0001')).toBeVisible();
  await expect(page.getByText('李明')).toBeVisible();

  await page.getByRole('button', { name: '新建申请' }).click();
  await expect(page.getByRole('heading', { name: '新建经费报销申请' })).toBeVisible();
  await expect(page.getByText('安全上传')).toBeVisible();

  await page.getByRole('menuitem', { name: /审核任务/ }).click();
  await expect(page.getByRole('heading', { name: '待我处理' })).toBeVisible();
  await expect(page.getByText('金额可能超过适用标准')).toBeVisible();

  await page.getByRole('menuitem', { name: /质量评测/ }).click();
  await expect(page.getByRole('heading', { name: '回归评测' })).toBeVisible();
  await expect(page.getByText('extraction-golden-v1')).toBeVisible();
  await expect(page.getByText('关键字段回归通过')).toBeVisible();
});

test('covers upload, evidence detail, review decision and evaluation with fixture contracts', async ({
  page,
}) => {
  await page.goto('/cases/new');

  await page.getByLabel('申请人').fill('李明');
  await page.getByLabel('经费项目编码').fill('CS-SRTP');
  await page.getByLabel('报销事项').fill('蓝桥杯竞赛差旅报销');
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

  await expect(page.getByRole('heading', { name: '蓝桥杯竞赛差旅报销' })).toBeVisible();
  await expect(page.getByText('CF-20260622-0001').first()).toBeVisible();
  await expect(page.getByText('票据已识别')).toBeVisible();

  await page.getByRole('button', { name: /票据识别/ }).click();
  await expect(page.getByText('INV-20260622-001')).toBeVisible();
  await expect(page.getByText('南京青奥酒店')).toBeVisible();

  await page.getByRole('button', { name: /证据收集/ }).click();
  await expect(page.getByText('受治理执行策略').first()).toBeVisible();
  await expect(page.getByText('本次申请按固定能力边界核对票据、制度和风险。')).toBeVisible();

  await page.getByRole('button', { name: /制度核对/ }).click();
  await expect(page.getByRole('button', { name: /学生竞赛差旅经费管理办法 v1/ })).toBeVisible();

  await page.getByRole('button', { name: /风险评估/ }).click();
  await expect(page.getByText('超过制度额度')).toBeVisible();
  await expect(page.getByText('票面金额超过学生竞赛住宿标准。')).toBeVisible();

  await page.getByRole('menuitem', { name: /审核任务/ }).click();
  await expect(page.getByRole('heading', { name: '待我处理' })).toBeVisible();
  await page.getByRole('button', { name: /处\s*理/ }).click();
  await page.getByLabel('审核说明').fill('已核验证据链，批准按制度处理。');
  await page.getByRole('button', { name: '确认提交' }).click();
  await expect(page.getByText('审核动作已提交')).toBeVisible();

  await page.getByRole('menuitem', { name: /经费申请/ }).click();
  await page.getByText('CF-20260622-0001').click();
  await expect(page.getByText('已批准', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '发起入账', exact: true }).click();
  await expect(page.getByText(/入账提交完成/)).toBeVisible();
  await expect(page.getByText('入账已提交').first()).toBeVisible();
  await expect(page.getByRole('button', { name: '发起入账', exact: true })).not.toBeVisible();

  await page.getByRole('menuitem', { name: /质量评测/ }).click();
  await page.getByRole('tab', { name: '风险分流' }).click();
  await expect(page.getByText('分级准确率')).toBeVisible();
  await expect(page.getByText('路由准确率')).toBeVisible();
  await expect(page.getByText('高风险召回率')).toBeVisible();
});

test('resumes a failed workflow and refreshes recovered evidence', async ({ page }) => {
  let recovered = false;

  await page.route(`**/api/v1/fund-applications/${failedCaseId}`, async (route) => {
    await route.fulfill({ json: recovered ? recoveredCase : failedCase });
  });

  await page.route(`**/api/v1/fund-applications/${failedCaseId}/documents`, async (route) => {
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
              buyerName: '江南大学',
              issueDate: '2026-06-21',
              totalAmount: 2600,
              currency: 'CNY',
              confidence: 0.68,
              warnings: ['置信度偏低，恢复后必须进入人工复核。'],
              items: [{ description: '实验耗材试剂', quantity: 1, unitPrice: 2600, amount: 2600 }],
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

  await page.route(`**/api/v1/fund-applications/${failedCaseId}/events`, async (route) => {
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

  await page.route(`**/api/v1/fund-applications/${failedCaseId}/evidence`, async (route) => {
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
                name: 'MCP_APPLICANT_CONTEXT',
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
                policyId: 'policy-lab-supply',
                policyCode: 'LAB-SUPPLY-v1',
                policyName: '学生科研项目耗材经费管理办法',
                version: 'v1',
                section: '高额耗材审批',
                chunkId: 'chunk-recovered',
                content: '学生科研项目单笔耗材超过 2000 元必须进入指导老师与学院复核，并保留采购审批证据。',
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
                  message: '实验耗材金额超过项目经费制度阈值。',
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

  await page.route(`**/api/v1/fund-applications/${failedCaseId}/workflow`, async (route) => {
    recovered = true;
    await route.fulfill({ json: { caseId: failedCaseId, status: 'WAITING_HUMAN' } });
  });

  await page.goto(`/cases/${failedCaseId}`);

  await expect(page.getByRole('heading', { name: '实验耗材票据恢复测试' })).toBeVisible();
  await expect(page.getByText('处理失败').first()).toBeVisible();
  await expect(page.getByText('历史重复检测失败')).toBeVisible();
  await expect(page.getByText('audit-history MCP timeout')).toBeVisible();

  await page.getByRole('button', { name: '从这里重试' }).click();
  await page.getByLabel('经费科目').click();
  await page.getByText('实验耗材费', { exact: true }).click();
  await expect(page.getByText('将从上次失败的位置继续')).toBeVisible();
  await expect(page.getByText('已经完成的步骤不会重复处理，只会重新执行失败的部分。')).toBeVisible();
  await page.getByRole('button', { name: '重试失败环节' }).click();

  await expect(page.getByText('等待人工审核').first()).toBeVisible();
  await expect(page.getByText('高风险 · 76').first()).toBeVisible();

  await page.getByRole('button', { name: /风险评估/ }).click();
  await expect(page.getByText('票据字段置信度低，需要人工复核。')).toBeVisible();
  await expect(page.getByText('实验耗材金额超过项目经费制度阈值。')).toBeVisible();

  await page.getByRole('button', { name: /制度核对/ }).click();
  await expect(page.getByRole('button', { name: /学生科研项目耗材经费管理办法 v1/ })).toBeVisible();
});

test('shows forbidden page for reviewer-only route without reviewer role', async ({ page }) => {
  await page.addInitScript(() => {
    window.localStorage.setItem(
      'expense-e2e-user',
      JSON.stringify({
        subject: 'student-only',
        displayName: '学生申请人',
        roles: ['STUDENT'],
      }),
    );
  });

  await page.goto('/reviews');

  await expect(page.getByText('无权访问')).toBeVisible();
  await expect(page.getByText('当前角色不能访问此页面。')).toBeVisible();
  await expect(page.getByText('人工审核队列')).not.toBeVisible();
});

test('allows auditor to inspect review records without decision controls', async ({ page }) => {
  await page.addInitScript(() => {
    window.localStorage.setItem(
      'expense-e2e-user',
      JSON.stringify({
        subject: 'auditor-only',
        displayName: '审计员',
        roles: ['AUDITOR'],
      }),
    );
  });

  await page.goto('/reviews');

  await expect(page.getByRole('heading', { name: '待我处理' })).toBeVisible();
  await expect(page.getByRole('link', { name: '查看记录' })).toBeVisible();
  await expect(page.getByRole('button', { name: '快速处理' })).not.toBeVisible();
  await page.getByRole('link', { name: '查看记录' }).click();
  await expect(page.getByText('当前账号不能处理该任务')).toBeVisible();
  await expect(page.getByRole('button', { name: /提\s*交/ })).toBeDisabled();
});

test('redirects to login when API returns 401', async ({ page }) => {
  await page.route('**/api/v1/fund-applications?**', async (route) => {
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
  await expect(page.getByRole('button', { name: /登\s*录/ })).toBeVisible();
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

  await expect(page.getByRole('heading', { name: '待我处理' })).toBeVisible();
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

  await page.route(`**/api/v1/fund-applications/${caseId}/events`, async (route) => {
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

  await expect(page.getByText('EVENT STREAM RESET RECOVERED')).toBeVisible();
});

test('renders empty case list state from backend pagination contract', async ({ page }) => {
  await page.route('**/api/v1/fund-applications?**', async (route) => {
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

  await expect(page.getByRole('heading', { name: '经费申请' })).toBeVisible();
  await expect(page.getByText('暂无经费申请')).toBeVisible();
  await expect(page.getByText('CF-20260622-0001')).not.toBeVisible();
});

test('shows backend-driven error state for evaluation page', async ({ page }) => {
  await page.route('**/api/v1/evaluations/risk/latest', async (route) => {
    await route.fulfill({
      status: 500,
      json: {
        code: 'EVALUATION_REPORT_UNAVAILABLE',
        message: '评测报告生成失败，请检查数据集。',
        requestId: 'request-evaluation-500',
      },
    });
  });

  await page.goto('/evaluation');

  await expect(page.getByText('评测报告加载失败')).toBeVisible();
});

test('keeps upload form recoverable after backend upload failure and allows retry', async ({
  page,
}) => {
  let uploadAttempts = 0;

  await page.route(`**/api/v1/fund-applications/${caseId}/documents`, async (route) => {
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

  await page.getByLabel('申请人').fill('李明');
  await page.getByLabel('经费项目编码').fill('CS-SRTP');
  await page.getByLabel('报销事项').fill('蓝桥杯竞赛差旅报销');
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

  await expect(page.getByText('申请已创建，后续处理未完成')).toBeVisible();
  await expect(page.getByText('MinIO 暂时不可用，请稍后重试。')).toBeVisible();
  await expect(page.getByRole('heading', { name: '新建经费报销申请' })).toBeVisible();
  await expect(page.getByRole('button', { name: '创建、上传并提取' })).toBeEnabled();

  await page.getByRole('button', { name: '创建、上传并提取' }).click();

  await expect(page.getByRole('heading', { name: '蓝桥杯竞赛差旅报销' })).toBeVisible();
  await expect(page.getByText('票据已识别')).toBeVisible();
  expect(uploadAttempts).toBe(2);
});

test('keeps the application shell usable without horizontal overflow on mobile', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/cases');
  await expect(page.getByRole('heading', { name: '经费申请' })).toBeVisible();

  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  expect(overflow).toBeLessThanOrEqual(1);

  const navigationTrigger = page.getByRole('button', { name: '打开导航' });
  await expect(navigationTrigger).toBeVisible();
  await navigationTrigger.click();
  await expect(page.getByRole('menuitem', { name: /经费申请/ })).toBeVisible();
  await page.screenshot({ path: 'test-results/my-expense-agent-mobile.png', fullPage: true });
});
