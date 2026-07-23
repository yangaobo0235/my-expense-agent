import { Alert, Card, Descriptions, Empty, Space, Table, Tag, Typography } from 'antd';
import { CaseEvidence, ReviewTask } from '../api/contracts';

const policySignals = new Set(['POLICY_LIMIT_EXCEEDED', 'AMOUNT_MISMATCH']);
const fraudSignals = new Set([
  'DUPLICATE_DOCUMENT',
  'SELLER_ANOMALY',
  'FORBIDDEN_EXPENSE_ITEM',
  'PROMPT_INJECTION_DETECTED',
]);

export function RiskEvidenceBoard({
  evidence,
  task,
}: {
  evidence?: CaseEvidence;
  task?: ReviewTask;
}) {
  const signals = evidence?.risk?.signals ?? [];
  if (!evidence?.risk) {
    return <Empty description="暂无风险证据" />;
  }
  const approveArguments = [
    evidence.risk.level === 'LOW' ? '风险评分处于低风险区间' : '',
    evidence.policyFindings.length > 0 ? '已检索到可引用的制度依据' : '',
    signals.length === 0 ? '未发现明确风险信号' : '',
  ].filter(Boolean);
  const rejectArguments = signals
    .filter((signal) => fraudSignals.has(signal.code))
    .map((signal) => `${signal.code}: ${signal.message}`);
  const manualReviewArguments = signals
    .filter((signal) => policySignals.has(signal.code))
    .map((signal) => `${signal.code}: ${signal.message}`);

  return (
    <Space orientation="vertical" size="large" className="page-stack">
      {task?.userFacingMessage && (
        <Alert
          showIcon
          type={task.debateAssistEnabled ? 'warning' : 'info'}
          title={task.userFacingMessage}
          description={`队列：${task.routingQueue ?? '-'} · SLA：${task.slaHours ?? '-'} 小时 · 处理角色：${task.assigneeRole ?? '-'}`}
        />
      )}
      <div className="evidence-summary-grid">
        <Card size="small" title="风险证据对比">
          <Descriptions column={1} size="small">
            <Descriptions.Item label="风险等级">{evidence.risk.level}</Descriptions.Item>
            <Descriptions.Item label="风险分值">{evidence.risk.score}</Descriptions.Item>
            <Descriptions.Item label="人工复核">
              {evidence.risk.requiresHumanReview ? '需要' : '不需要'}
            </Descriptions.Item>
            <Descriptions.Item label="制度命中">{evidence.policyFindings.length} 条</Descriptions.Item>
          </Descriptions>
        </Card>
        <Card size="small" title="所需证据">
          <Space wrap>
            {(task?.requiredEvidence ?? []).length === 0 ? (
              <Typography.Text type="secondary">无额外证据要求</Typography.Text>
            ) : (
              task?.requiredEvidence.map((item) => <Tag key={item}>{item}</Tag>)
            )}
          </Space>
        </Card>
      </div>
      <div className="three-column-grid">
        <ArgumentCard title="支持通过" color="green" items={approveArguments} />
        <ArgumentCard title="支持调整/补充" color="orange" items={manualReviewArguments} />
        <ArgumentCard title="支持驳回/升级" color="red" items={rejectArguments} />
      </div>
      <Card title="风险信号明细">
        <Table
          rowKey="code"
          size="small"
          pagination={false}
          dataSource={signals}
          columns={[
            { title: '信号', dataIndex: 'code' },
            { title: '分值', dataIndex: 'score' },
            { title: '解释', dataIndex: 'message' },
            {
              title: '证据',
              dataIndex: 'evidence',
              render: (value) => <Typography.Text code>{JSON.stringify(value)}</Typography.Text>,
            },
          ]}
        />
      </Card>
    </Space>
  );
}

function ArgumentCard({
  title,
  color,
  items,
}: {
  title: string;
  color: string;
  items: string[];
}) {
  return (
    <Card size="small" title={<Tag color={color}>{title}</Tag>}>
      {items.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无明确证据" />
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
