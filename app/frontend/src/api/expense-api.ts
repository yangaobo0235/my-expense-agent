import { httpClient } from './http-client';
import {
  CaseEvidence,
  CreateExpenseCaseRequest,
  ExpenseCase,
  ExpenseCasePage,
  ExpenseCaseStatus,
  ExpenseWorkflowRequest,
  ExpenseDocumentDetail,
  EvidenceChatResponse,
  AgentSecurityEvaluationReport,
  CaseObservability,
  MoreInfoSuggestion,
  ModelCallRecord,
  ModelCallSummary,
  PolicyRagEvaluationReport,
  PromptChangeRequest,
  PromptVersionReview,
  PromptTemplate,
  PromptTemplateInput,
  PolicyCatalogEntry,
  PolicySearchMatch,
  ObservableWorkflowRun,
  ReviewReport,
  ReviewDecisionRequest,
  ReviewTask,
  RiskEvaluationReport,
  SettlementResult,
  UpdateExpenseCaseRequest,
} from './contracts';
import type {
  OperationQuery,
  OperationRequest,
  OperationResponse,
} from './openapi-types';

export type CaseFilters = OperationQuery<'search'>;
export type PolicySearchFilters = OperationQuery<'search_1'>;
export type WorkflowInput = ExpenseWorkflowRequest;
export type CreateCaseInput = CreateExpenseCaseRequest;
export type UpdateCaseInput = UpdateExpenseCaseRequest;
export type ReviewDecisionInput = Pick<
  ReviewDecisionRequest,
  'approvedAmount' | 'comment'
>;

export async function listCases(filters: CaseFilters): Promise<ExpenseCasePage> {
  const response = await httpClient.get<OperationResponse<'search'>>(
    '/api/v1/expense-cases',
    { params: filters },
  );
  return response.data as ExpenseCasePage;
}

export async function getCase(caseId: string): Promise<ExpenseCase> {
  const response = await httpClient.get<OperationResponse<'get_1'>>(
    `/api/v1/expense-cases/${caseId}`,
  );
  return response.data as ExpenseCase;
}

export async function createCase(input: CreateCaseInput): Promise<ExpenseCase> {
  const response = await httpClient.post<OperationResponse<'create'>>(
    '/api/v1/expense-cases',
    input satisfies OperationRequest<'create'>,
  );
  return response.data as ExpenseCase;
}

export async function updateCase(
  caseId: string,
  input: UpdateCaseInput,
): Promise<ExpenseCase> {
  const response = await httpClient.put<ExpenseCase>(
    `/api/v1/expense-cases/${caseId}`,
    input,
  );
  return response.data;
}

export async function deleteCase(caseId: string): Promise<void> {
  await httpClient.delete(`/api/v1/expense-cases/${caseId}`);
}

export async function uploadCaseDocument(
  caseId: string,
  file: File,
  onProgress?: (percent: number) => void,
): Promise<OperationResponse<'uploadDocument'>> {
  const body = new FormData();
  body.append('file', file);
  return (
    await httpClient.post<OperationResponse<'uploadDocument'>>(
      `/api/v1/expense-cases/${caseId}/documents`,
      body,
      {
        onUploadProgress: (event) => {
          if (event.total) onProgress?.(Math.round((event.loaded / event.total) * 100));
        },
      },
    )
  ).data;
}

export async function analyzeCase(
  caseId: string,
): Promise<OperationResponse<'analyze'>> {
  return (
    await httpClient.post<OperationResponse<'analyze'>>(
      `/api/v1/expense-cases/${caseId}/analyze`,
    )
  ).data;
}

export async function runCaseWorkflow(
  caseId: string,
  input: WorkflowInput,
): Promise<OperationResponse<'runWorkflow'>> {
  return (
    await httpClient.post<OperationResponse<'runWorkflow'>>(
      `/api/v1/expense-cases/${caseId}/workflow`,
      input satisfies OperationRequest<'runWorkflow'>,
    )
  ).data;
}

export async function getCaseEvidence(caseId: string): Promise<CaseEvidence> {
  return (
    await httpClient.get<OperationResponse<'evidence'>>(
      `/api/v1/expense-cases/${caseId}/evidence`,
    )
  ).data as CaseEvidence;
}

export async function listCaseDocuments(
  caseId: string,
): Promise<ExpenseDocumentDetail[]> {
  return (
    await httpClient.get<OperationResponse<'documents'>>(
      `/api/v1/expense-cases/${caseId}/documents`,
    )
  ).data as ExpenseDocumentDetail[];
}

export async function listReviewTasks(): Promise<ReviewTask[]> {
  return (
    await httpClient.get<OperationResponse<'openTasks'>>('/api/v1/review-tasks')
  ).data as ReviewTask[];
}

export async function getReviewTask(taskId: string): Promise<ReviewTask> {
  return (
    await httpClient.get<OperationResponse<'get'>>(
      `/api/v1/review-tasks/${taskId}`,
    )
  ).data as ReviewTask;
}

export async function getMoreInfoSuggestion(taskId: string): Promise<MoreInfoSuggestion> {
  return (
    await httpClient.get<MoreInfoSuggestion>(
      `/api/v1/review-tasks/${taskId}/more-info-suggestion`,
    )
  ).data;
}

export async function decideReview(
  task: ReviewTask,
  action: 'approve' | 'reject' | 'request-more-info',
  input: ReviewDecisionInput,
): Promise<ExpenseCase> {
  const request = {
    requestId: crypto.randomUUID(),
    version: task.version,
    approvedAmount: input.approvedAmount,
    comment: input.comment,
  } satisfies OperationRequest<'approve'>;
  return (
    await httpClient.post<OperationResponse<'approve'>>(
      `/api/v1/review-tasks/${task.id}/${action}`,
      request,
    )
  ).data as ExpenseCase;
}

export async function listPolicies(): Promise<PolicyCatalogEntry[]> {
  return (
    await httpClient.get<OperationResponse<'list'>>('/api/v1/policies')
  ).data as PolicyCatalogEntry[];
}

export async function searchPolicies(
  input: PolicySearchFilters,
): Promise<PolicySearchMatch[]> {
  return (
    await httpClient.get<OperationResponse<'search_1'>>('/api/v1/policies/search', {
      params: input,
    })
  ).data as PolicySearchMatch[];
}

export async function getRiskEvaluationReport(): Promise<RiskEvaluationReport> {
  return (
    await httpClient.get<OperationResponse<'riskReport'>>(
      '/api/v1/evaluation/risk-report',
    )
  ).data as RiskEvaluationReport;
}

export async function getPolicyRagEvaluationReport(): Promise<PolicyRagEvaluationReport> {
  return (
    await httpClient.get<PolicyRagEvaluationReport>(
      '/api/v1/evaluation/policy-rag-report',
    )
  ).data;
}

export async function getAgentSecurityEvaluationReport(): Promise<AgentSecurityEvaluationReport> {
  return (
    await httpClient.get<AgentSecurityEvaluationReport>(
      '/api/v1/evaluation/agent-security-report',
    )
  ).data;
}

export async function getReviewReport(caseId: string): Promise<ReviewReport> {
  return (
    await httpClient.get<ReviewReport>(
      `/api/v1/expense-cases/${caseId}/review-report`,
    )
  ).data;
}

export async function generateReviewReport(caseId: string): Promise<ReviewReport> {
  return (
    await httpClient.post<ReviewReport>(
      `/api/v1/expense-cases/${caseId}/review-report`,
    )
  ).data;
}

export async function askEvidenceChat(
  caseId: string,
  question: string,
): Promise<EvidenceChatResponse> {
  return (
    await httpClient.post<EvidenceChatResponse>(
      `/api/v1/expense-cases/${caseId}/evidence-chat`,
      { question },
    )
  ).data;
}

export async function settleExpenseCase(caseId: string): Promise<SettlementResult> {
  return (
    await httpClient.post<OperationResponse<'settle'>>(
      `/api/v1/expense-cases/${caseId}/settlement`,
      { requestId: crypto.randomUUID() } satisfies OperationRequest<'settle'>,
    )
  ).data as SettlementResult;
}

export async function listObservableRuns(
  limit = 20,
): Promise<ObservableWorkflowRun[]> {
  return (
    await httpClient.get<OperationResponse<'recentRuns'>>(
      '/api/v1/observability/runs',
      { params: { limit } satisfies OperationQuery<'recentRuns'> },
    )
  ).data as ObservableWorkflowRun[];
}

export async function getModelCallSummary(): Promise<ModelCallSummary> {
  return (
    await httpClient.get<ModelCallSummary>('/api/v1/observability/model-summary')
  ).data;
}

export async function listModelCalls(limit = 50): Promise<ModelCallRecord[]> {
  return (
    await httpClient.get<ModelCallRecord[]>(
      '/api/v1/observability/model-calls',
      { params: { limit } },
    )
  ).data;
}

export async function getCaseObservability(caseId: string): Promise<CaseObservability> {
  return (
    await httpClient.get<CaseObservability>(
      `/api/v1/observability/cases/${caseId}`,
    )
  ).data;
}

export function grafanaTraceUrl(traceId: string) {
  const base = import.meta.env.VITE_GRAFANA_URL;
  if (!base) return undefined;
  const datasource = import.meta.env.VITE_TEMPO_DATASOURCE_UID || 'tempo';
  const panes = {
    trace: {
      datasource,
      queries: [{ refId: 'A', queryType: 'traceql', query: traceId }],
      range: { from: 'now-24h', to: 'now' },
    },
  };
  return `${base}/explore?schemaVersion=1&panes=${encodeURIComponent(JSON.stringify(panes))}`;
}

export async function listPrompts(promptKey?: string): Promise<PromptTemplate[]> {
  return (
    await httpClient.get<PromptTemplate[]>('/api/v1/prompts', {
      params: promptKey ? { promptKey } : undefined,
    })
  ).data;
}

export async function createPrompt(input: PromptTemplateInput): Promise<PromptTemplate> {
  return (await httpClient.post<PromptTemplate>('/api/v1/prompts', input)).data;
}

export async function updatePrompt(id: string, input: PromptTemplateInput): Promise<PromptTemplate> {
  return (await httpClient.put<PromptTemplate>(`/api/v1/prompts/${id}`, input)).data;
}

export async function submitPrompt(id: string, diffSummary: string): Promise<PromptChangeRequest> {
  return (await httpClient.post<PromptChangeRequest>(`/api/v1/prompts/${id}/submit`, { diffSummary })).data;
}

export async function listPromptChanges(id: string): Promise<PromptChangeRequest[]> {
  return (await httpClient.get<PromptChangeRequest[]>(`/api/v1/prompts/${id}/changes`)).data;
}

export async function getPromptReview(id: string): Promise<PromptVersionReview> {
  return (await httpClient.get<PromptVersionReview>(`/api/v1/prompts/${id}/review`)).data;
}

export async function approvePromptChange(id: string, comment: string): Promise<PromptChangeRequest> {
  return (await httpClient.post<PromptChangeRequest>(`/api/v1/prompts/changes/${id}/approve`, { comment })).data;
}

export async function rejectPromptChange(id: string, comment: string): Promise<PromptChangeRequest> {
  return (await httpClient.post<PromptChangeRequest>(`/api/v1/prompts/changes/${id}/reject`, { comment })).data;
}

export async function activatePrompt(id: string): Promise<PromptTemplate> {
  return (await httpClient.post<PromptTemplate>(`/api/v1/prompts/${id}/activate`)).data;
}
