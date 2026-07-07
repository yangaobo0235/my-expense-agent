import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Card,
  Col,
  Descriptions,
  Empty,
  Progress,
  Row,
  Space,
  Statistic,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import {
  getAgentSecurityEvaluationReport,
  getPolicyRagEvaluationReport,
  getRiskEvaluationReport,
} from '../../api/expense-api';
import {
  AgentSecurityEvaluationReport,
  PolicyRagEvaluationReport,
  RiskEvaluationReport,
} from '../../api/contracts';

const percent = (value: number) => Number((value * 100).toFixed(1));

export function EvaluationReportPage() {
  const risk = useQuery({
    queryKey: ['risk-evaluation-report'],
    queryFn: getRiskEvaluationReport,
  });
  const rag = useQuery({
    queryKey: ['policy-rag-evaluation-report'],
    queryFn: getPolicyRagEvaluationReport,
  });
  const security = useQuery({
    queryKey: ['agent-security-evaluation-report'],
    queryFn: getAgentSecurityEvaluationReport,
  });

  if (risk.isError) {
    return (
      <Alert
        type="error"
        showIcon
        title="评测报告加载失败"
        description="请确认后端从项目根目录启动，且评测数据集路径配置正确。"
      />
    );
  }

  return (
    <Space orientation="vertical" size="large" className="page-stack">
      <div className="page-heading">
        <div>
          <Typography.Title level={2}>离线评测报告</Typography.Title>
          <Typography.Text type="secondary">
            固定数据集的可重复基线，覆盖风险规则、制度 RAG 和 Agent 安全边界。
          </Typography.Text>
        </div>
        <Space>
          <Tag color="blue">{risk.data?.datasetVersion ?? '加载中'}</Tag>
          <Tag>{risk.data?.engineVersion ?? '规则引擎'}</Tag>
        </Space>
      </div>

      <Tabs
        defaultActiveKey="risk"
        items={[
          {
            key: 'risk',
            label: '风险评测',
            children: <RiskReportPanel data={risk.data} loading={risk.isLoading} />,
          },
          {
            key: 'rag',
            label: '制度 RAG 评测',
            children: (
              <PolicyRagPanel
                data={rag.data}
                loading={rag.isLoading}
                error={rag.isError}
              />
            ),
          },
          {
            key: 'security',
            label: 'Agent 安全评测',
            children: (
              <AgentSecurityPanel
                data={security.data}
                loading={security.isLoading}
                error={security.isError}
              />
            ),
          },
        ]}
      />
    </Space>
  );
}

function RiskReportPanel({
  data,
  loading,
}: {
  data?: RiskEvaluationReport;
  loading: boolean;
}) {
  const governance = data?.agentGovernance;
  return (
    <Space orientation="vertical" size="large" className="page-stack">
      <Card loading={loading}>
        <Row gutter={[20, 20]}>
          <Col span={4}><Statistic title="黄金案例" value={data?.caseCount ?? 0} suffix="条" /></Col>
          <Col span={4}><Statistic title="精确率" value={percent(data?.metrics.precision ?? 0)} suffix="%" /></Col>
          <Col span={4}><Statistic title="召回率" value={percent(data?.metrics.recall ?? 0)} suffix="%" /></Col>
          <Col span={4}><Statistic title="综合得分" value={percent(data?.metrics.f1 ?? 0)} suffix="%" /></Col>
          <Col span={4}><Statistic title="高风险漏报率" value={percent(data?.metrics.highRiskMissRate ?? 0)} suffix="%" styles={{ content: { color: data?.metrics.highRiskMissRate ? '#c53030' : '#0f766e' } }} /></Col>
          <Col span={4}><Statistic title="人工复核触发率" value={percent(data?.metrics.humanReviewTriggerRate ?? 0)} suffix="%" /></Col>
        </Row>
      </Card>

      <div className="evaluation-grid">
        <Card title="质量门禁">
          <Metric label="风险等级准确率" value={data?.metrics.riskLevelAccuracy ?? 0} />
          <Metric label="人工复核判断准确率" value={data?.metrics.humanReviewAccuracy ?? 0} />
          <Metric label="风险信号召回率" value={data?.metrics.recall ?? 0} />
          <Typography.Text type="secondary">
            数据集 SHA-256：<Typography.Text code copyable>{data?.datasetSha256}</Typography.Text>
          </Typography.Text>
        </Card>
        <Card title="案例分布">
          <Table
            rowKey="category"
            pagination={false}
            size="small"
            dataSource={Object.entries(data?.categoryCounts ?? {}).map(([category, count]) => ({ category, count }))}
            columns={[
              { title: '场景', dataIndex: 'category', key: 'category' },
              { title: '案例数', dataIndex: 'count', key: 'count' },
            ]}
          />
        </Card>
        <Card title="Agent 治理门禁">
          <Space orientation="vertical" className="full-width">
            <Space wrap>
              <Tag color={governance?.writeToolIsolationPassed ? 'green' : 'red'}>
                写 Tool 隔离{governance?.writeToolIsolationPassed ? '通过' : '待确认'}
              </Tag>
              <Tag color={governance?.settlementWriteRetryProtected ? 'green' : 'orange'}>
                审批写入幂等重试{governance?.settlementWriteRetryProtected ? '已保护' : '待确认'}
              </Tag>
              <Tag color="blue">{governance?.planVersion ?? '未生成 Agent 计划'}</Tag>
            </Space>
            <Descriptions size="small" column={1}>
              <Descriptions.Item label="Agent 总数">{governance?.totalAgents ?? 0}</Descriptions.Item>
              <Descriptions.Item label="写 Agent">
                {governance?.writeAgentCount ?? 0} 个，其中幂等写 Agent {governance?.idempotentWriteAgentCount ?? 0} 个
              </Descriptions.Item>
            </Descriptions>
            <Metric label="人工交接覆盖率" value={governance?.humanHandoffCoverage ?? 0} />
            <Metric label="可重试 Agent 占比" value={governance?.retryableAgentRate ?? 0} />
          </Space>
        </Card>
      </div>

      <Card title={`失败案例（${data?.failures.length ?? 0}）`}>
        <Table
          rowKey="caseId"
          pagination={false}
          dataSource={data?.failures}
          locale={{ emptyText: <Empty description="当前基线没有回归失败案例" /> }}
          columns={[
            { title: '案例', dataIndex: 'caseId', key: 'caseId' },
            { title: '期望信号', dataIndex: 'expectedSignals', key: 'expectedSignals', render: (values: string[]) => values.join('、') },
            { title: '实际信号', dataIndex: 'actualSignals', key: 'actualSignals', render: (values: string[]) => values.join('、') },
            { title: '期望等级', dataIndex: 'expectedRiskLevel', key: 'expectedRiskLevel' },
            { title: '实际等级', dataIndex: 'actualRiskLevel', key: 'actualRiskLevel' },
          ]}
        />
      </Card>
    </Space>
  );
}

function PolicyRagPanel({
  data,
  loading,
  error,
}: {
  data?: PolicyRagEvaluationReport;
  loading: boolean;
  error: boolean;
}) {
  if (error) return <Alert type="warning" showIcon message="制度 RAG 评测报告加载失败" />;
  return (
    <Space orientation="vertical" size="large" className="page-stack">
      <Card loading={loading}>
        <Row gutter={[20, 20]}>
          <Col span={4}><Statistic title="总案例数" value={data?.caseCount ?? 0} /></Col>
          <Col span={4}><Statistic title="前 5 条召回率" value={percent(data?.metrics.recallAt5 ?? 0)} suffix="%" /></Col>
          <Col span={4}><Statistic title="前 5 条精确率" value={percent(data?.metrics.precisionAt5 ?? 0)} suffix="%" /></Col>
          <Col span={4}><Statistic title="制度命中率" value={percent(data?.metrics.expectedPolicyHitRate ?? 0)} suffix="%" /></Col>
          <Col span={4}><Statistic title="章节命中率" value={percent(data?.metrics.expectedSectionHitRate ?? 0)} suffix="%" /></Col>
          <Col span={4}><Statistic title="平均检索延迟" value={Math.round(data?.metrics.averageSearchLatencyMs ?? 0)} suffix="ms" /></Col>
        </Row>
      </Card>
      <Card title="RAG 门禁">
        <Space wrap>
          <Tag color={data?.metrics.injectionDefensePassed ? 'green' : 'red'}>
            提示注入防护{data?.metrics.injectionDefensePassed ? '通过' : '失败'}
          </Tag>
          <Tag color="blue">拒答准确率 {percent(data?.metrics.noAnswerAccuracy ?? 0)}%</Tag>
          <Tag color="blue">{data?.datasetVersion ?? 'policy-rag-golden-v1'}</Tag>
        </Space>
      </Card>
      <FailureTable failures={data?.failures ?? []} />
    </Space>
  );
}

function AgentSecurityPanel({
  data,
  loading,
  error,
}: {
  data?: AgentSecurityEvaluationReport;
  loading: boolean;
  error: boolean;
}) {
  if (error) return <Alert type="warning" showIcon message="Agent 安全评测报告加载失败" />;
  return (
    <Space orientation="vertical" size="large" className="page-stack">
      <Card loading={loading}>
        <Row gutter={[20, 20]}>
          <Col span={4}><Statistic title="安全案例" value={data?.caseCount ?? 0} /></Col>
          <Col span={5}><Statistic title="阻断写 Tool" value={data?.metrics.blockedWriteToolCount ?? 0} /></Col>
          <Col span={5}><Statistic title="越权写 Tool" value={data?.metrics.unsafeWriteToolCallCount ?? 0} /></Col>
          <Col span={5}><Statistic title="注入识别" value={data?.metrics.injectionDetectedCount ?? 0} /></Col>
          <Col span={5}><Statistic title="通过率" value={percent(data?.metrics.securityPassRate ?? 0)} suffix="%" /></Col>
        </Row>
      </Card>
      <Card title={`失败样例（${data?.failures.length ?? 0}）`}>
        <Table
          rowKey="caseId"
          pagination={false}
          dataSource={data?.failures}
          locale={{ emptyText: <Empty description="安全评测全部通过" /> }}
          columns={[
            { title: '案例', dataIndex: 'caseId', key: 'caseId' },
            { title: '原因', dataIndex: 'reason', key: 'reason' },
            { title: '恶意文本', dataIndex: 'maliciousText', key: 'maliciousText' },
          ]}
        />
      </Card>
    </Space>
  );
}

function FailureTable({ failures }: { failures: PolicyRagEvaluationReport['failures'] }) {
  return (
    <Card title={`失败案例（${failures.length}）`}>
      <Table
        rowKey="caseId"
        pagination={false}
        dataSource={failures}
        locale={{ emptyText: <Empty description="制度 RAG 评测全部通过" /> }}
        columns={[
          { title: '案例', dataIndex: 'caseId', key: 'caseId' },
          { title: 'Query', dataIndex: 'query', key: 'query' },
          { title: '期望制度', dataIndex: 'expectedPolicyCode', key: 'expectedPolicyCode' },
          { title: '期望章节', dataIndex: 'expectedSections', key: 'expectedSections', render: (values: string[]) => values.join('、') },
          { title: '原因', dataIndex: 'reason', key: 'reason' },
        ]}
      />
    </Card>
  );
}

function Metric({ label, value }: { label: string; value: number }) {
  return (
    <div className="evaluation-metric">
      <Typography.Text>{label}</Typography.Text>
      <Progress percent={percent(value)} status={value >= 0.9 ? 'success' : 'normal'} />
    </div>
  );
}
