# W1 Harness 内核实现计划

> **For Claude:** REQUIRED SUB-SKILL: 采用 superpowers:subagent-driven-development / executing-plans 精神，在单会话中按任务顺序手动执行；任务之间高度耦合，需顺序推进。

**Goal:** 完成 M1（W1）内核：自研 AgentLoop、ToolRegistry、Permission、Retry/熔断、ContextManager（含压缩）、LoopGuard、两个真实/模拟工具、最小 SSE 对话接口与前端页，确保 harness 单测覆盖率 ≥60% 且 CI 全绿。

**Architecture:** 严格遵循 ADR-001：clawops-harness 为主代码零第三方依赖，仅通过自研 SPI（`ModelClient`、`ToolDefinition`、`ToolResult`、`AgentEventListener` 等）与外界交互；LangChain4j/Spring/HTTP 全部隔离在 harness 之外，分别由 adapter、gateway、connector 实现。W1 先不引入持久化与 RAG，所有状态放内存。

**Tech Stack:** Java 25 + Spring Boot 4.1.1 + LangChain4j 1.20.2（仅 adapter/测试）+ Maven + JUnit 5 + AssertJ + ArchUnit。

---

## Task 1：清理项目内 JDK 副本

**Files:**
- 删除：`D:\Dev\Projects\ClawOps\.tools\jdk-25.0.4.1+1`
- 修改：`docs/adr/ADR-000-version-lock.md`（补充说明本地已改用系统 JDK）

**验证：** `./mvnw -v` 显示 Java version 25.0.4.1（Eclipse Adoptium）。

---

## Task 2：定义 Harness 核心 SPI

**Files:**
- 创建：`clawops-harness/src/main/java/com/clawops/harness/spi/AgentMessage.java`
- 创建：`clawops-harness/src/main/java/com/clawops/harness/spi/ToolCall.java`
- 创建：`clawops-harness/src/main/java/com/clawops/harness/spi/ToolResult.java`
- 创建：`clawops-harness/src/main/java/com/clawops/harness/spi/ToolDefinition.java`
- 创建：`clawops-harness/src/main/java/com/clawops/harness/spi/Permission.java`
- 创建：`clawops-harness/src/main/java/com/clawops/harness/spi/ModelClient.java`
- 创建：`clawops-harness/src/main/java/com/clawops/harness/spi/ModelResponse.java`
- 创建：`clawops-harness/src/main/java/com/clawops/harness/spi/TokenCounter.java`
- 创建：`clawops-harness/src/main/java/com/clawops/harness/spi/AgentEventListener.java`
- 测试：`clawops-harness/src/test/java/com/clawops/harness/spi/SpiMessageTest.java`

**要点：**
- `AgentMessage` 仅含 `role`（system/user/assistant/tool）、`content`、`toolCalls`、`toolCallId`。
- `ToolDefinition` 含 `name / description / permission / parameterSchema / executor`。
- `ModelClient` 抽象单次 chat：`ModelResponse chat(List<AgentMessage> messages, List<ToolDefinition> tools)`。
- 全部接口禁止出现 LangChain4j/Spring/HTTP 类型。

---

## Task 3：实现 ToolRegistry 与注解注册

**Files:**
- 创建：`clawops-harness/src/main/java/com/clawops/harness/tool/Tool.java`
- 创建：`clawops-harness/src/main/java/com/clawops/harness/tool/ToolParam.java`
- 创建：`clawops-harness/src/main/java/com/clawops/harness/tool/ToolRegistry.java`
- 创建：`clawops-harness/src/main/java/com/clawops/harness/tool/JsonSchemaBuilder.java`
- 创建：`clawops-harness/src/main/java/com/clawops/harness/tool/ParameterValidator.java`
- 测试：`clawops-harness/src/test/java/com/clawops/harness/tool/ToolRegistryTest.java`

**要点：**
- `@Tool(name, description, permission)` 标注方法；`@ToolParam(description, required)` 标注参数。
- `ToolRegistry.register(Object provider)` 扫描注解，生成 `ToolDefinition`（JSON Schema 通过 `JsonSchemaBuilder`）。
- `ParameterValidator.validate(toolName, schema, args)` 检查必填、类型，错误抛 `ToolParameterInvalidException`（后续映射错误码 3001）。

---

## Task 4：实现权限分级与拦截

**Files:**
- 创建：`clawops-harness/src/main/java/com/clawops/harness/permission/PermissionManager.java`
- 创建：`clawops-harness/src/main/java/com/clawops/harness/permission/PermissionDeniedException.java`
- 测试：`clawops-harness/src/test/java/com/clawops/harness/permission/PermissionManagerTest.java`

**要点：**
- `Permission` 已定义在 SPI；`PermissionManager` 维护会话最大权限。
- 默认会话权限 `WRITE`；`DANGEROUS` 工具调用被拒绝并返回 `ToolResult.error(FORBIDDEN, "需要审批")`。
- 提供 `elevate(Permission)` 用于后续审批通过后的临时提权。

---

## Task 5：实现重试与熔断策略

**Files:**
- 创建：`clawops-harness/src/main/java/com/clawops/harness/retry/RetryPolicy.java`
- 创建：`clawops-harness/src/main/java/com/clawops/harness/retry/CircuitBreaker.java`
- 创建：`clawops-harness/src/main/java/com/clawops/harness/retry/RetryExecutor.java`
- 测试：`clawops-harness/src/test/java/com/clawops/harness/retry/RetryExecutorTest.java`

**要点：**
- 指数退避：baseDelay 500ms，maxAttempts 3，超时 5s（可配置）。
- `CircuitBreaker` 5 次失败/30s 窗口后打开，10s 半开。
- 重试耗尽或熔断返回 `ToolResult.degraded("降级结果")`，AgentLoop 继续而非崩溃。

---

## Task 6：实现 ContextManager 与压缩

**Files:**
- 创建：`clawops-harness/src/main/java/com/clawops/harness/context/ContextManager.java`
- 创建：`clawops-harness/src/main/java/com/clawops/harness/context/DefaultTokenCounter.java`
- 创建：`clawops-harness/src/main/java/com/clawops/harness/context/CompressionStrategy.java`
- 测试：`clawops-harness/src/test/java/com/clawops/harness/context/ContextManagerTest.java`

**要点：**
- 分层组装：System → Role → Tool 描述 → Memory → Current Task。
- 阈值：默认 maxTokens 4000，compressionThresholdRatio 0.8，触发时保留 System/Role/Current Task，把老对话压缩为一条 summary。
- `DefaultTokenCounter` 用字符数/4 做近似，真实场景由 adapter 注入 LLM tokenizer。

---

## Task 7：实现死循环检测

**Files:**
- 创建：`clawops-harness/src/main/java/com/clawops/harness/loop/LoopGuard.java`
- 测试：`clawops-harness/src/test/java/com/clawops/harness/loop/LoopGuardTest.java`

**要点：**
- 记录最近 N 次工具调用（工具名 + 规范化参数 JSON）。
- 同一调用连续出现 2 次即触发 guard，AgentLoop 终止并返回结果附带 `LOOP_DETECTED` 信息。

---

## Task 8：实现 AgentLoop

**Files:**
- 创建：`clawops-harness/src/main/java/com/clawops/harness/loop/AgentLoop.java`
- 创建：`clawops-harness/src/main/java/com/clawops/harness/loop/LoopConfig.java`
- 创建：`clawops-harness/src/main/java/com/clawops/harness/loop/LoopResult.java`
- 测试：`clawops-harness/src/test/java/com/clawops/harness/loop/AgentLoopTest.java`

**要点：**
- 输入 task，回调 `AgentEventListener`。
- 每轮：contextManager.build() → modelClient.chat() → 若文本则 onDelta + 结束；若 toolCall 则 onToolCall → permission/loopGuard/retry → toolExecutor → onToolResult → 加入 context。
- 限制：maxSteps（默认 10）、maxDuration（默认 60s）。

---

## Task 9：实现 metrics.query 与 log.search 工具

**Files:**
- 创建：`clawops-connector/src/main/java/com/clawops/connector/metrics/MetricsConnector.java`
- 创建：`clawops-connector/src/main/java/com/clawops/connector/log/MockLogConnector.java`
- 创建：`clawops-gateway/src/main/java/com/clawops/gateway/harness/HarnessConfig.java`
- 测试：`clawops-connector/src/test/java/com/clawops/connector/metrics/MetricsConnectorTest.java`
- 测试：`clawops-connector/src/test/java/com/clawops/connector/log/MockLogConnectorTest.java`

**要点：**
- `MetricsConnector.query(String promql)` 调用 `http://localhost:9090/api/v1/query`；单元测试使用 WireMock（或 JDK HttpServer） mock。
- `MockLogConnector.search(String level, String keyword)` 返回内存列表，支持 `inject(String line)` 注入 ERROR。
- Gateway `HarnessConfig` 实例化 connector 并注册到 `ToolRegistry`。

---

## Task 10：实现 SSE 对话接口

**Files：**
- 创建：`clawops-gateway/src/main/java/com/clawops/gateway/chat/ChatController.java`
- 创建：`clawops-gateway/src/main/java/com/clawops/gateway/chat/ChatRequest.java`
- 创建：`clawops-gateway/src/main/java/com/clawops/gateway/chat/ChatService.java`
- 创建：`clawops-gateway/src/main/java/com/clawops/gateway/chat/SseEventBuilder.java`
- 测试：`clawops-gateway/src/test/java/com/clawops/gateway/chat/ChatControllerTest.java`

**要点：**
- `POST /api/v1/chat/stream` 接受 `ChatRequest`，返回 `SseEmitter`。
- event 类型：`delta / tool_call / tool_result / done / error`。
- `ChatService` 组装 `AgentLoop`、`FakeModelClient`/真实 adapter、`ToolRegistry`。

---

## Task 11：添加极简前端对话页

**Files：**
- 创建：`clawops-gateway/src/main/resources/static/chat.html`

**要点：**
- 单页 HTML + 原生 JS：输入框、发送按钮、SSE 接收、按 event 类型渲染文本/工具调用/结果。
- 不引入构建工具，W2 再迁移到 console-web Vue 工程。

---

## Task 12：压缩效果验证与文档

**Files：**
- 创建：`docs/metrics/w1-context-compression.md`
- 测试：`clawops-harness/src/test/java/com/clawops/harness/context/ContextCompressionBenchmarkTest.java`

**要点：**
- 构造 20 轮工具调用对话，分别关闭/开启压缩，记录 Token 与轮数。
- 文档中给出压缩比与结论。

---

## Task 13：更新 PROGRESS 与 ADR-002

**Files：**
- 创建：`docs/adr/ADR-002-context-compression.md`
- 修改：`docs/PROGRESS.md`

**要点：**
- ADR-002 记录压缩阈值 80%、保留策略、触发条件。
- PROGRESS 更新 W1 勾选、每日日志（2026-10-14）、阻塞点、资源观测。

---

## Task 14：跑通全量验证与打 tag v0.1.0

**Files：**
- 运行： `./mvnw verify`（本地 Docker Desktop 就绪则含 IT，否则 `-DskipITs`）
- 运行： `./mvnw spotless:apply`
- 命令： `git tag -a v0.1.0 -m "M1: Harness kernel + 2 tools + SSE chat"`

**验收：**
- harness 行覆盖率 ≥60%。
- Spotless / Checkstyle / SpotBugs / ArchUnit / Jacoco 全绿。
- MySQL grep 红线无命中。

---

## 依赖顺序

```text
Task 1 (env)
   ↓
Task 2 (SPI)
   ↓
Task 3 → Task 4 → Task 5 → Task 6 → Task 7 (并列可小范围穿插，但 AgentLoop 依赖全部)
   ↓
Task 8 (AgentLoop)
   ↓
Task 9 (Tools) + Task 10 (SSE) 可并行
   ↓
Task 11 (UI)
   ↓
Task 12 (Metrics doc)
   ↓
Task 13 (Docs)
   ↓
Task 14 (Verify + tag)
```
