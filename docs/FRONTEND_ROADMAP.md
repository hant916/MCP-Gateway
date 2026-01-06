# MCP Gateway 前端开发规划

## 📋 目录

- [当前状态](#当前状态)
- [功能需求](#功能需求)
- [技术栈建议](#技术栈建议)
- [开发路线图](#开发路线图)
- [何时开始前端开发](#何时开始前端开发)
- [API 对接清单](#api-对接清单)

---

## 📊 当前状态

### ✅ 已完成的后端基础设施

| 模块 | 完成度 | 说明 |
|------|--------|------|
| **认证授权** | 100% | JWT + Spring Security，用户注册/登录/刷新令牌 |
| **传输协议** | 100% | SSE, Streamable HTTP, WebSocket, STDIO 全部支持 |
| **MCP 服务器管理** | 100% | 注册、配置、状态管理、连接测试 |
| **会话管理** | 100% | 创建、查询、关闭会话，支持所有传输类型 |
| **计费系统** | 90% | 使用记录、费用计算、订阅管理（缺支付集成） |
| **限流配额** | 100% | ✨ 刚完成！分布式限流、4种算法、订阅分级 |
| **数据库** | 100% | H2/PostgreSQL/MySQL 多环境支持 |
| **API 文档** | 100% | Swagger UI 完整文档 |
| **测试覆盖** | 60% | 57个单元测试 + 集成测试 |
| **生产部署** | 95% | Docker、多环境配置、安全加固 |

### ❌ 前端现状

**当前没有任何前端代码**，仅有：
- Swagger UI（API 测试界面）
- H2 Console（数据库管理，仅开发环境）

**缺失的用户界面：**
- ❌ 用户登录/注册页面
- ❌ 管理控制台
- ❌ 服务器配置界面
- ❌ 会话监控面板
- ❌ 计费和配额管理
- ❌ 工具市场
- ❌ 分析仪表板

---

## 🎯 功能需求

### 1. 用户端功能 (User Portal)

#### 1.1 认证模块
```
页面：
- 登录页 (Login)
- 注册页 (Register)
- 忘记密码 (Forgot Password)
- 个人资料 (Profile)

功能：
- JWT 令牌管理（自动刷新）
- 持久化登录状态
- OAuth 第三方登录（可选）

后端 API：
POST /api/v1/auth/register
POST /api/v1/auth/authenticate
POST /api/v1/auth/refresh-token
GET /api/v1/users/me
PUT /api/v1/users/me
```

#### 1.2 仪表板 (Dashboard)
```
功能：
- 使用概览（本月请求数、费用、配额）
- 活跃会话数量
- 快速操作（创建会话、查看工具）
- 最近活动记录

组件：
- StatisticsCard（统计卡片）
- UsageChart（使用趋势图）
- QuickActions（快速操作）
- ActivityFeed（活动流）

后端 API：
GET /api/v1/users/me/dashboard
GET /api/v1/users/me/statistics
GET /api/v1/users/me/activities
```

#### 1.3 MCP 服务器管理
```
页面：
- 服务器列表 (Servers List)
- 注册服务器 (Register Server)
- 服务器详情 (Server Detail)
- 编辑服务器 (Edit Server)

功能：
- 注册新的 MCP 服务器
- 选择传输协议（SSE/HTTP/WebSocket/STDIO）
- 配置认证（OAuth2/API Key）
- 测试连接
- 查看服务器状态
- 编辑/删除服务器

表单字段：
- 服务名称 (serviceName)
- 描述 (description)
- 图标 URL (iconUrl)
- 仓库 URL (repositoryUrl)
- 传输类型 (transportType)
- 服务端点 (serviceEndpoint)
- 消息端点 (messageEndpoint)
- 认证配置 (authType, clientId, clientSecret...)

后端 API：
GET /api/v1/mcp-servers
POST /api/v1/mcp-servers
GET /api/v1/mcp-servers/{serverId}
PUT /api/v1/mcp-servers/{serverId}
DELETE /api/v1/mcp-servers/{serverId}
POST /api/v1/mcp-servers/{serverId}/test-connection
```

#### 1.4 会话管理
```
页面：
- 会话列表 (Sessions List)
- 创建会话 (Create Session)
- 会话详情 (Session Detail)

功能：
- 创建新会话（选择服务器和传输类型）
- 查看活跃会话
- 查看会话详情（消息记录、统计）
- 终止会话
- 会话历史

组件：
- SessionCard（会话卡片）
- SessionStatus（状态指示器）
- MessageLog（消息日志）

后端 API：
GET /api/v1/sessions
POST /api/v1/sessions
GET /api/v1/sessions/{sessionId}
DELETE /api/v1/sessions/{sessionId}
GET /api/v1/sessions/history
```

#### 1.5 订阅与计费
```
页面：
- 订阅管理 (Subscriptions)
- 升级计划 (Upgrade Plan)
- 使用详情 (Usage Details)
- 账单历史 (Billing History)

功能：
- 查看当前订阅计划
- 升级/降级订阅
- 查看配额使用情况
- 配额用量图表
- 账单记录
- 支付历史

订阅计划展示：
┌──────────────────────────────────────┐
│  当前计划: PRO                        │
│  ────────────────────────────────    │
│  月度配额:  7,500 / 10,000 (75%)     │
│  每日限制:    847 / 1,000 (84.7%)    │
│  每分钟:       42 / 50 (84%)         │
│                                      │
│  下次重置: 2026-02-01                │
│  月费: $49.99                        │
│                                      │
│  [查看详情] [升级到 ENTERPRISE]       │
└──────────────────────────────────────┘

后端 API：
GET /api/v1/users/me/subscriptions
GET /api/v1/users/me/quota
GET /api/v1/users/me/usage
GET /api/v1/users/me/billing/history
POST /api/v1/subscriptions/upgrade
```

#### 1.6 API Key 管理
```
页面：
- API Keys 列表 (API Keys)

功能：
- 查看 API Keys
- 生成新的 API Key
- 撤销 API Key
- 复制 API Key
- 查看使用统计

组件：
- APIKeyCard（密钥卡片）
- GenerateKeyModal（生成对话框）

后端 API：
GET /api/v1/api-keys
POST /api/v1/api-keys/generate
DELETE /api/v1/api-keys/{keyId}
GET /api/v1/api-keys/{keyId}/usage
```

#### 1.7 工具市场 (Marketplace)
```
页面：
- 工具浏览 (Browse Tools)
- 工具详情 (Tool Detail)
- 我的订阅工具 (My Tools)

功能：
- 浏览可用工具
- 搜索和过滤（分类、价格、评分）
- 查看工具详情（描述、定价、评价）
- 订阅工具
- 查看已订阅工具
- 取消订阅

工具卡片：
┌─────────────────────────────────────┐
│ 🔧 GPT-4 Code Assistant             │
│ ★★★★☆ 4.5 (1,234 评价)              │
│                                     │
│ 强大的代码生成和调试助手              │
│                                     │
│ 💰 定价:                             │
│ • 免费: 100次/月                     │
│ • 基础: $9.99 - 1,000次/月           │
│ • 专业: $49.99 - 10,000次/月         │
│ • 企业: 按需定价                     │
│                                     │
│ 📊 23,456 用户使用                   │
│                                     │
│ [查看详情] [订阅]                     │
└─────────────────────────────────────┘

后端 API：
GET /api/v1/marketplace/tools
GET /api/v1/marketplace/tools/{toolId}
POST /api/v1/marketplace/tools/{toolId}/subscribe
DELETE /api/v1/marketplace/subscriptions/{subscriptionId}
GET /api/v1/marketplace/categories
```

---

### 2. 管理端功能 (Admin Portal)

#### 2.1 管理仪表板
```
功能：
- 系统总览（总用户、总请求、总收入）
- 实时监控（活跃会话、请求速率）
- 系统健康状态
- 关键指标趋势

图表：
- 请求量趋势（折线图）
- 用户增长（面积图）
- 收入统计（柱状图）
- 订阅分布（饼图）

后端 API：
GET /api/v1/admin/dashboard
GET /api/v1/admin/statistics
GET /api/v1/admin/health
```

#### 2.2 用户管理
```
页面：
- 用户列表 (Users List)
- 用户详情 (User Detail)
- 用户编辑 (Edit User)

功能：
- 查看所有用户
- 搜索用户（用户名、邮箱）
- 查看用户详情
- 编辑用户信息
- 启用/禁用用户
- 重置用户配额
- 查看用户活动日志

后端 API：
GET /api/v1/admin/users
GET /api/v1/admin/users/{userId}
PUT /api/v1/admin/users/{userId}
PUT /api/v1/admin/users/{userId}/status
POST /api/v1/admin/users/{userId}/quota/reset
GET /api/v1/admin/users/{userId}/activities
```

#### 2.3 限流管理
```
页面：
- 限流规则 (Rate Limit Rules)
- 创建规则 (Create Rule)
- 限流监控 (Rate Limit Monitor)

功能：
- 查看所有限流规则
- 创建/编辑规则
- 启用/禁用规则
- 查看限流统计
- 实时监控被限流的请求
- 临时解除用户限流

规则配置表单：
┌────────────────────────────────────┐
│  限流规则配置                       │
├────────────────────────────────────┤
│ 规则名称: [__________________]     │
│                                    │
│ 限流策略: [▼ Sliding Window]       │
│  • Sliding Window (推荐)           │
│  • Token Bucket                   │
│  • Fixed Window                   │
│  • Leaky Bucket                   │
│                                    │
│ 限制数量: [1000] 次                │
│ 时间窗口: [3600] 秒                │
│                                    │
│ Key 类型:  [▼ user]                │
│  • user (按用户)                   │
│  • ip (按IP)                       │
│  • user:tool (用户+工具)           │
│  • global (全局)                   │
│  • custom (自定义)                 │
│                                    │
│ 应用条件:                           │
│  [+ 添加条件]                       │
│                                    │
│ 错误消息: [__________________]     │
│                                    │
│ [测试规则] [保存] [取消]            │
└────────────────────────────────────┘

后端 API：
GET /api/v1/admin/rate-limits/rules
POST /api/v1/admin/rate-limits/rules
PUT /api/v1/admin/rate-limits/rules/{ruleId}
DELETE /api/v1/admin/rate-limits/rules/{ruleId}
GET /api/v1/admin/rate-limits/statistics
GET /api/v1/admin/rate-limits/violations
POST /api/v1/admin/rate-limits/reset/{userId}
```

#### 2.4 配额管理
```
页面：
- 配额监控 (Quota Monitor)
- 配额告警 (Quota Alerts)

功能：
- 实时配额使用监控
- 配额告警列表
- 批量重置配额
- 配额使用趋势
- 用户配额详情

配额监控面板：
┌──────────────────────────────────────┐
│  配额监控                本月          │
├──────────────────────────────────────┤
│  ⚠️  配额告警 (15)                    │
│  • user123 (PRO)   95% 已使用         │
│  • user456 (BASIC) 98% 已使用         │
│  • user789 (FREE)  100% 已使用        │
│                                      │
│  📊 全局配额使用: 78%                 │
│  ████████████████░░░░                │
│                                      │
│  分级统计:                            │
│  FREE:       65% 平均使用率           │
│  BASIC:      72% 平均使用率           │
│  PRO:        58% 平均使用率           │
│  ENTERPRISE: 34% 平均使用率           │
│                                      │
│  [导出报告] [批量重置] [发送提醒]      │
└──────────────────────────────────────┘

后端 API：
GET /api/v1/admin/quotas/monitor
GET /api/v1/admin/quotas/alerts
POST /api/v1/admin/quotas/batch-reset
GET /api/v1/admin/quotas/analytics
```

#### 2.5 计费规则配置
```
页面：
- 计费规则 (Billing Rules)
- 创建规则 (Create Rule)

功能：
- 配置工具计费规则
- 设置定价模型
- 配置订阅计划
- 折扣和促销管理

后端 API：
GET /api/v1/admin/billing/rules
POST /api/v1/admin/billing/rules
PUT /api/v1/admin/billing/rules/{ruleId}
DELETE /api/v1/admin/billing/rules/{ruleId}
GET /api/v1/admin/billing/plans
POST /api/v1/admin/billing/plans
```

#### 2.6 系统配置
```
页面：
- 全局配置 (System Config)
- 安全设置 (Security)
- 邮件配置 (Email)

功能：
- JWT 配置
- CORS 配置
- Redis 配置
- 限流默认值
- 邮件服务配置
- 日志级别

后端 API：
GET /api/v1/admin/config
PUT /api/v1/admin/config
POST /api/v1/admin/config/test
```

#### 2.7 分析报表
```
页面：
- 使用分析 (Analytics)
- 收入报表 (Revenue Report)
- 用户分析 (User Analytics)

功能：
- 请求量分析（按时间、工具、用户）
- 收入分析（按订阅、工具）
- 用户增长分析
- 留存率分析
- 导出报表（CSV/Excel）

图表类型：
- 时间序列图（请求量趋势）
- 漏斗图（用户转化）
- 热力图（使用时段）
- 地理分布图（用户分布）

后端 API：
GET /api/v1/admin/analytics/usage
GET /api/v1/admin/analytics/revenue
GET /api/v1/admin/analytics/users
GET /api/v1/admin/analytics/export
```

---

## 🛠️ 技术栈建议

### 方案 1: React 生态（推荐）

```typescript
核心框架：
- React 18.2+
- TypeScript 5.0+
- Vite 5.0 (构建工具)

UI 组件库：
- Ant Design 5.x (推荐)
  或
- Material-UI (MUI) 5.x

状态管理：
- Zustand (轻量级，推荐)
  或
- Redux Toolkit

路由：
- React Router 6.x

HTTP 客户端：
- Axios
- React Query / TanStack Query (数据获取和缓存)

图表：
- Recharts (推荐)
  或
- Apache ECharts
  或
- Chart.js

表单处理：
- React Hook Form
- Zod (表单验证)

实时通信：
- Socket.IO Client (WebSocket)
- EventSource (SSE)

工具库：
- date-fns (日期处理)
- lodash-es (工具函数)
- clsx (类名管理)

代码质量：
- ESLint
- Prettier
- Husky (Git Hooks)
```

**项目结构：**
```
frontend/
├── public/
│   └── favicon.ico
├── src/
│   ├── api/              # API 客户端
│   │   ├── client.ts     # Axios 配置
│   │   ├── auth.ts       # 认证 API
│   │   ├── servers.ts    # 服务器 API
│   │   ├── sessions.ts   # 会话 API
│   │   └── ...
│   ├── components/       # 通用组件
│   │   ├── Layout/
│   │   ├── Dashboard/
│   │   ├── Charts/
│   │   └── ...
│   ├── pages/            # 页面组件
│   │   ├── Login/
│   │   ├── Dashboard/
│   │   ├── Servers/
│   │   └── ...
│   ├── hooks/            # 自定义 Hooks
│   │   ├── useAuth.ts
│   │   ├── useServers.ts
│   │   └── ...
│   ├── stores/           # 状态管理
│   │   ├── authStore.ts
│   │   ├── configStore.ts
│   │   └── ...
│   ├── types/            # TypeScript 类型
│   │   ├── auth.ts
│   │   ├── server.ts
│   │   └── ...
│   ├── utils/            # 工具函数
│   │   ├── format.ts
│   │   ├── validation.ts
│   │   └── ...
│   ├── App.tsx
│   └── main.tsx
├── package.json
├── tsconfig.json
└── vite.config.ts
```

### 方案 2: Vue 生态

```typescript
核心框架：
- Vue 3.3+
- TypeScript 5.0+
- Vite 5.0

UI 组件库：
- Element Plus (推荐)
  或
- Ant Design Vue

状态管理：
- Pinia

路由：
- Vue Router 4.x

HTTP 客户端：
- Axios
- VueUse (组合式函数库)

图表：
- ECharts

表单处理：
- VeeValidate
- Yup (验证)
```

### 推荐：React + Ant Design

**理由：**
1. ✅ Ant Design 组件丰富，特别适合后台管理系统
2. ✅ TypeScript 支持完善
3. ✅ 生态成熟，第三方库丰富
4. ✅ 性能优秀（React 18 并发特性）
5. ✅ 招聘容易，开发者多
6. ✅ 中文文档完善

---

## 📅 开发路线图

### Phase 1: MVP - 核心功能（4-6 周）

**Week 1-2: 基础设施**
- [ ] 项目初始化（Vite + React + TypeScript）
- [ ] 配置路由（React Router）
- [ ] 配置 Ant Design
- [ ] 创建基础布局（Header, Sidebar, Footer）
- [ ] 配置 Axios + JWT 拦截器
- [ ] 实现认证流程（登录/注册/刷新令牌）
- [ ] 创建 API 客户端封装

**Week 3-4: 用户核心功能**
- [ ] 实现仪表板页面
- [ ] 实现 MCP 服务器管理（列表、创建、编辑、删除）
- [ ] 实现会话管理（列表、创建、详情）
- [ ] 实现 API Key 管理

**Week 5-6: 计费基础**
- [ ] 实现订阅信息展示
- [ ] 实现配额使用展示
- [ ] 实现使用统计图表
- [ ] 基础响应式适配

**交付物：**
- ✅ 用户可以登录系统
- ✅ 用户可以注册和管理 MCP 服务器
- ✅ 用户可以创建和查看会话
- ✅ 用户可以查看配额使用情况

---

### Phase 2: 商业化功能（4-5 周）

**Week 7-8: 工具市场**
- [ ] 实现工具浏览页面
- [ ] 实现工具详情页面
- [ ] 实现订阅工具功能
- [ ] 实现我的工具页面

**Week 9-10: 限流管理**
- [ ] 实现限流规则列表
- [ ] 实现创建/编辑限流规则
- [ ] 实现限流监控面板
- [ ] 实现限流统计图表

**Week 11: 账单和支付**
- [ ] 实现账单历史页面
- [ ] 实现升级订阅流程
- [ ] 集成支付网关（Stripe/支付宝）
- [ ] 实现发票下载

**交付物：**
- ✅ 工具市场完整功能
- ✅ 限流管理界面
- ✅ 订阅升级和支付流程

---

### Phase 3: 管理端（3-4 周）

**Week 12-13: 管理仪表板**
- [ ] 实现管理员仪表板
- [ ] 实现用户管理
- [ ] 实现系统监控
- [ ] 实现配额管理

**Week 14-15: 分析和配置**
- [ ] 实现使用分析页面
- [ ] 实现收入报表
- [ ] 实现系统配置页面
- [ ] 实现角色权限管理

**交付物：**
- ✅ 完整的管理员控制台
- ✅ 数据分析和报表功能

---

### Phase 4: 优化和完善（持续）

**Week 16+:**
- [ ] 性能优化（代码分割、懒加载）
- [ ] 移动端适配优化
- [ ] 国际化（i18n）
- [ ] 无障碍优化（A11y）
- [ ] 单元测试（Jest + React Testing Library）
- [ ] E2E 测试（Playwright/Cypress）
- [ ] SEO 优化
- [ ] PWA 支持（离线访问）

---

## ⏰ 何时开始前端开发？

### 当前后端完成度评估

| 功能模块 | 完成度 | 前端依赖程度 | 是否阻塞前端开发 |
|---------|-------|------------|----------------|
| 认证授权 API | 100% | 高 | ✅ 不阻塞 |
| MCP 服务器 CRUD | 100% | 高 | ✅ 不阻塞 |
| 会话管理 API | 100% | 高 | ✅ 不阻塞 |
| 限流配额 API | 100% | 中 | ✅ 不阻塞 |
| 计费订阅 API | 90% | 中 | ⚠️ 部分阻塞（缺支付） |
| 工具市场 API | 0% | 高 | ⚠️ 阻塞市场功能 |
| 分析报表 API | 0% | 中 | ⚠️ 阻塞分析功能 |
| 管理员 API | 30% | 中 | ⚠️ 阻塞管理端 |

### 建议：**现在可以开始前端开发！**

#### ✅ 为什么现在是好时机：

1. **核心 API 已完成**（~70%）
   - 认证、服务器管理、会话管理都已就绪
   - 足够支撑 MVP 开发

2. **并行开发效率高**
   - 前端团队开发界面
   - 后端团队补充剩余 API
   - 提前发现接口设计问题

3. **迭代反馈更快**
   - UI/UX 可以尽早验证
   - 用户体验问题早期发现
   - API 设计可根据前端需求调整

4. **降低风险**
   - 避免后期前后端联调时间过长
   - 分散技术风险

#### 📋 开发策略：分阶段并行

**阶段 1（现在开始）：基于现有 API 的核心功能**
```
前端开发（4-6 周）：
✅ 认证页面（登录/注册）
✅ 仪表板（基础统计）
✅ MCP 服务器管理（完整CRUD）
✅ 会话管理（列表、创建、详情）
✅ API Key 管理
✅ 配额展示（读取现有数据）

后端补充（同步进行）：
⏳ 工具市场 API
⏳ 分析报表 API
⏳ 管理员 API
⏳ 支付集成
```

**阶段 2（6-8 周后）：商业化功能**
```
前端开发：
⏳ 工具市场界面
⏳ 订阅升级流程
⏳ 限流管理界面
⏳ 账单和支付

后端补充：
⏳ Webhook 通知
⏳ 邮件服务
⏳ 高级分析
```

**阶段 3（10-12 周后）：管理端和优化**
```
前端开发：
⏳ 管理员控制台
⏳ 性能优化
⏳ 移动端适配

后端补充：
⏳ 高级权限控制
⏳ 审计日志
⏳ 监控告警
```

---

## 🔌 API 对接清单

### ✅ 已就绪的 API（可立即对接）

#### 1. 认证 API
```typescript
// 注册
POST /api/v1/auth/register
Body: { username, email, password }
Response: { token, username }

// 登录
POST /api/v1/auth/authenticate
Body: { username, password }
Response: { token, username }

// 刷新令牌
POST /api/v1/auth/refresh-token
Header: Authorization: Bearer {token}
Response: { token }

// 获取当前用户
GET /api/v1/users/me
Header: Authorization: Bearer {token}
Response: { id, username, email, ... }
```

#### 2. MCP 服务器 API
```typescript
// 获取服务器列表
GET /api/v1/mcp-servers
Response: McpServer[]

// 注册服务器
POST /api/v1/mcp-servers
Body: RegisterMcpServerRequest

// 获取服务器详情
GET /api/v1/mcp-servers/{serverId}
Response: McpServer

// 更新服务器
PUT /api/v1/mcp-servers/{serverId}
Body: UpdateMcpServerRequest

// 删除服务器
DELETE /api/v1/mcp-servers/{serverId}

// 测试连接
POST /api/v1/mcp-servers/{serverId}/test-connection
Response: { success: boolean, message: string }
```

#### 3. 会话 API
```typescript
// 创建会话
POST /api/v1/mcp-server/{serverId}/sessions
Body: { transportType: "sse" | "http" | "websocket" | "stdio" }
Response: SessionDTO

// 获取会话列表
GET /api/v1/sessions
Response: SessionDTO[]

// 获取会话详情
GET /api/v1/sessions/{sessionId}
Response: SessionDTO

// 关闭会话
DELETE /api/v1/sessions/{sessionId}
```

#### 4. API Key API
```typescript
// 生成 API Key
POST /api/v1/api-keys/generate
Response: { keyValue: string, createdAt: string }

// 获取 API Key 列表
GET /api/v1/api-keys
Response: ApiKey[]

// 撤销 API Key
DELETE /api/v1/api-keys/{keyId}
```

#### 5. 订阅和配额 API
```typescript
// 获取订阅信息
GET /api/v1/users/me/subscriptions
Response: Subscription[]

// 获取配额使用情况
GET /api/v1/users/me/quota
Response: {
  tier: string,
  monthlyQuota: number,
  remainingQuota: number,
  usagePercentage: number,
  resetTime: string
}

// 获取使用统计
GET /api/v1/users/me/usage
Response: UsageStats
```

### ⏳ 待开发的 API（前端可先用 Mock）

#### 6. 工具市场 API
```typescript
// 浏览工具
GET /api/v1/marketplace/tools
Query: { category?, priceModel?, page?, size? }

// 工具详情
GET /api/v1/marketplace/tools/{toolId}

// 订阅工具
POST /api/v1/marketplace/tools/{toolId}/subscribe

// 取消订阅
DELETE /api/v1/marketplace/subscriptions/{subscriptionId}
```

#### 7. 管理员 API
```typescript
// 管理仪表板
GET /api/v1/admin/dashboard

// 用户管理
GET /api/v1/admin/users
PUT /api/v1/admin/users/{userId}

// 限流规则管理
GET /api/v1/admin/rate-limits/rules
POST /api/v1/admin/rate-limits/rules
PUT /api/v1/admin/rate-limits/rules/{ruleId}

// 配额管理
GET /api/v1/admin/quotas/monitor
POST /api/v1/admin/quotas/batch-reset
```

#### 8. 分析 API
```typescript
// 使用分析
GET /api/v1/analytics/usage
Query: { startDate, endDate, groupBy }

// 收入分析
GET /api/v1/analytics/revenue
Query: { startDate, endDate }

// 用户分析
GET /api/v1/analytics/users
Query: { metric: "growth" | "retention" | "churn" }
```

---

## 🚀 立即开始：快速启动指南

### Step 1: 创建前端项目

```bash
# 使用 Vite 创建 React + TypeScript 项目
npm create vite@latest mcp-gateway-frontend -- --template react-ts

cd mcp-gateway-frontend

# 安装依赖
npm install

# 安装 Ant Design
npm install antd

# 安装其他依赖
npm install axios react-router-dom zustand
npm install @tanstack/react-query
npm install recharts
npm install react-hook-form zod
npm install date-fns lodash-es

# 安装开发依赖
npm install -D @types/lodash-es
npm install -D eslint prettier
```

### Step 2: 配置代理（开发环境）

```typescript
// vite.config.ts
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',  // 后端地址
        changeOrigin: true,
      }
    }
  }
})
```

### Step 3: 创建 API 客户端

```typescript
// src/api/client.ts
import axios from 'axios'

const apiClient = axios.create({
  baseURL: '/api/v1',
  timeout: 10000,
})

// JWT 拦截器
apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 刷新令牌拦截器
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401) {
      // 处理令牌过期
      // 可以实现自动刷新令牌逻辑
    }
    return Promise.reject(error)
  }
)

export default apiClient
```

### Step 4: 实现第一个页面（登录）

```typescript
// src/pages/Login/index.tsx
import { Form, Input, Button, message } from 'antd'
import { useNavigate } from 'react-router-dom'
import { login } from '@/api/auth'

export default function Login() {
  const navigate = useNavigate()

  const onFinish = async (values: any) => {
    try {
      const response = await login(values)
      localStorage.setItem('token', response.token)
      message.success('登录成功')
      navigate('/dashboard')
    } catch (error) {
      message.error('登录失败')
    }
  }

  return (
    <div className="login-container">
      <Form onFinish={onFinish}>
        <Form.Item name="username" rules={[{ required: true }]}>
          <Input placeholder="用户名" />
        </Form.Item>
        <Form.Item name="password" rules={[{ required: true }]}>
          <Input.Password placeholder="密码" />
        </Form.Item>
        <Form.Item>
          <Button type="primary" htmlType="submit" block>
            登录
          </Button>
        </Form.Item>
      </Form>
    </div>
  )
}
```

---

## 📝 总结

### 当前状态
- ❌ 前端代码：0%
- ✅ 后端 API：70%（核心功能完成）
- ✅ 基础设施：100%（Docker、数据库、限流等）

### 建议行动

#### ✅ 立即开始（本周）
1. 创建 React + TypeScript 项目
2. 配置 Ant Design + 路由
3. 实现认证页面（登录/注册）
4. 实现基础布局（Header/Sidebar）

#### 📅 近期规划（2-4 周）
5. 实现仪表板
6. 实现 MCP 服务器管理
7. 实现会话管理
8. 实现 API Key 管理

#### 🔮 中期规划（4-8 周）
9. 实现工具市场（需后端 API 补充）
10. 实现订阅升级流程
11. 实现限流管理界面

#### 🎯 长期规划（8-12 周）
12. 实现管理员控制台
13. 实现分析报表
14. 性能优化和测试

### 开发模式

**前后端并行开发策略：**
```
Week 1-2:  前端基础设施 + 后端补充 API
Week 3-4:  前端核心页面 + 后端工具市场 API
Week 5-6:  前端计费界面 + 后端分析 API
Week 7-8:  前端市场页面 + 后端管理 API
Week 9-10: 前端管理界面 + 后端优化
Week 11+:  联调、测试、优化
```

---

## 🎯 下一步行动

需要我帮你做以下哪一项？

1. **生成 React 项目脚手架**（完整的目录结构和配置）
2. **创建 TypeScript API 类型定义**（基于后端实体）
3. **实现认证流程**（登录/注册页面 + JWT 管理）
4. **创建布局组件**（Header/Sidebar/Footer）
5. **实现第一个业务页面**（仪表板或服务器管理）
6. **创建 Mock API**（用于待开发的后端接口）

或者，如果你想先完善后端，我可以继续实现：
- 工具市场 API
- 分析报表 API
- 管理员 API
- 支付集成

**你的选择是？** 🤔
