import { Badge } from 'antd';
import { ExpenseCaseStatus } from '../api/contracts';

const labels: Record<ExpenseCaseStatus, string> = {
  DRAFT: '草稿',
  UPLOADED: '已上传',
  EXTRACTING: '提取中',
  EXTRACTED: '已提取',
  POLICY_CHECKING: '制度核验中',
  RISK_CHECKING: '风险分析中',
  WAITING_HUMAN: '等待人工审核',
  APPROVED: '已批准',
  REJECTED: '已拒绝',
  FAILED: '处理失败',
};

export function StatusBadge({ status }: { status: ExpenseCaseStatus }) {
  const state =
    status === 'APPROVED' ? 'success' :
    status === 'REJECTED' || status === 'FAILED' ? 'error' :
    status === 'WAITING_HUMAN' ? 'warning' : 'processing';
  return <Badge status={state} text={labels[status]} />;
}
