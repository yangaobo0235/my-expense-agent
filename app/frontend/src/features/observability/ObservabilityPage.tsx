import { useQuery } from '@tanstack/react-query';
import { Card, Col, Empty, Row, Space, Statistic, Table, Tag, Typography } from 'antd';
import { getModelCallSummary, listModelCalls, listObservableRuns } from '../../api/expense-api';
import { ModelCallRecord, ObservableWorkflowRun } from '../../api/contracts';

export function ObservabilityPage() {
  const runs = useQuery({
    queryKey: ['observable-runs'],
    queryFn: () => listObservableRuns(30),
  });
  const modelSummary = useQuery({
    queryKey: ['model-call-summary'],
    queryFn: getModelCallSummary,
  });
  const modelCalls = useQuery({
    queryKey: ['model-calls'],
    queryFn: () => listModelCalls(50),
  });

  return (
    <Space orientation="vertical" size="large" className="page-stack">
      <div className="page-heading">
        <div>
          <Typography.Title level={2}>运行审计</Typography.Title>
          <Typography.Text type="secondary">
            查看工作流步骤、模型调用、失败原因和管理员操作记录。
          </Typography.Text>
        </div>
      </div>

      <Card title="模型调用成本与延迟" loading={modelSummary.isLoading}>
        <Row gutter={[20, 20]}>
          <Col span={4}><Statistic title="调用总数" value={modelSummary.data?.totalCalls ?? 0} /></Col>
          <Col span={4}><Statistic title="成功率" value={Math.round((modelSummary.data?.successRate ?? 0) * 1000) / 10} suffix="%" /></Col>
          <Col span={4}><Statistic title="平均延迟" value={Math.round(modelSummary.data?.averageLatencyMs ?? 0)} suffix="ms" /></Col>
          <Col span={4}><Statistic title="P95 延迟" value={modelSummary.data?.p95LatencyMs ?? 0} suffix="ms" /></Col>
          <Col span={4}><Statistic title="Token 总量" value={modelSummary.data?.totalTokens ?? 0} /></Col>
          <Col span={4}>
            <Space wrap>
              {Object.entries(modelSummary.data?.callsByModel ?? {}).slice(0, 3).map(([model, count]) => (
                <Tag key={model} color="blue">{model}: {count}</Tag>
              ))}
            </Space>
          </Col>
        </Row>
      </Card>

      <Card title="模型调用明细">
        <Table<ModelCallRecord>
          rowKey="id"
          loading={modelCalls.isLoading}
          dataSource={modelCalls.data}
          pagination={{ pageSize: 10 }}
          locale={{
            emptyText: modelCalls.isError
              ? <Empty description="模型调用加载失败" />
              : <Empty description="暂无模型调用记录" />,
          }}
          columns={[
            {
              title: '步骤',
              dataIndex: 'stepName',
              key: 'stepName',
              render: (value: string, row) => (
                <Space orientation="vertical" size={0}>
                  <Typography.Text strong>{value}</Typography.Text>
                  {row.caseId && <Typography.Link href={`/cases/${row.caseId}`}>{row.caseId}</Typography.Link>}
                </Space>
              ),
            },
            { title: '模型', dataIndex: 'modelName', key: 'modelName' },
            { title: 'Prompt', dataIndex: 'promptVersion', key: 'promptVersion' },
            { title: 'Token', dataIndex: 'totalTokens', key: 'totalTokens' },
            { title: '耗时', dataIndex: 'latencyMs', key: 'latencyMs', render: (value: number) => `${value} ms` },
            {
              title: '状态',
              dataIndex: 'status',
              key: 'status',
              render: (status: string, row) => (
                <Tag color={status === 'SUCCEEDED' ? 'green' : 'red'}>{row.errorCode ?? status}</Tag>
              ),
            },
            {
              title: '输出 Hash',
              dataIndex: 'outputHash',
              key: 'outputHash',
              render: (value?: string) => value ? <Typography.Text code copyable>{value.slice(0, 12)}</Typography.Text> : '-',
            },
          ]}
        />
      </Card>

      <Card title="最近工作流运行">
        <Table<ObservableWorkflowRun>
          rowKey="runId"
          loading={runs.isLoading}
          dataSource={runs.data}
          pagination={false}
          locale={{
            emptyText: runs.isError
              ? <Empty description="运行索引加载失败" />
              : <Empty description="暂无工作流运行" />,
          }}
          columns={[
            {
              title: '运行',
              key: 'run',
              render: (_, row) => (
                <Space orientation="vertical" size={0}>
                  <Typography.Link href={`/cases/${row.caseId}`}>{row.caseId}</Typography.Link>
                  <Typography.Text type="secondary" copyable>{row.runId}</Typography.Text>
                </Space>
              ),
            },
            {
              title: '状态',
              dataIndex: 'status',
              key: 'status',
              render: (status: string) => <Tag color={status === 'SUCCEEDED' ? 'green' : status === 'FAILED' ? 'red' : 'blue'}>{status}</Tag>,
            },
            {
              title: '步骤健康',
              key: 'steps',
              render: (_, row) => (
                <Space wrap>
                  <Tag color="blue">{row.succeededStepCount ?? 0}/{row.stepCount ?? 0} 成功</Tag>
                  {(row.failedStepCount ?? 0) > 0 && <Tag color="red">{row.failedStepCount} 失败</Tag>}
                  <Tag color={row.executionPolicyRecorded ? 'green' : 'default'}>
                    {row.executionPolicyRecorded ? '已记录执行策略' : '未记录执行策略'}
                  </Tag>
                  {row.documentVersion && <Tag>V{row.documentVersion}</Tag>}
                </Space>
              ),
            },
            {
              title: '开始时间',
              dataIndex: 'startedAt',
              key: 'startedAt',
              render: (value: string) => new Date(value).toLocaleString('zh-CN'),
            },
            {
              title: '耗时',
              dataIndex: 'durationMs',
              key: 'durationMs',
              render: (value?: number) => value === undefined ? '-' : `${value} ms`,
            },
          ]}
        />
      </Card>
    </Space>
  );
}
