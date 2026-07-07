import type { components } from './generated/schema';

type Schema<Name extends keyof components['schemas']> = components['schemas'][Name];
type RequiredField<T, Key extends keyof T> = NonNullable<T[Key]>;

type ExpenseCaseResponse = Schema<'ExpenseCaseResponse'>;
type ExpenseCasePageResponse = Schema<'ExpenseCasePageResponse'>;
type ReviewTaskResponse = Schema<'ReviewTaskResponse'>;
type ExpenseDocumentDetailResponse = Schema<'ExpenseDocumentDetailResponse'>;
type ExtractedExpenseDocumentResponse = Schema<'ExtractedExpenseDocument'>;
type ExtractedExpenseItemResponse = Schema<'ExtractedExpenseItem'>;
type CaseEvidenceResponse = Schema<'CaseEvidence'>;
type RiskSignalResponse = Schema<'RiskSignal'>;
type PolicyCatalogResponse = Schema<'PolicyCatalogResponse'>;
type PolicySearchResponse = Schema<'PolicySearchResponse'>;
type RiskEvaluationReportResponse = Schema<'RiskEvaluationReport'>;
type ObservableRunResponse = Schema<'ObservableRunResponse'>;

export type ExpenseCaseStatus = RequiredField<ExpenseCaseResponse, 'status'>;
export type SettlementStatus = 'NOT_SUBMITTED' | 'FAILED' | 'SUBMITTED';
export type RiskLevel = RequiredField<Schema<'RiskAssessment'>, 'level'>;
export type PolicyStatus = RequiredField<PolicyCatalogResponse, 'status'>;
export type ApiError = Schema<'ApiErrorResponse'>;
export type CreateExpenseCaseRequest = Schema<'CreateExpenseCaseRequest'>;
export type UpdateExpenseCaseRequest = CreateExpenseCaseRequest;
export type ExpenseWorkflowRequest = Schema<'ExpenseWorkflowRequest'>;
export type ReviewDecisionRequest = Schema<'ReviewDecisionRequest'>;
export type SettlementResult = Schema<'SettlementResult'>;

export interface ExpenseCase {
  id: RequiredField<ExpenseCaseResponse, 'id'>;
  caseNumber: RequiredField<ExpenseCaseResponse, 'caseNumber'>;
  applicantName: RequiredField<ExpenseCaseResponse, 'applicantName'>;
  departmentCode: RequiredField<ExpenseCaseResponse, 'departmentCode'>;
  title: RequiredField<ExpenseCaseResponse, 'title'>;
  claimedAmount: RequiredField<ExpenseCaseResponse, 'claimedAmount'>;
  currency: RequiredField<ExpenseCaseResponse, 'currency'>;
  status: ExpenseCaseStatus;
  riskLevel?: RiskLevel;
  riskScore?: RequiredField<ExpenseCaseResponse, 'riskScore'>;
  failureStage?: ExpenseCaseResponse['failureStage'];
  failureReason?: ExpenseCaseResponse['failureReason'];
  settlementStatus?: SettlementStatus;
  version: RequiredField<ExpenseCaseResponse, 'version'>;
  createdAt: RequiredField<ExpenseCaseResponse, 'createdAt'>;
  updatedAt: RequiredField<ExpenseCaseResponse, 'updatedAt'>;
}

export interface ExpenseCasePage {
  items: ExpenseCase[];
  page: RequiredField<ExpenseCasePageResponse, 'page'>;
  size: RequiredField<ExpenseCasePageResponse, 'size'>;
  total: RequiredField<ExpenseCasePageResponse, 'total'>;
}

export interface ReviewTask {
  id: RequiredField<ReviewTaskResponse, 'id'>;
  caseId: RequiredField<ReviewTaskResponse, 'caseId'>;
  status: RequiredField<ReviewTaskResponse, 'status'>;
  assigneeSubject?: ReviewTaskResponse['assigneeSubject'];
  reasonCodes: RequiredField<ReviewTaskResponse, 'reasonCodes'>;
  routingAction?: ReviewTaskResponse['routingAction'];
  routingQueue?: ReviewTaskResponse['routingQueue'];
  assigneeRole?: ReviewTaskResponse['assigneeRole'];
  slaHours?: ReviewTaskResponse['slaHours'];
  requiredEvidence: RequiredField<ReviewTaskResponse, 'requiredEvidence'>;
  userFacingMessage?: ReviewTaskResponse['userFacingMessage'];
  fallbackStrategy?: ReviewTaskResponse['fallbackStrategy'];
  debateAssistEnabled?: ReviewTaskResponse['debateAssistEnabled'];
  reviewerComment?: ReviewTaskResponse['reviewerComment'];
  dueAt?: ReviewTaskResponse['dueAt'];
  version: RequiredField<ReviewTaskResponse, 'version'>;
  createdAt: RequiredField<ReviewTaskResponse, 'createdAt'>;
  updatedAt: RequiredField<ReviewTaskResponse, 'updatedAt'>;
}

export interface ExpenseWorkflowEvent {
  eventId: string;
  caseId: string;
  type: string;
  sequence: number;
  occurredAt: string;
  payload: Record<string, unknown>;
}

export interface ExtractedExpenseItem {
  description: RequiredField<ExtractedExpenseItemResponse, 'description'>;
  quantity: RequiredField<ExtractedExpenseItemResponse, 'quantity'>;
  unitPrice: RequiredField<ExtractedExpenseItemResponse, 'unitPrice'>;
  amount: RequiredField<ExtractedExpenseItemResponse, 'amount'>;
}

export interface ExtractedExpenseDocument {
  documentType: RequiredField<ExtractedExpenseDocumentResponse, 'documentType'>;
  invoiceCode?: ExtractedExpenseDocumentResponse['invoiceCode'];
  invoiceNumber?: ExtractedExpenseDocumentResponse['invoiceNumber'];
  sellerName?: ExtractedExpenseDocumentResponse['sellerName'];
  buyerName?: ExtractedExpenseDocumentResponse['buyerName'];
  issueDate?: ExtractedExpenseDocumentResponse['issueDate'];
  totalAmount?: ExtractedExpenseDocumentResponse['totalAmount'];
  currency?: ExtractedExpenseDocumentResponse['currency'];
  items: ExtractedExpenseItem[];
  confidence: RequiredField<ExtractedExpenseDocumentResponse, 'confidence'>;
  warnings: RequiredField<ExtractedExpenseDocumentResponse, 'warnings'>;
}

export interface ExpenseDocumentDetail {
  id: RequiredField<ExpenseDocumentDetailResponse, 'id'>;
  originalFilename: RequiredField<ExpenseDocumentDetailResponse, 'originalFilename'>;
  contentType: RequiredField<ExpenseDocumentDetailResponse, 'contentType'>;
  fileSize: RequiredField<ExpenseDocumentDetailResponse, 'fileSize'>;
  sha256: RequiredField<ExpenseDocumentDetailResponse, 'sha256'>;
  previewUrl: RequiredField<ExpenseDocumentDetailResponse, 'previewUrl'>;
  previewExpiresAt: RequiredField<ExpenseDocumentDetailResponse, 'previewExpiresAt'>;
  extraction?: {
    result: ExtractedExpenseDocument;
    validationErrors: string[];
    modelName: string;
    promptVersion: string;
    tokenUsage?: number;
    extractionLatencyMs?: number;
    extractorMode?: string;
  };
  createdAt: RequiredField<ExpenseDocumentDetailResponse, 'createdAt'>;
}

export interface RiskSignal {
  code: RequiredField<RiskSignalResponse, 'code'>;
  score: RequiredField<RiskSignalResponse, 'score'>;
  message: RequiredField<RiskSignalResponse, 'message'>;
  evidence: RequiredField<RiskSignalResponse, 'evidence'>;
}

export interface CaseEvidence {
  run?: {
    id: RequiredField<RequiredField<CaseEvidenceResponse, 'run'>, 'id'>;
    requestId: RequiredField<RequiredField<CaseEvidenceResponse, 'run'>, 'requestId'>;
    status: RequiredField<RequiredField<CaseEvidenceResponse, 'run'>, 'status'>;
    startedAt: RequiredField<RequiredField<CaseEvidenceResponse, 'run'>, 'startedAt'>;
    completedAt?: RequiredField<CaseEvidenceResponse, 'run'>['completedAt'];
    errorCode?: RequiredField<CaseEvidenceResponse, 'run'>['errorCode'];
    errorMessage?: RequiredField<CaseEvidenceResponse, 'run'>['errorMessage'];
    traceId?: RequiredField<CaseEvidenceResponse, 'run'>['traceId'];
  };
  steps: Array<{
    id: string;
    name: string;
    attempt: number;
    status: string;
    durationMs?: number;
    errorCode?: string;
    errorMessage?: string;
    evidence: Record<string, unknown>;
  }>;
  policyFindings: Array<{
    policyId: string;
    policyCode: string;
    policyName: string;
    version: string;
    section: string;
    chunkId: string;
    content: string;
    score: number;
  }>;
  risk?: {
    score: number;
    level: RiskLevel;
    requiresHumanReview: boolean;
    signals: RiskSignal[];
  };
  toolCalls: Array<{
    id: string;
    toolName: string;
    writeOperation: boolean;
    status: string;
    output: Record<string, unknown>;
    durationMs: number;
    errorCode?: string;
    approvalReference?: string;
    createdAt: string;
    completedAt?: string;
  }>;
}

export interface PolicyCatalogEntry {
  id: RequiredField<PolicyCatalogResponse, 'id'>;
  policyCode: RequiredField<PolicyCatalogResponse, 'policyCode'>;
  name: RequiredField<PolicyCatalogResponse, 'name'>;
  category: RequiredField<PolicyCatalogResponse, 'category'>;
  region: RequiredField<PolicyCatalogResponse, 'region'>;
  employeeGrade: RequiredField<PolicyCatalogResponse, 'employeeGrade'>;
  version: RequiredField<PolicyCatalogResponse, 'version'>;
  effectiveFrom: RequiredField<PolicyCatalogResponse, 'effectiveFrom'>;
  effectiveTo?: PolicyCatalogResponse['effectiveTo'];
  status: PolicyStatus;
  sourceUri?: PolicyCatalogResponse['sourceUri'];
  chunkCount: RequiredField<PolicyCatalogResponse, 'chunkCount'>;
  indexedChunkCount: RequiredField<PolicyCatalogResponse, 'indexedChunkCount'>;
  updatedAt: RequiredField<PolicyCatalogResponse, 'updatedAt'>;
}

export interface PolicySearchMatch {
  policyId: RequiredField<PolicySearchResponse, 'policyId'>;
  policyCode: RequiredField<PolicySearchResponse, 'policyCode'>;
  policyName: RequiredField<PolicySearchResponse, 'policyName'>;
  policyVersion: RequiredField<PolicySearchResponse, 'policyVersion'>;
  category: RequiredField<PolicySearchResponse, 'category'>;
  region: RequiredField<PolicySearchResponse, 'region'>;
  employeeGrade: RequiredField<PolicySearchResponse, 'employeeGrade'>;
  effectiveFrom: RequiredField<PolicySearchResponse, 'effectiveFrom'>;
  effectiveTo?: PolicySearchResponse['effectiveTo'];
  sourceUri?: PolicySearchResponse['sourceUri'];
  chunkId: RequiredField<PolicySearchResponse, 'chunkId'>;
  chunkIndex: RequiredField<PolicySearchResponse, 'chunkIndex'>;
  section: RequiredField<PolicySearchResponse, 'section'>;
  content: RequiredField<PolicySearchResponse, 'content'>;
  score: RequiredField<PolicySearchResponse, 'score'>;
  citation: RequiredField<PolicySearchResponse, 'citation'>;
}

export interface RiskEvaluationReport {
  datasetVersion: RequiredField<RiskEvaluationReportResponse, 'datasetVersion'>;
  datasetSha256: RequiredField<RiskEvaluationReportResponse, 'datasetSha256'>;
  engineVersion: RequiredField<RiskEvaluationReportResponse, 'engineVersion'>;
  generatedAt: RequiredField<RiskEvaluationReportResponse, 'generatedAt'>;
  caseCount: RequiredField<RiskEvaluationReportResponse, 'caseCount'>;
  categoryCounts: RequiredField<RiskEvaluationReportResponse, 'categoryCounts'>;
  metrics: {
    precision: number;
    recall: number;
    f1: number;
    riskLevelAccuracy: number;
    humanReviewAccuracy: number;
    highRiskMissRate: number;
    humanReviewTriggerRate: number;
  };
  agentGovernance?: {
    planVersion?: string;
    totalAgents?: number;
    writeAgentCount?: number;
    idempotentWriteAgentCount?: number;
    writeToolIsolationPassed?: boolean;
    settlementWriteRetryProtected?: boolean;
    humanHandoffCoverage?: number;
    retryableAgentRate?: number;
  };
  failures: Array<{
    caseId: string;
    expectedSignals: string[];
    actualSignals: string[];
    expectedRiskLevel: string;
    actualRiskLevel: string;
    expectedHumanReview: boolean;
    actualHumanReview: boolean;
  }>;
}

export interface ObservableWorkflowRun {
  runId: RequiredField<ObservableRunResponse, 'runId'>;
  caseId: RequiredField<ObservableRunResponse, 'caseId'>;
  requestId: RequiredField<ObservableRunResponse, 'requestId'>;
  status: RequiredField<ObservableRunResponse, 'status'>;
  startedAt: RequiredField<ObservableRunResponse, 'startedAt'>;
  completedAt?: ObservableRunResponse['completedAt'];
  errorCode?: ObservableRunResponse['errorCode'];
  traceId?: ObservableRunResponse['traceId'];
  stepCount?: ObservableRunResponse['stepCount'];
  succeededStepCount?: ObservableRunResponse['succeededStepCount'];
  failedStepCount?: ObservableRunResponse['failedStepCount'];
  durationMs?: ObservableRunResponse['durationMs'];
  agentPlanRecorded?: ObservableRunResponse['agentPlanRecorded'];
}

export interface ReviewReport {
  id: string;
  caseId: string;
  summary: string;
  riskExplanation: string[];
  policyCitations: Array<{
    policyCode: string;
    section: string;
    chunkId: string;
  }>;
  humanReviewHints: string[];
  limitations: string[];
  modelName: string;
  promptVersion: string;
  createdAt: string;
}

export interface EvidenceChatResponse {
  answer: string;
  citations: Array<{ type: string; id: string }>;
}

export interface PolicyRagEvaluationReport {
  datasetVersion: string;
  generatedAt: string;
  caseCount: number;
  metrics: {
    recallAt5: number;
    precisionAt5: number;
    expectedPolicyHitRate: number;
    expectedSectionHitRate: number;
    noAnswerAccuracy: number;
    injectionDefensePassed: boolean;
    averageSearchLatencyMs: number;
  };
  failures: Array<{
    caseId: string;
    query: string;
    expectedPolicyCode: string;
    expectedSections: string[];
    actualMatches: Array<Record<string, unknown>>;
    reason: string;
  }>;
}

export interface AgentSecurityEvaluationReport {
  datasetVersion: string;
  generatedAt: string;
  caseCount: number;
  metrics: {
    blockedWriteToolCount: number;
    unsafeWriteToolCallCount: number;
    injectionDetectedCount: number;
    humanHandoffCount: number;
    securityPassRate: number;
  };
  failures: Array<{
    caseId: string;
    reason: string;
    maliciousText: string;
  }>;
}

export interface ModelCallRecord {
  id: string;
  caseId?: string;
  runId?: string;
  stepName: string;
  modelName: string;
  promptVersion: string;
  promptHash: string;
  inputHash: string;
  outputHash?: string;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  latencyMs: number;
  retryCount: number;
  status: 'SUCCEEDED' | 'FAILED';
  errorCode?: string;
  createdAt: string;
}

export interface ModelCallSummary {
  totalCalls: number;
  successRate: number;
  averageLatencyMs: number;
  p95LatencyMs: number;
  totalTokens: number;
  callsByModel: Record<string, number>;
  failuresByStep: Record<string, number>;
}

export interface CaseAuditEvent {
  id: string;
  caseId?: string;
  actorSubject: string;
  actorType: string;
  action: string;
  resourceType: string;
  resourceId?: string;
  requestId?: string;
  metadata: Record<string, unknown>;
  occurredAt: string;
}

export interface CaseObservability {
  latestRun?: ObservableWorkflowRun;
  steps: CaseEvidence['steps'];
  modelCalls: ModelCallRecord[];
  auditEvents: CaseAuditEvent[];
  modelCallCount: number;
  totalTokens: number;
  failedStepCount: number;
}

export interface PromptTemplate {
  id: string;
  promptKey: string;
  version: string;
  name: string;
  description: string;
  content: string;
  variableSchema: Record<string, unknown>;
  modelName: string;
  temperature: number;
  maxTokens: number;
  status: 'DRAFT' | 'IN_REVIEW' | 'APPROVED' | 'ACTIVE' | 'REJECTED' | 'DEPRECATED';
  promptHash: string;
  createdBy: string;
  updatedBy: string;
  approvedBy?: string;
  createdAt: string;
  updatedAt: string;
  approvedAt?: string;
  activatedAt?: string;
  replacedVersion?: string;
}

export interface PromptChangeRequest {
  id: string;
  promptTemplateId: string;
  requestType: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';
  diffSummary: string;
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH';
  evaluationReport: Record<string, unknown>;
  reviewComment: string;
  submittedBy: string;
  reviewedBy?: string;
  submittedAt: string;
  reviewedAt?: string;
}

export interface PromptAuditEvent {
  id: string;
  promptKey: string;
  version: string;
  action: string;
  actorSubject: string;
  payload: Record<string, unknown>;
  occurredAt: string;
}

export interface PromptVersionReview {
  candidate: PromptTemplate;
  active?: PromptTemplate;
  diff: {
    activeLineCount: number;
    candidateLineCount: number;
    lineDelta: number;
    changedFields: string[];
    contentChanged: boolean;
    rollbackCandidate: boolean;
    currentlyActive: boolean;
  };
  changes: PromptChangeRequest[];
  auditEvents: PromptAuditEvent[];
}

export interface MoreInfoSuggestion {
  userFacingMessage: string;
  requestedEvidence: string[];
  reviewerQuestions: string[];
}

export interface PromptTemplateInput {
  promptKey: string;
  version: string;
  name: string;
  description?: string;
  content: string;
  variableSchema?: Record<string, unknown>;
  modelName: string;
  temperature?: number;
  maxTokens: number;
}
