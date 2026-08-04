# 认证与界面改造计划

## 1. 改造目标

本次改造同时解决两个问题：

1. 移除 Keycloak 及其 OAuth2/OIDC 运行依赖，改为项目自有的账号、角色和会话认证。
2. 保留 React、TypeScript 和 Ant Design 技术栈，重做登录页、应用框架和核心业务页面，降低模板化与 AI 生成感，并修复移动端遮挡、溢出和信息层级问题。

改造完成后，系统应能在不启动 Keycloak 的情况下完成登录、权限控制、业务服务调用和退出登录。

## 2. 现状诊断

### 2.1 认证

- 前端通过 `keycloak-js` 执行 Authorization Code + PKCE，并把 Bearer Token 注入 API 请求。
- 主后端通过 Spring OAuth2 Resource Server 校验 JWT，并从 Keycloak Realm Claim 中提取角色。
- 主后端调用三个 MCP 服务时通过 Keycloak Client Credentials 获取服务令牌。
- 三个业务服务通过 JWK、Issuer 和 Audience 校验 MCP 请求。
- Realm、用户、角色和客户端初始化由 `deploy/keycloak` 下的文件维护。

因此，移除 Keycloak 必须同时覆盖浏览器认证、接口授权和内部服务鉴权，不能只替换登录页面。

### 2.2 界面

当前界面具备基本功能，但属于“可用的技术演示”而不是稳定的业务工作台：

- 品牌使用英文项目名和 `CF` 缩写，产品身份不清晰。
- 顶部存在偏演示性质的宣传文案和英文角色编码。
- 页面大量使用同质卡片、标签和说明文字，主要任务不够突出。
- 移动端侧栏触发器遮挡标题，品牌文本断行，表格内容超出视口。
- 桌面表格直接缩放到移动端，缺少适合触屏浏览的列表结构。

## 3. 目标认证架构

```text
React 浏览器
  -> POST /api/v1/auth/login
  -> HttpOnly Session Cookie + CSRF Cookie
expense-backend
  -> Spring Security
  -> PostgreSQL 用户、角色和 Session

expense-backend
  -> 固定服务令牌
account / expense / audit-history MCP
  -> 常量时间比较服务令牌
```

### 3.1 用户认证

- 在现有 PostgreSQL 中新增 `auth_user`、`auth_user_role` 和 Spring Session 表。
- 用户名作为 `Principal#getName()`，保持现有 `owner_subject` 和审核人字段的语义稳定。
- 密码使用 Spring Security BCrypt 编码，不保存明文。
- 使用服务端 Session 和 HttpOnly Cookie，不在 `localStorage` 或 JavaScript 中保存访问令牌。
- 提供登录、退出和当前会话接口。
- 保留 `STUDENT`、`ADVISOR`、`COLLEGE_REVIEWER`、`FINANCE_ADMIN`、`AUDITOR` 五类角色。
- 通过环境变量初始化一个管理员账号；演示账号由显式初始化脚本创建，不在迁移文件中写入通用密码。

### 3.2 Web 安全

- 使用 Cookie CSRF Token，并由 Axios 通过 `X-XSRF-TOKEN` 回传。
- CORS 仅允许配置中的前端地址，并允许携带凭据。
- Session Cookie 使用 `HttpOnly`、`SameSite=Lax`；生产 HTTPS 环境通过配置启用 `Secure`。
- 登录失败统一返回 401，不区分用户不存在和密码错误。
- 未登录 API 返回结构化 401，权限不足返回结构化 403。

### 3.3 内部 MCP 鉴权

- 移除 Client Credentials Token Provider、JWT Decoder、JWK、Issuer 和 Audience 配置。
- 主后端直接以 `Authorization: Bearer <service-token>` 调用 MCP 服务。
- 三个 MCP 服务从环境变量读取同一高强度服务令牌，并使用常量时间比较。
- Web Session 和内部服务凭据完全分离。

## 4. 目标界面方向

界面定位为“克制、紧凑、可信的校园经费工作台”，而不是 AI 产品展示页。

### 4.1 保留与移除

- 保留 React 19、TypeScript、Ant Design、TanStack Query、Zustand。
- 不引入 Tailwind、shadcn、Framer Motion、3D、玻璃拟态或渐变背景。
- 移除 `keycloak-js`。
- 继续使用 Ant Design 图标，统一按钮、导航和状态表达。

### 4.2 视觉系统

- 产品名统一为“校园经费工作台”，弱化仓库工程名。
- 使用白色与中性灰作为主要表面，以墨绿作为单一操作强调色。
- 圆角控制在 4px 至 8px，主要依赖边框分层，减少大阴影。
- 页面标题、辅助说明、筛选区、数据区形成稳定阅读顺序。
- 英文角色编码映射为中文岗位名称。
- 动效只用于抽屉、菜单和状态反馈，不添加装饰性入场动画。

### 4.3 页面与响应式

- 登录页改为真实账号密码表单，包含校内工作台身份、错误态和提交态。
- 桌面端保留侧栏，但重做品牌、导航选中态和用户菜单。
- 移动端使用顶部栏和抽屉导航，避免折叠侧栏触发器遮挡内容。
- 申请列表在桌面使用表格，在移动端切换为结构化列表项。
- 全局统一页面最大宽度、间距、表单、卡片和表格密度。
- 对详情、审核、制度和评测页面做全局响应式修复，优先保证无横向溢出和操作可达。

## 5. 实施步骤

### 阶段 A：数据库与后端用户认证

1. 添加认证与 Session 数据库迁移。
2. 添加用户实体查询、`UserDetailsService`、密码编码器和初始管理员创建逻辑。
3. 添加 `/api/v1/auth/login`、`/api/v1/auth/logout`、`/api/v1/auth/session`。
4. 将主后端安全配置从无状态 JWT 改为 Session + CSRF。
5. 更新 OpenAPI 安全描述和认证接口测试。

### 阶段 B：内部服务鉴权

1. 将 MCP Client Credentials 替换为固定服务令牌提供器。
2. 将共享 MCP JWT 校验替换为服务令牌过滤器。
3. 更新三个业务服务配置与对应测试。
4. 移除不再使用的 OAuth2 Resource Server 依赖和 Keycloak 专用代码。

### 阶段 C：前端认证与应用框架

1. 移除 `keycloak-js` 和 Bearer Token 注入。
2. Axios 启用 Cookie 凭据和 CSRF Header。
3. Auth Provider 通过会话接口恢复登录状态。
4. 登录页实现账号密码、校验、错误和加载状态。
5. 重做桌面侧栏、移动抽屉、顶部用户菜单和中文角色展示。

### 阶段 D：核心 UI 改造

1. 建立 Ant Design Token 与全局 CSS 变量。
2. 重做申请列表的信息层级、筛选区、空状态和移动列表。
3. 统一详情、审核、制度、评测页面的页面头和内容容器。
4. 修复所有已知移动端遮挡、断行、溢出和过宽固定列。
5. 更新 Playwright 场景与截图断言。

### 阶段 E：配置、部署与文档

1. 从环境模板和 README 移除 Keycloak 配置。
2. 添加管理员初始化、Session、Cookie 和 MCP 服务令牌配置。
3. 移除 `deploy/keycloak`，新增本地用户管理脚本或说明。
4. 更新启动步骤、架构说明和故障排查。

## 6. 验收标准

- 不启动 Keycloak 时，用户可以登录、刷新页面保持会话、退出并被正确重定向。
- 五类角色的现有接口授权行为保持一致。
- CSRF 缺失的写请求被拒绝，合法前端请求可正常执行。
- 主后端能使用服务令牌访问三个 MCP 服务，错误令牌返回 401。
- 仓库源码、配置和依赖中不再存在运行时 Keycloak 引用。
- 前端在 1440px、1024px、390px 三种视口下无导航遮挡和页面级横向滚动。
- 移动端申请列表不依赖横向滚动即可查看标题、状态、申请人和下一步操作。
- `mvn test`、前端类型检查、单元测试、构建和关键 Playwright 场景通过。

## 7. 风险与回滚点

- 现有 Keycloak 用户密码无法迁移，因为系统拿不到原始密码；需创建新用户或要求用户首次重设密码。
- 生产环境若前后端跨站部署，`SameSite`、`Secure` 与 CORS 必须按实际域名调整；推荐由同一域名反向代理。
- 固定 MCP 服务令牌适合当前单部署环境；如果未来有多调用方、令牌轮换或细粒度服务权限需求，应升级为独立机器身份方案。
- 数据库迁移只新增认证与 Session 表，不删除原业务数据；回滚应用版本时新增表可以暂时保留。

## 8. 实施记录

- 2026-08-03：完成现状审计与方案确定，开始按本计划实施。
- 2026-08-03：完成 PostgreSQL 本地用户、Spring Session、Cookie CSRF、登录/会话/退出接口及初始管理员同步逻辑。
- 2026-08-03：完成 Keycloak、OIDC、JWT 运行依赖与部署文件移除，MCP 服务改用独立共享令牌。
- 2026-08-03：完成登录页、应用导航、用户菜单、申请列表、移动抽屉和全局视觉系统改造。
- 2026-08-03：修复独立 MCP Servlet 的 Security Matcher、1280px 详情页交互遮挡和 JSON UTF-8 编码问题。
- 2026-08-03：后端全工程测试通过；前端类型检查、8 项单元测试、生产构建和 11 项 Playwright 场景通过。
- 2026-08-03：三个 MCP 服务的错误令牌均返回 401，真实 MCP 工具枚举、只读调用和受控写入集成测试通过。
- 2026-08-03：1440px、1024px、390px 三种视口验收通过，无页面级横向溢出或导航遮挡；前端开发服务器运行于 `http://localhost:25105`。

## 9. 完成状态

本计划已于 2026-08-03 全部实施并验证完成。当前运行时不再依赖 Keycloak；Web 用户认证与内部 MCP 服务身份已经分离，前端已调整为克制、紧凑的校园经费业务工作台。
