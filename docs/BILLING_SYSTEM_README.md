# 💰 MCP Gateway 计费系统

MCP Gateway的Pay-for-Usage计费系统，提供实时使用量跟踪和费用计算。

## 🏗️ 核心组件

- **UsageRecord**: 使用记录实体，记录每次API调用详情
- **BillingRule**: 计费规则配置，支持多种计费模式
- **UsageBillingService**: 核心计费服务，处理使用量记录和费用计算
- **BillingController**: REST API控制器，提供查询和统计接口

## ✨ 主要功能

### 🔄 自动使用量跟踪
- **实时记录**: 自动记录所有API调用
- **异步处理**: 不影响主业务性能
- **完整信息**: 记录时间、端点、状态码、处理时间等
- **防重复**: 检测并警告重复计费记录

### 💳 灵活计费规则
- **多种模式**: 按次数、数据量、时间、组合计费
- **通配符匹配**: API模式支持`*`通配符
- **优先级系统**: 高优先级规则优先匹配
- **失败调用**: 可配置是否对失败调用计费

### 📊 统计分析
- **时间范围查询**: 支持按时间段查询使用记录
- **多维度统计**: 按用户、会话、API端点、状态等维度统计
- **成本分析**: 总费用、平均费用、按时间分布等
- **实时查询**: 获取当前累计费用

## 🗄️ 数据模型

### UsageRecord (使用记录)
```java
- id: UUID (主键)
- sessionId: UUID (会话ID)  
- userId: UUID (用户ID)
- timestamp: Timestamp (调用时间)
- apiEndpoint: String (API端点)
- httpMethod: String (HTTP方法)
- statusCode: Integer (响应状态码)
- requestSize: Long (请求大小,字节)
- responseSize: Long (响应大小,字节)
- processingMs: Integer (处理时间,毫秒)
- costAmount: BigDecimal (费用金额)
- messageType: String (消息类型)
- billingStatus: Enum (计费状态)
```

### BillingRule (计费规则)
```java
- id: UUID (主键)
- ruleName: String (规则名称)
- apiPattern: String (API路径模式,支持通配符)
- httpMethod: String (HTTP方法,可选)
- costPerCall: BigDecimal (每次调用费用)
- costPerKb: BigDecimal (每KB数据费用)
- costPerSecond: BigDecimal (每秒处理时间费用)
- ruleType: Enum (计费类型)
- priority: Integer (优先级)
- isActive: Boolean (是否激活)
```

## 🚀 REST API接口

### 核心端点
```bash
# 健康检查
GET /api/v1/billing/health

# 获取当前费用
GET /api/v1/billing/cost/current?userId={userId}

# 查询使用记录
GET /api/v1/billing/usage?userId={userId}&startTime=2024-01-01T00:00:00Z&endTime=2024-12-31T23:59:59Z&page=0&size=20

# 获取会话使用记录
GET /api/v1/billing/usage/session/{sessionId}

# 使用量摘要
GET /api/v1/billing/usage/summary?userId={userId}&startTime=2024-01-01T00:00:00Z&endTime=2024-12-31T23:59:59Z

# 测试记录使用量
POST /api/v1/billing/test/record?sessionId={sessionId}&apiEndpoint=/api/v1/test&httpMethod=POST&statusCode=200
```

## ⚙️ 默认计费规则

| API模式 | 费用 | 描述 |
|---------|------|------|
| `/api/v1/sse/message` | $0.001/次 | SSE消息发送 |
| `/api/v1/mcp-server/*/sessions` | $0.005/次 | 会话创建 |
| `/api/v1/sessions/*/sse` | $0.002/次 | SSE连接建立 |
| `/api/v1/sessions/*/streamable-http` | $0.003/次 | 流式HTTP请求 |
| `*` | $0.001/次 | 默认规则(兜底) |

## 📈 使用统计示例

### 当前费用查询
```json
{
    "userId": "123e4567-e89b-12d3-a456-426614174000",
    "currentCost": 0.0150,
    "currency": "USD",
    "timestamp": "2024-01-15T10:30:00Z"
}
```

### 使用量摘要
```json
{
    "userId": "123e4567-e89b-12d3-a456-426614174000",
    "totalCalls": 45,
    "totalCost": 0.0510,
    "periodStart": "2024-01-01T00:00:00Z",
    "periodEnd": "2024-01-15T23:59:59Z",
    "apiEndpointStats": [
        {
            "endpoint": "/api/v1/sse/message",
            "calls": 30,
            "cost": 0.030
        }
    ],
    "dailyStats": [
        {
            "date": "2024-01-15",
            "calls": 15,
            "cost": 0.015
        }
    ]
}
```

## 🔧 集成配置

### 自动集成
计费系统已自动集成到`SessionService`中，无需额外配置：

```java
@Service
public class SessionService {
    @Autowired
    private UsageBillingService billingService;
    
    // 自动记录使用量
    public void recordApiUsage(UUID sessionId, String endpoint, int statusCode) {
        billingService.recordUsageAsync(sessionId, endpoint, "GET", statusCode, 
            System.currentTimeMillis(), null, null, 100);
    }
}
```

### 自定义计费规则
可通过数据库直接添加自定义计费规则：

```sql
INSERT INTO billing_rules (id, rule_name, api_pattern, cost_per_call, description, priority, is_active) 
VALUES (gen_random_uuid(), 'Custom API Rule', '/api/v1/custom/*', 0.01, '自定义API计费', 10, true);
```

## 🔒 安全与性能

### 数据保护
- **外键约束**: 确保数据完整性
- **级联删除**: 用户/会话删除时自动清理相关记录
- **敏感信息脱敏**: 对Authorization等敏感信息进行处理

### 性能优化
- **异步处理**: 使用@Async注解，不影响主业务
- **索引优化**: 针对常用查询字段建立索引
- **批量操作**: 支持批量记录和查询

## 🐛 故障排查

### 常见问题
1. **计费规则不匹配**: 检查`api_pattern`和`is_active`状态
2. **重复计费警告**: 正常警告，检查是否有重试逻辑
3. **费用计算错误**: 检查对应的计费规则配置

### 日志级别
- **INFO**: 记录使用量记录的基本信息
- **DEBUG**: 详细的计费规则匹配和费用计算过程
- **WARN**: 重复记录、规则匹配失败等警告

## 🔄 扩展性

### 微服务化准备
- **独立服务**: 计费逻辑已独立封装，易于拆分
- **API标准化**: REST API遵循标准规范
- **数据库独立**: 计费相关表可独立部署

### 功能扩展
- **实时计费**: 当前支持异步计费，可扩展为实时计费
- **配额管理**: 可扩展用户配额和限流功能
- **账单生成**: 可扩展定期账单生成和发送功能

---

*最后更新: 2024-01-15 | 版本: v2.0.0* 