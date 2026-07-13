import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Button, Card, Descriptions, Form, Input, InputNumber, List, Modal, Radio, Space, Tag, Typography, message } from 'antd';
import axios from 'axios';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { decideReview, listReviewTasks } from '../../api/expense-api';
import { ReviewTask } from '../../api/contracts';

type Action = 'approve' | 'reject' | 'request-more-info';

export function ReviewQueuePage() {
  const queryClient = useQueryClient();
  const [selected, setSelected] = useState<ReviewTask>();
  const [form] = Form.useForm();
  const query = useQuery({ queryKey: ['review-tasks'], queryFn: listReviewTasks });
  const mutation = useMutation({
    mutationFn: ({ action, values }: { action: Action; values: { approvedAmount?: number; comment: string } }) =>
      decideReview(selected!, action, values),
    onSuccess: () => {
      message.success('审核动作已提交');
      setSelected(undefined);
      form.resetFields();
      void queryClient.invalidateQueries({ queryKey: ['review-tasks'] });
    },
    onError: (error) => {
      const fallback = '审核提交失败，请刷新任务后重试。';
      const apiMessage = axios.isAxiosError(error)
        ? (error.response?.data as { message?: string } | undefined)?.message
        : undefined;
      message.error(apiMessage ?? fallback);
    },
  });
  return (
    <Space orientation="vertical" size="large" className="page-stack">
      <div className="page-heading">
        <div>
          <Typography.Title level={2}>待我处理</Typography.Title>
          <Typography.Text type="secondary">按风险路由、SLA 和缺失证据组织人工审核任务。</Typography.Text>
        </div>
      </div>
      <Card>
        <Alert
          className="form-alert"
          type="info"
          showIcon
          title="优先处理需要人工判断的经费申请"
          description="列表已按系统判断生成待办。预算超标、疑似重复、依据缺失的申请建议进入工作台查看票据、制度和风险说明后再决定。"
        />
        <List
          loading={query.isLoading}
          dataSource={query.data}
          locale={{ emptyText: '当前没有待处理任务' }}
          renderItem={(task) => (
            <List.Item
              className="review-queue-item"
              actions={[
                <Button key="detail" type="primary">
                  <Link to={`/reviews/${task.id}`}>进入工作台</Link>
                </Button>,
                <Button key="quick" type="text" onClick={() => setSelected(task)}>快速处理</Button>,
              ]}
            >
              <List.Item.Meta
                title={
                  <Space wrap>
                    <Typography.Text strong>申请 {shortCaseId(task.caseId)}</Typography.Text>
                    <Tag color={task.status === 'MORE_INFO' ? 'orange' : 'blue'}>{taskStatusLabel(task.status)}</Tag>
                    <Tag color={queueColor(task.routingQueue)}>{queueLabel(task.routingQueue)}</Tag>
                    {task.slaHours && <Tag color="orange">{task.slaHours} 小时内处理</Tag>}
                  </Space>
                }
                description={
                  <Space orientation="vertical" size={4}>
                    <Typography.Text>
                      {businessMessage(task.userFacingMessage) || '该申请需要人工复核后决定。'}
                    </Typography.Text>
                    <Typography.Text type="secondary">
                      关注点：{reasonLabels(task.reasonCodes).join('、') || '系统建议人工确认'}
                    </Typography.Text>
                    {(task.requiredEvidence ?? []).length > 0 && (
                      <Space wrap>
                        {(task.requiredEvidence ?? []).map((item) => (
                          <Tag key={item}>{requiredEvidenceLabel(item)}</Tag>
                        ))}
                      </Space>
                    )}
                  </Space>
                }
              />
            </List.Item>
          )}
        />
      </Card>
      <Modal
        title="提交人工审核动作"
        open={Boolean(selected)}
        onCancel={() => setSelected(undefined)}
        okText="确认提交"
        confirmLoading={mutation.isPending}
        onOk={() => form.validateFields().then((values) => mutation.mutate({ action: values.action, values }))}
      >
        {selected && (
          <Descriptions size="small" column={1} className="compact-descriptions">
            <Descriptions.Item label="处理建议">{routingActionLabel(selected.routingAction)}</Descriptions.Item>
            <Descriptions.Item label="任务类型">{queueLabel(selected.routingQueue)}</Descriptions.Item>
            <Descriptions.Item label="需要补充">{(selected.requiredEvidence ?? []).map(requiredEvidenceLabel).join('、') || '暂无'}</Descriptions.Item>
            <Descriptions.Item label="辅助信息">
              {selected.debateAssistEnabled ? '已准备正反证据供参考' : '按常规审核处理'}
            </Descriptions.Item>
          </Descriptions>
        )}
        <Form form={form} layout="vertical" initialValues={{ action: 'approve' }}>
          <Form.Item name="action" label="审核动作" rules={[{ required: true }]}>
            <Radio.Group>
              <Radio value="approve">批准</Radio>
              <Radio value="reject">拒绝</Radio>
              <Radio value="request-more-info">要求补充材料</Radio>
            </Radio.Group>
          </Form.Item>
          <Form.Item name="approvedAmount" label="批准金额">
            <InputNumber min={0} precision={2} className="full-width" />
          </Form.Item>
          <Form.Item name="comment" label="审核说明" rules={[{ required: true, message: '请填写审核说明' }]}>
            <Input.TextArea rows={4} maxLength={2000} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

function shortCaseId(caseId: string) {
  return caseId.length > 12 ? `${caseId.slice(0, 8)}...${caseId.slice(-4)}` : caseId;
}

function taskStatusLabel(status: string) {
  const labels: Record<string, string> = {
    OPEN: '待处理',
    MORE_INFO: '待补充',
    APPROVED: '已批准',
    REJECTED: '已拒绝',
  };
  return labels[status] ?? '待处理';
}

function queueLabel(queue?: string) {
  const labels: Record<string, string> = {
    STANDARD_REVIEW: '常规审核',
    DEPENDENCY_RECOVERY: '依据补全',
    HIGH_RISK_REVIEW: '高风险复核',
    DUPLICATE_REVIEW: '疑似重复复核',
  };
  return queue ? labels[queue] ?? '人工复核' : '人工复核';
}

function queueColor(queue?: string) {
  if (queue === 'DEPENDENCY_RECOVERY') return 'orange';
  if (queue === 'HIGH_RISK_REVIEW') return 'red';
  if (queue === 'DUPLICATE_REVIEW') return 'purple';
  return 'blue';
}

function reasonLabels(codes: string[]) {
  return codes.map(reasonLabel).filter(Boolean);
}

function reasonLabel(code: string) {
  const labels: Record<string, string> = {
    POLICY_LIMIT_EXCEEDED: '金额可能超过适用标准',
    DEPENDENCY_UNAVAILABLE: '部分外部依据暂时缺失',
    DUPLICATE_DOCUMENT: '疑似重复报销',
    MISSING_REQUIRED_DOCUMENT: '缺少必要凭证',
    FORBIDDEN_EXPENSE_ITEM: '可能包含不可报销项目',
    DATE_ANOMALY: '支出日期异常',
    SELLER_ANOMALY: '收款方需要确认',
  };
  return labels[code] ?? '';
}

function requiredEvidenceLabel(value: string) {
  const labels: Record<string, string> = {
    DEPENDENCY_FAILURE_DETAIL: '依赖失败详情',
    CACHED_EVIDENCE: '缓存依据',
    ORIGINAL_DOCUMENT: '原始票据',
    POLICY_CITATION: '制度依据',
    PAYMENT_PROOF: '支付或垫付证明',
  };
  return labels[value] ?? value.replaceAll('_', ' ').toLowerCase();
}

function routingActionLabel(value?: string) {
  const labels: Record<string, string> = {
    REVIEW: '请审核员复核后决定',
    MORE_INFO: '建议要求申请人补充材料',
    APPROVE: '可考虑批准',
    REJECT: '可考虑拒绝',
  };
  return value ? labels[value] ?? '请审核员复核后决定' : '请审核员复核后决定';
}

function businessMessage(value?: string) {
  if (!value) return '';
  return value
    .replaceAll('DEPENDENCY_UNAVAILABLE', '部分外部依据暂时缺失')
    .replaceAll('POLICY_LIMIT_EXCEEDED', '金额可能超过适用标准')
    .replaceAll('SLA', '处理时限')
    .replaceAll('MCP', '外部数据')
    .replace(/\s+/g, ' ')
    .trim();
}
