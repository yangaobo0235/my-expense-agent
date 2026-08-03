import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Empty,
  Form,
  Input,
  InputNumber,
  message,
  Modal,
  Progress,
  Select,
  Space,
  Spin,
  Table,
  Tabs,
  Tag,
  Timeline,
  Typography,
} from 'antd';
import axios from 'axios';
import { useEffect, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { CaseEvidence, ExpenseCase, ExpenseDocumentDetail, ExpenseWorkflowEvent, ExtractedExpenseItem } from '../../api/contracts';
import { consumeCaseEvents } from '../../api/event-client';
import { analyzeCase, deleteCase, getCase, getCaseEvidence, getCaseObservability, listCaseDocuments, settleExpenseCase, updateCase } from '../../api/expense-api';
import { CaseObservabilityPanel } from '../../components/CaseObservabilityPanel';
import { RiskBadge } from '../../components/RiskBadge';
import { StatusBadge } from '../../components/StatusBadge';
import { hasOnlyRole, useAuthStore } from '../auth/auth-store';
import {
  CaseDiagnosis,
  CaseStage,
  CaseStageKey,
  buildCaseDiagnosis,
  buildCaseStages,
  workflowStepLabel,
} from './case-workbench-model';
import { EvidenceSourceBoard, PolicyEvidenceWorkbench, RiskExplanationPanel, SettlementWorkbench } from './workbench-panels';
import { WorkflowLauncher } from './WorkflowLauncher';
import { GovernedReviewTimeline } from './GovernedReviewTimeline';

interface CaseEditForm {
  applicantName: string;
  projectCode: string;
  title: string;
  claimedAmount: number;
  currency: string;
}

export function CaseDetailPage() {
  const { caseId = '' } = useParams();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const queryClient = useQueryClient();
  const user = useAuthStore((state) => state.user);
  const [modal, modalContextHolder] = Modal.useModal();
  const [editForm] = Form.useForm<CaseEditForm>();
  const [events, setEvents] = useState<ExpenseWorkflowEvent[]>([]);
  const [streamError, setStreamError] = useState<string>();
  const [activeStage, setActiveStage] = useState<CaseStageKey>('summary');
  const [editOpen, setEditOpen] = useState(false);
  const query = useQuery({ queryKey: ['case', caseId], queryFn: () => getCase(caseId), enabled: Boolean(caseId) });
  const documentsQuery = useQuery({
    queryKey: ['case-documents', caseId],
    queryFn: () => listCaseDocuments(caseId),
    enabled: Boolean(caseId),
  });
  const evidenceQuery = useQuery({
    queryKey: ['case-evidence', caseId],
    queryFn: () => getCaseEvidence(caseId),
    enabled: Boolean(caseId),
  });
  const observabilityQuery = useQuery({
    queryKey: ['case-observability', caseId],
    queryFn: () => getCaseObservability(caseId),
    enabled: Boolean(caseId),
  });
  const updateMutation = useMutation({
    mutationFn: (values: CaseEditForm) => updateCase(caseId, values),
    onSuccess: (updated) => {
      message.success('草稿已更新');
      setEditOpen(false);
      clearEditParam();
      queryClient.setQueryData(['case', caseId], updated);
      void queryClient.invalidateQueries({ queryKey: ['cases'] });
    },
    onError: (error) => {
      const apiMessage = axios.isAxiosError(error)
        ? (error.response?.data as { message?: string } | undefined)?.message
        : undefined;
      message.error(apiMessage ?? '草稿更新失败，请检查表单后重试。');
    },
  });
  const deleteMutation = useMutation({
    mutationFn: () => deleteCase(caseId),
    onSuccess: () => {
      message.success(user?.roles.includes('FINANCE_ADMIN') ? '申请已删除' : '草稿已删除');
      void queryClient.invalidateQueries({ queryKey: ['cases'] });
      navigate('/cases');
    },
    onError: (error) => {
      const apiMessage = axios.isAxiosError(error)
        ? (error.response?.data as { message?: string } | undefined)?.message
        : undefined;
      message.error(apiMessage ?? '草稿删除失败，请稍后重试。');
    },
  });
  const extractionMutation = useMutation({
    mutationFn: () => analyzeCase(caseId),
    onSuccess: () => {
      message.success('票据识别已完成');
      void queryClient.invalidateQueries({ queryKey: ['case', caseId] });
      void queryClient.invalidateQueries({ queryKey: ['case-documents', caseId] });
      void queryClient.invalidateQueries({ queryKey: ['case-evidence', caseId] });
      void queryClient.invalidateQueries({ queryKey: ['case-observability', caseId] });
    },
    onError: (error) => {
      const apiMessage = axios.isAxiosError(error)
        ? (error.response?.data as { message?: string } | undefined)?.message
        : undefined;
      message.error(apiMessage ?? '票据识别失败，请稍后重试或联系管理员。');
      void queryClient.invalidateQueries({ queryKey: ['case', caseId] });
      void queryClient.invalidateQueries({ queryKey: ['case-documents', caseId] });
      void queryClient.invalidateQueries({ queryKey: ['case-evidence', caseId] });
      void queryClient.invalidateQueries({ queryKey: ['case-observability', caseId] });
    },
  });
  const settlementMutation = useMutation({
    mutationFn: () => settleExpenseCase(caseId),
    onSuccess: (result) => {
      message.success(`入账提交完成，状态：${result.status ?? '已提交'}`);
      void queryClient.invalidateQueries({ queryKey: ['case', caseId] });
      void queryClient.invalidateQueries({ queryKey: ['cases'] });
      void queryClient.invalidateQueries({ queryKey: ['case-evidence', caseId] });
      void queryClient.invalidateQueries({ queryKey: ['case-observability', caseId] });
    },
    onError: (error) => {
      const fallback = '入账提交失败，请稍后重试。';
      const apiMessage = axios.isAxiosError(error)
        ? (error.response?.data as { message?: string } | undefined)?.message
        : undefined;
      message.error(settlementErrorMessage(apiMessage) ?? fallback);
    },
  });

  useEffect(() => {
    if (query.data?.status === 'APPROVED' || query.data?.status === 'REJECTED') {
      return;
    }
    const controller = new AbortController();
    void consumeCaseEvents({
      caseId,
      signal: controller.signal,
      onEvent: (event) => {
        setEvents((current) => current.some((item) => item.eventId === event.eventId) ? current : [...current, event].sort((a, b) => a.sequence - b.sequence));
      void queryClient.invalidateQueries({ queryKey: ['case', caseId] });
    },
    onResetRequired: () => void queryClient.invalidateQueries({ queryKey: ['case', caseId] }),
    }).catch((error: Error) => {
      if (!controller.signal.aborted) setStreamError(error.message);
    });
    return () => controller.abort();
  }, [caseId, query.data?.status, queryClient]);

  const isApplicant = Boolean(
    user?.roles.some((role) => ['STUDENT', 'ADVISOR'].includes(role)),
  );
  const isReadOnlyAuditor = hasOnlyRole(user?.roles, 'AUDITOR');
  const canOperateCase = !isReadOnlyAuditor && Boolean(user?.roles.length);
  const canEditDraft = isApplicant && query.data?.status === 'DRAFT';
  const canDeleteCase =
    Boolean(user?.roles.includes('FINANCE_ADMIN')) || canEditDraft;

  useEffect(() => {
    if (searchParams.get('edit') === '1' && canEditDraft && query.data) {
      editForm.setFieldsValue(caseToEditForm(query.data));
      setEditOpen(true);
    }
  }, [canEditDraft, editForm, query.data, searchParams]);

  function clearEditParam() {
    setSearchParams((current) => {
      current.delete('edit');
      return current;
    }, { replace: true });
  }

  function openDraftEditor(expenseCase: ExpenseCase) {
    editForm.setFieldsValue(caseToEditForm(expenseCase));
    setEditOpen(true);
  }

  function closeDraftEditor() {
    setEditOpen(false);
    clearEditParam();
  }

  function confirmDeleteDraft() {
    const adminDelete = Boolean(user?.roles.includes('FINANCE_ADMIN'));
    modal.confirm({
      title: adminDelete ? '删除该申请？' : '删除草稿申请？',
      content: adminDelete
        ? '删除后该申请和相关票据、处理记录将不再显示。请确认这是一条误建或测试记录。'
        : '删除后该草稿不会再出现在列表中。',
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: () => deleteMutation.mutateAsync(),
    });
  }

  if (query.isLoading) return <Spin />;
  if (!query.data) return <Empty description="申请不存在或无权访问" />;
  const expenseCase = query.data;
  const settlementCompleted = expenseCase.settlementStatus === 'SUBMITTED';
  const canSettle =
    user?.roles.includes('FINANCE_ADMIN') &&
    expenseCase.status === 'APPROVED' &&
    !settlementCompleted;
  const stages = buildCaseStages(expenseCase, documentsQuery.data ?? [], evidenceQuery.data);
  const diagnosis =
    expenseCase.status === 'FAILED' && evidenceQuery.isLoading
      ? undefined
      : buildCaseDiagnosis(expenseCase, evidenceQuery.data);
  return (
    <>
      {modalContextHolder}
      <Space orientation="vertical" size="large" className="page-stack">
        <div className="page-heading">
          <div>
            <Typography.Title level={2}>{expenseCase.title}</Typography.Title>
            <Typography.Text type="secondary">{expenseCase.caseNumber}</Typography.Text>
          </div>
          <Space>
            <StatusBadge status={expenseCase.status} />
            <RiskBadge level={expenseCase.riskLevel} score={expenseCase.riskScore} />
            {settlementCompleted && <Tag color="green">入账已提交</Tag>}
            {canEditDraft && (
              <>
                <Button onClick={() => openDraftEditor(expenseCase)}>修改草稿</Button>
              </>
            )}
            {canDeleteCase && (
              <Button danger loading={deleteMutation.isPending} onClick={confirmDeleteDraft}>
                {user?.roles.includes('FINANCE_ADMIN') ? '删除申请' : '删除草稿'}
              </Button>
            )}
            {canSettle && (
              <Button
                type="primary"
                loading={settlementMutation.isPending}
                onClick={() => settlementMutation.mutate()}
              >
                发起入账
              </Button>
            )}
            {isApplicant &&
              expenseCase.status === 'UPLOADED' && (
                <Button
                  loading={extractionMutation.isPending}
                  onClick={() => extractionMutation.mutate()}
                >
                  重新识别票据
                </Button>
              )}
            {isApplicant &&
              expenseCase.status === 'EXTRACTED' && (
                <WorkflowLauncher caseId={caseId} />
              )}
          </Space>
        </div>
        {canEditDraft && (
          <Alert
            type="info"
            showIcon
            title="该申请仍是草稿，可以修改或删除"
            description="上传票据后申请会进入处理链路，后续更正应通过补充材料和人工审核完成。"
          />
        )}
        {streamError && <Alert type="warning" showIcon title="实时连接暂时不可用" description={streamError} />}
        <div className="case-workbench-grid">
          <StageRail stages={stages} activeStage={activeStage} onSelect={setActiveStage} />
          <Card className="stage-workspace" title={stages.find((stage) => stage.key === activeStage)?.title ?? '申请详情'}>
            <StageWorkspace
              stage={activeStage}
              expenseCase={expenseCase}
              documents={documentsQuery.data ?? []}
              documentsLoading={documentsQuery.isLoading}
              evidence={evidenceQuery.data}
              evidenceLoading={evidenceQuery.isLoading}
              events={events}
              caseId={caseId}
              canSettle={Boolean(canSettle)}
              settling={settlementMutation.isPending}
              onSettle={() => settlementMutation.mutate()}
              settlementCompleted={Boolean(settlementCompleted)}
              canSubmitMoreInfo={isApplicant}
            />
          </Card>
          <DiagnosisSidebar
            diagnosis={diagnosis}
            loading={expenseCase.status === 'FAILED' && evidenceQuery.isLoading}
            caseId={caseId}
            extracting={extractionMutation.isPending}
            onRetryExtraction={() => extractionMutation.mutate()}
            observability={observabilityQuery.data}
            observabilityLoading={observabilityQuery.isLoading}
            canRetry={canOperateCase}
          />
        </div>
      </Space>
      <Modal
        title="修改草稿申请"
        open={editOpen}
        onCancel={closeDraftEditor}
        onOk={() => editForm.submit()}
        confirmLoading={updateMutation.isPending}
        okText="保存修改"
        cancelText="取消"
      >
        <Form
          form={editForm}
          layout="vertical"
          onFinish={(values) => updateMutation.mutate(values)}
        >
          <Form.Item
            name="applicantName"
            label="申请人"
            rules={[{ required: true, message: '请输入申请人姓名' }]}
          >
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item
            name="projectCode"
            label="经费项目编码"
            rules={[{ required: true, message: '请输入学院或项目组编码' }]}
          >
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item
            name="title"
            label="报销事项"
            rules={[{ required: true, message: '请输入报销事项' }]}
          >
            <Input maxLength={256} />
          </Form.Item>
          <Form.Item
            name="claimedAmount"
            label="申报金额"
            rules={[{ required: true, message: '请输入申报金额' }]}
          >
            <InputNumber min={0} precision={2} className="full-width" />
          </Form.Item>
          <Form.Item name="currency" label="币种" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'CNY', label: 'CNY 人民币' },
                { value: 'USD', label: 'USD 美元' },
                { value: 'EUR', label: 'EUR 欧元' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}

function caseToEditForm(expenseCase: ExpenseCase): CaseEditForm {
  return {
    applicantName: expenseCase.applicantName,
    projectCode: expenseCase.projectCode,
    title: expenseCase.title,
    claimedAmount: expenseCase.claimedAmount,
    currency: expenseCase.currency,
  };
}

function eventLabel(type: string) {
  const labels: Record<string, string> = {
    DOCUMENT_EXTRACTED: '票据已识别',
    WORKFLOW_STARTED: '审核已开始',
    WORKFLOW_COMPLETED: '审核处理完成',
    WORKFLOW_FAILED: '处理失败',
    POLICY_RETRIEVED: '制度依据已核对',
    RISK_ASSESSED: '风险已评估',
    REVIEW_TASK_CREATED: '已转人工审核',
  };
  return labels[type] ?? type.replaceAll('_', ' ');
}

function StageRail({
  stages,
  activeStage,
  onSelect,
}: {
  stages: CaseStage[];
  activeStage: CaseStageKey;
  onSelect: (stage: CaseStageKey) => void;
}) {
  return (
    <Card className="stage-rail" title="申请阶段">
      <Space orientation="vertical" className="page-stack" size={8}>
        {stages.map((stage) => (
          <button
            key={stage.key}
            className={`stage-nav-item ${activeStage === stage.key ? 'active' : ''} ${stage.state}`}
            onClick={() => onSelect(stage.key)}
            type="button"
          >
            <span className="stage-dot" />
            <span className="stage-copy">
              <strong>{stage.title}</strong>
              <small>{stage.detail}</small>
            </span>
          </button>
        ))}
      </Space>
    </Card>
  );
}

function StageWorkspace({
  stage,
  expenseCase,
  documents,
  documentsLoading,
  evidence,
  evidenceLoading,
  events,
  caseId,
  canSettle,
  settling,
  onSettle,
  settlementCompleted,
  canSubmitMoreInfo,
}: {
  stage: CaseStageKey;
  expenseCase: ExpenseCase;
  documents: ExpenseDocumentDetail[];
  documentsLoading: boolean;
  evidence?: CaseEvidence;
  evidenceLoading: boolean;
  events: ExpenseWorkflowEvent[];
  caseId: string;
  canSettle: boolean;
  settling: boolean;
  onSettle: () => void;
  settlementCompleted: boolean;
  canSubmitMoreInfo: boolean;
}) {
  if (stage === 'summary') {
    return (
      <Space orientation="vertical" size="middle" className="page-stack">
        <Descriptions bordered column={2}>
          <Descriptions.Item label="申请编号">{expenseCase.caseNumber}</Descriptions.Item>
          <Descriptions.Item label="状态"><StatusBadge status={expenseCase.status} /></Descriptions.Item>
          <Descriptions.Item label="申请人">{expenseCase.applicantName}</Descriptions.Item>
          <Descriptions.Item label="经费项目">{expenseCase.projectCode}</Descriptions.Item>
          <Descriptions.Item label="申报金额">{expenseCase.claimedAmount} {expenseCase.currency}</Descriptions.Item>
          <Descriptions.Item label="风险"><RiskBadge level={expenseCase.riskLevel} score={expenseCase.riskScore} /></Descriptions.Item>
          <Descriptions.Item label="创建时间">{new Date(expenseCase.createdAt).toLocaleString('zh-CN')}</Descriptions.Item>
          <Descriptions.Item label="更新时间">{new Date(expenseCase.updatedAt).toLocaleString('zh-CN')}</Descriptions.Item>
        </Descriptions>
        <Card size="small" title="执行时间线">
          {events.length === 0 ? <Empty description="暂无可回放事件" /> : (
            <Timeline items={events.map((event) => ({
              children: (
                <>
                  <strong>{eventLabel(event.type)}</strong>
                  <div className="muted">{new Date(event.occurredAt).toLocaleString('zh-CN')} · #{event.sequence}</div>
                </>
              ),
            }))} />
          )}
        </Card>
      </Space>
    );
  }

  if (stage === 'documents' || stage === 'extraction') {
    if (documentsLoading) return <Spin />;
    if (documents.length === 0) return <Empty description="尚未上传票据" />;
    return (
      <Tabs
        items={documents.map((document) => ({
          key: document.id,
          label: document.originalFilename,
          children: <DocumentEvidence document={document} />,
        }))}
      />
    );
  }

  if (stage === 'evidence') {
    if (evidenceLoading) return <Spin />;
    return <EvidenceSourceBoard evidence={evidence} expenseCase={expenseCase} />;
  }

  if (stage === 'policy') {
    if (evidenceLoading) return <Spin />;
    return <PolicyEvidenceWorkbench evidence={evidence} expenseCase={expenseCase} />;
  }

  if (stage === 'risk') {
    if (evidenceLoading) return <Spin />;
    return <RiskExplanationPanel evidence={evidence} expenseCase={expenseCase} />;
  }

  if (stage === 'review') {
    return (
      <GovernedReviewTimeline
        caseId={caseId}
        status={expenseCase.status}
        canSubmit={canSubmitMoreInfo}
      />
    );
  }

  return (
    <SettlementWorkbench
      evidence={evidence}
      expenseCase={expenseCase}
      canSettle={canSettle}
      settling={settling}
      settlementCompleted={settlementCompleted}
      onSettle={onSettle}
    />
  );
}

function DiagnosisSidebar({
  diagnosis,
  loading,
  caseId,
  extracting,
  onRetryExtraction,
  observability,
  observabilityLoading,
  canRetry,
}: {
  diagnosis?: CaseDiagnosis;
  loading: boolean;
  caseId: string;
  extracting: boolean;
  onRetryExtraction: () => void;
  observability?: Parameters<typeof CaseObservabilityPanel>[0]['observability'];
  observabilityLoading: boolean;
  canRetry: boolean;
}) {
  if (loading) {
    return (
      <Card className="diagnosis-sidebar" title="处理状态">
        <Spin />
      </Card>
    );
  }

  return (
    <Space orientation="vertical" size="middle" className="page-stack diagnosis-sidebar">
      <Card title="当前处理状态">
        {diagnosis ? (
          <Space orientation="vertical" className="page-stack">
            <Alert
              type={diagnosis.severity}
              showIcon
              title={diagnosis.title}
              description={diagnosis.description}
            />
            {canRetry && diagnosis.retryKind === 'extraction' && (
              <Button type="primary" loading={extracting} onClick={onRetryExtraction}>
                {diagnosis.actionLabel ?? '重新识别票据'}
              </Button>
            )}
            {canRetry && diagnosis.retryKind === 'workflow' && diagnosis.requestId && (
              <WorkflowLauncher
                caseId={caseId}
                initialRequestId={diagnosis.requestId}
                buttonText={diagnosis.actionLabel ?? '从这里重试'}
                buttonType="default"
                recoveryMode
              />
            )}
            {diagnosis.stage && <Tag>{workflowStepLabel(diagnosis.stage)}</Tag>}
          </Space>
        ) : (
          <Empty description="暂无处理提醒" />
        )}
      </Card>
      <CaseObservabilityPanel
        observability={observability}
        loading={observabilityLoading}
      />
    </Space>
  );
}

function shortTechnicalId(value?: string) {
  if (!value) return '-';
  if (value.length <= 12) return value;
  return `${value.slice(0, 6)}...${value.slice(-4)}`;
}

function settlementErrorMessage(apiMessage?: string) {
  if (!apiMessage) return undefined;
  if (
    apiMessage.includes('MCP') ||
    apiMessage.includes('submit_fund_reimbursement') ||
    apiMessage.includes('submit_fund_posting') ||
    apiMessage.includes('debit_project_budget') ||
    apiMessage.includes('record_fund_reimbursement_history') ||
    apiMessage.includes('NON_RETRYABLE') ||
    apiMessage.includes('DEPENDENCY')
  ) {
    return '入账服务暂时不可用，本申请的审核结果已保留。请稍后重试入账，或联系管理员检查入账服务。';
  }
  return apiMessage;
}

function DocumentEvidence({ document }: { document: ExpenseDocumentDetail }) {
  const extraction = document.extraction;
  const isImage = document.contentType.startsWith('image/');
  return (
    <div className="evidence-grid">
      <div className="document-preview">
        {isImage ? (
          <img src={document.previewUrl} alt={document.originalFilename} />
        ) : (
          <iframe
            src={document.previewUrl}
            title={document.originalFilename}
            sandbox=""
          />
        )}
        <Typography.Text type="secondary">
          预览地址将在 {new Date(document.previewExpiresAt).toLocaleTimeString('zh-CN')} 失效
        </Typography.Text>
      </div>
      <div>
        {!extraction ? (
          <Empty description="该票据尚未完成提取" />
        ) : (
          <Space orientation="vertical" size="middle" className="page-stack">
            <div>
              <Typography.Text strong>字段置信度</Typography.Text>
              <Progress
                percent={Math.round(extraction.result.confidence * 100)}
                status={extraction.result.confidence < 0.7 ? 'exception' : 'normal'}
              />
            </div>
            {(extraction.validationErrors.length > 0 ||
              extraction.result.warnings.length > 0) && (
              <Alert
                type="warning"
                showIcon
                title="需要人工关注"
                description={[
                  ...extraction.validationErrors,
                  ...extraction.result.warnings,
                ].join('；')}
              />
            )}
            <Descriptions bordered size="small" column={2}>
              <Descriptions.Item label="票据类型">{extraction.result.documentType || '-'}</Descriptions.Item>
              <Descriptions.Item label="发票号码">{extraction.result.invoiceNumber || '-'}</Descriptions.Item>
              <Descriptions.Item label="销售方">{extraction.result.sellerName || '-'}</Descriptions.Item>
              <Descriptions.Item label="购买方">{extraction.result.buyerName || '-'}</Descriptions.Item>
              <Descriptions.Item label="开票日期">{extraction.result.issueDate || '-'}</Descriptions.Item>
              <Descriptions.Item label="票面金额">
                {extraction.result.totalAmount ?? '-'} {extraction.result.currency ?? ''}
              </Descriptions.Item>
              <Descriptions.Item label="识别服务">{extraction.modelName || '-'}</Descriptions.Item>
              <Descriptions.Item label="规则版本">{extraction.promptVersion || '-'}</Descriptions.Item>
              <Descriptions.Item label="提取模式">{extraction.extractorMode || '-'}</Descriptions.Item>
              <Descriptions.Item label="用量 / 耗时">
                {extraction.tokenUsage ?? 0} / {extraction.extractionLatencyMs ?? 0} ms
              </Descriptions.Item>
            </Descriptions>
            <Table
              rowKey="attemptNo"
              size="small"
              pagination={false}
              dataSource={document.extractionAttempts ?? []}
              locale={{ emptyText: '该票据没有发生输出修正' }}
              columns={[
                {
                  title: '输出阶段',
                  dataIndex: 'attemptType',
                  width: 110,
                  render: (value: string) => value === 'REPAIR' ? '修正输出' : '原始输出',
                },
                {
                  title: '校验结果',
                  dataIndex: 'status',
                  width: 130,
                  render: (value: string) => (
                    <Tag color={value === 'SUCCEEDED' ? 'green' : 'orange'}>
                      {value === 'SUCCEEDED' ? '通过' : '未通过'}
                    </Tag>
                  ),
                },
                {
                  title: '校验错误',
                  dataIndex: 'validationErrors',
                  render: (values: Array<{ code: string; field: string; message: string }>) =>
                    values?.map((item) => `${item.field || 'response'}: ${item.message}`).join('；') || '-',
                },
                {
                  title: '输出 Hash',
                  dataIndex: 'outputHash',
                  width: 145,
                  render: (value?: string) => value ? <Typography.Text code>{shortTechnicalId(value)}</Typography.Text> : '-',
                },
                {
                  title: '网络重试',
                  dataIndex: 'networkRetryCount',
                  width: 90,
                },
              ]}
            />
            <Table<ExtractedExpenseItem>
              rowKey={(_, index) => String(index)}
              size="small"
              pagination={false}
              dataSource={extraction.result.items}
              locale={{ emptyText: '未提取到票据明细' }}
              columns={[
                { title: '明细', dataIndex: 'description' },
                { title: '数量', dataIndex: 'quantity' },
                { title: '单价', dataIndex: 'unitPrice' },
                { title: '金额', dataIndex: 'amount' },
              ]}
            />
          </Space>
        )}
      </div>
    </div>
  );
}
