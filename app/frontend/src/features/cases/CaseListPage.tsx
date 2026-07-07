import { DeleteOutlined, EditOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Button, Card, Empty, Input, Popconfirm, Select, Space, Table, Tag, Typography, message } from 'antd';
import axios from 'axios';
import { useMemo } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { ExpenseCase, ExpenseCaseStatus } from '../../api/contracts';
import { deleteCase, listCases } from '../../api/expense-api';
import { RiskBadge } from '../../components/RiskBadge';
import { StatusBadge } from '../../components/StatusBadge';
import { useAuthStore } from '../auth/auth-store';
import { buildCaseDiagnosis, nextActionTitle } from './case-workbench-model';

const statusOptions: Array<{ value: ExpenseCaseStatus; label: string }> = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'UPLOADED', label: '已上传票据' },
  { value: 'EXTRACTING', label: '正在识别票据' },
  { value: 'EXTRACTED', label: '票据已识别' },
  { value: 'POLICY_CHECKING', label: '正在核对制度' },
  { value: 'RISK_CHECKING', label: '正在评估风险' },
  { value: 'WAITING_HUMAN', label: '等待人工审核' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已拒绝' },
  { value: 'FAILED', label: '处理失败' },
];

export function CaseListPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const user = useAuthStore((state) => state.user);
  const [params, setParams] = useSearchParams();
  const page = Number(params.get('page') ?? 0);
  const size = Number(params.get('size') ?? 20);
  const status = params.get('status') as ExpenseCaseStatus | null;
  const applicant = params.get('applicant') ?? undefined;
  const canEditDraft = Boolean(user?.roles.includes('EMPLOYEE'));
  const canDeleteAnyCase = Boolean(user?.roles.includes('FINANCE_ADMIN'));
  const query = useQuery({
    queryKey: ['cases', page, size, status, applicant],
    queryFn: () => listCases({ page, size, status: status ?? undefined, applicant }),
  });
  const deleteMutation = useMutation({
    mutationFn: (caseId: string) => deleteCase(caseId),
    onSuccess: () => {
      message.success(canDeleteAnyCase ? '案例已删除' : '草稿已删除');
      void queryClient.invalidateQueries({ queryKey: ['cases'] });
    },
    onError: (error) => {
      const apiMessage = axios.isAxiosError(error)
        ? (error.response?.data as { message?: string } | undefined)?.message
        : undefined;
      message.error(apiMessage ?? '删除失败，请稍后重试。');
    },
  });
  const columns = useMemo(
    () => [
      {
        title: '案例',
        key: 'case',
        render: (_: unknown, row: ExpenseCase) => (
          <Space orientation="vertical" size={0}>
            <Typography.Text strong>{row.title}</Typography.Text>
            <Typography.Text type="secondary">{row.caseNumber}</Typography.Text>
          </Space>
        ),
      },
      {
        title: '申请人',
        key: 'applicant',
        render: (_: unknown, row: ExpenseCase) => (
          <Space orientation="vertical" size={0}>
            <span>{row.applicantName}</span>
            <Typography.Text type="secondary">{row.departmentCode}</Typography.Text>
          </Space>
        ),
      },
      {
        title: '申报金额',
        key: 'amount',
        render: (_: unknown, row: ExpenseCase) =>
          new Intl.NumberFormat('zh-CN', { style: 'currency', currency: row.currency }).format(row.claimedAmount),
      },
      { title: '状态', key: 'status', render: (_: unknown, row: ExpenseCase) => <StatusBadge status={row.status} /> },
      { title: '风险', key: 'risk', render: (_: unknown, row: ExpenseCase) => <RiskBadge level={row.riskLevel} score={row.riskScore} /> },
      {
        title: '当前阶段',
        key: 'stage',
        render: (_: unknown, row: ExpenseCase) => <CaseQueueState expenseCase={row} />,
      },
      {
        title: '下一步',
        key: 'next',
        render: (_: unknown, row: ExpenseCase) => (
          <Tag color={nextActionColor(row)}>{nextActionLabel(row)}</Tag>
        ),
      },
      { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', render: (value: string) => new Date(value).toLocaleString('zh-CN') },
      {
        title: '操作',
        key: 'actions',
        render: (_: unknown, row: ExpenseCase) => {
          const canEditRow = canEditDraft && row.status === 'DRAFT';
          const canDeleteRow = canDeleteAnyCase || canEditRow;
          if (!canEditRow && !canDeleteRow) {
            return <Typography.Text type="secondary">-</Typography.Text>;
          }
          return (
            <Space onClick={(event) => event.stopPropagation()}>
              {canEditRow && (
                <Button
                  size="small"
                  icon={<EditOutlined />}
                  onClick={() => navigate(`/cases/${row.id}?edit=1`)}
                >
                  修改
                </Button>
              )}
              {canDeleteRow && (
                <Popconfirm
                  title={canDeleteAnyCase ? '删除该案例？' : '删除草稿案例？'}
                  description={
                    canDeleteAnyCase
                      ? '删除后该案例和相关票据、处理记录将不再显示。'
                      : '删除后该草稿不会再出现在列表中。'
                  }
                  okText="删除"
                  cancelText="取消"
                  okButtonProps={{ danger: true }}
                  onConfirm={() => deleteMutation.mutate(row.id)}
                >
                  <Button
                    size="small"
                    danger
                    icon={<DeleteOutlined />}
                    loading={deleteMutation.isPending}
                  >
                    删除
                  </Button>
                </Popconfirm>
              )}
            </Space>
          );
        },
      },
    ],
    [canDeleteAnyCase, canEditDraft, deleteMutation, navigate],
  );
  return (
    <Space orientation="vertical" size="large" className="page-stack">
      <div className="page-heading">
        <div>
          <Typography.Title level={2}>费用案例</Typography.Title>
          <Typography.Text type="secondary">查看报销案例进度，处理需要补充或重试的事项。</Typography.Text>
        </div>
        <Button type="primary" onClick={() => navigate('/cases/new')}>新建案例</Button>
      </div>
      <Card>
        <Alert
          className="form-alert"
          type="info"
          showIcon
          message="处理失败不会让案例作废"
          description="进入详情后可以查看失败原因，并从出错的步骤继续处理。草稿填错时，也可以先修改或删除。"
        />
        <Space wrap className="filter-row">
          <Input.Search
            allowClear
            placeholder="按申请人搜索"
            defaultValue={applicant}
            onSearch={(value) => setParams((current) => {
              value ? current.set('applicant', value) : current.delete('applicant');
              current.set('page', '0');
              return current;
            })}
          />
          <Select
            allowClear
            placeholder="状态"
            value={status}
            options={statusOptions}
            onChange={(value) => setParams((current) => {
              value ? current.set('status', value) : current.delete('status');
              current.set('page', '0');
              return current;
            })}
          />
        </Space>
        <Table<ExpenseCase>
          rowKey="id"
          loading={query.isLoading}
          dataSource={query.data?.items}
          columns={columns}
          locale={{ emptyText: query.isError ? <Empty description="加载失败，请稍后重试" /> : <Empty description="暂无费用案例" /> }}
          onRow={(record) => ({ onClick: () => navigate(`/cases/${record.id}`) })}
          pagination={{
            current: page + 1,
            pageSize: size,
            total: query.data?.total,
            onChange: (nextPage, nextSize) => setParams((current) => {
              current.set('page', String(nextPage - 1));
              current.set('size', String(nextSize));
              return current;
            }),
          }}
        />
      </Card>
    </Space>
  );
}

function CaseQueueState({ expenseCase }: { expenseCase: ExpenseCase }) {
  const diagnosis = buildCaseDiagnosis(expenseCase);
  const color =
    diagnosis.severity === 'error' ? 'red' :
    diagnosis.severity === 'warning' ? 'orange' :
    diagnosis.severity === 'success' ? 'green' : 'blue';
  return (
    <Space orientation="vertical" size={0}>
      <Tag color={color}>{diagnosis.title}</Tag>
      <Typography.Text type="secondary">{diagnosis.description}</Typography.Text>
    </Space>
  );
}

function nextActionLabel(expenseCase: ExpenseCase) {
  if (expenseCase.status === 'APPROVED') {
    if (expenseCase.settlementStatus === 'SUBMITTED') return '结算已完成';
    if (expenseCase.settlementStatus === 'FAILED') return '结算需重试';
    return '待发起结算';
  }
  return nextActionTitle(expenseCase);
}

function nextActionColor(expenseCase: ExpenseCase) {
  if (expenseCase.status === 'FAILED') return 'red';
  if (expenseCase.status === 'WAITING_HUMAN') return 'orange';
  if (expenseCase.status === 'APPROVED') {
    if (expenseCase.settlementStatus === 'FAILED') return 'red';
    return 'green';
  }
  if (expenseCase.status === 'REJECTED') return 'default';
  return 'blue';
}
