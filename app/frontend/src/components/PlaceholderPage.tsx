import { Button, Card, Space, Typography } from 'antd';

export function PlaceholderPage({
  title,
  description,
  links = false,
}: {
  title: string;
  description: string;
  links?: boolean;
}) {
  return (
    <Card>
      <Typography.Title level={2}>{title}</Typography.Title>
      <Typography.Paragraph type="secondary">{description}</Typography.Paragraph>
      {links && (
        <Space>
          <Button href={import.meta.env.VITE_LANGFUSE_URL} target="_blank">打开 Langfuse</Button>
          <Button href={import.meta.env.VITE_GRAFANA_URL} target="_blank">打开 Grafana</Button>
        </Space>
      )}
    </Card>
  );
}
