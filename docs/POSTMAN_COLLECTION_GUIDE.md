# 📮 MCP Gateway Postman Collection 使用指南

完整的MCP Gateway API测试集合，包含计费系统和SSE代理功能。

## 🚀 快速开始

### 导入和配置
1. 在Postman中导入 `mcp-gateway-complete.postman_collection.json`
2. 确认环境变量：
   - `base_url`: `http://localhost:8080`
   - 其他变量将自动设置

### 核心API流程

#### 1. 认证
```bash
POST /api/v1/auth/authenticate
{
    "username": "testuser", 
    "password": "password123"
}
```
✅ 自动设置 `jwt_token` 和 `user_id`

#### 2. 注册MCP服务器
```bash
POST /api/v1/mcp-server/register
{
    "serviceName": "Awesome MCP Server",
    "transport": {
        "type": "SSE",
        "config": {
            "serviceEndpoint": "https://remote-aiai.tianyi-han.workers.dev/sse",
            "messageEndpoint": "https://remote-aiai.tianyi-han.workers.dev/sse/message",
            "sessionIdLocation": "QUERY_PARAM",
            "sessionIdParamName": "sessionId"
        }
    }
}
```
✅ 自动设置 `server_id`

#### 3. 创建会话
```bash
POST /api/v1/mcp-server/{serverId}/sessions
{
    "transportType": "SSE"
}
```
✅ 自动设置 `session_id`

#### 4. 发送消息
支持两种格式：

**JSON-RPC (推荐)**:
```bash
POST /api/v1/sse/message?sessionId={sessionId}
{
    "jsonrpc": "2.0",
    "method": "tools/list",
    "params": {},
    "id": "request-1"
}
```

**标准格式**:
```bash
{
    "type": "invoke_tool",
    "tool": "file_search",
    "arguments": {
        "query": "hello world"
    }
}
```

## 💰 计费系统API

### 核心端点
- **健康检查**: `GET /api/v1/billing/health`
- **当前费用**: `GET /api/v1/billing/cost/current?userId={userId}`
- **使用记录**: `GET /api/v1/billing/usage?userId={userId}`
- **会话记录**: `GET /api/v1/billing/usage/session/{sessionId}`
- **使用摘要**: `GET /api/v1/billing/usage/summary?userId={userId}`

### 默认计费规则
- SSE消息: $0.001/次
- 会话创建: $0.005/次
- SSE连接: $0.002/次
- 流式HTTP: $0.003/次

## 🔄 完整工作流

Collection提供了"Complete SSE Workflow with Billing"，包含：
1. 认证 → 2. 注册服务器 → 3. 创建会话 → 4. 建立连接 → 5. 发送消息 → 6. 查看计费

## 🧪 自动化测试

### 全局测试
- ✅ 响应时间 < 5秒
- ✅ 状态码验证
- ✅ 自动变量设置
- ✅ 计费信息日志

### 使用技巧
1. **变量管理**: 关键ID自动设置
2. **消息格式**: 推荐JSON-RPC 2.0格式
3. **错误处理**: 检查HTTP状态码和响应结构
4. **计费监控**: 每次调用后自动显示费用信息

## 📊 响应示例

### 计费响应
```json
{
    "userId": "123e4567-e89b-12d3-a456-426614174000",
    "currentCost": 0.0150,
    "currency": "USD",
    "timestamp": "2024-01-15T10:30:00Z"
}
```

### 使用记录
```json
{
    "content": [
        {
            "sessionId": "session-id",
            "apiEndpoint": "/api/v1/sse/message",
            "httpMethod": "POST",
            "statusCode": 200,
            "costAmount": 0.001,
            "billingStatus": "SUCCESS"
        }
    ],
    "totalElements": 1
}
```

## ⚠️ 注意事项

1. **认证**: 先执行认证获取JWT token
2. **会话管理**: 会话有过期时间
3. **计费**: 所有API调用都产生费用
4. **JSON-RPC**: 确保请求ID唯一性

---

**版本**: v2.0.0 | **更新日期**: 2024-01-15 