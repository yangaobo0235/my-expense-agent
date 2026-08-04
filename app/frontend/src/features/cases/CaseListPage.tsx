import { DeleteOutlined, EditOutlined, PlusOutlined, RightOutlined, SearchOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Button,
  Empty,
  Grid,
  Input,
  Pagination,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
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
  { value: 'UPLOADED', label: '票据已上传' },
  { value: 'EXTRACTING', label: '票据识别中' },
  { value: 'EXTRACTED', label: '票据已识别' },
  { value: 'POLICY_CHECKING', label: '制度核对中' },
  { value: 'RISK_CHECKING', label: '风险评估中' },
  { value: 'WAITING_HUMAN', label: '等待人工审核' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已拒绝' },
  { value: 'FAILED', label: '处理失败' },
];

export function CaseListPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const screens = Grid.useBreakpoint();
  const user = useAuthStore((state) => state.user);
  const [params, setParams] = useSearchParams();
  const page = Number(params.get('page') ?? 0);
  const size = Number(params.get('size') ?? 20);
  const status = params.get('status') as ExpenseCaseStatus | null;
  const applicant = params.get('applicant') ?? undefined;
  const canEditDraft = Boolean(user?.roles.some((role) => ['STUDENT', 'ADVISOR'].includes(role)));
  const canDeleteAnyCase = Boolean(user?.roles.includes('FINANCE_ADMIN'));
  const query = useQuery({
    queryKey: ['cases', page, size, status, applicant],
    queryFn: () => listCases({ page, size, status: status ?? undefined, applicant }),
  });
  const deleteMutation = useMutation({
    mutationFn: (caseId: string) => deleteCase(caseId),
    onSuccess: () => {
      message.success(canDeleteAnyCase ? '申请已删除' : '草稿已删除');
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
        title: '申请事项',
        key: 'case',
        width: 280,
        render: (_: unknown, row: ExpenseCase) => (
          <div className="case-primary-cell">
            <Typography.Text strong ellipsis={{ tooltip: row.title }}>{row.title}</Typography.Text>
            <Typography.Text type="secondary">{row.caseNumber}</Typography.Text>
          </div>
        ),
      },
      {
        title: '申请人 / 项目',
        key: 'applicant',
        width: 160,
        render: (_: unknown, row: ExpenseCase) => (
          <div className="case-primary-cell">
            <span>{row.applicantName}</span>
            <Typography.Text type="secondary">{row.projectCode}</Typography.Text>
          </div>
        ),
      },
      {
        title: '申报金额',
        key: 'amount',
        width: 130,
        align: 'right' as const,
        render: (_: unknown, row: ExpenseCase) => <strong>{formatAmount(row)}</strong>,
      },
      { title: '状态', key: 'status', width: 130, render: (_: unknown, row: ExpenseCase) => <StatusBadge status={row.status} /> },
      { title: '风险', key: 'risk', width: 120, render: (_: unknown, row: ExpenseCase) => <RiskBadge level={row.riskLevel} score={row.riskScore} /> },
      {
        title: '当前进展',
        key: 'stage',
        width: 260,
        render: (_: unknown, row: ExpenseCase) => <CaseQueueState expenseCase={row} />,
      },
      {
        title: '更新时间',
        dataIndex: 'updatedAt',
        key: 'updatedAt',
        width: 160,
        render: (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false }),
      },
      {
        title: '',
        key: 'actions',
        width: 104,
        fixed: 'right' as const,
        render: (_: unknown, row: ExpenseCase) => (
          <RowActions
            row={row}
            canEditDraft={canEditDraft}
            canDeleteAnyCase={canDeleteAnyCase}
            deleting={deleteMutation.isPending}
            onEdit={() => navigate(`/cases/${row.id}?edit=1`)}
            onDelete={() => deleteMutation.mutate(row.id)}
          />
        ),
      },
    ],
    [canDeleteAnyCase, canEditDraft, deleteMutation, navigate],
  );

  const updatePage = (nextPage: number, nextSize: number) =>
    setParams((current) => {
      current.set('page', String(nextPage - 1));
      current.set('size', String(nextSize));
      return current;
    });

  return (
    <div className="page-stack">
      <div className="page-heading">
        <div>
          <Typography.Title level={2}>经费申请</Typography.Title>
          <Typography.Text type="secondary">
            {query.data ? `共 ${query.data.total} 笔申请` : '申请、审核与入账进度'}
          </Typography.Text>
        </div>
        {canEditDraft && (
          <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/cases/new')}>
            新建申请
          </Button>
        )}
      </div>

      <section className="data-section case-data-workspace" aria-label="申请列表">
        <div className="filter-bar">
          <span className="filter-label">查询条件</span>
          <Input
            allowClear
            prefix={<SearchOutlined />}
            placeholder="搜索申请人"
            defaultValue={applicant}
            onPressEnter={(event) => {
              const value = event.currentTarget.value.trim();
              setParams((current) => {
                value ? current.set('applicant', value) : current.delete('applicant');
                current.set('page', '0');
                return current;
              });
            }}
          />
          <Select
            allowClear
            placeholder="全部状态"
            value={status}
            options={statusOptions}
            onChange={(value) => setParams((current) => {
              value ? current.set('status', value) : current.delete('status');
              current.set('page', '0');
              return current;
            })}
          />
        </div>

        {screens.md ? (
          <Table<ExpenseCase>
            className="case-list-table"
            rowKey="id"
            loading={query.isLoading}
            dataSource={query.data?.items}
            columns={columns}
            scroll={{ x: 1280 }}
            locale={{ emptyText: <ListEmptyState error={query.isError} /> }}
            onRow={(record) => ({ onClick: () => navigate(`/cases/${record.id}`) })}
            pagination={{
              current: page + 1,
              pageSize: size,
              total: query.data?.total,
              showSizeChanger: true,
              showTotal: (total) => `共 ${total} 笔`,
              onChange: updatePage,
            }}
          />
        ) : (
          <>
            <div className="mobile-case-list">
              {query.data?.items?.map((row) => (
                <article className="mobile-case-item" key={row.id} onClick={() => navigate(`/cases/${row.id}`)}>
                  <div className="mobile-case-title-row">
                    <div>
                      <Typography.Text strong>{row.title}</Typography.Text>
                      <Typography.Text type="secondary">{row.caseNumber}</Typography.Text>
                    </div>
                    <RightOutlined aria-hidden="true" />
                  </div>
                  <div className="mobile-case-status">
                    <StatusBadge status={row.status} />
                    <RiskBadge level={row.riskLevel} score={row.riskScore} />
                  </div>
                  <dl className="mobile-case-meta">
                    <div><dt>申请人</dt><dd>{row.applicantName}</dd></div>
                    <div><dt>申报金额</dt><dd>{formatAmount(row)}</dd></div>
                    <div><dt>下一步</dt><dd>{nextActionLabel(row)}</dd></div>
                  </dl>
                  <RowActions
                    row={row}
                    canEditDraft={canEditDraft}
                    canDeleteAnyCase={canDeleteAnyCase}
                    deleting={deleteMutation.isPending}
                    onEdit={() => navigate(`/cases/${row.id}?edit=1`)}
                    onDelete={() => deleteMutation.mutate(row.id)}
                  />
                </article>
              ))}
              {!query.isLoading && !query.data?.items?.length && (
                <ListEmptyState error={query.isError} />
              )}
            </div>
            {(query.data?.total ?? 0) > size && (
              <Pagination
                simple
                current={page + 1}
                pageSize={size}
                total={query.data?.total}
                onChange={updatePage}
              />
            )}
          </>
        )}
      </section>
    </div>
  );
}

function ListEmptyState({ error }: { error: boolean }) {
  return (
    <div className="list-empty-state">
      <strong>{error ? '申请列表加载失败' : '暂无经费申请'}</strong>
      <span>{error ? '请稍后重新加载页面' : '当前查询条件下没有记录'}</span>
    </div>
  );
}

function RowActions({
  row,
  canEditDraft,
  canDeleteAnyCase,
  deleting,
  onEdit,
  onDelete,
}: {
  row: ExpenseCase;
  canEditDraft: boolean;
  canDeleteAnyCase: boolean;
  deleting: boolean;
  onEdit: () => void;
  onDelete: () => void;
}) {
  const canEdit = canEditDraft && row.status === 'DRAFT';
  const canDelete = canDeleteAnyCase || canEdit;
  if (!canEdit && !canDelete) return null;
  return (
    <Space className="row-actions" onClick={(event) => event.stopPropagation()}>
      {canEdit && <Button type="text" icon={<EditOutlined />} aria-label="修改申请" onClick={onEdit} />}
      {canDelete && (
        <Popconfirm
          title={canDeleteAnyCase ? '删除该申请？' : '删除草稿申请？'}
          description="删除后无法恢复。"
          okText="删除"
          cancelText="取消"
          okButtonProps={{ danger: true }}
          onConfirm={onDelete}
        >
          <Button type="text" danger icon={<DeleteOutlined />} aria-label="删除申请" loading={deleting} />
        </Popconfirm>
      )}
    </Space>
  );
}

function CaseQueueState({ expenseCase }: { expenseCase: ExpenseCase }) {
  const diagnosis = buildCaseDiagnosis(expenseCase);
  return (
    <div className="case-progress-cell">
      <Typography.Text>{diagnosis.title}</Typography.Text>
      <Typography.Text type="secondary" ellipsis={{ tooltip: diagnosis.description }}>
        {diagnosis.description}
      </Typography.Text>
    </div>
  );
}

function formatAmount(expenseCase: ExpenseCase) {
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: expenseCase.currency,
  }).format(expenseCase.claimedAmount);
}

function nextActionLabel(expenseCase: ExpenseCase) {
  if (expenseCase.status === 'APPROVED') {
    if (expenseCase.settlementStatus === 'SUBMITTED') return '入账已完成';
    if (expenseCase.settlementStatus === 'FAILED') return '重试入账';
    return '发起入账';
  }
  return nextActionTitle(expenseCase);
}
