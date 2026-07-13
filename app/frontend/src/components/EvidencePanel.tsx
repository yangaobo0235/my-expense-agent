import {
  Alert,
  Card,
  Collapse,
  Descriptions,
  Empty,
  Progress,
  Space,
  Table,
  Tag,
  Timeline,
  Typography,
} from 'antd';
import { CaseEvidence } from '../api/contracts';
import { workflowStepLabel } from '../features/cases/case-workbench-model';
import { RiskBadge } from './RiskBadge';

type EvidenceStep = CaseEvidence['steps'][number];

const stepLabels: Record<string, string> = {
  AGENT_PLAN: '处理计划',
  MCP_APPLICANT_CONTEXT: '申请人信息核对',
  MCP_DUPLICATE_CHECK: '历史重复检测',
  MCP_PROJECT_BUDGET: '项目预算核对',
  MCP_REIMBURSEMENT_HISTORY: '历史报销核对',
  MCP_REVIEW_EVIDENCE: '审核证据保存',
  POLICY_RETRIEVAL: '经费制度核对',
  RISK_ASSESSMENT: '风险评估',
  FINALIZE: '形成审核意见',
};

interface AgentPlanEvidence {
  planVersion?: string;
  agents?: Array<{
    sequence?: number;
    role?: string;
    name?: string;
    responsibility?: string;
    allowedTools?: string[];
    writeOperationAllowed?: boolean;
    failurePolicy?: string;
    maxAttempts?: number;
    handoffTarget?: string;
  }>;
}

export function EvidencePanel({ evidence }: { evidence?: CaseEvidence }) {
  if (!evidence?.run) return <Empty description="尚未开始审核处理" />;
  return (
    <Space orientation="vertical" size="large" className="page-stack">
      {evidence.run.errorMessage && (
        <Alert
          type="error"
          showIcon
          title="部分处理失败"
          description={safeErrorDescription(undefined, evidence.run.errorCode, evidence.run.errorMessage)}
        />
      )}
      <div className="evidence-summary-grid">
        <Card size="small" title="运行摘要">
          <Descriptions column={1} size="small">
            <Descriptions.Item label="状态">
              <Tag color={evidence.run.status === 'SUCCEEDED' ? 'green' : 'orange'}>
                {runStatusLabel(evidence.run.status)}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="开始时间">
              {new Date(evidence.run.startedAt).toLocaleString('zh-CN')}
            </Descriptions.Item>
            <Descriptions.Item label="链路编号">
              {evidence.run.traceId ?? '尚未关联'}
            </Descriptions.Item>
          </Descriptions>
        </Card>
        <Card size="small" title="风险结论">
          {evidence.risk ? (
            <>
              <RiskBadge level={evidence.risk.level} score={evidence.risk.score} />
              <Progress percent={evidence.risk.score} status={evidence.risk.level === 'HIGH' ? 'exception' : 'normal'} />
              <Typography.Text type="secondary">
                {evidence.risk.requiresHumanReview ? '需要人工复核' : '满足自动建议条件'}
              </Typography.Text>
            </>
          ) : <Empty description="暂无风险结果" />}
        </Card>
      </div>
      <Card title="处理步骤">
        <Timeline
          items={evidence.steps.map((step) => ({
            color: step.status === 'SUCCEEDED' ? 'green' : step.status === 'FAILED' ? 'red' : 'blue',
            content: (
              <div>
                <Space>
                  <strong>{stepLabels[step.name] ?? workflowStepLabel(step.name)}</strong>
                  <Tag>{runStatusLabel(step.status)}</Tag>
                  {step.durationMs !== undefined && <span className="muted">{step.durationMs} ms</span>}
                </Space>
                {step.errorMessage && (
                  <Alert
                    type="error"
                    title={safeErrorTitle(step)}
                    description={safeErrorDescription(step, step.errorCode, step.errorMessage)}
                    showIcon
                  />
                )}
                {Object.keys(step.evidence).length > 0 && (
                  <Collapse
                    ghost
                    items={[{
                      key: step.id,
                      label: step.name === 'AGENT_PLAN' ? '查看处理分工' : '查看处理依据',
                      children: renderStepEvidence(step.name, step.evidence),
                    }]}
                  />
                )}
              </div>
            ),
          }))}
        />
      </Card>
      <Card title="制度依据">
        <Collapse
          items={evidence.policyFindings.map((policy) => ({
            key: policy.chunkId,
            label: `${policy.policyName} v${policy.version} · ${policy.section}`,
            extra: <Tag color="blue">相关度 {Math.round(policy.score * 100)}%</Tag>,
            children: (
              <>
                <Typography.Paragraph>{policy.content}</Typography.Paragraph>
                <Typography.Text type="secondary">制度编码：{policy.policyCode}</Typography.Text>
              </>
            ),
          }))}
        />
        {evidence.policyFindings.length === 0 && <Empty description="未检索到匹配制度" />}
      </Card>
      <Card title="风险提示">
        <Table
          rowKey="code"
          pagination={false}
          dataSource={evidence.risk?.signals ?? []}
          locale={{ emptyText: '暂无风险信号' }}
          columns={[
            { title: '提示', dataIndex: 'code', render: (value: string) => riskSignalLabel(value) },
            { title: '分值', dataIndex: 'score' },
            { title: '解释', dataIndex: 'message' },
            { title: '证据', dataIndex: 'evidence', render: (value) => JSON.stringify(value) },
          ]}
        />
      </Card>
      <Card title="入账提交记录">
        <Table
          rowKey="id"
          pagination={false}
          dataSource={evidence.toolCalls}
          locale={{ emptyText: '尚无入账提交记录' }}
          columns={[
            { title: '操作', dataIndex: 'toolName', render: (value: string) => settlementOperationLabel(value) },
            { title: '状态', dataIndex: 'status', render: (value: string) => runStatusLabel(value) },
            { title: '耗时', dataIndex: 'durationMs', render: (value) => `${value ?? 0} ms` },
            { title: '审批引用', dataIndex: 'approvalReference', render: (value) => value || '-' },
          ]}
        />
      </Card>
    </Space>
  );
}

function renderStepEvidence(stepName: string, evidence: Record<string, unknown>) {
  if (stepName !== 'AGENT_PLAN') {
    return <pre className="json-evidence">{JSON.stringify(evidence, null, 2)}</pre>;
  }

  const plan = evidence as AgentPlanEvidence;
  const agents = Array.isArray(plan.agents) ? plan.agents : [];
  return (
    <Space orientation="vertical" className="full-width">
      <Typography.Text type="secondary">
        系统已生成本次处理计划。
      </Typography.Text>
      <Table
        size="small"
        pagination={false}
        rowKey={(row) => row.role ?? row.name ?? `step-${row.sequence ?? 'unknown'}`}
        dataSource={agents}
        columns={[
          { title: '序号', dataIndex: 'sequence', width: 72 },
          { title: '处理角色', dataIndex: 'role', render: (value?: string) => roleLabel(value) },
          { title: '名称', dataIndex: 'name', render: (value?: string) => cleanDisplayName(value) },
          { title: '职责', dataIndex: 'responsibility' },
          {
            title: '操作权限',
            key: 'tools',
            render: (_, row) => (
              <Space wrap>
                {row.writeOperationAllowed ? <Tag color="red">可提交</Tag> : <Tag color="green">只读</Tag>}
                {(row.allowedTools?.length ?? 0) > 0 && <Tag>{row.allowedTools?.length} 项内部操作</Tag>}
              </Space>
            ),
          },
          { title: '失败处理', dataIndex: 'failurePolicy', render: (value?: string) => failurePolicyLabel(value) },
          { title: '最大尝试', dataIndex: 'maxAttempts', render: (value) => value ?? 1 },
          { title: '失败交接', dataIndex: 'handoffTarget', render: (value) => value || '-' },
        ]}
      />
    </Space>
  );
}

function runStatusLabel(status: string) {
  const labels: Record<string, string> = {
    SUCCEEDED: '已完成',
    FAILED: '失败',
    RUNNING: '处理中',
    PENDING: '等待中',
  };
  return labels[status] ?? status;
}

function safeErrorTitle(step: EvidenceStep) {
  if (step.errorCode === 'DEPENDENCY_UNAVAILABLE') {
    return `${workflowStepLabel(step.name)}暂时不可用`;
  }
  return `${workflowStepLabel(step.name)}失败`;
}

function safeErrorDescription(step: EvidenceStep | undefined, errorCode?: string, errorMessage?: string) {
  if (errorCode === 'DEPENDENCY_UNAVAILABLE' || errorMessage?.includes('MCP 调用失败')) {
    const service = step ? serviceLabel(step, errorMessage) : '外部服务';
    return `${service}暂时没有返回结果。申请已保留当前进度，可以稍后重试或交给人工确认。`;
  }
  if (errorMessage?.includes('_') || errorMessage?.includes('NON_RETRYABLE')) {
    return '该步骤未处理完成。申请已保留当前进度，可以稍后重试或交给人工确认。';
  }
  return errorMessage ?? '该步骤未处理完成。';
}

function serviceLabel(step: EvidenceStep, errorMessage?: string) {
  const raw = `${errorMessage ?? ''} ${JSON.stringify(step.evidence ?? {})}`;
  if (raw.includes('get_applicant_profile')) return '申请人信息服务';
  if (raw.includes('check_duplicate_document')) return '历史重复检测服务';
  if (raw.includes('save_review_evidence')) return '审核依据保存服务';
  if (raw.includes('calculate_allowed_amount')) return '制度核对服务';
  return workflowStepLabel(step.name);
}

function settlementOperationLabel(toolName: string) {
  const labels: Record<string, string> = {
    debit_project_budget: '扣减项目预算',
    submit_fund_reimbursement: '提交报销登记',
    submit_fund_posting: '提交经费入账',
    record_fund_reimbursement_history: '回写报销历史',
    save_review_evidence: '保存审核依据',
  };
  return labels[toolName] ?? toolName;
}

function riskSignalLabel(code: string) {
  const labels: Record<string, string> = {
    DUPLICATE_DOCUMENT: '疑似重复票据',
    POLICY_LIMIT_EXCEEDED: '超过制度额度',
    MISSING_REQUIRED_DOCUMENT: '缺少必要凭证',
    FORBIDDEN_EXPENSE_ITEM: '包含不可报销项目',
    PROJECT_BUDGET_EXCEEDED: '项目可用预算不足或币种不一致',
    POLICY_EVIDENCE_MISSING: '缺少可追溯制度依据',
    DATE_ANOMALY: '日期异常',
    SELLER_ANOMALY: '销售方异常',
    DEPENDENCY_UNAVAILABLE: '外部数据暂不可用',
  };
  return labels[code] ?? code.replaceAll('_', ' ');
}

function roleLabel(role?: string) {
  const labels: Record<string, string> = {
    RECEIPT_EXTRACTION_AGENT: '票据识别',
    MCP_CONTEXT_AGENT: '申请人信息核对',
    POLICY_RAG_AGENT: '制度依据核对',
    RISK_RULE_AGENT: '风险评估',
    REVIEW_SUMMARY_AGENT: '审核意见整理',
    APPROVED_SETTLEMENT_AGENT: '审批后入账',
  };
  return role ? labels[role] ?? role.replaceAll('_', ' ') : '-';
}

function cleanDisplayName(value?: string) {
  return value ? value.replace(/\s*Agent$/i, '') : '-';
}

function failurePolicyLabel(value?: string) {
  const labels: Record<string, string> = {
    REQUIRE_HUMAN_REVIEW: '转人工确认',
    RETRY_THEN_HUMAN_REVIEW: '先重试，再转人工',
    STOP_AND_ESCALATE: '停止并升级处理',
    IDEMPOTENT_WRITE_RETRY: '可安全重试',
  };
  return value ? labels[value] ?? value.replaceAll('_', ' ') : '-';
}
