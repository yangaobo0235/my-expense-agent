import { Button, Card, Collapse, Empty, Space, Table, Tag, Timeline, Typography } from 'antd';
import { CaseObservability } from '../api/contracts';
import { grafanaTraceUrl } from '../api/expense-api';
import { workflowStepLabel } from '../features/cases/case-workbench-model';

export function CaseObservabilityPanel({
  observability,
  loading,
}: {
  observability?: CaseObservability;
  loading?: boolean;
}) {
  const traceUrl = observability?.latestRun?.traceId
    ? grafanaTraceUrl(observability.latestRun.traceId)
    : undefined;
  return (
    <Space orientation="vertical" size="middle" className="page-stack">
      <Card size="small" title="处理记录" loading={loading}>
        {!observability?.latestRun ? (
          <Empty description="暂无处理记录" />
        ) : (
          <Space orientation="vertical" className="page-stack">
            <div className="observability-summary-grid">
              <div className="observability-summary-item">
                <Typography.Text type="secondary">处理状态</Typography.Text>
                <Tag color={runStatusColor(observability.latestRun.status)}>
                  {runStatusLabel(observability.latestRun.status)}
                </Tag>
              </div>
              <div className="observability-summary-item">
                <Typography.Text type="secondary">完成步骤</Typography.Text>
                <Typography.Text strong>
                  {observability.latestRun.succeededStepCount ?? 0}/{observability.latestRun.stepCount ?? 0}
                </Typography.Text>
              </div>
              <div className="observability-summary-item">
                <Typography.Text type="secondary">需处理</Typography.Text>
                <Typography.Text strong>{observability.failedStepCount}</Typography.Text>
              </div>
              <div className="observability-summary-item">
                <Typography.Text type="secondary">耗时</Typography.Text>
                <Typography.Text strong>{formatDuration(observability.latestRun.durationMs)}</Typography.Text>
              </div>
            </div>
            <Collapse
              ghost
              items={[
                {
                  key: 'technical',
                  label: '管理员排查信息',
                  children: (
                    <Space orientation="vertical" size="small">
                      <Typography.Text type="secondary">
                        处理编号：<Typography.Text code copyable>{shortId(observability.latestRun.runId)}</Typography.Text>
                      </Typography.Text>
                      <Typography.Text type="secondary">
                        链路编号：{observability.latestRun.traceId ? (
                          <Typography.Text code copyable>{shortId(observability.latestRun.traceId)}</Typography.Text>
                        ) : '暂无'}
                      </Typography.Text>
                      <Space wrap>
                        <Button size="small" disabled={!traceUrl} href={traceUrl} target="_blank">查看链路</Button>
                        {import.meta.env.VITE_LANGFUSE_URL && (
                          <Button size="small" href={import.meta.env.VITE_LANGFUSE_URL} target="_blank">查看生成记录</Button>
                        )}
                      </Space>
                    </Space>
                  ),
                },
              ]}
            />
          </Space>
        )}
      </Card>

      <Card size="small" title="处理步骤">
        <Table
          rowKey="id"
          size="small"
          pagination={false}
          dataSource={observability?.steps ?? []}
          locale={{ emptyText: <Empty description="暂无步骤记录" /> }}
          scroll={{ x: 520 }}
          columns={[
            { title: '步骤', dataIndex: 'name', width: 180, render: (value: string) => workflowStepLabel(value) },
            { title: '次数', dataIndex: 'attempt', width: 72 },
            {
              title: '状态',
              dataIndex: 'status',
              width: 150,
              render: (status: string, row) => (
                <Tag color={runStatusColor(status)}>
                  {row.errorCode ? errorCodeLabel(row.errorCode) : runStatusLabel(status)}
                </Tag>
              ),
            },
            { title: '耗时', dataIndex: 'durationMs', width: 110, render: formatDuration },
          ]}
        />
      </Card>

      <Collapse
        items={[
          {
            key: 'system-records',
            label: '管理员排查记录',
            children: (
              <Table
                rowKey="id"
                size="small"
                pagination={{ pageSize: 5 }}
                dataSource={observability?.modelCalls ?? []}
                locale={{ emptyText: <Empty description="暂无排查记录" /> }}
                scroll={{ x: 720 }}
                columns={[
                  { title: '步骤', dataIndex: 'stepName', width: 180, render: (value: string) => workflowStepLabel(value) },
                  { title: '服务', dataIndex: 'modelName', width: 160 },
                  { title: '规则版本', dataIndex: 'promptVersion', width: 120 },
                  { title: '调用量', dataIndex: 'totalTokens', width: 90 },
                  { title: '耗时', dataIndex: 'latencyMs', width: 100, render: formatDuration },
                  {
                    title: '状态',
                    dataIndex: 'status',
                    width: 130,
                    render: (status: string, row) => (
                      <Tag color={runStatusColor(status)}>
                        {row.errorCode ? errorCodeLabel(row.errorCode) : runStatusLabel(status)}
                      </Tag>
                    ),
                  },
                ]}
              />
            ),
          },
        ]}
      />

      <Card size="small" title="操作记录">
        {(observability?.auditEvents ?? []).length === 0 ? (
          <Empty description="暂无操作记录" />
        ) : (
          <Timeline
            items={(observability?.auditEvents ?? []).map((event) => ({
              children: (
                <Space orientation="vertical" size={0}>
                  <Typography.Text strong>{event.action}</Typography.Text>
                  <Typography.Text type="secondary">
                    {event.actorSubject} · {event.resourceType} · {new Date(event.occurredAt).toLocaleString('zh-CN')}
                  </Typography.Text>
                </Space>
              ),
            }))}
          />
        )}
      </Card>
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

function runStatusColor(status: string) {
  if (status === 'SUCCEEDED') return 'green';
  if (status === 'FAILED') return 'red';
  if (status === 'RUNNING') return 'blue';
  return 'default';
}

function errorCodeLabel(code: string) {
  const labels: Record<string, string> = {
    DEPENDENCY_UNAVAILABLE: '外部服务暂不可用',
    NON_RETRYABLE_DEPENDENCY_FAILURE: '外部服务暂不可用',
    RETRYABLE_DEPENDENCY_FAILURE: '外部服务暂不可用',
    VALIDATION_FAILED: '校验失败',
    ACCESS_DENIED: '无权访问',
    OPTIMISTIC_LOCK_CONFLICT: '数据已变化',
  };
  return labels[code] ?? '处理异常';
}

function formatDuration(value?: number) {
  if (value === undefined) return '-';
  if (value < 1000) return `${value} ms`;
  return `${(value / 1000).toFixed(1)} 秒`;
}

function shortId(value?: string) {
  if (!value) return '-';
  return value.length > 12 ? `${value.slice(0, 6)}...${value.slice(-4)}` : value;
}
