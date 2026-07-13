import { Alert, Button, DatePicker, Form, Modal, Select, message } from 'antd';
import dayjs from 'dayjs';
import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { runCaseWorkflow } from '../../api/expense-api';

interface WorkflowForm {
  category: string;
  expenseDate: dayjs.Dayjs;
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
      return runCaseWorkflow(caseId, {
        requestId,
        category: values.category,
        expenseDate: values.expenseDate.format('YYYY-MM-DD'),
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
        title={recoveryMode ? '从失败处继续处理' : '开始经费合规审核'}
        open={open}
        onCancel={() => setOpen(false)}
        okText={recoveryMode ? '重试失败环节' : '开始审核'}
        confirmLoading={mutation.isPending}
        onOk={() => form.validateFields().then((values) => mutation.mutate(values))}
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{ expenseDate: dayjs() }}
        >
          <Form.Item name="category" label="经费科目" rules={[{ required: true }]}>
            <Select options={['竞赛差旅费', '会议注册费', '实验耗材费', '活动物料费', '打印装订费', '市内交通费'].map((value) => ({ value }))} />
          </Form.Item>
          <Form.Item name="expenseDate" label="申报支出日期" rules={[{ required: true }]}>
            <DatePicker className="full-width" />
          </Form.Item>
          <Alert
            type={recoveryMode ? 'warning' : 'info'}
            showIcon
            title={recoveryMode ? '将从上次失败的位置继续' : '合规事实由系统自动核验'}
            description={
              recoveryMode
                ? '已经完成的步骤不会重复处理，只会重新执行失败的部分。'
                : '重复票据、项目预算、制度依据、商户、日期和票据明细均以服务端证据为准。'
            }
          />
          {mutation.isError && <div className="error-text">处理失败，请保留当前页面并再次提交。</div>}
        </Form>
      </Modal>
    </>
  );
}
