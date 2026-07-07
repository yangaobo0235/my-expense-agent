import { CaseEvidence, ExpenseCase, ExpenseDocumentDetail } from '../../api/contracts';

export type CaseStageKey =
  | 'summary'
  | 'documents'
  | 'extraction'
  | 'evidence'
  | 'policy'
  | 'risk'
  | 'review'
  | 'settlement';

export type StageState = 'pending' | 'active' | 'succeeded' | 'warning' | 'failed';

export interface CaseStage {
  key: CaseStageKey;
  title: string;
  state: StageState;
  detail: string;
  failedStep?: string;
}

export interface CaseDiagnosis {
  severity: 'info' | 'warning' | 'error' | 'success';
  title: string;
  description: string;
  actionLabel?: string;
  requestId?: string;
  retryKind?: 'extraction' | 'workflow';
  stage?: string;
}

const stepLabels: Record<string, string> = {
  AGENT_PLAN: '处理计划',
  MCP_EMPLOYEE_CONTEXT: '员工信息核对',
  MCP_DUPLICATE_CHECK: '历史重复检测',
  MCP_REVIEW_EVIDENCE: '审核证据保存',
  PARALLEL_EVIDENCE_COLLECTION: '证据收集',
  POLICY_RETRIEVAL: '费用制度核对',
  RISK_ASSESSMENT: '风险评估',
  RISK_ROUTING: '分配审核方式',
  FINALIZE: '形成审核意见',
  DOCUMENT_EXTRACTION: '票据识别',
  COORDINATOR: '整体处理',
};

export function workflowStepLabel(stepName: string) {
  return stepLabels[stepName] ?? stepName;
}

export function buildCaseStages(
  expenseCase: ExpenseCase,
  documents: ExpenseDocumentDetail[] = [],
  evidence?: CaseEvidence,
): CaseStage[] {
  const failedStep = latestFailedStep(evidence);
  const documentsUploaded = documents.length > 0 || !['DRAFT'].includes(expenseCase.status);
  const extracted = documents.some((document) => document.extraction) || after(expenseCase.status, 'EXTRACTED');
  const hasPolicy = Boolean(evidence?.policyFindings.length);
  const hasRisk = Boolean(evidence?.risk) || Boolean(expenseCase.riskLevel);
  const hasRun = Boolean(evidence?.run);
  const settlementDone = Boolean(
    evidence?.toolCalls.some(
      (call) =>
        call.writeOperation &&
        call.status === 'SUCCEEDED' &&
        ['submit_reimbursement', 'submit_payment'].includes(call.toolName),
    ),
  );

  return [
    {
      key: 'summary',
      title: '基本信息',
      state: 'succeeded',
      detail: `${expenseCase.applicantName} / ${expenseCase.departmentCode}`,
    },
    {
      key: 'documents',
      title: '票据上传',
      state: documentsUploaded ? 'succeeded' : activeIf(expenseCase.status === 'DRAFT'),
      detail: documentsUploaded ? `${documents.length} 张票据` : '等待上传票据',
    },
    {
      key: 'extraction',
      title: '票据识别',
      state: stageState({
        failed: expenseCase.status === 'FAILED' && expenseCase.failureStage === 'DOCUMENT_EXTRACTION',
        warning: documents.some((document) => document.extraction?.validationErrors.length),
        succeeded: extracted,
        active: ['UPLOADED', 'EXTRACTING'].includes(expenseCase.status),
      }),
      detail: extractionDetail(expenseCase, documents),
    },
    {
      key: 'evidence',
      title: '证据收集',
      state: stageState({
        failed: failedStep ? isEvidenceStep(failedStep.name) && evidence?.run?.status === 'FAILED' : false,
        warning: failedStep ? isEvidenceStep(failedStep.name) : false,
        succeeded: hasRun,
        active: ['POLICY_CHECKING', 'RISK_CHECKING'].includes(expenseCase.status),
      }),
      detail: evidenceDetail(evidence),
      failedStep: failedStep && isEvidenceStep(failedStep.name) ? failedStep.name : undefined,
    },
    {
      key: 'policy',
      title: '制度核对',
      state: stageState({
        failed: failedStep?.name === 'POLICY_RETRIEVAL' && evidence?.run?.status === 'FAILED',
        warning: false,
        succeeded: hasPolicy || after(expenseCase.status, 'POLICY_CHECKING'),
        active: expenseCase.status === 'POLICY_CHECKING',
      }),
      detail: hasPolicy ? `${evidence?.policyFindings.length ?? 0} 条制度依据` : '暂无制度依据',
    },
    {
      key: 'risk',
      title: '风险评估',
      state: stageState({
        failed: failedStep?.name === 'RISK_ASSESSMENT' && evidence?.run?.status === 'FAILED',
        warning: Boolean(evidence?.risk?.requiresHumanReview),
        succeeded: hasRisk,
        active: expenseCase.status === 'RISK_CHECKING',
      }),
      detail: hasRisk ? `${expenseCase.riskLevel ?? evidence?.risk?.level} / ${expenseCase.riskScore ?? evidence?.risk?.score ?? '-'}` : '未评分',
    },
    {
      key: 'review',
      title: '人工审核',
      state: stageState({
        failed: false,
        warning: expenseCase.status === 'WAITING_HUMAN',
        succeeded: ['APPROVED', 'REJECTED'].includes(expenseCase.status),
        active: expenseCase.status === 'WAITING_HUMAN',
      }),
      detail: reviewDetail(expenseCase),
    },
    {
      key: 'settlement',
      title: '结算',
      state: stageState({
        failed: false,
        warning: expenseCase.status === 'APPROVED' && !settlementDone,
        succeeded: settlementDone,
        active: expenseCase.status === 'APPROVED' && !settlementDone,
      }),
      detail: settlementDone ? '结算已提交' : expenseCase.status === 'APPROVED' ? '待发起结算' : '审批后可用',
    },
  ];
}

export function buildCaseDiagnosis(
  expenseCase: ExpenseCase,
  evidence?: CaseEvidence,
): CaseDiagnosis {
  const failedStep = latestFailedStep(evidence);
  if (expenseCase.status === 'FAILED' || failedStep) {
    const stage = failedStep?.name ?? expenseCase.failureStage ?? 'UNKNOWN';
    const workflowRetry = Boolean(evidence?.run?.requestId) && isRecoverableWorkflowStep(stage);
    if (stage === 'DOCUMENT_EXTRACTION' || (!workflowRetry && expenseCase.status === 'FAILED')) {
      return {
        severity: 'error',
        title: `停在${workflowStepLabel(stage)}`,
        description: failureDescription(
          stage,
          expenseCase.failureReason || failedStep?.errorMessage,
          '票据识别失败，已保留案例和票据，可重新识别。',
        ),
        actionLabel: '重新识别票据',
        retryKind: 'extraction',
        stage,
      };
    }
    return {
      severity: expenseCase.status === 'FAILED' ? 'error' : 'warning',
      title: `${workflowStepLabel(stage)}${expenseCase.status === 'FAILED' ? '失败' : '部分失败'}`,
      description: failureDescription(
        stage,
        failedStep?.errorMessage || expenseCase.failureReason || evidence?.run?.errorMessage,
        '部分处理未完成，系统已保留当前进度，可以从出错处重试。',
      ),
      actionLabel: '从这里重试',
      retryKind: 'workflow',
      requestId: evidence?.run?.requestId,
      stage,
    };
  }

  if (expenseCase.status === 'WAITING_HUMAN') {
    return {
      severity: 'warning',
      title: '等待人工审核',
      description: '该案例需要人工确认，请审核员结合票据、制度依据和风险提示作出决定。',
    };
  }

  if (expenseCase.status === 'APPROVED') {
    return {
      severity: 'success',
      title: '审核已批准',
      description: '案例已通过审核，财务管理员可以继续发起结算。',
    };
  }

  return {
    severity: 'info',
    title: nextActionTitle(expenseCase),
    description: nextActionDescription(expenseCase),
  };
}

export function nextActionTitle(expenseCase: ExpenseCase) {
  switch (expenseCase.status) {
    case 'DRAFT':
      return '等待上传票据';
    case 'UPLOADED':
      return '可以开始识别票据';
    case 'EXTRACTED':
      return '可以开始审核';
    case 'EXTRACTING':
      return '正在识别票据内容';
    case 'POLICY_CHECKING':
      return '正在核对制度依据';
    case 'RISK_CHECKING':
      return '正在进行风险评估';
    case 'WAITING_HUMAN':
      return '等待人工审核';
    case 'APPROVED':
      return '待发起结算';
    case 'REJECTED':
      return '审核已拒绝';
    default:
      return '案例处理中';
  }
}

export function nextActionDescription(expenseCase: ExpenseCase) {
  switch (expenseCase.status) {
    case 'UPLOADED':
      return '票据已保存，下一步是识别发票字段和明细。';
    case 'EXTRACTED':
      return '票据内容已识别，下一步是核对制度并评估风险。';
    case 'WAITING_HUMAN':
      return '该案例需要审核员确认。';
    case 'APPROVED':
      return '案例已通过审核，下一步由财务管理员发起结算。';
    case 'REJECTED':
      return '该案例已有拒绝决定，后续可查看审核记录和证据。';
    default:
      return '系统会保留每一步的处理结果和失败原因。';
  }
}

export function latestFailedStep(evidence?: CaseEvidence) {
  return [...(evidence?.steps ?? [])].reverse().find((step) => step.status === 'FAILED');
}

export function isRecoverableWorkflowStep(stage: string) {
  return [
    'AGENT_PLAN',
    'MCP_EMPLOYEE_CONTEXT',
    'MCP_DUPLICATE_CHECK',
    'MCP_REVIEW_EVIDENCE',
    'PARALLEL_EVIDENCE_COLLECTION',
    'POLICY_RETRIEVAL',
    'RISK_ASSESSMENT',
    'RISK_ROUTING',
    'FINALIZE',
    'COORDINATOR',
  ].includes(stage);
}

function stageState(input: {
  failed: boolean;
  warning: boolean;
  succeeded: boolean;
  active: boolean;
}): StageState {
  if (input.failed) return 'failed';
  if (input.warning) return 'warning';
  if (input.succeeded) return 'succeeded';
  if (input.active) return 'active';
  return 'pending';
}

function activeIf(condition: boolean): StageState {
  return condition ? 'active' : 'pending';
}

function after(status: ExpenseCase['status'], checkpoint: ExpenseCase['status']) {
  const order = [
    'DRAFT',
    'UPLOADED',
    'EXTRACTING',
    'EXTRACTED',
    'POLICY_CHECKING',
    'RISK_CHECKING',
    'WAITING_HUMAN',
    'APPROVED',
    'REJECTED',
  ];
  return order.indexOf(status) > order.indexOf(checkpoint);
}

function extractionDetail(expenseCase: ExpenseCase, documents: ExpenseDocumentDetail[]) {
  if (expenseCase.status === 'FAILED' && expenseCase.failureStage === 'DOCUMENT_EXTRACTION') {
    return '识别失败，可重试';
  }
  const extracted = documents.filter((document) => document.extraction).length;
  if (extracted > 0) return `${extracted}/${documents.length} 张已提取`;
  if (expenseCase.status === 'EXTRACTING') return '提取中';
  return '等待提取';
}

function evidenceDetail(evidence?: CaseEvidence) {
  if (!evidence?.run) return '未启动';
  const failed = evidence.steps.filter((step) => step.status === 'FAILED').length;
  if (failed > 0) return `${failed} 个证据步骤失败`;
  return `${evidence.steps.length} 个步骤`;
}

function reviewDetail(expenseCase: ExpenseCase) {
  if (expenseCase.status === 'WAITING_HUMAN') return '等待审核员决定';
  if (expenseCase.status === 'APPROVED') return '已批准';
  if (expenseCase.status === 'REJECTED') return '已拒绝';
  return '风险路由后进入';
}

function isEvidenceStep(stepName: string) {
  return [
    'MCP_EMPLOYEE_CONTEXT',
    'MCP_DUPLICATE_CHECK',
    'MCP_REVIEW_EVIDENCE',
    'PARALLEL_EVIDENCE_COLLECTION',
  ].includes(stepName);
}

function failureDescription(stage: string, rawReason: string | undefined, fallback: string) {
  if (!rawReason) return fallback;
  if (rawReason.includes('DEPENDENCY_UNAVAILABLE') || rawReason.includes('MCP 调用失败')) {
    return `${workflowStepLabel(stage)}暂时没有返回结果。案例已保留当前进度，可以稍后重试，或交给人工确认。`;
  }
  if (rawReason.includes('_') || rawReason.includes('NON_RETRYABLE') || rawReason.includes('RETRYABLE')) {
    return fallback;
  }
  return rawReason;
}
