import { InboxOutlined } from '@ant-design/icons';
import { useMutation } from '@tanstack/react-query';
import axios from 'axios';
import {
  Alert,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Progress,
  Select,
  Space,
  Steps,
  Typography,
  Upload,
  message,
} from 'antd';
import type { UploadFile } from 'antd';
import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { analyzeCase, createCase, uploadCaseDocument } from '../../api/expense-api';
import type { ExpenseCase } from '../../api/contracts';
import { isValidExpenseFile } from './file-validation';

interface CaseForm {
  applicantName: string;
  departmentCode: string;
  title: string;
  claimedAmount: number;
  currency: string;
}

export function NewCasePage() {
  const navigate = useNavigate();
  const [form] = Form.useForm<CaseForm>();
  const [files, setFiles] = useState<UploadFile[]>([]);
  const [progress, setProgress] = useState<Record<string, number>>({});
  const [step, setStep] = useState(0);
  const [createdCase, setCreatedCase] = useState<ExpenseCase>();

  const mutation = useMutation({
    mutationFn: async (values: CaseForm) => {
      setStep(1);
      setCreatedCase(undefined);
      const expenseCase = await createCase(values);
      setCreatedCase(expenseCase);
      for (const uploadFile of files) {
        const file = uploadFile.originFileObj;
        if (!file) continue;
        await uploadCaseDocument(expenseCase.id, file, (percent) =>
          setProgress((current) => ({ ...current, [uploadFile.uid]: percent })),
        );
      }
      setStep(2);
      await analyzeCase(expenseCase.id);
      return expenseCase;
    },
    onSuccess: (expenseCase) => {
      message.success('票据已上传并完成识别');
      navigate(`/cases/${expenseCase.id}`);
    },
    onError: () => setStep(0),
  });

  const fileErrors = useMemo(
    () =>
      files.filter(
        (file) =>
          !isValidExpenseFile({
            type: file.type ?? '',
            size: file.size ?? 0,
          }),
      ),
    [files],
  );
  const mutationErrorMessage = axios.isAxiosError(mutation.error)
    ? (mutation.error.response?.data as { message?: string } | undefined)?.message
    : undefined;

  return (
    <Space orientation="vertical" size="large" className="page-stack">
      <div className="page-heading">
        <div>
          <Typography.Title level={2}>新建费用案例</Typography.Title>
          <Typography.Text type="secondary">
            填写报销信息并上传票据，系统会自动识别票据内容。单文件最大 10 MB。
          </Typography.Text>
        </div>
      </div>
      <Steps
        current={step}
        items={[
          { title: '填写与选择票据' },
          { title: '安全上传' },
          { title: '识别票据' },
        ]}
      />
      <Card>
        <Form
          form={form}
          layout="vertical"
          initialValues={{ currency: 'CNY' }}
          onFinish={(values) => mutation.mutate(values)}
        >
          <div className="form-grid">
            <Form.Item
              name="applicantName"
              label="申请人"
              rules={[{ required: true, message: '请输入申请人姓名' }]}
            >
              <Input maxLength={128} />
            </Form.Item>
            <Form.Item
              name="departmentCode"
              label="部门编码"
              rules={[{ required: true, message: '请输入部门编码' }]}
            >
              <Input maxLength={64} />
            </Form.Item>
          </div>
          <Form.Item
            name="title"
            label="费用标题"
            rules={[{ required: true, message: '请输入费用标题' }]}
          >
            <Input maxLength={256} />
          </Form.Item>
          <div className="form-grid">
            <Form.Item
              name="claimedAmount"
              label="申报金额"
              rules={[{ required: true, message: '请输入申报金额' }]}
            >
              <InputNumber min={0} precision={2} className="full-width" />
            </Form.Item>
            <Form.Item name="currency" label="币种" rules={[{ required: true }]}>
              <Select
                options={[
                  { value: 'CNY', label: 'CNY 人民币' },
                  { value: 'USD', label: 'USD 美元' },
                  { value: 'EUR', label: 'EUR 欧元' },
                ]}
              />
            </Form.Item>
          </div>
          <Form.Item label="票据文件" required>
            <Upload.Dragger
              multiple
              accept=".pdf,.png,.jpg,.jpeg"
              fileList={files}
              beforeUpload={() => false}
              onChange={({ fileList }) => setFiles(fileList.slice(0, 20))}
            >
              <p className="ant-upload-drag-icon"><InboxOutlined /></p>
              <p>拖拽 PDF、PNG 或 JPEG 到此处，或点击选择</p>
              <p className="muted">最多 20 份文件；系统会校验文件类型并避免重复上传</p>
            </Upload.Dragger>
          </Form.Item>
          {fileErrors.length > 0 && (
            <Alert
              type="error"
              showIcon
              message="存在不支持或超过 10 MB 的文件"
              description={fileErrors.map((file) => file.name).join('、')}
            />
          )}
          {files.some((file) => progress[file.uid] !== undefined) && (
            <div className="upload-progress-list">
              {files.map((file) =>
                progress[file.uid] === undefined ? null : (
                  <div key={file.uid}>
                    <span>{file.name}</span>
                    <Progress percent={progress[file.uid]} size="small" />
                  </div>
                ),
              )}
            </div>
          )}
          {mutation.isError && createdCase && (
            <Alert
              className="form-alert"
              type="warning"
              showIcon
              message="案例已创建，后续处理未完成"
              description={
                <Space orientation="vertical" size="small">
                  <Typography.Text>
                    案例和已上传票据已保存在后端，可进入详情页查看票据状态并继续处理。
                  </Typography.Text>
                  {mutationErrorMessage && (
                    <Typography.Text type="secondary">{mutationErrorMessage}</Typography.Text>
                  )}
                  <Space wrap>
                    <Button type="primary" onClick={() => navigate(`/cases/${createdCase.id}`)}>
                      查看案例详情
                    </Button>
                    <Button onClick={() => navigate('/cases')}>返回案例列表</Button>
                  </Space>
                </Space>
              }
            />
          )}
          {mutation.isError && !createdCase && (
            <Alert
              className="form-alert"
              type="error"
              showIcon
              message="创建失败"
              description={mutationErrorMessage ?? '案例未创建，请检查后端服务和表单内容后重试。'}
            />
          )}
          <Button
            type="primary"
            htmlType="submit"
            size="large"
            loading={mutation.isPending}
            disabled={files.length === 0 || fileErrors.length > 0}
          >
            创建、上传并提取
          </Button>
        </Form>
      </Card>
    </Space>
  );
}
