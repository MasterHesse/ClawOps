# ADR-001：Harness 与 LangChain4j 的职责边界

> 状态：已接受
> 日期：2026-10-13
> 关联：TASK_BOOK §5.5；conventions/quality-gates.md §2（ArchUnit 规则 3 的机器化校验依据）

## 背景

ClawOps 需要同时满足两个目标：自研 Agent Harness 内核（G1）与 LangChain 生态接入（G3）。若边界不清，LangChain4j 类型会渗透进内核，导致：内核无法独立测试（依赖模型 SDK）、clean-room 架构对照失去意义、框架替换成本不可控（任务书备选 Spring AI 2.0 的前提是可切换）。

## 选项

| 选项 | 说明 |
| --- | --- |
| A. Harness 直接基于 LangChain4j 构建 | 开发快，但内核逻辑与框架耦合，G2 对照文档与备选框架切换均受阻 |
| B. Harness 纯自研内核，LangChain4j 仅作能力适配层 | 内核只依赖自研 SPI；框架类型被隔离在 adapter 实现内 |

## 决定

采用**选项 B**。职责划分：

### Harness（clawops-harness，纯内核）承担

- AgentLoop（感知-推理-行动-反馈循环、步数/时间上限、死循环防护）
- ToolRegistry（注册、JSON Schema 生成、参数校验）与工具执行三阶防护（校验 → 超时+退避重试 → 熔断/降级/转人工）
- ContextManager（分层 Prompt 组装、token 计数、阈值压缩）
- PermissionManager（READ / WRITE / DANGEROUS 分级与拦截）
- SubAgent 调度、TaskStore、RetryPolicy
- 对外仅暴露自研 SPI：`ModelClient`、`ToolDefinition`、`ToolResult` 等**

### LangChain4j（1.20.x，能力适配层）承担

- 模型接入（ChatModel / EmbeddingModel 具体实现）
- @Tool 注解扫描与框架侧工具描述生成（适配为自研 ToolDefinition）
- ChatMemory 存储、PgVector 混合检索、MCP Client、Skills 加载

### 硬边界（由 ArchUnit 机器化校验，见 quality-gates.md §2）

1. `clawops-harness` 禁止依赖任何兄弟模块、`org.springframework.web.*`、`jakarta.servlet.*`、任何 HTTP 客户端。
2. `dev.langchain4j.*` 类型不得出现在 harness / orchestrator 的 **public API 签名**中，只允许存在于 adapter 实现类内部。
3. 模型调用在单测/集测中一律经自研 FakeChatModel（conventions/testing.md §2），内核测试不感知框架。

## 影响

- Harness 可在无任何 AI 框架依赖下完成 TDD（W1 核心）。
- 若切换 Spring AI 2.0（备选），只需重写 adapter 层，内核不动。
- adapter 层是本 ADR 唯一允许的框架接触面，其变更须在 PR 中显式标注。
