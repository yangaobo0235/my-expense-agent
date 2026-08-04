import {
  AuditOutlined,
  BankOutlined,
  BarChartOutlined,
  FileSearchOutlined,
  FolderOpenOutlined,
  LogoutOutlined,
  MenuOutlined,
  PlusOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { Avatar, Button, Drawer, Dropdown, Grid, Layout, Menu, Space, Typography } from 'antd';
import { useMemo, useState } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { hasAnyRole, roleLabel, useAuthStore } from '../features/auth/auth-store';
import { logout } from '../features/auth/AuthProvider';

const { Header, Sider, Content } = Layout;

export function AppLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const screens = Grid.useBreakpoint();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const user = useAuthStore((state) => state.user);
  const canSubmit = hasAnyRole(user?.roles, ['STUDENT', 'ADVISOR']);
  const canViewReviews = hasAnyRole(user?.roles, [
    'ADVISOR',
    'COLLEGE_REVIEWER',
    'FINANCE_ADMIN',
    'AUDITOR',
  ]);
  const canViewPolicyAndEvaluation = hasAnyRole(user?.roles, [
    'COLLEGE_REVIEWER',
    'FINANCE_ADMIN',
    'AUDITOR',
  ]);
  const items = useMemo(
    () => [
      { key: '/cases', icon: <FolderOpenOutlined />, label: '经费申请' },
      ...(canSubmit ? [{ key: '/cases/new', icon: <PlusOutlined />, label: '新建申请' }] : []),
      ...(canViewReviews ? [{ key: '/reviews', icon: <AuditOutlined />, label: '审核任务' }] : []),
      ...(canViewPolicyAndEvaluation
        ? [
            { key: '/policies', icon: <FileSearchOutlined />, label: '制度管理' },
            { key: '/evaluation', icon: <BarChartOutlined />, label: '质量评测' },
          ]
        : []),
    ],
    [canSubmit, canViewPolicyAndEvaluation, canViewReviews],
  );
  const selected =
    [...items]
      .sort((left, right) => right.key.length - left.key.length)
      .find((item) => location.pathname.startsWith(item.key))?.key ?? '/cases';

  const navigation = (
    <>
      <div className="nav-section-label">经费业务</div>
      <Menu
        mode="inline"
        selectedKeys={[selected]}
        items={items}
        onClick={({ key }) => {
          navigate(key);
          setDrawerOpen(false);
        }}
      />
    </>
  );

  return (
    <Layout className="app-shell">
      <Header className="app-header">
        <div className="system-identity">
          <div className="mobile-brand">
            {!screens.lg && (
              <Button
                type="text"
                className="menu-button"
                icon={<MenuOutlined />}
                aria-label="打开导航"
                onClick={() => setDrawerOpen(true)}
              />
            )}
            <BankOutlined className="system-icon" aria-hidden="true" />
            <div className="system-name">
              <strong>财务管理信息平台</strong>
              <span>校园经费业务</span>
            </div>
          </div>
          {screens.lg && <span className="header-page-context">{pageContext(location.pathname)}</span>}
        </div>
        <Dropdown
          trigger={['click']}
          menu={{
            items: [
              {
                key: 'identity',
                icon: <UserOutlined />,
                label: user?.roles.map(roleLabel).join('、') || '普通用户',
                disabled: true,
              },
              { type: 'divider' },
              { key: 'logout', icon: <LogoutOutlined />, label: '退出登录' },
            ],
            onClick: ({ key }) => key === 'logout' && void logout(),
          }}
        >
          <Button type="text" className="user-menu-button">
            <Space size={8}>
              <Avatar size={28}>{user?.displayName.slice(0, 1)}</Avatar>
              <span className="header-user-name">{user?.displayName}</span>
            </Space>
          </Button>
        </Dropdown>
      </Header>
      <Layout className="app-body">
        {screens.lg && (
          <Sider width={188} trigger={null} className="app-sider">
            <nav aria-label="主导航">{navigation}</nav>
          </Sider>
        )}
        <Content className="app-content">
          <div className="content-container">
            <Outlet />
          </div>
        </Content>
      </Layout>
      {!screens.lg && (
        <Drawer
          title={
            <div className="drawer-title">
              <BankOutlined />
              <span>财务管理信息平台</span>
            </div>
          }
          placement="left"
          size={264}
          open={drawerOpen}
          onClose={() => setDrawerOpen(false)}
          styles={{ body: { padding: 0 } }}
        >
          <nav className="mobile-navigation" aria-label="主导航">{navigation}</nav>
        </Drawer>
      )}
    </Layout>
  );
}

function pageContext(pathname: string) {
  if (pathname.startsWith('/reviews')) return '审核任务';
  if (pathname.startsWith('/policies')) return '制度管理';
  if (pathname.startsWith('/evaluation')) return '质量评测';
  if (pathname.startsWith('/cases/new')) return '新建申请';
  if (/^\/cases\/.+/.test(pathname)) return '申请详情';
  return '经费申请';
}
