import {
  AuditOutlined,
  BarChartOutlined,
  FileSearchOutlined,
  FolderOpenOutlined,
  LogoutOutlined,
  PlusOutlined,
  ProfileOutlined,
  RadarChartOutlined,
} from '@ant-design/icons';
import { Avatar, Button, Layout, Menu, Space, Tag, Typography } from 'antd';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { hasAnyRole, useAuthStore } from '../features/auth/auth-store';
import { logout } from '../features/auth/AuthProvider';

const { Header, Sider, Content } = Layout;

export function AppLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const user = useAuthStore((state) => state.user);
  const canReview = hasAnyRole(user?.roles, ['REVIEWER', 'FINANCE_ADMIN']);
  const canUsePromptGovernance = hasAnyRole(user?.roles, [
    'PROMPT_AUTHOR',
    'PROMPT_REVIEWER',
    'PROMPT_PUBLISHER',
    'FINANCE_ADMIN',
  ]);
  const canObserve = hasAnyRole(user?.roles, ['REVIEWER', 'FINANCE_ADMIN', 'AUDITOR']);
  const items = [
    { key: '/cases', icon: <FolderOpenOutlined />, label: '费用案例' },
    { key: '/cases/new', icon: <PlusOutlined />, label: '新建上传' },
    ...(canReview
      ? [
          { key: '/reviews', icon: <AuditOutlined />, label: '人工审核' },
          { key: '/policies', icon: <FileSearchOutlined />, label: '制度索引' },
          { key: '/evaluation', icon: <BarChartOutlined />, label: '评测报告' },
        ]
      : []),
    ...(canUsePromptGovernance
      ? [{ key: '/prompts', icon: <ProfileOutlined />, label: 'Prompt 审批' }]
      : []),
    ...(canObserve
      ? [{ key: '/observability', icon: <RadarChartOutlined />, label: '可观测性' }]
      : []),
  ];
  const selected = items.find((item) => location.pathname.startsWith(item.key))?.key;

  return (
    <Layout className="app-shell">
      <Sider width={232} className="app-sider">
        <div className="brand">
          <div className="brand-mark">EF</div>
          <div>
            <strong>ExpenseFlow</strong>
            <span>智能费用审核</span>
          </div>
        </div>
        <Menu
          mode="inline"
          selectedKeys={selected ? [selected] : []}
          items={items}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <Layout>
        <Header className="app-header">
          <Typography.Text type="secondary">确定性工作流 · Agent 证据 · 人工决策</Typography.Text>
          <Space>
            {user?.roles.map((role) => <Tag key={role}>{role}</Tag>)}
            <Avatar>{user?.displayName.slice(0, 1)}</Avatar>
            <span>{user?.displayName}</span>
            <Button type="text" icon={<LogoutOutlined />} onClick={() => void logout()}>
              退出
            </Button>
          </Space>
        </Header>
        <Content className="app-content">
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
