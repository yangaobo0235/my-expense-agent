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
  Modal,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd';
import { useMemo, useState } from 'react';
import {
  activatePrompt,
  approvePromptChange,
  createPrompt,
  getPromptReview,
  listPromptChanges,
  listPrompts,
  rejectPromptChange,
  submitPrompt,
  updatePrompt,
} from '../../api/expense-api';
import { PromptChangeRequest, PromptTemplate, PromptTemplateInput, PromptVersionReview } from '../../api/contracts';
import { hasAnyRole, useAuthStore } from '../auth/auth-store';

const promptKeys = ['receipt-extraction', 'review-report', 'evidence-chat', 'more-info-suggestion'];

const statusColor: Record<PromptTemplate['status'], string> = {
  DRAFT: 'gold',
  IN_REVIEW: 'blue',
  APPROVED: 'cyan',
  ACTIVE: 'green',
  REJECTED: 'red',
  DEPRECATED: 'default',
};

export function PromptGovernancePage() {
  const [selected, setSelected] = useState<PromptTemplate>();
  const [form] = Form.useForm<PromptTemplateInput>();
  const queryClient = useQueryClient();
  const user = useAuthStore((state) => state.user);
  const prompts = useQuery({ queryKey: ['prompts'], queryFn: () => listPrompts() });
  const changes = useQuery({
    queryKey: ['prompt-changes', selected?.id],
    queryFn: () => listPromptChanges(selected!.id),
    enabled: Boolean(selected),
  });
  const review = useQuery({
    queryKey: ['prompt-review', selected?.id],
    queryFn: () => getPromptReview(selected!.id),
    enabled: Boolean(selected),
  });

  const refresh = async () => {
    await queryClient.invalidateQueries({ queryKey: ['prompts'] });
    await queryClient.invalidateQueries({ queryKey: ['prompt-changes'] });
    await queryClient.invalidateQueries({ queryKey: ['prompt-review'] });
  };

  const createOrUpdate = useMutation({
    mutationFn: async (input: PromptTemplateInput) => {
      if (selected && ['DRAFT', 'REJECTED'].includes(selected.status)) {
        return updatePrompt(selected.id, input);
      }
      return createPrompt(input);
    },
    onSuccess: async (template) => {
      message.success('Prompt 草稿已保存');
      setSelected(template);
      await refresh();
    },
  });
  const submit = useMutation({
    mutationFn: (summary: string) => submitPrompt(selected!.id, summary),
    onSuccess: async () => {
      message.success('已提交审批');
      await refresh();
    },
  });
  const activate = useMutation({
    mutationFn: () => activatePrompt(selected!.id),
    onSuccess: async () => {
      message.success('已激活 Prompt 版本');
      await refresh();
    },
  });
  const approve = useMutation({
    mutationFn: ({ id, comment }: { id: string; comment: string }) => approvePromptChange(id, comment),
    onSuccess: async () => {
      message.success('审批已通过');
      await refresh();
    },
  });
  const reject = useMutation({
    mutationFn: ({ id, comment }: { id: string; comment: string }) => rejectPromptChange(id, comment),
    onSuccess: async () => {
      message.success('审批已拒绝');
      await refresh();
    },
  });

  const activeByKey = useMemo(
    () =>
      Object.fromEntries(
        (prompts.data ?? [])
          .filter((prompt) => prompt.status === 'ACTIVE')
          .map((prompt) => [prompt.promptKey, prompt.version]),
      ),
    [prompts.data],
  );

  const editDisabled = selected ? !['DRAFT', 'REJECTED'].includes(selected.status) : false;
  const canAuthorPrompt = hasAnyRole(user?.roles, ['PROMPT_AUTHOR', 'FINANCE_ADMIN']);
  const canReviewPrompt = hasAnyRole(user?.roles, ['PROMPT_REVIEWER', 'FINANCE_ADMIN']);
  const canPublishPrompt = hasAnyRole(user?.roles, ['PROMPT_PUBLISHER', 'FINANCE_ADMIN']);

  function selectTemplate(template: PromptTemplate) {
    setSelected(template);
    form.setFieldsValue({
      promptKey: template.promptKey,
      version: template.version,
      name: template.name,
      description: template.description,
      content: template.content,
      variableSchema: template.variableSchema,
      modelName: template.modelName,
      temperature: template.temperature,
      maxTokens: template.maxTokens,
    });
  }

  function newDraft() {
    setSelected(undefined);
    form.setFieldsValue({
      promptKey: 'receipt-extraction',
      version: '',
      name: '',
      description: '',
      content: '',
      variableSchema: {},
      modelName: 'qwen-plus',
      temperature: 0,
      maxTokens: 2048,
    });
  }

  function pendingChange(): PromptChangeRequest | undefined {
    return changes.data?.find((item) => item.status === 'PENDING');
  }

  return (
    <Space orientation="vertical" size="large" className="page-stack">
      <div className="page-heading">
        <div>
          <Typography.Title level={2}>Prompt 审批平台</Typography.Title>
          <Typography.Text type="secondary">
            管理模型 Prompt 的草稿、审批、激活和回滚版本，所有运行时调用只读取 Active 版本。
          </Typography.Text>
        </div>
        <Button type="primary" onClick={newDraft} disabled={!canAuthorPrompt}>新建草稿</Button>
      </div>

      <Card title="Active 版本">
        <Space wrap>
          {promptKeys.map((key) => (
            <Tag key={key} color={activeByKey[key] ? 'green' : 'red'}>
              {key}: {activeByKey[key] ?? '未初始化'}
            </Tag>
          ))}
        </Space>
      </Card>

      <div className="two-column-grid">
        <Card title="版本列表">
          <Table<PromptTemplate>
            rowKey="id"
            loading={prompts.isLoading}
            dataSource={prompts.data}
            pagination={{ pageSize: 8 }}
            onRow={(row) => ({ onClick: () => selectTemplate(row) })}
            columns={[
              {
                title: 'Prompt',
                key: 'prompt',
                render: (_, row) => (
                  <Space orientation="vertical" size={0}>
                    <Typography.Text strong>{row.name}</Typography.Text>
                    <Typography.Text type="secondary">{row.promptKey}</Typography.Text>
                  </Space>
                ),
              },
              { title: '版本', dataIndex: 'version', key: 'version' },
              {
                title: '状态',
                key: 'status',
                render: (_, row) => <Tag color={statusColor[row.status]}>{row.status}</Tag>,
              },
              { title: '模型', dataIndex: 'modelName', key: 'modelName' },
            ]}
          />
        </Card>

        <Card title={selected ? `编辑 ${selected.version}` : '新建 Prompt 草稿'}>
          <Tabs
            items={[
              {
                key: 'editor',
                label: '模板',
                children: (
                  <Form<PromptTemplateInput>
                    form={form}
                    layout="vertical"
                    onFinish={(values) => createOrUpdate.mutate(values)}
                  >
                    {editDisabled && (
                      <Alert
                        type="info"
                        showIcon
                        message="当前版本不可直接编辑，请新建草稿版本。"
                        style={{ marginBottom: 16 }}
                      />
                    )}
                    {!canAuthorPrompt && (
                      <Alert
                        type="warning"
                        showIcon
                        message="当前账号不能创建或编辑 Prompt 草稿。"
                        style={{ marginBottom: 16 }}
                      />
                    )}
                    <Form.Item name="promptKey" label="Prompt Key" rules={[{ required: true }]}>
                      <Select options={promptKeys.map((key) => ({ label: key, value: key }))} disabled={!canAuthorPrompt || editDisabled || Boolean(selected)} />
                    </Form.Item>
                    <Form.Item name="version" label="版本" rules={[{ required: true }]}>
                      <Input placeholder="例如 receipt-extraction-v3" disabled={!canAuthorPrompt || editDisabled || Boolean(selected)} />
                    </Form.Item>
                    <Form.Item name="name" label="名称" rules={[{ required: true }]}>
                      <Input disabled={!canAuthorPrompt || editDisabled} />
                    </Form.Item>
                    <Form.Item name="description" label="说明">
                      <Input disabled={!canAuthorPrompt || editDisabled} />
                    </Form.Item>
                    <Form.Item name="modelName" label="模型" rules={[{ required: true }]}>
                      <Input disabled={!canAuthorPrompt || editDisabled} />
                    </Form.Item>
                    <Space>
                      <Form.Item name="temperature" label="Temperature">
                        <InputNumber min={0} max={2} step={0.1} disabled={!canAuthorPrompt || editDisabled} />
                      </Form.Item>
                      <Form.Item name="maxTokens" label="Max Tokens" rules={[{ required: true }]}>
                        <InputNumber min={1} max={128000} disabled={!canAuthorPrompt || editDisabled} />
                      </Form.Item>
                    </Space>
                    <Form.Item name="content" label="Prompt 内容" rules={[{ required: true }]}>
                      <Input.TextArea rows={14} disabled={!canAuthorPrompt || editDisabled} />
                    </Form.Item>
                    <Space wrap>
                      <Button type="primary" htmlType="submit" loading={createOrUpdate.isPending} disabled={!canAuthorPrompt || editDisabled}>
                        保存草稿
                      </Button>
                      <Button
                        disabled={!canAuthorPrompt || !selected || !['DRAFT', 'REJECTED'].includes(selected.status)}
                        loading={submit.isPending}
                        onClick={() =>
                          Modal.confirm({
                            title: '提交审批',
                            content: '自动门禁会扫描 Prompt 中的越权审批、付款和泄密风险。',
                            onOk: () => submit.mutate('提交 Prompt 版本审批'),
                          })
                        }
                      >
                        提交审批
                      </Button>
                      <Button
                        disabled={!canPublishPrompt || !selected || !['APPROVED', 'DEPRECATED'].includes(selected.status)}
                        loading={activate.isPending}
                        onClick={() =>
                          Modal.confirm({
                            title: selected?.status === 'DEPRECATED' ? '确认回滚 Prompt 版本' : '确认激活 Prompt 版本',
                            content: '激活后运行时会读取该版本作为 Active Prompt。',
                            onOk: () => activate.mutate(),
                          })
                        }
                      >
                        {selected?.status === 'DEPRECATED' ? '回滚到此版本' : '激活版本'}
                      </Button>
                    </Space>
                  </Form>
                ),
              },
              {
                key: 'changes',
                label: '审批',
                children: (
                  <Space orientation="vertical" className="page-stack">
                    {pendingChange() && (
                      <PromptEvaluationReportPanel change={pendingChange()!} />
                    )}
                    <Table<PromptChangeRequest>
                      rowKey="id"
                      loading={changes.isLoading}
                      dataSource={changes.data}
                      pagination={false}
                      columns={[
                        { title: '类型', dataIndex: 'requestType', key: 'requestType' },
                        {
                          title: '状态',
                          key: 'status',
                          render: (_, row) => <Tag>{row.status}</Tag>,
                        },
                        { title: '风险', dataIndex: 'riskLevel', key: 'riskLevel' },
                        { title: '提交人', dataIndex: 'submittedBy', key: 'submittedBy' },
                        {
                          title: '操作',
                          key: 'actions',
                          render: (_, row) =>
                            row.status === 'PENDING' ? (
                              <Space>
                                <Button size="small" disabled={!canReviewPrompt} onClick={() => approve.mutate({ id: row.id, comment: '同意发布' })}>
                                  通过
                                </Button>
                                <Button size="small" danger disabled={!canReviewPrompt} onClick={() => reject.mutate({ id: row.id, comment: '需要修改' })}>
                                  拒绝
                                </Button>
                              </Space>
                            ) : null,
                        },
                      ]}
                    />
                  </Space>
                ),
              },
              {
                key: 'review',
                label: '对比与审计',
                children: <PromptReviewPanel review={review.data} loading={review.isLoading} />,
              },
            ]}
          />
        </Card>
      </div>
    </Space>
  );
}

function PromptEvaluationReportPanel({ change }: { change: PromptChangeRequest }) {
  const report = change.evaluationReport ?? {};
  const violations = stringList(report.violations);
  const gateFailures = stringList(report.gateFailures);
  const checks = stringList(report.checks);
  const regression = recordValue(report.regression);
  const passed = report.passed === true;
  return (
    <Card size="small" title="Prompt 自动门禁报告">
      <Space orientation="vertical" size="middle" className="page-stack">
        <Alert
          type={passed ? 'success' : 'warning'}
          showIcon
          message={passed ? '自动门禁通过' : '自动门禁未通过'}
          description={`风险等级：${change.riskLevel} · 变更摘要：${change.diffSummary || '未填写'}`}
        />
        <Descriptions bordered size="small" column={2}>
          <Descriptions.Item label="Prompt Hash">
            {typeof report.promptHash === 'string' ? (
              <Typography.Text code copyable>{report.promptHash.slice(0, 16)}</Typography.Text>
            ) : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="检查项">{checks.length}</Descriptions.Item>
          <Descriptions.Item label="安全违规">{violations.length}</Descriptions.Item>
          <Descriptions.Item label="门禁失败">{gateFailures.length}</Descriptions.Item>
        </Descriptions>
        <div className="three-column-grid">
          <PromptReportList title="安全违规" color="red" items={violations} emptyText="未发现危险 Prompt 语义" />
          <PromptReportList title="门禁失败" color="orange" items={gateFailures} emptyText="无门禁失败" />
          <PromptReportList title="检查范围" color="blue" items={checks} emptyText="暂无检查项" />
        </div>
        <Table
          rowKey="key"
          size="small"
          pagination={false}
          dataSource={Object.entries(regression).map(([key, value]) => ({ key, value }))}
          locale={{ emptyText: <Empty description="暂无回归评测摘要" /> }}
          columns={[
            { title: '指标', dataIndex: 'key' },
            {
              title: '结果',
              dataIndex: 'value',
              render: (value: unknown) => formatReportValue(value),
            },
          ]}
        />
      </Space>
    </Card>
  );
}

function PromptReviewPanel({
  review,
  loading,
}: {
  review?: PromptVersionReview;
  loading: boolean;
}) {
  if (loading) {
    return <Card loading />;
  }
  if (!review) {
    return <Empty description="请选择一个 Prompt 版本" />;
  }
  return (
    <Space orientation="vertical" size="middle" className="page-stack">
      <Card size="small" title="版本对比">
        <Descriptions bordered size="small" column={2}>
          <Descriptions.Item label="当前 Active">
            {review.active ? `${review.active.version} · ${review.active.status}` : '无 Active 版本'}
          </Descriptions.Item>
          <Descriptions.Item label="候选版本">
            {review.candidate.version} · {review.candidate.status}
          </Descriptions.Item>
          <Descriptions.Item label="Active 行数">{review.diff.activeLineCount}</Descriptions.Item>
          <Descriptions.Item label="候选行数">{review.diff.candidateLineCount}</Descriptions.Item>
          <Descriptions.Item label="内容变化">
            <Tag color={review.diff.contentChanged ? 'orange' : 'green'}>
              {review.diff.contentChanged ? '已变化' : '未变化'}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label="回滚候选">
            <Tag color={review.diff.rollbackCandidate ? 'blue' : 'default'}>
              {review.diff.rollbackCandidate ? '是' : '否'}
            </Tag>
          </Descriptions.Item>
        </Descriptions>
        <Space wrap style={{ marginTop: 12 }}>
          {review.diff.changedFields.length === 0 ? (
            <Tag color="green">字段无变化</Tag>
          ) : (
            review.diff.changedFields.map((field) => <Tag key={field} color="orange">{field}</Tag>)
          )}
        </Space>
      </Card>

      <div className="prompt-diff-grid">
        <Card size="small" title="Active Prompt">
          <Typography.Paragraph className="prompt-preview">
            {review.active?.content ?? '暂无 Active 内容'}
          </Typography.Paragraph>
        </Card>
        <Card size="small" title="候选 Prompt">
          <Typography.Paragraph className="prompt-preview">
            {review.candidate.content}
          </Typography.Paragraph>
        </Card>
      </div>

      <Card size="small" title="审批记录">
        <Table
          rowKey="id"
          size="small"
          pagination={false}
          dataSource={review.changes}
          locale={{ emptyText: <Empty description="暂无审批记录" /> }}
          columns={[
            { title: '类型', dataIndex: 'requestType' },
            { title: '状态', dataIndex: 'status', render: (value: string) => <Tag>{value}</Tag> },
            { title: '风险', dataIndex: 'riskLevel' },
            { title: '提交人', dataIndex: 'submittedBy' },
            { title: '审批人', dataIndex: 'reviewedBy', render: (value?: string) => value ?? '-' },
            { title: '意见', dataIndex: 'reviewComment', render: (value?: string) => value || '-' },
          ]}
        />
      </Card>

      <Card size="small" title="Prompt 审计日志">
        <Table
          rowKey="id"
          size="small"
          pagination={false}
          dataSource={review.auditEvents}
          locale={{ emptyText: <Empty description="暂无审计日志" /> }}
          columns={[
            { title: '动作', dataIndex: 'action' },
            { title: '操作者', dataIndex: 'actorSubject' },
            { title: '时间', dataIndex: 'occurredAt', render: (value: string) => new Date(value).toLocaleString('zh-CN') },
            {
              title: '载荷',
              dataIndex: 'payload',
              render: (value: Record<string, unknown>) => (
                <Typography.Text code>{JSON.stringify(value).slice(0, 120)}</Typography.Text>
              ),
            },
          ]}
        />
      </Card>
    </Space>
  );
}

function PromptReportList({
  title,
  color,
  items,
  emptyText,
}: {
  title: string;
  color: string;
  items: string[];
  emptyText: string;
}) {
  return (
    <Card size="small" title={<Tag color={color}>{title}</Tag>}>
      {items.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyText} />
      ) : (
        <Space orientation="vertical">
          {items.map((item) => (
            <Typography.Text key={item}>{item}</Typography.Text>
          ))}
        </Space>
      )}
    </Card>
  );
}

function stringList(value: unknown): string[] {
  return Array.isArray(value) ? value.map((item) => String(item)) : [];
}

function recordValue(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {};
}

function formatReportValue(value: unknown) {
  if (typeof value === 'boolean') {
    return <Tag color={value ? 'green' : 'red'}>{value ? '通过' : '未通过'}</Tag>;
  }
  if (typeof value === 'number') {
    return Number.isInteger(value) ? value : Math.round(value * 1000) / 1000;
  }
  return String(value ?? '-');
}
