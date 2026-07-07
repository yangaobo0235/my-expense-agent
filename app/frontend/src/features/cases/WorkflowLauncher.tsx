import { Alert, Button, Checkbox, DatePicker, Form, Input, Modal, Select, Space, message } from 'antd';
import dayjs from 'dayjs';
import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { runCaseWorkflow } from '../../api/expense-api';

interface WorkflowForm {
  category: string;
  region: string;
  employeeGrade: string;
  expenseDate: dayjs.Dayjs;
  flags?: string[];
}

export function WorkflowLauncher({
  caseId,
  initialRequestId,
  buttonText = '开始审核',
  buttonType = 'primary',
  recoveryMode = false,
}: {
  caseId: string;
  initialRequestId?: string;
  buttonText?: string;
  buttonType?: 'primary' | 'default';
  recoveryMode?: boolean;
}) {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [requestId, setRequestId] = useState(() => initialRequestId ?? crypto.randomUUID());
  const [form] = Form.useForm<WorkflowForm>();
  const mutation = useMutation({
    mutationFn: (values: WorkflowForm) => {
      const flags = new Set(values.flags ?? []);
      return runCaseWorkflow(caseId, {
        requestId,
        category: values.category,
        region: values.region,
        employeeGrade: values.employeeGrade,
        expenseDate: values.expenseDate.format('YYYY-MM-DD'),
        duplicateDocument: flags.has('duplicateDocument'),
        dateAnomaly: flags.has('dateAnomaly'),
        sellerAnomaly: flags.has('sellerAnomaly'),
        policyLimitExceeded: flags.has('policyLimitExceeded'),
        missingRequiredDocument: flags.has('missingRequiredDocument'),
        forbiddenExpenseItem: flags.has('forbiddenExpenseItem'),
      });
    },
    onSuccess: () => {
      message.success(recoveryMode ? '已从失败处重新处理' : '审核处理已完成');
      setOpen(false);
      setRequestId(initialRequestId ?? crypto.randomUUID());
      void queryClient.invalidateQueries({ queryKey: ['case', caseId] });
      void queryClient.invalidateQueries({ queryKey: ['case-evidence', caseId] });
      void queryClient.invalidateQueries({ queryKey: ['case-observability', caseId] });
      void queryClient.invalidateQueries({ queryKey: ['observable-runs'] });
    },
  });

  return (
    <>
      <Button type={buttonType} onClick={() => setOpen(true)}>{buttonText}</Button>
      <Modal
        title={recoveryMode ? '从失败处继续处理' : '开始费用审核'}
        open={open}
        onCancel={() => setOpen(false)}
        okText={recoveryMode ? '重试失败环节' : '开始审核'}
        confirmLoading={mutation.isPending}
        onOk={() => form.validateFields().then((values) => mutation.mutate(values))}
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{ region: 'CN', employeeGrade: 'ALL', expenseDate: dayjs(), flags: [] }}
        >
          <Form.Item name="category" label="费用类别" rules={[{ required: true }]}>
            <Select options={['住宿费', '餐饮费', '差旅费', '市内交通费', '业务招待费'].map((value) => ({ value }))} />
          </Form.Item>
          <Space className="page-stack">
            <Form.Item name="region" label="地区" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="employeeGrade" label="员工等级" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="expenseDate" label="费用日期" rules={[{ required: true }]}>
              <DatePicker />
            </Form.Item>
          </Space>
          <Form.Item name="flags" label="补充情况">
            <Checkbox.Group>
              <Space orientation="vertical">
                <Checkbox value="duplicateDocument">已知重复凭证</Checkbox>
                <Checkbox value="dateAnomaly">日期异常</Checkbox>
                <Checkbox value="sellerAnomaly">销售方异常</Checkbox>
                <Checkbox value="policyLimitExceeded">制度额度超标</Checkbox>
                <Checkbox value="missingRequiredDocument">缺少必要凭证</Checkbox>
                <Checkbox value="forbiddenExpenseItem">包含禁止报销项目</Checkbox>
              </Space>
            </Checkbox.Group>
          </Form.Item>
          <Alert
            type={recoveryMode ? 'warning' : 'info'}
            showIcon
            message={recoveryMode ? '将从上次失败的位置继续' : '系统会保留处理进度'}
            description={
              recoveryMode
                ? '已经完成的步骤不会重复处理，只会重新执行失败的部分。'
                : '如果中途失败，案例不会作废，后续可以回到详情页继续处理。'
            }
          />
          {mutation.isError && <div className="error-text">处理失败，请保留当前页面并再次提交。</div>}
        </Form>
      </Modal>
    </>
  );
}
