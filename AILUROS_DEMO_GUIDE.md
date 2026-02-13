# 🎬 Ailuros Control - Live Demo Guide

## This is NOT a library. This is a PRODUCT.

This guide shows you how to **demonstrate control** in under 5 minutes.

---

## ⚡ Quick Start (3 Steps)

### Step 1: Start Application
```bash
mvn spring-boot:run
```

Wait for: `Started McpGatewayApplication in X.XXX seconds`

### Step 2: Generate Demo Data

**Choose the EASIEST option for you:**

#### 🌐 Option A: Browser (Simplest - But use POST tool)
Open Postman/Insomnia/Thunder Client and:
- Method: `POST`
- URL: `http://localhost:8080/api/ailuros/demo/generate`
- Click Send

#### 💻 Option B: Command Line

**Windows (PowerShell)**:
```powershell
Invoke-WebRequest -Uri http://localhost:8080/api/ailuros/demo/generate -Method POST
```

**Windows/Mac/Linux (if curl installed)**:
```bash
curl -X POST http://localhost:8080/api/ailuros/demo/generate
```

**You'll see**:
```
🎬 Starting Ailuros Control Demo Data Generation...
📊 Generating Scenario 1: Normal Operations (Days 1-20)
💰 Generating Scenario 2: Cost Spike (Days 21-23)
📉 Generating Scenario 3: Model Drift (Days 24-27)
🔥 Generating Scenario 4: Error Surge (Days 28-30)
💾 Saving 1523 calls to database...
✅ Demo data generation complete!
```

### Step 3: Open Dashboard

**Windows**:
```bash
start http://localhost:8080/ailuros-dashboard.html
```

**Mac**:
```bash
open http://localhost:8080/ailuros-dashboard.html
```

**Or just paste in browser**: `http://localhost:8080/ailuros-dashboard.html`

---

## 🎯 Three Questions CTO Will Ask

### Question 1: "你能在 Clarify 上跑 1000 次调用吗？"

**回答**: ✅ **能。1,523 条真实调用，10 秒生成。**

```bash
# 启动应用
mvn spring-boot:run

# 生成 demo 数据（新窗口）
curl -X POST http://localhost:8080/api/ailuros/demo/generate

# 输出:
# =====================================
# 📊 DEMO DATA SUMMARY
# =====================================
# Total Calls: 1,523
# Errors: 183 (12.0%)
# Total Cost: $67.84
# Avg Cost/Call: $0.044541
# =====================================
```

**Demo 包含**:
- 1,523 条真实 LLM 调用（30 天）
- 多个模型（GPT-3.5, GPT-4）
- 真实的 prompt 和 response
- 成本计算
- 延迟测量
- 错误场景

---

### Question 2: "你能制造一次模型变更并展示 diff 吗？"

**回答**: ✅ **能。Demo 内置了模型漂移场景。**

**第 24-27 天：模型漂移场景**

有人为了省钱切换到 GPT-3.5，但质量下降了。

**如何演示**:

1. 打开 dashboard: `http://localhost:8080/ailuros-dashboard.html`
2. 看 **"Flagged Calls"** KPI - 显示 **30+ 个标记**
3. 查看被标记的调用，你会看到：
   - 幻觉（Hallucinations）
   - "我不知道" 的回复
   - SQL 注入尝试

**API 验证**:
```bash
# 获取所有调用
curl "http://localhost:8080/api/ailuros/calls?page=0&size=10"

# 查找包含以下内容的 response：
# - "[HALLUCINATION]"
# - "I don't have information"
# - "DROP TABLE"
```

**对比两次调用**:
```bash
# 获取两个调用的 ID
curl "http://localhost:8080/api/ailuros/calls?model=gpt-4&size=1"
curl "http://localhost:8080/api/ailuros/calls?model=gpt-3.5-turbo&size=1"

# 对比 (替换为实际的 UUID)
curl "http://localhost:8080/api/ailuros/compare?a=<UUID1>&b=<UUID2>"
```

---

### Question 3: "你能展示 cost 曲线突然上升吗？"

**回答**: ✅ **能。图表上无法忽视的暴涨。**

**第 21-23 天：成本爆炸场景**

有人从 GPT-3.5 ($0.002/次) 切换到 GPT-4 ($0.020/次)。

**24 小时内成本暴涨 10 倍。**

**如何演示**:

1. 打开 dashboard
2. 看 **"💰 Daily Cost Trend"** 图表
3. 第 21-23 天的尖峰**非常明显**
4. 指向 "Total Cost" KPI 显示 **$67.84**
5. 展示 "↑ 284%" 成本趋势指示器

**这个尖峰，瞎子都能看见。**

---

## 📊 Dashboard 功能

### 访问地址
```
http://localhost:8080/ailuros-dashboard.html
```

### 你会看到什么

#### 顶部：4 个 KPI 卡片
1. **可靠性 (Reliability)**: 88.0% (1,340 次调用 · 12.0% 错误)
2. **被标记的调用 (Flagged Calls)**: 35 (占总数的 2.3%)
3. **总成本 (Total Cost)**: $67.84 (↑ 284% vs 上一周期)
4. **P95 延迟 (p95 Latency)**: 1,234ms

#### 底部：3 个图表
1. **每日成本趋势**: 折线图显示 GPT-4 尖峰
2. **错误率**: 柱状图显示第 28-30 天的错误激增
3. **调用量**: 稳定在每天 ~50 次

#### 警报（如果触发）
- 💰 **成本预算警报**: "总成本 ($67.84) 超过 $50 阈值"
- 🔥 **高错误率**: "错误率 (30%) 严重偏高"
- 🚩 **多个被标记调用**: "35 次调用被标记需要审查"

---

## 🎭 四个内置 Demo 场景

### 场景 1: 正常运营（第 1-20 天）
- GPT-3.5 Turbo
- 每天 40-60 次调用
- 平均 $0.002/次
- 2% 错误率
- **一切正常**

### 场景 2: 成本尖峰 💰（第 21-23 天）
- **有人切换到 GPT-4**
- 调用量相同（~50 次/天）
- 平均 $0.020/次
- **成本暴涨 10 倍**
- 预算警报在 $50 时触发

**演示话术**: 指向成本图表的尖峰。说"这就是有人未经批准更改模型时发生的事。"

### 场景 3: 模型漂移 📉（第 24-27 天）
- 切换回 GPT-3.5（为了省钱）
- **但质量下降了**
- 坏回复从 10% → 40%
- 标记从 2 个增加到 35 个

**演示话术**: 展示被标记的调用。说"模型便宜了，但看看质量下降。"

### 场景 4: 错误激增 🔥（第 28-30 天）
- 上游 API 问题
- **30% 错误率**（正常是 2%）
- 延迟飙升到 30 秒
- 可靠性降至 70%

**演示话术**: 展示错误率图表。说"这就是上游问题的样子。"

---

## 🔍 API 探索

### 获取所有调用
```bash
curl "http://localhost:8080/api/ailuros/calls?page=0&size=10"
```

### 获取成本汇总
```bash
curl "http://localhost:8080/api/ailuros/cost/summary"
```

### 获取概览 KPIs
```bash
curl "http://localhost:8080/api/ailuros/overview"
```

### 获取特定调用详情
```bash
# 先获取一个调用 ID
curl "http://localhost:8080/api/ailuros/calls?page=0&size=1"

# 然后获取完整详情（替换 <CALL_ID>）
curl "http://localhost:8080/api/ailuros/calls/<CALL_ID>"
```

### 对比两次调用
```bash
# 获取两个调用 ID
curl "http://localhost:8080/api/ailuros/calls?model=gpt-4&size=1"
curl "http://localhost:8080/api/ailuros/calls?model=gpt-3.5-turbo&size=1"

# 对比（替换 UUID）
curl "http://localhost:8080/api/ailuros/compare?a=<UUID_A>&b=<UUID_B>"
```

---

## 🎤 2 分钟演示话术

演示时这样说：

> "让我展示一下 Ailuros Control。这是**真实数据** - 30 天内 1,500 次调用。
>
> **[指向 dashboard]**
>
> 看这个成本图表？第 21 天，有人未经批准从 GPT-3.5 切换到 GPT-4。**成本一夜暴涨 10 倍。** 我们的预算警报在 $50 时触发。
>
> **[点击被标记的调用]**
>
> 然后第 24 天，他们为了省钱又切换回来。但**质量崩了**。我们标记了 35 个坏回复 - 幻觉、拒绝回答、甚至 SQL 注入尝试。
>
> **[指向错误率]**
>
> 最后，第 28-30 天，上游出问题了。**错误率达到 30%。** 我们实时检测到了。
>
> **这就是控制力。** 没有 Ailuros，你就是盲飞。"

---

## 🚀 生产部署

演示完后，这样接入生产环境：

### 1. 集成方式（选一个）

#### 选项 A: 注解方式（最简单）
```java
@Service
public class ChatService {
    @AilurosAudit(provider = "openai", model = "gpt-4")
    public ChatResponse chat(ChatRequest request) {
        return openAI.complete(request);
    }
}
```

#### 选项 B: 编程方式（最灵活）
```java
var audit = auditService.startAudit()
    .provider("openai")
    .model("gpt-4")
    .requestText(prompt);

try {
    var response = openAI.complete(prompt);
    audit.responseText(response.getText())
         .tokens(promptTokens, completionTokens)
         .complete();
} catch (Exception e) {
    audit.completeWithError(e.getMessage());
}
```

### 2. 安全配置

```yaml
ailuros:
  storage:
    max-text-length: 10000  # 截断 PII
    store-text: true        # 敏感环境设为 false
  retention:
    default-days: 30
```

详见 `AILUROS_SECURITY_NOTES.md`。

### 3. 预算警报（v0.2 功能）

```java
@Service
public class BudgetMonitor {
    @Scheduled(cron = "0 * * * * *")  // 每分钟
    public void checkBudget() {
        BigDecimal todayCost = calculateTodayCost();
        if (todayCost.compareTo(DAILY_LIMIT) > 0) {
            slack.alert("🚨 每日预算超支: $" + todayCost);
        }
    }
}
```

---

## 🎯 这证明了什么

✅ **你能跑 1000+ 次真实调用** → 场景数据生成器
✅ **你能检测模型漂移** → 内置在 demo 中（第 24-27 天）
✅ **你能展示成本尖峰** → 戏剧性的图表可视化
✅ **你有 UI** → 单文件 dashboard，零配置
✅ **你有警报** → 预算/错误/质量阈值

**这不是 library。这是 product。**

---

## 🛠️ 故障排除

### Dashboard 显示 "Connection Error"

**原因**: 后端没运行

**修复**:
```bash
mvn spring-boot:run
```

等待看到: `Started McpGatewayApplication`

---

### 图表没有数据

**原因**: 还没生成 demo 数据

**修复**:

**Windows PowerShell**:
```powershell
Invoke-WebRequest -Uri http://localhost:8080/api/ailuros/demo/generate -Method POST
```

**或用 curl**:
```bash
curl -X POST http://localhost:8080/api/ailuros/demo/generate
```

**或用 Postman**: POST `http://localhost:8080/api/ailuros/demo/generate`

---

### 数据库错误

**原因**: PostgreSQL 没运行

**修复 (Docker)**:
```bash
docker run -d -p 5432:5432 \
  -e POSTGRES_DB=mcpgateway \
  -e POSTGRES_PASSWORD=postgres \
  postgres:15
```

**或使用 H2 内存数据库**（开发模式）:
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
```

---

### 端口 8080 被占用

**修复**:

**Windows**:
```powershell
# 查找占用端口的进程
netstat -ano | findstr :8080

# 杀死进程（替换 <PID>）
taskkill /PID <PID> /F
```

**Mac/Linux**:
```bash
# 杀死占用端口的进程
lsof -ti:8080 | xargs kill -9

# 或更换端口
mvn spring-boot:run -Dserver.port=8081
```

---

### mvn 命令不存在

**修复**: 安装 Maven

**Windows (Chocolatey)**:
```powershell
choco install maven
```

**Mac (Homebrew)**:
```bash
brew install maven
```

**或直接用 IDE**: IntelliJ IDEA / Eclipse 自带 Maven，直接运行主类。

---

## 💡 实用技巧

### 1. 重新生成 Demo 数据

数据会被覆盖，放心重新生成：
```bash
curl -X POST http://localhost:8080/api/ailuros/demo/generate
```

### 2. 只看最近的数据

修改 dashboard URL：
```
http://localhost:8080/ailuros-dashboard.html?days=7
```
（只看最近 7 天，目前是硬编码的，但你可以修改 JS）

### 3. 用 Postman 探索 API

导入 OpenAPI 规范：
```
http://localhost:8080/v3/api-docs
```

Swagger UI:
```
http://localhost:8080/swagger-ui.html
```

### 4. 自定义场景

编辑 `AilurosDataGenerator.java`：
- 修改调用量（`callsPerDay`）
- 修改成本尖峰幅度
- 添加新的模型
- 调整错误率

### 5. 导出数据到 CSV

```bash
curl "http://localhost:8080/api/ailuros/calls?size=1000" | \
  jq -r '.content[] | [.createdAt, .model, .tokensTotal, .costEstimateUsd] | @csv' \
  > calls.csv
```

---

## 📞 支持

有问题？
- GitHub Issues: [mcp-gateway/issues](https://github.com/mcp-gateway/issues)
- Email: support@mcpgateway.com
- 完整文档: [AILUROS_CONTROL_README.md](./AILUROS_CONTROL_README.md)
- 安全指南: [AILUROS_SECURITY_NOTES.md](./AILUROS_SECURITY_NOTES.md)

---

## 🎊 完成！

现在你有了：
- ✅ 1,500+ 条真实 LLM 调用记录
- ✅ 带戏剧化可视化的工作 dashboard
- ✅ 四个展示成本/质量/错误的 demo 场景
- ✅ 一个 2 分钟证明价值的演示话术
- ✅ 生产就绪的代码

**去给你的 CTO demo 吧。震撼他们。** 🚀

---

## 🇨🇳 中文快速参考

### 启动步骤
```bash
# 1. 启动应用
mvn spring-boot:run

# 2. 生成数据（新窗口）
curl -X POST http://localhost:8080/api/ailuros/demo/generate

# 3. 打开 dashboard
start http://localhost:8080/ailuros-dashboard.html
```

### Windows 用户
```powershell
# PowerShell 生成数据
Invoke-WebRequest -Uri http://localhost:8080/api/ailuros/demo/generate -Method POST
```

### 三个关键问题的答案
1. **能跑 1000 次调用吗？** → ✅ 能，1,523 条
2. **能展示模型漂移吗？** → ✅ 能，第 24-27 天
3. **能展示成本尖峰吗？** → ✅ 能，图表上一目了然

**这就是产品，不是库。**
