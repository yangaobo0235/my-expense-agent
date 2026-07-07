import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Card, Collapse, DatePicker, Descriptions, Empty, Form, Input, Progress, Space, Table, Tag, Typography } from 'antd';
import dayjs from 'dayjs';
import { CaseEvidence, ExpenseCase, ExpenseDocumentDetail, PolicySearchMatch, RiskSignal } from '../../api/contracts';
import { searchPolicies } from '../../api/expense-api';
import { RiskBadge } from '../../components/RiskBadge';
import { StatusBadge } from '../../components/StatusBadge';
import { workflowStepLabel } from './case-workbench-model';

type EvidenceStep = CaseEvidence['steps'][number];

const evidenceStepGroups = [
  {
    title: '员工信息',
    description: '员工等级、地区、账户和可报销范围。',
    steps: ['MCP_EMPLOYEE_CONTEXT', 'PARALLEL_EVIDENCE_COLLECTION'],
  },
  {
    title: '历史重复检测',
    description: '文件指纹、历史报销和疑似重复凭证。',
    steps: ['MCP_DUPLICATE_CHECK', 'PARALLEL_EVIDENCE_COLLECTION'],
  },
  {
    title: '审核依据',
    description: '保存关键依据，便于后续查看和追溯。',
    steps: ['MCP_REVIEW_EVIDENCE', 'PARALLEL_EVIDENCE_COLLECTION'],
  },
  {
    title: '处理计划',
    description: '本次案例会按固定步骤核对票据、制度和风险。',
    steps: ['AGENT_PLAN'],
  },
];

export function EvidenceSourceBoard({ evidence }: { evidence?: CaseEvidence }) {
  if (!evidence?.run) return <Empty description="尚未启动证据收集" />;
  return (
    <Space orientation="vertical" size="middle" className="page-stack">
      <Alert
        type={evidence.steps.some((step) => step.status === 'FAILED') ? 'warning' : 'success'}
        showIcon
        message={evidence.steps.some((step) => step.status === 'FAILED') ? '部分依据收集失败' : '依据已收集完成'}
        description="每个依据来源都会保留处理状态和失败原因。失败不会让案例作废，可在右侧处理状态中继续重试或交给人工确认。"
      />
      <div className="evidence-source-grid">
        {evidenceStepGroups.map((group) => {
          const steps = evidence.steps.filter((step) => group.steps.includes(step.name));
          const failed = steps.filter((step) => step.status === 'FAILED');
          const status = failed.length > 0 ? 'failed' : steps.length > 0 ? 'succeeded' : 'pending';
          return (
            <Card key={group.title} size="small" className={`evidence-source-card ${status}`}>
              <Space orientation="vertical" className="page-stack">
                <Space>
                  <span className={`source-status-dot ${status}`} />
                  <Typography.Text strong>{group.title}</Typography.Text>
                  <Tag color={status === 'failed' ? 'red' : status === 'succeeded' ? 'green' : 'default'}>
                    {status === 'failed' ? '需处理' : status === 'succeeded' ? '已收集' : '未开始'}
                  </Tag>
                </Space>
                <Typography.Text type="secondary">{group.description}</Typography.Text>
                {steps.length === 0 ? (
                  <Typography.Text type="secondary">暂无处理记录</Typography.Text>
                ) : (
                  <Collapse
                    ghost
                    items={steps.map((step) => ({
                      key: step.id,
                      label: (
                        <Space wrap>
                          <span>{workflowStepLabel(step.name)}</span>
                          <Tag color={step.status === 'FAILED' ? 'red' : 'green'}>{runStatusLabel(step.status)}</Tag>
                          <Typography.Text type="secondary">{step.durationMs ?? 0} ms</Typography.Text>
                        </Space>
                      ),
                      children: (
                        <Space orientation="vertical" className="page-stack">
                          {step.errorMessage && (
                            <Alert
                              type="error"
                              showIcon
                              message={stepErrorTitle(step)}
                              description={stepErrorDescription(step)}
                            />
                          )}
                          {(hasEvidence(step) || step.errorCode || step.errorMessage) && (
                            <Collapse
                              ghost
                              items={[{
                                key: `${step.id}-detail`,
                                label: '查看详细原因',
                                children: (
                                  <pre className="json-evidence">
                                    {JSON.stringify(stepDetail(step), null, 2)}
                                  </pre>
                                ),
                              }]}
                            />
                          )}
                        </Space>
                      ),
                    }))}
                  />
                )}
              </Space>
            </Card>
          );
        })}
      </div>
    </Space>
  );
}

export function RiskExplanationPanel({
  evidence,
  expenseCase,
}: {
  evidence?: CaseEvidence;
  expenseCase: ExpenseCase;
}) {
  const risk = evidence?.risk;
  const score = risk?.score ?? expenseCase.riskScore ?? 0;
  return (
    <Space orientation="vertical" size="middle" className="page-stack">
      <div className="risk-explanation-grid">
        <Card size="small" title="风险结论">
          <Space orientation="vertical" className="page-stack">
            <RiskBadge level={risk?.level ?? expenseCase.riskLevel} score={score} />
            <Progress percent={score} status={score >= 60 ? 'exception' : score >= 30 ? 'active' : 'normal'} />
            <Typography.Text type="secondary">
              {risk?.requiresHumanReview || expenseCase.status === 'WAITING_HUMAN'
                ? '需要人工复核'
                : '未触发人工复核'}
            </Typography.Text>
          </Space>
        </Card>
        <Card size="small" title="风险说明">
          <Typography.Paragraph>
            风险分数由系统规则计算，智能分析只提供参考依据，不能直接批准、驳回或付款。
          </Typography.Paragraph>
          <Typography.Text type="secondary">
            如果外部数据暂时不可用，系统会提示人工确认。
          </Typography.Text>
        </Card>
      </div>
      <Table<RiskSignal>
        rowKey="code"
        pagination={false}
        dataSource={risk?.signals ?? []}
        locale={{ emptyText: '暂无风险信号' }}
        columns={[
          {
            title: '风险提示',
            dataIndex: 'code',
            render: (value: string) => <Tag color={riskSignalColor(value)}>{riskSignalLabel(value)}</Tag>,
          },
          { title: '分值', dataIndex: 'score', width: 84 },
          { title: '解释', dataIndex: 'message' },
          {
            title: '证据',
            dataIndex: 'evidence',
            render: (value) => <Typography.Text>{JSON.stringify(value)}</Typography.Text>,
          },
        ]}
      />
    </Space>
  );
}

export function DocumentSummaryPanel({ documents }: { documents: ExpenseDocumentDetail[] }) {
  if (documents.length === 0) return <Empty description="暂无票据" />;
  return (
    <Descriptions bordered size="small" column={1}>
      {documents.map((document) => (
        <Descriptions.Item key={document.id} label={document.originalFilename}>
          {document.extraction
            ? `${document.extraction.result.documentType} · ${document.extraction.result.totalAmount ?? '-'} ${document.extraction.result.currency ?? ''} · 置信度 ${Math.round(document.extraction.result.confidence * 100)}%`
            : '尚未识别'}
        </Descriptions.Item>
      ))}
    </Descriptions>
  );
}

interface PolicySearchForm {
  query: string;
  category: string;
  region: string;
  employeeGrade: string;
  expenseDate?: dayjs.Dayjs;
}

export function PolicyEvidenceWorkbench({
  evidence,
  expenseCase,
}: {
  evidence?: CaseEvidence;
  expenseCase: ExpenseCase;
}) {
  const [form] = Form.useForm<PolicySearchForm>();
  const values = Form.useWatch([], form);
  const canSearch = Boolean(values?.query && values.category && values.region && values.employeeGrade);
  const matches = useQuery({
    queryKey: ['case-policy-manual-search', expenseCase.id, values],
    queryFn: () =>
      searchPolicies({
        query: values!.query,
        category: values!.category,
        region: values!.region,
        employeeGrade: values!.employeeGrade,
        expenseDate: values?.expenseDate?.format('YYYY-MM-DD'),
        limit: 8,
        minimumScore: 0.45,
      }),
    enabled: canSearch,
  });

  return (
    <Space orientation="vertical" size="middle" className="page-stack">
      <Alert
        type={(evidence?.policyFindings ?? []).length > 0 ? 'success' : 'warning'}
        showIcon
        message={(evidence?.policyFindings ?? []).length > 0 ? '已找到制度依据' : '暂无自动命中的制度依据'}
        description="系统会按费用类别、地区、员工等级和费用日期匹配可用制度，再找出最相关的依据。"
      />
      <Card size="small" title="本案例命中的制度依据">
        <PolicyMatchList
          matches={(evidence?.policyFindings ?? []).map((item) => ({
            chunkId: item.chunkId,
            citation: `${item.policyName} ${item.version} · ${item.section}`,
            content: item.content,
            score: item.score,
            category: '',
            region: '',
            employeeGrade: '',
            chunkIndex: 0,
            policyCode: item.policyCode,
            policyName: item.policyName,
            policyVersion: item.version,
            policyId: item.policyId,
            effectiveFrom: '',
            section: item.section,
          }))}
          emptyText="当前处理没有返回制度依据"
        />
      </Card>
      <Card size="small" title="手动查找制度依据">
        <Form<PolicySearchForm>
          form={form}
          layout="vertical"
          initialValues={{ region: 'CN', employeeGrade: 'ALL' }}
        >
          <div className="policy-search-form-grid">
            <Form.Item name="query" label="检索问题" rules={[{ required: true }]}>
              <Input placeholder="例如：住宿费每晚上限" />
            </Form.Item>
            <Form.Item name="category" label="费用类别" rules={[{ required: true }]}>
              <Input placeholder="住宿费 / 餐饮费 / 差旅费" />
            </Form.Item>
            <Form.Item name="region" label="地区" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="employeeGrade" label="员工等级" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="expenseDate" label="费用日期">
              <DatePicker />
            </Form.Item>
          </div>
        </Form>
        {canSearch ? (
          <PolicyMatchList
            loading={matches.isFetching}
            matches={matches.data ?? []}
            emptyText="当前条件下没有找到足够相关的制度依据"
          />
        ) : (
          <Empty description="填写检索问题和条件后自动查找制度依据" />
        )}
      </Card>
    </Space>
  );
}

export function SettlementWorkbench({
  evidence,
  expenseCase,
  canSettle,
  settling,
  settlementCompleted,
  onSettle,
}: {
  evidence?: CaseEvidence;
  expenseCase: ExpenseCase;
  canSettle: boolean;
  settling: boolean;
  settlementCompleted: boolean;
  onSettle: () => void;
}) {
  const settlementCalls = (evidence?.toolCalls ?? []).filter((call) =>
    ['submit_reimbursement', 'submit_payment'].includes(call.toolName),
  );
  const failedCalls = settlementCalls.filter((call) => call.status === 'FAILED');
  const pendingApproval = expenseCase.status !== 'APPROVED';
  const latestFailedCall = failedCalls.at(-1);
  return (
    <Space orientation="vertical" size="middle" className="page-stack">
      <Alert
        type={settlementCompleted ? 'success' : failedCalls.length > 0 ? 'error' : pendingApproval ? 'warning' : 'info'}
        showIcon
        message={
          settlementCompleted
            ? '结算已提交'
            : failedCalls.length > 0
              ? '结算提交失败'
              : pendingApproval
                ? '审批通过后可结算'
                : '可以发起结算'
        }
        description="结算是审核通过后的独立步骤。即使提交失败，也可以单独重试，不会改变审核决定。"
      />
      <div className="settlement-summary-grid">
        <Card size="small" title="审批状态">
          <Descriptions size="small" column={1}>
            <Descriptions.Item label="案例状态"><StatusBadge status={expenseCase.status} /></Descriptions.Item>
            <Descriptions.Item label="金额">{expenseCase.claimedAmount} {expenseCase.currency}</Descriptions.Item>
            <Descriptions.Item label="风险"><RiskBadge level={expenseCase.riskLevel} score={expenseCase.riskScore} /></Descriptions.Item>
          </Descriptions>
        </Card>
        <Card size="small" title="补偿建议">
          {settlementCompleted ? (
            <Typography.Text type="success">结算已经提交成功，无需补偿处理。</Typography.Text>
          ) : failedCalls.length > 0 ? (
            <Space orientation="vertical">
              <Typography.Text>{settlementFailureMessage(latestFailedCall?.errorCode, latestFailedCall?.toolName)}</Typography.Text>
              {canSettle && <Button type="primary" loading={settling} onClick={onSettle}>重试结算</Button>}
            </Space>
          ) : canSettle ? (
            <Button type="primary" loading={settling} onClick={onSettle}>发起结算</Button>
          ) : (
            <Typography.Text type="secondary">当前账号或案例状态不允许发起结算。</Typography.Text>
          )}
        </Card>
      </div>
      <Table
        rowKey="id"
        pagination={false}
        dataSource={settlementCalls}
        locale={{ emptyText: '尚无结算提交记录' }}
        className="settlement-table"
        scroll={{ x: 760 }}
        columns={[
          {
            title: '操作',
            dataIndex: 'toolName',
            width: 140,
            render: (value: string) => settlementOperationLabel(value),
          },
          {
            title: '状态',
            dataIndex: 'status',
            width: 150,
            render: (status: string, row) => (
              <Tag color={status === 'SUCCEEDED' ? 'green' : status === 'FAILED' ? 'red' : 'blue'}>
                {status === 'FAILED' && settlementCompleted
                  ? '历史失败'
                  : row.errorCode
                    ? settlementErrorLabel(row.errorCode)
                    : runStatusLabel(status)}
              </Tag>
            ),
          },
          {
            title: '说明',
            width: 260,
            render: (_, row) => (
              <Typography.Text type={row.status === 'FAILED' ? 'danger' : undefined}>
                {row.status === 'FAILED' && settlementCompleted
                  ? '之前提交失败过，后续已重新提交成功。'
                  : row.status === 'FAILED'
                  ? settlementFailureMessage(row.errorCode, row.toolName)
                  : row.status === 'SUCCEEDED'
                    ? '已提交到结算系统'
                    : '正在处理'}
              </Typography.Text>
            ),
          },
          {
            title: '审批引用',
            dataIndex: 'approvalReference',
            width: 140,
            render: (value) => shortId(value) || '-',
          },
          {
            title: '完成时间',
            dataIndex: 'completedAt',
            width: 180,
            render: (value?: string) => value ? new Date(value).toLocaleString('zh-CN') : '-',
          },
        ]}
      />
    </Space>
  );
}

function PolicyMatchList({
  matches,
  loading,
  emptyText,
}: {
  matches: Array<PolicySearchMatch | {
    chunkId: string;
    citation: string;
    content: string;
    score: number;
    category: string;
    region: string;
    employeeGrade: string;
    chunkIndex: number;
    policyCode: string;
    policyName: string;
    policyVersion: string;
    policyId: string;
    effectiveFrom: string;
    section: string;
  }>;
  loading?: boolean;
  emptyText: string;
}) {
  if (loading) return <Progress percent={70} status="active" showInfo={false} />;
  if (matches.length === 0) return <Empty description={emptyText} />;
  return (
    <Collapse
      className="policy-match-list"
      items={matches.map((match) => ({
        key: match.chunkId,
        label: (
          <Space wrap>
            <Typography.Text strong>{match.citation}</Typography.Text>
            <Tag color="blue">相关度 {(match.score * 100).toFixed(1)}%</Tag>
          </Space>
        ),
        children: (
          <Space orientation="vertical" className="page-stack">
            <Typography.Paragraph>{match.content}</Typography.Paragraph>
            <Typography.Text type="secondary">
              {match.category || '本案例引用'} · {match.region || '-'} · {match.employeeGrade || '-'} · 引用 #{match.chunkIndex}
            </Typography.Text>
          </Space>
        ),
      }))}
    />
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

function hasEvidence(step: EvidenceStep) {
  return Object.keys(step.evidence ?? {}).length > 0;
}

function stepDetail(step: EvidenceStep) {
  return {
    步骤: workflowStepLabel(step.name),
    状态: runStatusLabel(step.status),
    问题类型: errorCodeLabel(step.errorCode),
    情况说明: step.errorMessage ? stepErrorDescription(step) : '该步骤已处理完成。',
    处理依据: sanitizeEvidence(step.evidence),
  };
}

function errorCodeLabel(errorCode?: string) {
  const labels: Record<string, string> = {
    DEPENDENCY_UNAVAILABLE: '外部数据暂不可用',
    VALIDATION_FAILED: '信息填写不完整',
    INVALID_STATE_TRANSITION: '当前状态不允许操作',
  };
  return errorCode ? labels[errorCode] ?? '处理异常' : '无';
}

function sanitizeEvidence(value: unknown): unknown {
  if (typeof value === 'string') {
    return sanitizeInternalText(value);
  }
  if (Array.isArray(value)) {
    return value.map(sanitizeEvidence);
  }
  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value as Record<string, unknown>).map(([key, entry]) => [
        businessKeyLabel(key),
        sanitizeEvidence(entry),
      ]),
    );
  }
  return value;
}

function sanitizeInternalText(value: string) {
  if (
    value.includes('MCP 调用失败')
    || value.includes('NON_RETRYABLE')
    || value.includes('RETRYABLE')
    || value.includes('DEPENDENCY_UNAVAILABLE')
  ) {
    return '外部服务暂时没有返回结果。';
  }
  return value
    .replaceAll('save_review_evidence', '保存审核依据')
    .replaceAll('get_employee_profile', '员工信息核对')
    .replaceAll('check_duplicate_document', '历史重复检测')
    .replaceAll('calculate_allowed_amount', '制度额度核对');
}

function businessKeyLabel(key: string) {
  const labels: Record<string, string> = {
    failure: '失败原因',
    duplicate: '是否重复',
    matches: '匹配记录',
    source: '来源',
    dependencyFailure: '是否外部服务异常',
    raw: '原始结果',
  };
  return labels[key] ?? key;
}

function stepErrorTitle(step: EvidenceStep) {
  if (step.errorCode === 'DEPENDENCY_UNAVAILABLE') {
    return `${workflowStepLabel(step.name)}暂时不可用`;
  }
  return `${workflowStepLabel(step.name)}失败`;
}

function stepErrorDescription(step: EvidenceStep) {
  const service = serviceLabel(step);
  if (step.errorCode === 'DEPENDENCY_UNAVAILABLE') {
    return `${service}暂时没有返回结果。案例已保留当前进度，可以稍后从右侧“当前处理状态”继续重试，或交给人工确认。`;
  }
  return '该步骤未处理完成。案例已保留当前进度，可以稍后重试或交给人工确认。';
}

function serviceLabel(step: EvidenceStep) {
  const raw = `${step.errorMessage ?? ''} ${JSON.stringify(step.evidence ?? {})}`;
  if (raw.includes('get_employee_profile')) return '员工信息服务';
  if (raw.includes('check_duplicate_document')) return '历史重复检测服务';
  if (raw.includes('save_review_evidence')) return '审核依据保存服务';
  if (raw.includes('calculate_allowed_amount')) return '制度核对服务';
  if (raw.includes('submit_reimbursement')) return '报销提交服务';
  if (raw.includes('submit_payment')) return '付款提交服务';
  return workflowStepLabel(step.name);
}

function settlementOperationLabel(toolName: string) {
  const labels: Record<string, string> = {
    submit_reimbursement: '提交报销',
    submit_payment: '提交付款',
    save_review_evidence: '保存审核依据',
  };
  return labels[toolName] ?? toolName;
}

function settlementErrorLabel(errorCode?: string) {
  const labels: Record<string, string> = {
    DEPENDENCY_UNAVAILABLE: '结算服务暂不可用',
    NON_RETRYABLE_DEPENDENCY_FAILURE: '结算服务暂不可用',
    RETRYABLE_DEPENDENCY_FAILURE: '结算服务暂不可用',
    VALIDATION_FAILED: '信息校验未通过',
    INVALID_STATE_TRANSITION: '状态暂不允许',
  };
  return errorCode ? labels[errorCode] ?? '提交失败' : '提交失败';
}

function settlementFailureMessage(errorCode?: string, toolName?: string) {
  if (
    errorCode?.includes('DEPENDENCY') ||
    toolName === 'submit_reimbursement' ||
    toolName === 'submit_payment'
  ) {
    return '结算服务暂时不可用，审核结果已保留。请稍后重试结算，或联系管理员检查结算服务。';
  }
  if (errorCode === 'VALIDATION_FAILED') {
    return '结算信息校验未通过，请确认金额、员工和审批结果后再提交。';
  }
  return '结算提交失败，请稍后重试。';
}

function shortId(value?: string) {
  if (!value) return '';
  return value.length > 12 ? `${value.slice(0, 6)}...${value.slice(-4)}` : value;
}

function riskSignalLabel(code: string) {
  const labels: Record<string, string> = {
    DUPLICATE_DOCUMENT: '疑似重复票据',
    POLICY_LIMIT_EXCEEDED: '超过制度额度',
    MISSING_REQUIRED_DOCUMENT: '缺少必要凭证',
    FORBIDDEN_EXPENSE_ITEM: '包含不可报销项目',
    DATE_ANOMALY: '日期异常',
    SELLER_ANOMALY: '销售方异常',
    DEPENDENCY_UNAVAILABLE: '外部数据暂不可用',
  };
  return labels[code] ?? code.replaceAll('_', ' ');
}

function riskSignalColor(code: string) {
  if (code.includes('DUPLICATE') || code.includes('FORBIDDEN')) return 'red';
  if (code.includes('POLICY') || code.includes('MISSING')) return 'orange';
  if (code.includes('DEPENDENCY')) return 'purple';
  return 'blue';
}
