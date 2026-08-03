import { httpClient } from './http-client';
import {
  CaseEvidence,
  CreateExpenseCaseRequest,
  ExpenseCase,
  ExpenseCasePage,
  ExpenseCaseStatus,
  ExpenseWorkflowRequest,
  ExpenseDocumentDetail,
  DocumentVersion,
  CaseObservability,
  MoreInfoSuggestion,
  MoreInfoTask,
  PolicyCatalogEntry,
  PolicySearchMatch,
  ReviewDecisionRequest,
  ReviewTask,
  RiskEvaluationReport,
  ExtractionEvaluationReport,
  SettlementResult,
  UpdateExpenseCaseRequest,
  WorkflowRunDetail,
} from './contracts';
import type {
  OperationQuery,
  OperationRequest,
  OperationResponse,
} from './openapi-types';

const FUND_APPLICATIONS_API = '/api/v1/fund-applications';

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
    FUND_APPLICATIONS_API,
    { params: filters },
  );
  return response.data as ExpenseCasePage;
}

export async function getCase(caseId: string): Promise<ExpenseCase> {
  const response = await httpClient.get<OperationResponse<'get'>>(
    `${FUND_APPLICATIONS_API}/${caseId}`,
  );
  return response.data as ExpenseCase;
}

export async function createCase(input: CreateCaseInput): Promise<ExpenseCase> {
  const response = await httpClient.post<OperationResponse<'create'>>(
    FUND_APPLICATIONS_API,
    input satisfies OperationRequest<'create'>,
  );
  return response.data as ExpenseCase;
}

export async function updateCase(
  caseId: string,
  input: UpdateCaseInput,
): Promise<ExpenseCase> {
  const response = await httpClient.put<ExpenseCase>(
    `${FUND_APPLICATIONS_API}/${caseId}`,
    input,
  );
  return response.data;
}

export async function deleteCase(caseId: string): Promise<void> {
  await httpClient.delete(`${FUND_APPLICATIONS_API}/${caseId}`);
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
      `${FUND_APPLICATIONS_API}/${caseId}/documents`,
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
      `${FUND_APPLICATIONS_API}/${caseId}/analyze`,
    )
  ).data;
}

export async function runCaseWorkflow(
  caseId: string,
  input: WorkflowInput,
): Promise<OperationResponse<'runWorkflow'>> {
  return (
    await httpClient.post<OperationResponse<'runWorkflow'>>(
      `${FUND_APPLICATIONS_API}/${caseId}/workflow`,
      input satisfies OperationRequest<'runWorkflow'>,
    )
  ).data;
}

export async function getCaseEvidence(caseId: string): Promise<CaseEvidence> {
  return (
    await httpClient.get<OperationResponse<'evidence'>>(
      `${FUND_APPLICATIONS_API}/${caseId}/evidence`,
    )
  ).data as CaseEvidence;
}

export async function listCaseDocuments(
  caseId: string,
): Promise<ExpenseDocumentDetail[]> {
  return (
    await httpClient.get<OperationResponse<'documents'>>(
      `${FUND_APPLICATIONS_API}/${caseId}/documents`,
    )
  ).data as ExpenseDocumentDetail[];
}

export async function listCaseReviewRuns(caseId: string): Promise<WorkflowRunDetail[]> {
  return (await httpClient.get<WorkflowRunDetail[]>(`/api/v1/expense-cases/${caseId}/review-runs`)).data;
}

export async function listCaseDocumentVersions(caseId: string): Promise<DocumentVersion[]> {
  return (await httpClient.get<DocumentVersion[]>(`/api/v1/expense-cases/${caseId}/document-versions`)).data;
}

export async function getCurrentMoreInfoRequest(caseId: string): Promise<MoreInfoTask> {
  return (await httpClient.get<MoreInfoTask>(`/api/v1/expense-cases/${caseId}/more-info-request`)).data;
}

export interface MoreInfoSubmissionInput {
  taskId: string;
  file: File;
  category: string;
  expenseDate: string;
  reopenReason: string;
}

export async function submitMoreInfo(
  caseId: string,
  input: MoreInfoSubmissionInput,
): Promise<{ taskId: string; documentId: string; documentVersion: number }> {
  const body = new FormData();
  body.append('file', input.file);
  body.append('taskId', input.taskId);
  body.append('requestId', crypto.randomUUID());
  body.append('category', input.category);
  body.append('expenseDate', input.expenseDate);
  body.append('reopenReason', input.reopenReason);
  return (
    await httpClient.post<{ taskId: string; documentId: string; documentVersion: number }>(
      `/api/v1/expense-cases/${caseId}/more-info-submissions`,
      body,
    )
  ).data;
}

export async function listReviewTasks(): Promise<ReviewTask[]> {
  return (
    await httpClient.get<OperationResponse<'openTasks'>>('/api/v1/review-tasks')
  ).data as ReviewTask[];
}

export async function getReviewTask(taskId: string): Promise<ReviewTask> {
  return (
    await httpClient.get<OperationResponse<'get_1'>>(
      `/api/v1/review-tasks/${taskId}`,
    )
  ).data as ReviewTask;
}

export async function getMoreInfoSuggestion(taskId: string): Promise<MoreInfoSuggestion> {
  return (
    await httpClient.post<MoreInfoSuggestion>(
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
      '/api/v1/evaluations/risk/latest',
    )
  ).data as RiskEvaluationReport;
}

export async function getExtractionEvaluationReport(): Promise<ExtractionEvaluationReport> {
  return (
    await httpClient.get<ExtractionEvaluationReport>(
      '/api/v1/evaluations/extraction/latest',
    )
  ).data;
}

export async function settleExpenseCase(caseId: string): Promise<SettlementResult> {
  return (
    await httpClient.post<OperationResponse<'settle'>>(
      `${FUND_APPLICATIONS_API}/${caseId}/posting`,
      { requestId: crypto.randomUUID() } satisfies OperationRequest<'settle'>,
    )
  ).data as SettlementResult;
}

export async function getCaseObservability(caseId: string): Promise<CaseObservability> {
  return (
    await httpClient.get<CaseObservability>(
      `/api/v1/expense-cases/${caseId}/trace`,
    )
  ).data;
}
