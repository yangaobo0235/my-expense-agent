import { UploadOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Button, DatePicker, Descriptions, Empty, Form, Input, Space, Table, Tag, Timeline, Typography, Upload, message } from 'antd';
import type { UploadFile } from 'antd';
import dayjs from 'dayjs';
import { ExpenseCaseStatus } from '../../api/contracts';
import { getCurrentMoreInfoRequest, listCaseDocumentVersions, listCaseReviewRuns, submitMoreInfo } from '../../api/expense-api';

interface SubmissionForm {
  category: string;
  expenseDate: dayjs.Dayjs;
  reopenReason: string;
  files: UploadFile[];
}

export function GovernedReviewTimeline({
  caseId,
  status,
  canSubmit,
}: {
  caseId: string;
  status: ExpenseCaseStatus;
  canSubmit: boolean;
}) {
  const [form] = Form.useForm<SubmissionForm>();
  const queryClient = useQueryClient();
  const runs = useQuery({
    queryKey: ['case-review-runs', caseId],
    queryFn: () => listCaseReviewRuns(caseId),
  });
  const versions = useQuery({
    queryKey: ['case-document-versions', caseId],
    queryFn: () => listCaseDocumentVersions(caseId),
  });
  const moreInfo = useQuery({
    queryKey: ['case-more-info-request', caseId],
    queryFn: () => getCurrentMoreInfoRequest(caseId),
    enabled: status === 'WAITING_MORE_INFO',
    retry: false,
  });
  const submission = useMutation({
    mutationFn: async (values: SubmissionForm) => {
      const file = values.files?.[0]?.originFileObj;
      if (!file || !moreInfo.data) throw new Error('请选择补充材料');
      return submitMoreInfo(caseId, {
        taskId: moreInfo.data.id,
        file,
        category: values.category,
        expenseDate: values.expenseDate.format('YYYY-MM-DD'),
        reopenReason: values.reopenReason,
      });
    },
    onSuccess: (result) => {
      message.success(`补充材料已提交，当前文档版本 V${result.documentVersion}`);
      form.resetFields();
      void Promise.all([
        queryClient.invalidateQueries({ queryKey: ['case', caseId] }),
        queryClient.invalidateQueries({ queryKey: ['case-documents', caseId] }),
        queryClient.invalidateQueries({ queryKey: ['case-review-runs', caseId] }),
        queryClient.invalidateQueries({ queryKey: ['case-document-versions', caseId] }),
        queryClient.invalidateQueries({ queryKey: ['case-more-info-request', caseId] }),
        queryClient.invalidateQueries({ queryKey: ['case-evidence', caseId] }),
      ]);
    },
    onError: (error) => message.error(error instanceof Error ? error.message : '补充材料提交失败'),
  });

  const currentVersion = versions.data?.at(-1)?.version;
  return (
    <Space orientation="vertical" size="large" className="page-stack">
      <Descriptions bordered size="small" column={2}>
        <Descriptions.Item label="当前文档版本">{currentVersion ? `V${currentVersion}` : '-'}</Descriptions.Item>
        <Descriptions.Item label="审核次数">{runs.data?.length ?? 0}</Descriptions.Item>
      </Descriptions>

      {status === 'WAITING_MORE_INFO' && (
        <Alert
          type="warning"
          showIcon
          title="等待补充材料"
          description={(moreInfo.data?.requiredMaterials ?? []).join('、') || '审核员已要求补充材料'}
        />
      )}

      {status === 'WAITING_MORE_INFO' && canSubmit && moreInfo.data && (
        <Form<SubmissionForm>
          form={form}
          layout="vertical"
          initialValues={{ reopenReason: '补充材料后重新审核' }}
          onFinish={(values) => submission.mutate(values)}
        >
          <div className="policy-search-form-grid">
            <Form.Item name="category" label="经费科目" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="expenseDate" label="支出日期" rules={[{ required: true }]}>
              <DatePicker className="full-width" />
            </Form.Item>
            <Form.Item name="reopenReason" label="重新审核原因" rules={[{ required: true }]}>
              <Input maxLength={1000} />
            </Form.Item>
            <Form.Item
              name="files"
              label="补充材料"
              valuePropName="fileList"
              getValueFromEvent={(event) => event?.fileList}
              rules={[{ required: true, message: '请选择补充材料' }]}
            >
              <Upload beforeUpload={() => false} maxCount={1}>
                <Button icon={<UploadOutlined />}>选择文件</Button>
              </Upload>
            </Form.Item>
          </div>
          <Button type="primary" htmlType="submit" loading={submission.isPending}>
            提交并重新审核
          </Button>
        </Form>
      )}

      <div>
        <Typography.Title level={5}>审核 Run 时间线</Typography.Title>
        {!runs.data?.length ? (
          <Empty description="暂无审核 Run" />
        ) : (
          <Timeline
            items={[...runs.data].reverse().map((run) => ({
              color: run.status === 'SUCCEEDED' ? 'green' : run.status === 'FAILED' ? 'red' : 'blue',
              children: (
                <Space orientation="vertical" size={2}>
                  <Space wrap>
                    <Typography.Text strong>V{run.documentVersion} · {commandLabel(run.commandType)}</Typography.Text>
                    <Tag>{runStatusLabel(run.status)}</Tag>
                    {run.routeAction && <Tag color="blue">{routeLabel(run.routeAction)}</Tag>}
                  </Space>
                  {run.reopenReason && <Typography.Text>{run.reopenReason}</Typography.Text>}
                  {run.waitingReason && <Typography.Text type="warning">{run.waitingReason}</Typography.Text>}
                  <Typography.Text type="secondary">
                    Run {shortId(run.id)}{run.previousRunId ? ` · 父 Run ${shortId(run.previousRunId)}` : ''} · {new Date(run.startedAt).toLocaleString('zh-CN')}
                  </Typography.Text>
                </Space>
              ),
            }))}
          />
        )}
      </div>

      <div>
        <Typography.Title level={5}>文档版本</Typography.Title>
        <Table
          rowKey="version"
          size="small"
          pagination={false}
          loading={versions.isLoading}
          dataSource={versions.data ?? []}
          columns={[
            { title: '版本', dataIndex: 'version', width: 80, render: (value: number) => `V${value}` },
            { title: '来源', dataIndex: 'sourceType', render: sourceLabel },
            { title: '替换版本', dataIndex: 'replacesVersion', render: (value?: number) => value ? `V${value}` : '-' },
            { title: '上传人', dataIndex: 'uploadedBy' },
            { title: '时间', dataIndex: 'createdAt', render: (value: string) => new Date(value).toLocaleString('zh-CN') },
          ]}
        />
      </div>
    </Space>
  );
}

function commandLabel(value: string) {
  return value === 'REVIEW_AGAIN' ? '重新审核' : value === 'RESTORE' ? '恢复运行' : '首次审核';
}

function runStatusLabel(value: string) {
  return value === 'SUCCEEDED' ? '已完成' : value === 'FAILED' ? '失败' : '处理中';
}

function routeLabel(value: string) {
  const labels: Record<string, string> = {
    LOW_RISK_PATH: '低风险路径',
    REQUEST_MORE_INFO: '补材料',
    COLLEGE_REVIEW: '学院复核',
    FINANCE_REVIEW: '财务复核',
    DEPENDENCY_REVIEW: '依赖异常复核',
  };
  return labels[value] ?? value;
}

function sourceLabel(value: string) {
  return value === 'MORE_INFO_SUBMISSION' ? '补充材料' : '首次上传';
}

function shortId(value: string) {
  return value.length > 12 ? `${value.slice(0, 6)}...${value.slice(-4)}` : value;
}
