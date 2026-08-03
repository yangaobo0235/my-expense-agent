import { useQuery } from '@tanstack/react-query';
import { Alert, Card, Col, Empty, Row, Space, Statistic, Table, Tabs, Tag, Typography } from 'antd';
import { getExtractionEvaluationReport, getRiskEvaluationReport } from '../../api/expense-api';
import { ExtractionEvaluationReport, RiskEvaluationReport } from '../../api/contracts';

const percent = (value: number) => Number((value * 100).toFixed(1));

export function EvaluationReportPage() {
  const extraction = useQuery({
    queryKey: ['extraction-evaluation-report'],
    queryFn: getExtractionEvaluationReport,
  });
  const risk = useQuery({
    queryKey: ['risk-evaluation-report'],
    queryFn: getRiskEvaluationReport,
  });

  if (extraction.isError || risk.isError) {
    return <Alert type="error" showIcon title="评测报告加载失败" />;
  }

  return (
    <Space orientation="vertical" size="large" className="page-stack">
      <div className="page-heading">
        <div>
          <Typography.Title level={2}>回归评测</Typography.Title>
          <Typography.Text type="secondary">票据关键字段 30 条与风险分流 300 条固定样例。</Typography.Text>
        </div>
      </div>
      <Tabs
        defaultActiveKey="extraction"
        items={[
          {
            key: 'extraction',
            label: '票据解析',
            children: <ExtractionPanel data={extraction.data} loading={extraction.isLoading} />,
          },
          {
            key: 'risk',
            label: '风险分流',
            children: <RiskPanel data={risk.data} loading={risk.isLoading} />,
          },
        ]}
      />
    </Space>
  );
}

function ExtractionPanel({ data, loading }: { data?: ExtractionEvaluationReport; loading: boolean }) {
  return (
    <Space orientation="vertical" size="large" className="page-stack">
      <Card loading={loading}>
        <Row gutter={[20, 20]}>
          <Col span={4}><Statistic title="样例" value={data?.caseCount ?? 0} suffix="条" /></Col>
          <Col span={4}><Statistic title="JSON 有效率" value={percent(data?.metrics.jsonValidRate ?? 0)} suffix="%" /></Col>
          <Col span={4}><Statistic title="结构通过率" value={percent(data?.metrics.schemaPassRate ?? 0)} suffix="%" /></Col>
          <Col span={4}><Statistic title="金额匹配率" value={percent(data?.metrics.amountExactMatch ?? 0)} suffix="%" /></Col>
          <Col span={4}><Statistic title="日期准确率" value={percent(data?.metrics.dateAccuracy ?? 0)} suffix="%" /></Col>
          <Col span={4}><Statistic title="币种准确率" value={percent(data?.metrics.currencyAccuracy ?? 0)} suffix="%" /></Col>
        </Row>
      </Card>
      <Space wrap>
        <Tag color={data?.gatePassed ? 'green' : 'red'}>{data?.gatePassed ? '关键字段回归通过' : '关键字段回归未通过'}</Tag>
        <Tag color="blue">{data?.datasetVersion ?? 'extraction-golden-v1'}</Tag>
      </Space>
      <Table
        rowKey="caseId"
        pagination={false}
        dataSource={data?.failures ?? []}
        locale={{ emptyText: <Empty description="没有失败样例" /> }}
        columns={[
          { title: '样例', dataIndex: 'caseId' },
          { title: '不匹配字段', dataIndex: 'mismatchedFields', render: (values: string[]) => values.join('、') },
        ]}
      />
    </Space>
  );
}

function RiskPanel({ data, loading }: { data?: RiskEvaluationReport; loading: boolean }) {
  return (
    <Space orientation="vertical" size="large" className="page-stack">
      <Card loading={loading}>
        <Row gutter={[20, 20]}>
          <Col span={6}><Statistic title="案例" value={data?.caseCount ?? 0} suffix="条" /></Col>
          <Col span={6}><Statistic title="分级准确率" value={percent(data?.metrics.riskLevelAccuracy ?? 0)} suffix="%" /></Col>
          <Col span={6}><Statistic title="路由准确率" value={percent(data?.metrics.routingAccuracy ?? 0)} suffix="%" /></Col>
          <Col span={6}><Statistic title="高风险召回率" value={percent(data?.metrics.highRiskRecall ?? 0)} suffix="%" /></Col>
        </Row>
      </Card>
      <Space wrap>
        <Tag color="blue">{data?.datasetVersion ?? 'risk-golden-v3'}</Tag>
        <Tag>{data?.engineVersion ?? 'deterministic-risk-v1'}</Tag>
      </Space>
      <Table
        rowKey="caseId"
        pagination={{ pageSize: 10 }}
        dataSource={data?.failures ?? []}
        locale={{ emptyText: <Empty description="没有差异样例" /> }}
        columns={[
          { title: '样例', dataIndex: 'caseId' },
          { title: '期望等级', dataIndex: 'expectedRiskLevel' },
          { title: '实际等级', dataIndex: 'actualRiskLevel' },
          { title: '期望信号', dataIndex: 'expectedSignals', render: (values: string[]) => values.join('、') },
          { title: '实际信号', dataIndex: 'actualSignals', render: (values: string[]) => values.join('、') },
        ]}
      />
    </Space>
  );
}
