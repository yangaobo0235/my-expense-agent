import { useQuery } from '@tanstack/react-query';
import {
  Button,
  Card,
  Collapse,
  DatePicker,
  Empty,
  Form,
  Input,
  Progress,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import dayjs from 'dayjs';
import { useState } from 'react';
import { listPolicies, searchPolicies } from '../../api/expense-api';
import { PolicyCatalogEntry } from '../../api/contracts';

interface SearchValues {
  query: string;
  category: string;
  region: string;
  applicantType: string;
  expenseDate?: dayjs.Dayjs;
}

export function PolicyCatalogPage() {
  const [search, setSearch] = useState<SearchValues>();
  const catalog = useQuery({ queryKey: ['policies'], queryFn: listPolicies });
  const matches = useQuery({
    queryKey: ['policy-search', search],
    queryFn: () =>
      searchPolicies({
        ...search!,
        expenseDate: search?.expenseDate?.format('YYYY-MM-DD'),
        limit: 8,
        minimumScore: 0.55,
      }),
    enabled: Boolean(search),
  });

  return (
    <Space orientation="vertical" size="large" className="page-stack">
      <div className="page-heading">
        <div>
          <Typography.Title level={2}>校园制度库</Typography.Title>
          <Typography.Text type="secondary">
            查看校园经费制度版本、生效范围和向量分块状态，并用业务 metadata 验证召回结果。
          </Typography.Text>
        </div>
        <Tag color="cyan">metadata 过滤 + PGVector</Tag>
      </div>

      <Card title="制度目录">
        <Table<PolicyCatalogEntry>
          rowKey="id"
          loading={catalog.isLoading}
          dataSource={catalog.data}
          pagination={false}
          locale={{
            emptyText: catalog.isError
              ? <Empty description="制度目录加载失败" />
              : <Empty description="尚未导入制度" />,
          }}
          columns={[
            {
              title: '制度',
              key: 'policy',
              render: (_, row) => (
                <Space orientation="vertical" size={0}>
                  <Typography.Text strong>{row.name}</Typography.Text>
                  <Typography.Text type="secondary">{row.policyCode}</Typography.Text>
                </Space>
              ),
            },
            { title: '类别', dataIndex: 'category', key: 'category' },
            {
              title: '适用范围',
              key: 'scope',
              render: (_, row) => `${row.region} · ${row.applicantType}`,
            },
            { title: '版本', dataIndex: 'version', key: 'version' },
            {
              title: '有效期',
              key: 'effective',
              render: (_, row) =>
                `${row.effectiveFrom} ～ ${row.effectiveTo ?? '长期有效'}`,
            },
            {
              title: '状态',
              key: 'status',
              render: (_, row) => (
                <Tag color={row.status === 'ACTIVE' ? 'green' : row.status === 'DRAFT' ? 'gold' : 'default'}>
                  {row.status}
                </Tag>
              ),
            },
            {
              title: '索引',
              key: 'index',
              width: 180,
              render: (_, row) => {
                const percent = row.chunkCount
                  ? Math.round((row.indexedChunkCount / row.chunkCount) * 100)
                  : 0;
                return (
                  <Progress
                    percent={percent}
                    size="small"
                    status={percent === 100 ? 'success' : 'active'}
                    format={() => `${row.indexedChunkCount}/${row.chunkCount}`}
                  />
                );
              },
            },
          ]}
        />
      </Card>

      <Card title="制度召回验证">
        <Form<SearchValues>
          layout="inline"
          initialValues={{ region: 'CN', applicantType: 'ALL' }}
          onFinish={setSearch}
        >
          <Form.Item name="query" rules={[{ required: true, message: '请输入检索问题' }]}>
            <Input placeholder="例如：竞赛住宿费每晚上限" />
          </Form.Item>
          <Form.Item name="category" rules={[{ required: true, message: '请输入经费科目' }]}>
            <Input placeholder="经费科目，例如 竞赛差旅费" />
          </Form.Item>
          <Form.Item name="region" rules={[{ required: true }]}>
            <Input placeholder="校区 / 地区" />
          </Form.Item>
          <Form.Item name="applicantType" rules={[{ required: true }]}>
            <Input placeholder="申请人类型" />
          </Form.Item>
          <Form.Item name="expenseDate">
            <DatePicker placeholder="支出日期" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={matches.isFetching}>
              验证召回
            </Button>
          </Form.Item>
        </Form>

        {search && (
          <div className="policy-search-results">
            {matches.data?.length ? (
              <Collapse
                items={matches.data.map((match) => ({
                  key: match.chunkId,
                  label: (
                    <Space wrap>
                      <Typography.Text strong>{match.citation}</Typography.Text>
                      <Tag color="blue">相似度 {(match.score * 100).toFixed(1)}%</Tag>
                    </Space>
                  ),
                  children: (
                    <Space orientation="vertical">
                      <Typography.Paragraph>{match.content}</Typography.Paragraph>
                      <Typography.Text type="secondary">
                        {match.category} · {match.region} · {match.applicantType} · 分块 #{match.chunkIndex}
                      </Typography.Text>
                    </Space>
                  ),
                }))}
              />
            ) : (
              !matches.isFetching && <Empty description="当前 metadata 条件下没有达到阈值的制度片段" />
            )}
          </div>
        )}
      </Card>
    </Space>
  );
}
