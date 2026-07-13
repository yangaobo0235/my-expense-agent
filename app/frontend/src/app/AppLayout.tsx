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
  const canSubmit = hasAnyRole(user?.roles, ['STUDENT', 'ADVISOR']);
  const canReview = hasAnyRole(user?.roles, ['ADVISOR', 'COLLEGE_REVIEWER', 'FINANCE_ADMIN']);
  const canViewPolicyAndEvaluation = hasAnyRole(user?.roles, [
    'COLLEGE_REVIEWER',
    'FINANCE_ADMIN',
    'AUDITOR',
  ]);
  const canUsePromptGovernance = hasAnyRole(user?.roles, [
    'PROMPT_AUTHOR',
    'PROMPT_REVIEWER',
    'PROMPT_PUBLISHER',
    'FINANCE_ADMIN',
    'AUDITOR',
  ]);
  const canObserve = hasAnyRole(user?.roles, ['COLLEGE_REVIEWER', 'FINANCE_ADMIN', 'AUDITOR']);
  const items = [
    { key: '/cases', icon: <FolderOpenOutlined />, label: '经费申请' },
    ...(canSubmit ? [{ key: '/cases/new', icon: <PlusOutlined />, label: '新建报销' }] : []),
    ...(canReview
      ? [{ key: '/reviews', icon: <AuditOutlined />, label: '人工审核' }]
      : []),
    ...(canViewPolicyAndEvaluation
      ? [
          { key: '/policies', icon: <FileSearchOutlined />, label: '制度库' },
          { key: '/evaluation', icon: <BarChartOutlined />, label: '评测报告' },
        ]
      : []),
    ...(canUsePromptGovernance
      ? [{ key: '/prompts', icon: <ProfileOutlined />, label: 'Prompt 审批' }]
      : []),
    ...(canObserve
      ? [{ key: '/observability', icon: <RadarChartOutlined />, label: '审计观测' }]
      : []),
  ];
  const selected = items.find((item) => location.pathname.startsWith(item.key))?.key;

  return (
    <Layout className="app-shell">
      <Sider width={232} breakpoint="lg" collapsedWidth={0} className="app-sider">
        <div className="brand">
          <div className="brand-mark">CF</div>
          <div>
            <strong>CampusFundFlow</strong>
            <span>校园经费合规审核</span>
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
          <Typography.Text type="secondary" className="header-context">校园制度检索 · Agent 证据 · 人工复核</Typography.Text>
          <Space className="header-user">
            <span className="header-roles">{user?.roles.map((role) => <Tag key={role}>{role}</Tag>)}</span>
            <Avatar>{user?.displayName.slice(0, 1)}</Avatar>
            <span className="header-user-name">{user?.displayName}</span>
            <Button className="logout-button" type="text" icon={<LogoutOutlined />} onClick={() => void logout()}>
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
