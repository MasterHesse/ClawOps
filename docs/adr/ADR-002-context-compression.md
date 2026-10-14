# ADR-002：上下文压缩阈值与保留策略

> 状态：已接受
> 日期：2026-10-14
> 关联：TASK_BOOK §5.5；PROGRESS.md W1 ContextManager

## 背景

AgentLoop 在长时间诊断任务中会产生多轮工具调用历史，Prompt 长度持续增长会导致：

1. 模型 API Token 成本上升；
2. 模型注意力分散，关键早期信息被淹没；
3. 超出模型上下文窗口导致请求失败。

因此需要在 harness 中实现可配置的上下文压缩机制。

## 选项

| 选项 | 说明 |
| --- | --- |
| A. 固定窗口截断 | 只保留最近 N 轮，实现简单但会丢失早期关键事实 |
| B. 阈值触发 + 摘要生成 | 超过 token 阈值后由 LLM 生成摘要，保留关键信息，效果较好但增加一次 LLM 调用 |
| C. 阈值触发 + 窗口截断 + 摘要占位 | 超过阈值后丢弃最旧消息并插入摘要占位，保留 System/Role/工具/当前任务，实现简单、零额外 LLM 调用 |

## 决定

采用**选项 C** 作为 W1 实现（`WindowCompressionStrategy`）。

### 具体策略

- 阈值：`maxTokens=4000`，`compressionThresholdRatio=0.8`，默认在 token 数达到 3200 时触发。
- 保留内容（按优先级）：System 提示、Role 提示、工具描述、当前任务、最近历史。
- 压缩动作：丢弃超出预算的最旧历史消息，并插入一条摘要占位消息 `[上下文已压缩] 已省略 X 条较早消息...`。
- 触发与结果在 M2 持久化后写入审计表，用于对比压缩前后效果。

### 为什么不选 B

选项 B 需要额外调用 LLM 生成摘要，W1 阶段有两个顾虑：

1. 增加 API 成本与延迟，与 ≤¥100/月预算约束冲突；
2. 摘要质量依赖模型，难以在单测中稳定断言。

选项 C 在 W1 已能显著降低 Token 占用（实测 20 轮对话从 1697 降至 512，见 `docs/metrics/w1-context-compression.md`），并为后续升级到 B 预留接口（`CompressionStrategy`）。

## 影响

- `ContextManager` 依赖 `TokenCounter` 接口，真实 tokenizer 由上层 adapter 注入。
- 压缩效果验证数据进入 `docs/metrics/`。
- M2 若发现窗口截断导致命中率下降，可平滑替换为基于 LLM 的摘要策略，无需改动 `AgentLoop`。
