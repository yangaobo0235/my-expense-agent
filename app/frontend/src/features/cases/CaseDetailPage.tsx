import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Card,
  Collapse,
  Descriptions,
  Empty,
  Form,
  Input,
  InputNumber,
  List,
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
import { CaseEvidence, EvidenceChatResponse, ExpenseCase, ExpenseDocumentDetail, ExpenseWorkflowEvent, ExtractedExpenseItem, ReviewReport } from '../../api/contracts';
import { consumeCaseEvents } from '../../api/event-client';
import { analyzeCase, askEvidenceChat, deleteCase, generateReviewReport, getCase, getCaseEvidence, getCaseObservability, getReviewReport, listCaseDocuments, settleExpenseCase, updateCase } from '../../api/expense-api';
import { CaseObservabilityPanel } from '../../components/CaseObservabilityPanel';
import { RiskBadge } from '../../components/RiskBadge';
import { StatusBadge } from '../../components/StatusBadge';
import { useAuthStore } from '../auth/auth-store';
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

interface CaseEditForm {
  applicantName: string;
  departmentCode: string;
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
  const reviewReportQuery = useQuery({
    queryKey: ['review-report', caseId],
    queryFn: () => getReviewReport(caseId),
    enabled: Boolean(caseId),
    retry: false,
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
      message.success(user?.roles.includes('FINANCE_ADMIN') ? '案例已删除' : '草稿已删除');
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
  const reviewReportMutation = useMutation({
    mutationFn: () => generateReviewReport(caseId),
    onSuccess: () => {
      message.success('审核报告已生成');
      void queryClient.invalidateQueries({ queryKey: ['review-report', caseId] });
      void queryClient.invalidateQueries({ queryKey: ['model-call-summary'] });
      void queryClient.invalidateQueries({ queryKey: ['model-calls'] });
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
      void queryClient.invalidateQueries({ queryKey: ['observable-runs'] });
      void queryClient.invalidateQueries({ queryKey: ['model-call-summary'] });
      void queryClient.invalidateQueries({ queryKey: ['model-calls'] });
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
      void queryClient.invalidateQueries({ queryKey: ['observable-runs'] });
    },
  });
  const settlementMutation = useMutation({
    mutationFn: () => settleExpenseCase(caseId),
    onSuccess: (result) => {
      message.success(`结算完成，付款状态：${result.status ?? '已提交'}`);
      void queryClient.invalidateQueries({ queryKey: ['case-evidence', caseId] });
      void queryClient.invalidateQueries({ queryKey: ['observable-runs'] });
    },
    onError: (error) => {
      const fallback = '结算提交失败，请稍后重试。';
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

  const canEditDraft =
    Boolean(user?.roles.includes('EMPLOYEE')) && query.data?.status === 'DRAFT';
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
      title: adminDelete ? '删除该案例？' : '删除草稿案例？',
      content: adminDelete
        ? '删除后该案例和相关票据、处理记录将不再显示。请确认这是一条误建或测试记录。'
        : '删除后该草稿不会再出现在列表中。',
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: () => deleteMutation.mutateAsync(),
    });
  }

  if (query.isLoading) return <Spin />;
  if (!query.data) return <Empty description="案例不存在或无权访问" />;
  const expenseCase = query.data;
  const settlementCompleted = evidenceQuery.data?.toolCalls.some(
    (call) =>
      call.writeOperation &&
      call.status === 'SUCCEEDED' &&
      ['submit_reimbursement', 'submit_payment'].includes(call.toolName),
  );
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
            {settlementCompleted && <Tag color="green">结算已提交</Tag>}
            {canEditDraft && (
              <>
                <Button onClick={() => openDraftEditor(expenseCase)}>修改草稿</Button>
              </>
            )}
            {canDeleteCase && (
              <Button danger loading={deleteMutation.isPending} onClick={confirmDeleteDraft}>
                {user?.roles.includes('FINANCE_ADMIN') ? '删除案例' : '删除草稿'}
              </Button>
            )}
            {canSettle && (
              <Button
                type="primary"
                loading={settlementMutation.isPending}
                onClick={() => settlementMutation.mutate()}
              >
                发起结算
              </Button>
            )}
            {user?.roles.includes('EMPLOYEE') &&
              expenseCase.status === 'UPLOADED' && (
                <Button
                  loading={extractionMutation.isPending}
                  onClick={() => extractionMutation.mutate()}
                >
                  重新识别票据
                </Button>
              )}
            {user?.roles.includes('EMPLOYEE') &&
              expenseCase.status === 'EXTRACTED' && (
                <WorkflowLauncher caseId={caseId} />
              )}
          </Space>
        </div>
        {canEditDraft && (
          <Alert
            type="info"
            showIcon
            message="该案例仍是草稿，可以修改或删除"
            description="上传票据后案例会进入处理链路，后续更正应通过补充材料和人工审核完成。"
          />
        )}
        {streamError && <Alert type="warning" showIcon message="实时连接暂时不可用" description={streamError} />}
        <div className="case-workbench-grid">
          <StageRail stages={stages} activeStage={activeStage} onSelect={setActiveStage} />
          <Card className="stage-workspace" title={stages.find((stage) => stage.key === activeStage)?.title ?? '案例详情'}>
            <StageWorkspace
              stage={activeStage}
              expenseCase={expenseCase}
              documents={documentsQuery.data ?? []}
              documentsLoading={documentsQuery.isLoading}
              evidence={evidenceQuery.data}
              evidenceLoading={evidenceQuery.isLoading}
              events={events}
              report={reviewReportQuery.data}
              reportLoading={reviewReportQuery.isLoading || reviewReportMutation.isPending}
              onGenerateReport={() => reviewReportMutation.mutate()}
              caseId={caseId}
              canSettle={Boolean(canSettle)}
              settling={settlementMutation.isPending}
              onSettle={() => settlementMutation.mutate()}
              settlementCompleted={Boolean(settlementCompleted)}
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
          />
        </div>
      </Space>
      <Modal
        title="修改草稿案例"
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
            name="departmentCode"
            label="部门编码"
            rules={[{ required: true, message: '请输入部门编码' }]}
          >
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item
            name="title"
            label="费用标题"
            rules={[{ required: true, message: '请输入费用标题' }]}
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
    departmentCode: expenseCase.departmentCode,
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
    <Card className="stage-rail" title="案例阶段">
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
  report,
  reportLoading,
  onGenerateReport,
  caseId,
  canSettle,
  settling,
  onSettle,
  settlementCompleted,
}: {
  stage: CaseStageKey;
  expenseCase: ExpenseCase;
  documents: ExpenseDocumentDetail[];
  documentsLoading: boolean;
  evidence?: CaseEvidence;
  evidenceLoading: boolean;
  events: ExpenseWorkflowEvent[];
  report?: ReviewReport;
  reportLoading: boolean;
  onGenerateReport: () => void;
  caseId: string;
  canSettle: boolean;
  settling: boolean;
  onSettle: () => void;
  settlementCompleted: boolean;
}) {
  if (stage === 'summary') {
    return (
      <Space orientation="vertical" size="middle" className="page-stack">
        <Descriptions bordered column={2}>
          <Descriptions.Item label="案例编号">{expenseCase.caseNumber}</Descriptions.Item>
          <Descriptions.Item label="状态"><StatusBadge status={expenseCase.status} /></Descriptions.Item>
          <Descriptions.Item label="申请人">{expenseCase.applicantName}</Descriptions.Item>
          <Descriptions.Item label="部门">{expenseCase.departmentCode}</Descriptions.Item>
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
    return <EvidenceSourceBoard evidence={evidence} />;
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
      <Tabs
        items={[
          {
            key: 'report',
            label: '审核报告',
            children: (
              <ReviewReportPanel
                report={report}
                loading={reportLoading}
                onGenerate={onGenerateReport}
              />
            ),
          },
          {
            key: 'chat',
            label: '询问依据',
            children: <EvidenceChatPanel caseId={caseId} />,
          },
        ]}
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
}: {
  diagnosis?: CaseDiagnosis;
  loading: boolean;
  caseId: string;
  extracting: boolean;
  onRetryExtraction: () => void;
  observability?: Parameters<typeof CaseObservabilityPanel>[0]['observability'];
  observabilityLoading: boolean;
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
              message={diagnosis.title}
              description={diagnosis.description}
            />
            {diagnosis.retryKind === 'extraction' && (
              <Button type="primary" loading={extracting} onClick={onRetryExtraction}>
                {diagnosis.actionLabel ?? '重新识别票据'}
              </Button>
            )}
            {diagnosis.retryKind === 'workflow' && diagnosis.requestId && (
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

export function ReviewReportPanel({
  report,
  loading,
  onGenerate,
}: {
  report?: ReviewReport;
  loading: boolean;
  onGenerate: () => void;
}) {
  if (!report) {
    return (
      <Space orientation="vertical">
        <Empty description="尚未生成审核报告" />
        <Button type="primary" loading={loading} onClick={onGenerate}>生成审核报告</Button>
      </Space>
    );
  }
  return (
    <Space orientation="vertical" size="middle" className="page-stack">
      <Space>
        <Button loading={loading} onClick={onGenerate}>重新生成</Button>
        <Tag color="blue">辅助建议</Tag>
        <Typography.Text type="secondary">{new Date(report.createdAt).toLocaleString('zh-CN')}</Typography.Text>
      </Space>
      <Alert
        type="info"
        showIcon
        message="审核建议"
        description={businessText(report.summary) || '请结合票据、制度和历史记录完成审核判断。'}
      />
      <div className="review-report-grid">
        <Card size="small" title="为什么需要关注">
          <BusinessTextList
            items={report.riskExplanation}
            emptyText="暂无明确风险说明，请结合票据和制度人工判断。"
          />
        </Card>
        <Card size="small" title="审核时重点看什么">
          <BusinessTextList
            items={report.humanReviewHints}
            emptyText="暂无额外关注点。"
          />
        </Card>
        <Card size="small" title="当前还缺什么依据">
          <BusinessTextList
            items={report.limitations}
            emptyText="暂无明显缺失依据。"
          />
        </Card>
      </div>
      <Table
        rowKey={(row) => `${row.policyCode}-${row.chunkId}`}
        size="small"
        pagination={false}
        dataSource={report.policyCitations}
        locale={{ emptyText: '暂无明确制度引用，请人工核对适用制度。' }}
        columns={[
          { title: '制度', dataIndex: 'policyCode', render: (value: string) => businessText(value) || '-' },
          { title: '章节', dataIndex: 'section', render: (value: string) => businessText(value) || '-' },
          {
            title: '查看编号',
            dataIndex: 'chunkId',
            render: (value: string) => (
              <Typography.Text copyable type="secondary">
                {shortTechnicalId(value)}
              </Typography.Text>
            ),
          },
        ]}
      />
      <Collapse
        ghost
        items={[
          {
            key: 'raw-report-note',
            label: '管理员排查信息',
            children: (
              <Typography.Text type="secondary">
                以上内容已转成业务表述；完整生成记录可在处理记录页面查看。
              </Typography.Text>
            ),
          },
        ]}
      />
    </Space>
  );
}

function BusinessTextList({ items, emptyText }: { items: string[]; emptyText: string }) {
  const visibleItems = items.map(businessText).filter(Boolean);
  if (visibleItems.length === 0) {
    return <Typography.Text type="secondary">{emptyText}</Typography.Text>;
  }
  return (
    <List
      size="small"
      split={false}
      dataSource={visibleItems}
      renderItem={(item) => (
        <List.Item className="business-report-item">
          <Typography.Text>{item}</Typography.Text>
        </List.Item>
      )}
    />
  );
}

function businessText(value?: string) {
  if (!value) return '';
  return value
    .replace(/\s*（?\s*ID[:：]\s*[0-9a-f-]{20,}\s*）?/gi, '')
    .replace(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/gi, '该案例')
    .replaceAll('claimedAmount', '申报金额')
    .replaceAll('score', '风险分')
    .replaceAll('HIGH', '高')
    .replaceAll('MEDIUM', '中')
    .replaceAll('LOW', '低')
    .replaceAll('POLICY_LIMIT_EXCEEDED', '金额可能超过适用标准')
    .replaceAll('DEPENDENCY_UNAVAILABLE', '外部数据暂时不可用')
    .replaceAll('POLICY_RETRIEVAL', '制度依据核对')
    .replaceAll('RISK_ASSESSMENT', '风险评估')
    .replaceAll('RISK_ROUTING', '审核分配')
    .replaceAll('MCP_CONTEXT_AGENT', '员工信息核对')
    .replaceAll('POLICY_RAG_AGENT', '制度依据核对')
    .replaceAll('MCP', '外部数据')
    .replaceAll('policyFindings', '制度依据')
    .replaceAll('citations', '引用依据')
    .replaceAll('evidence: {}', '暂无可展示依据')
    .replace(/\bSHA256\b/gi, '文件指纹')
    .replace(/\bCN\b/g, '中国区')
    .replace(/\bG(\d)\b/g, '$1级')
    .replace(/\s+/g, ' ')
    .trim();
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
    apiMessage.includes('submit_reimbursement') ||
    apiMessage.includes('submit_payment') ||
    apiMessage.includes('NON_RETRYABLE') ||
    apiMessage.includes('DEPENDENCY')
  ) {
    return '结算服务暂时不可用，本案例的审核结果已保留。请稍后重试结算，或联系管理员检查结算服务。';
  }
  return apiMessage;
}

function EvidenceChatPanel({ caseId }: { caseId: string }) {
  const [question, setQuestion] = useState('');
  const [history, setHistory] = useState<Array<{ question: string; response: EvidenceChatResponse }>>([]);
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: (value: string) => askEvidenceChat(caseId, value),
    onSuccess: (response, asked) => {
      setHistory((current) => [...current, { question: asked, response }]);
      setQuestion('');
      void queryClient.invalidateQueries({ queryKey: ['model-call-summary'] });
      void queryClient.invalidateQueries({ queryKey: ['model-calls'] });
    },
    onError: () => message.error('查询依据失败，请稍后重试。'),
  });
  return (
    <Space orientation="vertical" size="middle" className="page-stack">
      <Input.Search
        value={question}
        onChange={(event) => setQuestion(event.target.value)}
        onSearch={(value) => value.trim() && mutation.mutate(value.trim())}
        loading={mutation.isPending}
        enterButton="提问"
        placeholder="例如：为什么这个案例需要人工审核？"
      />
      <List
        dataSource={history}
        locale={{ emptyText: <Empty description="暂无问答记录" /> }}
        renderItem={(item) => (
          <List.Item>
            <Space orientation="vertical" className="page-stack">
              <Typography.Text strong>{item.question}</Typography.Text>
              <Typography.Text>{item.response.answer}</Typography.Text>
              <Space wrap>
                {item.response.citations.map((citation) => (
                  <Tag key={`${citation.type}-${citation.id}`} color="blue">
                    {citation.type}: {citation.id}
                  </Tag>
                ))}
              </Space>
            </Space>
          </List.Item>
        )}
      />
    </Space>
  );
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
                message="需要人工关注"
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
            <Table<ExtractedExpenseItem>
              rowKey={(_, index) => String(index)}
              size="small"
              pagination={false}
              dataSource={extraction.result.items}
              locale={{ emptyText: '未提取到费用明细' }}
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
