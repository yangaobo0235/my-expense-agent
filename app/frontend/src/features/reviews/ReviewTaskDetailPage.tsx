import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Card,
  Collapse,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Modal,
  Radio,
  Space,
  Spin,
  Tag,
  Typography,
  message,
} from 'antd';
import axios from 'axios';
import { useMemo } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  decideReview,
  generateReviewReport,
  getCaseObservability,
  getCase,
  getCaseEvidence,
  getMoreInfoSuggestion,
  getReviewReport,
  getReviewTask,
  grafanaTraceUrl,
  listCaseDocuments,
} from '../../api/expense-api';
import { CaseObservabilityPanel } from '../../components/CaseObservabilityPanel';
import { RiskBadge } from '../../components/RiskBadge';
import { RiskEvidenceBoard } from '../../components/RiskEvidenceBoard';
import { StatusBadge } from '../../components/StatusBadge';
import { hasAnyRole, useAuthStore } from '../auth/auth-store';
import { ReviewReportPanel } from '../cases/CaseDetailPage';
import { DocumentSummaryPanel, EvidenceSourceBoard, RiskExplanationPanel } from '../cases/workbench-panels';

type Action = 'approve' | 'reject' | 'request-more-info';

export function ReviewTaskDetailPage() {
  const { taskId = '' } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const user = useAuthStore((state) => state.user);
  const [form] = Form.useForm<{ action: Action; approvedAmount?: number; comment: string }>();
  const taskQuery = useQuery({
    queryKey: ['review-task', taskId],
    queryFn: () => getReviewTask(taskId),
    enabled: Boolean(taskId),
  });
  const caseId = taskQuery.data?.caseId;
  const caseQuery = useQuery({
    queryKey: ['case', caseId],
    queryFn: () => getCase(caseId!),
    enabled: Boolean(caseId),
  });
  const evidenceQuery = useQuery({
    queryKey: ['case-evidence', caseId],
    queryFn: () => getCaseEvidence(caseId!),
    enabled: Boolean(caseId),
  });
  const documentsQuery = useQuery({
    queryKey: ['case-documents', caseId],
    queryFn: () => listCaseDocuments(caseId!),
    enabled: Boolean(caseId),
  });
  const reportQuery = useQuery({
    queryKey: ['review-report', caseId],
    queryFn: () => getReviewReport(caseId!),
    enabled: Boolean(caseId),
    retry: false,
  });
  const observabilityQuery = useQuery({
    queryKey: ['case-observability', caseId],
    queryFn: () => getCaseObservability(caseId!),
    enabled: Boolean(caseId),
  });
  const reportMutation = useMutation({
    mutationFn: () => generateReviewReport(caseId!),
    onSuccess: () => {
      message.success('审核报告已生成');
      void queryClient.invalidateQueries({ queryKey: ['review-report', caseId] });
    },
  });
  const suggestionMutation = useMutation({
    mutationFn: () => getMoreInfoSuggestion(taskId),
    onSuccess: (suggestion) => {
      form.setFieldsValue({
        action: 'request-more-info',
        comment: suggestion.userFacingMessage,
      });
      message.success('已生成补充材料建议');
    },
  });
  const decision = useMutation({
    mutationFn: ({ action, values }: { action: Action; values: { approvedAmount?: number; comment: string } }) =>
      decideReview(taskQuery.data!, action, values),
    onSuccess: async (result) => {
      message.success('审核动作已提交');
      await queryClient.invalidateQueries({ queryKey: ['review-tasks'] });
      await queryClient.invalidateQueries({ queryKey: ['review-task', taskId] });
      await queryClient.invalidateQueries({ queryKey: ['case', result.id] });
      navigate(`/cases/${result.id}`);
    },
    onError: (error) => {
      const apiMessage = axios.isAxiosError(error)
        ? (error.response?.data as { message?: string } | undefined)?.message
        : undefined;
      message.error(apiMessage ?? '审核提交失败，请刷新任务后重试。');
    },
  });

  const canHandle = useMemo(() => {
    if (!taskQuery.data) return false;
    return hasAnyRole(user?.roles, ['FINANCE_ADMIN'])
      || hasAnyRole(user?.roles, [taskQuery.data.assigneeRole as 'ADVISOR' | 'COLLEGE_REVIEWER']);
  }, [taskQuery.data, user?.roles]);

  if (taskQuery.isLoading || caseQuery.isLoading) return <Spin />;
  if (!taskQuery.data || !caseQuery.data) return <Alert type="warning" showIcon title="审核任务不存在或无权访问" />;
  const task = taskQuery.data;
  const expenseCase = caseQuery.data;
  const traceUrl = evidenceQuery.data?.run?.traceId
    ? grafanaTraceUrl(evidenceQuery.data.run.traceId)
    : undefined;

  return (
    <Space orientation="vertical" size="large" className="page-stack">
      <div className="page-heading">
        <div>
          <Typography.Title level={2}>审核任务详情</Typography.Title>
          <Typography.Text type="secondary">
            <Link to={`/cases/${task.caseId}`}>{expenseCase.caseNumber}</Link> · {expenseCase.title}
          </Typography.Text>
        </div>
        <Space>
          <StatusBadge status={expenseCase.status} />
          <RiskBadge level={expenseCase.riskLevel} score={expenseCase.riskScore} />
          {traceUrl && <Button href={traceUrl} target="_blank">Tempo Trace</Button>}
        </Space>
      </div>

      {!canHandle && (
        <Alert
          type="error"
          showIcon
          title="当前账号不能处理该任务"
          description={`任务要求角色：${task.assigneeRole ?? 'COLLEGE_REVIEWER'}`}
        />
      )}

      <div className="review-decision-grid">
        <Space orientation="vertical" size="middle" className="page-stack review-context-column">
          <Card title="审核上下文">
            <Descriptions bordered size="small" column={2}>
              <Descriptions.Item label="任务状态"><Tag>{task.status}</Tag></Descriptions.Item>
              <Descriptions.Item label="队列">{task.routingQueue ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="动作">{task.routingAction ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="处理角色">{task.assigneeRole ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="SLA">{task.slaHours ?? '-'} 小时</Descriptions.Item>
              <Descriptions.Item label="到期时间">{task.dueAt ? new Date(task.dueAt).toLocaleString('zh-CN') : '-'}</Descriptions.Item>
              <Descriptions.Item label="申请人">{expenseCase.applicantName}</Descriptions.Item>
              <Descriptions.Item label="金额">{expenseCase.claimedAmount} {expenseCase.currency}</Descriptions.Item>
            </Descriptions>
          </Card>
          <Card title="风险解释">
            <RiskExplanationPanel evidence={evidenceQuery.data} expenseCase={expenseCase} />
          </Card>
          <Card title="正反证据辅助">
            <RiskEvidenceBoard evidence={evidenceQuery.data} task={task} />
          </Card>
        </Space>
        <Space orientation="vertical" size="middle" className="page-stack review-decision-column">
          <Card title="提交人工审核动作">
            <Alert
              className="form-alert"
              type="info"
              showIcon
              title="人工决定会写入审计日志，并成为后续入账的唯一依据。"
              description="Agent、模型和制度检索只作为证据，不能直接改变审批结论。"
            />
            <Form form={form} layout="vertical" initialValues={{ action: 'approve' }}>
              <Form.Item name="action" label="审核动作" rules={[{ required: true }]}>
                <Radio.Group disabled={!canHandle}>
                  <Radio value="approve">批准</Radio>
                  <Radio value="reject">拒绝</Radio>
                  <Radio value="request-more-info">要求补充材料</Radio>
                </Radio.Group>
              </Form.Item>
              <Form.Item name="approvedAmount" label="批准金额">
                <InputNumber min={0} precision={2} className="full-width" disabled={!canHandle} />
              </Form.Item>
              <Form.Item name="comment" label="审核说明" rules={[{ required: true, message: '请填写审核说明' }]}>
                <Input.TextArea rows={5} maxLength={2000} showCount disabled={!canHandle} />
              </Form.Item>
              <Button
                block
                style={{ marginBottom: 12 }}
                disabled={!canHandle}
                loading={suggestionMutation.isPending}
                onClick={() => suggestionMutation.mutate()}
              >
                生成补充材料建议
              </Button>
              <Button
                type="primary"
                block
                disabled={!canHandle}
                loading={decision.isPending}
                onClick={() =>
                  form.validateFields().then((values) =>
                    Modal.confirm({
                      title: '确认提交审核动作',
                      content: '该动作会写入审计日志并更新申请状态。',
                      onOk: () => decision.mutate({ action: values.action, values }),
                    }),
                  )
                }
              >
                提交
              </Button>
            </Form>
          </Card>
          <Card title="智能审核摘要">
            <ReviewReportPanel
              report={reportQuery.data}
              loading={reportQuery.isLoading || reportMutation.isPending}
              onGenerate={() => reportMutation.mutate()}
            />
          </Card>
        </Space>
        <Space orientation="vertical" size="middle" className="page-stack review-evidence-column">
          <Card title="证据来源">
            {evidenceQuery.isLoading ? <Spin /> : <EvidenceSourceBoard evidence={evidenceQuery.data} />}
          </Card>
          <Card title={`票据 ${documentsQuery.data?.length ?? 0}`}>
            <DocumentSummaryPanel documents={documentsQuery.data ?? []} />
          </Card>
          <Card title="链路与审计">
            <Collapse
              ghost
              items={[
                {
                  key: 'observability',
                  label: '查看模型调用、Trace 与审计日志',
                  children: (
                    <CaseObservabilityPanel
                      observability={observabilityQuery.data}
                      loading={observabilityQuery.isLoading}
                    />
                  ),
                },
              ]}
            />
          </Card>
        </Space>
      </div>
    </Space>
  );
}
