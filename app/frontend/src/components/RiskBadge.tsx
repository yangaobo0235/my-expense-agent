import { Tag } from 'antd';

const labels = { LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险' } as const;
const colors = { LOW: 'green', MEDIUM: 'orange', HIGH: 'red' } as const;

export function RiskBadge({ level, score }: { level?: keyof typeof labels; score?: number }) {
  if (!level) return <Tag>未评分</Tag>;
  return <Tag color={colors[level]}>{labels[level]} · {score ?? '-'}</Tag>;
}
