import { http, HttpResponse } from 'msw';
import {
  fixtureCase,
  fixtureCaseObservability,
  fixtureCaseId,
  fixtureDocuments,
  fixtureEvidence,
  fixtureEvents,
  fixtureModelCalls,
  fixtureModelSummary,
  fixtureObservableRuns,
  fixturePolicies,
  fixturePolicyMatches,
  fixturePromptChanges,
  fixturePromptReview,
  fixturePrompts,
  fixtureReviewTaskId,
  fixtureReviewTasks,
  fixtureRiskReport,
} from './fixtures';

const sseBody = fixtureEvents
  .map((event) => [`id: ${event.eventId}`, `data: ${JSON.stringify(event)}`, '', ''].join('\n'))
  .join('');

export const handlers = [
  http.get('/api/v1/expense-cases', ({ request }) => {
    const url = new URL(request.url);
    return HttpResponse.json({
      items: [fixtureCase],
      page: Number(url.searchParams.get('page') ?? 0),
      size: Number(url.searchParams.get('size') ?? 20),
      total: 1,
    });
  }),
  http.post('/api/v1/expense-cases', async () =>
    HttpResponse.json({ ...fixtureCase, status: 'UPLOADED' }, { status: 201 }),
  ),
  http.get('/api/v1/expense-cases/:caseId', () => HttpResponse.json(fixtureCase)),
  http.put('/api/v1/expense-cases/:caseId', async ({ request }) => {
    const body = await request.json() as Record<string, unknown>;
    return HttpResponse.json({
      ...fixtureCase,
      ...body,
      status: 'DRAFT',
      version: fixtureCase.version + 1,
      updatedAt: new Date().toISOString(),
    });
  }),
  http.delete('/api/v1/expense-cases/:caseId', () =>
    new HttpResponse(null, { status: 204 }),
  ),
  http.get('/api/v1/expense-cases/:caseId/documents', () => HttpResponse.json(fixtureDocuments)),
  http.post('/api/v1/expense-cases/:caseId/documents', () =>
    HttpResponse.json({ id: fixtureDocuments[0].id, originalFilename: fixtureDocuments[0].originalFilename }, { status: 201 }),
  ),
  http.post('/api/v1/expense-cases/:caseId/analyze', () =>
    HttpResponse.json({ caseId: fixtureCaseId, status: 'EXTRACTED' }),
  ),
  http.post('/api/v1/expense-cases/:caseId/workflow', () =>
    HttpResponse.json({ caseId: fixtureCaseId, status: 'WAITING_HUMAN' }),
  ),
  http.post('/api/v1/expense-cases/:caseId/settlement', ({ params }) =>
    HttpResponse.json({
      caseId: params.caseId,
      reimbursementId: '55555555-5555-4555-8555-555555555555',
      paymentId: '66666666-6666-4666-8666-666666666666',
      amount: 1280.5,
      currency: 'CNY',
      status: 'SUBMITTED',
    }),
  ),
  http.get('/api/v1/expense-cases/:caseId/evidence', () => HttpResponse.json(fixtureEvidence)),
  http.get('/api/v1/expense-cases/:caseId/review-report', () =>
    HttpResponse.json({
      id: 'review-report-1',
      caseId: fixtureCaseId,
      summary: '本单主要风险为住宿超标和重复票据，需要财务管理员复核。',
      riskExplanation: ['住宿金额超过制度上限', '存在重复票据风险'],
      policyCitations: [{ policyCode: 'HOTEL-CN-V1', section: '华东地区住宿标准', chunkId: 'chunk-1' }],
      humanReviewHints: ['确认是否有特殊审批', '核对重复票据来源'],
      limitations: ['仅基于当前上传票据和系统证据生成'],
      modelName: 'qwen-plus',
      promptVersion: 'review-report-v1',
      createdAt: '2026-06-22T10:35:00+08:00',
    }),
  ),
  http.post('/api/v1/expense-cases/:caseId/review-report', () =>
    HttpResponse.json({
      id: 'review-report-2',
      caseId: fixtureCaseId,
      summary: '已生成新的审核摘要。',
      riskExplanation: ['住宿金额超过制度上限'],
      policyCitations: [],
      humanReviewHints: ['确认特殊审批'],
      limitations: [],
      modelName: 'qwen-plus',
      promptVersion: 'review-report-v1',
      createdAt: new Date().toISOString(),
    }, { status: 201 }),
  ),
  http.post('/api/v1/expense-cases/:caseId/evidence-chat', () =>
    HttpResponse.json({
      answer: '该单进入人工复核是因为住宿金额超过制度上限，并存在重复票据风险。',
      citations: [{ type: 'POLICY', id: 'HOTEL-CN-V1' }],
    }),
  ),
  http.get('/api/v1/expense-cases/:caseId/events', () =>
    new Response(sseBody, {
      headers: { 'Content-Type': 'text/event-stream' },
    }),
  ),
  http.get('/api/v1/review-tasks', () => HttpResponse.json(fixtureReviewTasks)),
  http.get('/api/v1/review-tasks/:taskId', () => HttpResponse.json(fixtureReviewTasks[0])),
  http.get('/api/v1/review-tasks/:taskId/more-info-suggestion', () =>
    HttpResponse.json({
      userFacingMessage: '请补充重复票据说明、商户异常证据、历史报销、原始票据。',
      requestedEvidence: fixtureReviewTasks[0].requiredEvidence,
      reviewerQuestions: ['该票据是否曾在其他报销单中提交过？'],
    }),
  ),
  http.post('/api/v1/review-tasks/:taskId/approve', ({ params }) =>
    HttpResponse.json({
      ...fixtureCase,
      status: params.taskId === fixtureReviewTaskId ? 'APPROVED' : fixtureCase.status,
      riskLevel: 'LOW',
      riskScore: 18,
    }),
  ),
  http.post('/api/v1/review-tasks/:taskId/reject', () =>
    HttpResponse.json({ ...fixtureCase, status: 'REJECTED' }),
  ),
  http.post('/api/v1/review-tasks/:taskId/request-more-info', () =>
    HttpResponse.json({ ...fixtureCase, status: 'WAITING_HUMAN' }),
  ),
  http.get('/api/v1/policies', () => HttpResponse.json(fixturePolicies)),
  http.get('/api/v1/policies/search', () => HttpResponse.json(fixturePolicyMatches)),
  http.get('/api/v1/evaluation/risk-report', () => HttpResponse.json(fixtureRiskReport)),
  http.get('/api/v1/observability/runs', () => HttpResponse.json(fixtureObservableRuns)),
  http.get('/api/v1/observability/model-summary', () => HttpResponse.json(fixtureModelSummary)),
  http.get('/api/v1/observability/model-calls', () => HttpResponse.json(fixtureModelCalls)),
  http.get('/api/v1/observability/cases/:caseId', () => HttpResponse.json(fixtureCaseObservability)),
  http.get('/api/v1/prompts', () => HttpResponse.json(fixturePrompts)),
  http.post('/api/v1/prompts', async () => HttpResponse.json(fixturePrompts[1], { status: 201 })),
  http.put('/api/v1/prompts/:id', async () => HttpResponse.json(fixturePrompts[1])),
  http.post('/api/v1/prompts/:id/submit', () => HttpResponse.json(fixturePromptChanges[0])),
  http.get('/api/v1/prompts/:id/changes', () => HttpResponse.json(fixturePromptChanges)),
  http.get('/api/v1/prompts/:id/review', () => HttpResponse.json(fixturePromptReview)),
  http.post('/api/v1/prompts/changes/:id/approve', () =>
    HttpResponse.json({ ...fixturePromptChanges[0], status: 'APPROVED', reviewedBy: 'reviewer01' }),
  ),
  http.post('/api/v1/prompts/changes/:id/reject', () =>
    HttpResponse.json({ ...fixturePromptChanges[0], status: 'REJECTED', reviewedBy: 'reviewer01' }),
  ),
  http.post('/api/v1/prompts/:id/activate', () =>
    HttpResponse.json({ ...fixturePrompts[1], status: 'ACTIVE', activatedAt: new Date().toISOString() }),
  ),
];

export async function startMockWorker() {
  const { setupWorker } = await import('msw/browser');
  const worker = setupWorker(...handlers);
  await worker.start({
    onUnhandledRequest: 'bypass',
  });
}

