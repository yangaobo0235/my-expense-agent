import { http, HttpResponse } from 'msw';
import {
  fixtureCase,
  fixtureCaseObservability,
  fixtureCaseId,
  fixtureDocuments,
  fixtureEvidence,
  fixtureEvents,
  fixturePolicies,
  fixturePolicyMatches,
  fixtureReviewTaskId,
  fixtureReviewTasks,
  fixtureRiskReport,
} from './fixtures';

const sseBody = fixtureEvents
  .map((event) => [`id: ${event.eventId}`, `data: ${JSON.stringify(event)}`, '', ''].join('\n'))
  .join('');

export const handlers = [
  http.get('/api/v1/fund-applications', ({ request }) => {
    const url = new URL(request.url);
    return HttpResponse.json({
      items: [fixtureCase],
      page: Number(url.searchParams.get('page') ?? 0),
      size: Number(url.searchParams.get('size') ?? 20),
      total: 1,
    });
  }),
  http.post('/api/v1/fund-applications', async () =>
    HttpResponse.json({ ...fixtureCase, status: 'UPLOADED' }, { status: 201 }),
  ),
  http.get('/api/v1/fund-applications/:caseId', () => HttpResponse.json(fixtureCase)),
  http.put('/api/v1/fund-applications/:caseId', async ({ request }) => {
    const body = await request.json() as Record<string, unknown>;
    return HttpResponse.json({
      ...fixtureCase,
      ...body,
      status: 'DRAFT',
      version: fixtureCase.version + 1,
      updatedAt: new Date().toISOString(),
    });
  }),
  http.delete('/api/v1/fund-applications/:caseId', () =>
    new HttpResponse(null, { status: 204 }),
  ),
  http.get('/api/v1/fund-applications/:caseId/documents', () => HttpResponse.json(fixtureDocuments)),
  http.post('/api/v1/fund-applications/:caseId/documents', () =>
    HttpResponse.json({ id: fixtureDocuments[0].id, originalFilename: fixtureDocuments[0].originalFilename }, { status: 201 }),
  ),
  http.post('/api/v1/fund-applications/:caseId/analyze', () =>
    HttpResponse.json({ caseId: fixtureCaseId, status: 'EXTRACTED' }),
  ),
  http.post('/api/v1/fund-applications/:caseId/workflow', () =>
    HttpResponse.json({ caseId: fixtureCaseId, status: 'WAITING_HUMAN' }),
  ),
  http.post('/api/v1/fund-applications/:caseId/posting', ({ params }) =>
    HttpResponse.json({
      caseId: params.caseId,
      reimbursementId: '55555555-5555-4555-8555-555555555555',
      postingId: '66666666-6666-4666-8666-666666666666',
      amount: 1280.5,
      currency: 'CNY',
      status: 'SUBMITTED',
    }),
  ),
  http.get('/api/v1/fund-applications/:caseId/evidence', () => HttpResponse.json(fixtureEvidence)),
  http.get('/api/v1/fund-applications/:caseId/events', () =>
    new Response(sseBody, {
      headers: { 'Content-Type': 'text/event-stream' },
    }),
  ),
  http.get('/api/v1/review-tasks', () => HttpResponse.json(fixtureReviewTasks)),
  http.get('/api/v1/review-tasks/:taskId', () => HttpResponse.json(fixtureReviewTasks[0])),
  http.post('/api/v1/review-tasks/:taskId/more-info-suggestion', () =>
    HttpResponse.json({
      userFacingMessage: '请补充重复票据说明、票据抬头核验证据、历史报销记录和原始票据。',
      requestedEvidence: fixtureReviewTasks[0].requiredEvidence,
      reviewerQuestions: ['该票据是否曾在其他经费申请中提交过？'],
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
  http.get('/api/v1/evaluations/risk/latest', () => HttpResponse.json(fixtureRiskReport)),
  http.get('/api/v1/evaluations/extraction/latest', () => HttpResponse.json({
    datasetVersion: 'extraction-golden-v1',
    generatedAt: new Date().toISOString(),
    caseCount: 30,
    categoryCounts: {},
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
      humanHandoffRate: 0.1,
      p50LatencyMs: 80,
      p95LatencyMs: 120,
      averageTokenUsage: 320,
    },
    gatePassed: true,
    failures: [],
  })),
  http.get('/api/v1/expense-cases/:caseId/trace', () => HttpResponse.json(fixtureCaseObservability)),
];

export async function startMockWorker() {
  const { setupWorker } = await import('msw/browser');
  const worker = setupWorker(...handlers);
  await worker.start({
    onUnhandledRequest: 'bypass',
  });
}

