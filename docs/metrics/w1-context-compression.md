# W1 上下文压缩效果验证

> 采集方式：`ContextCompressionBenchmarkTest`（clawops-harness）运行 20 轮工具调用对话，分别关闭/开启 `WindowCompressionStrategy`。
> Token 计数器：字符数（近似），仅用于对比压缩前后相对比例。
> 日期：2026-10-14

## 实验设置

- 系统提示 + 角色提示 + 工具说明 + 当前任务：约 100 token（保留不动）。
- 20 轮用户询问 + assistant 工具调用 + 工具结果，每轮结果约 80~100 字符。
- 压缩配置：`maxTokens=600`，`compressionThresholdRatio=0.8`。
- 未压缩配置：`maxTokens=200000`（远大于实际长度，不会触发压缩）。

## 结果

| 模式 | 最终 Prompt Token 数 | 相对比例 |
| --- | --- | --- |
| 未压缩 | 1697 | 100% |
| 窗口压缩 | 512 | 30% |

## 结论

- 在长工具调用链场景下，窗口压缩策略可将 Prompt Token 数降低约 70%。
- 当前实现保留 System / Role / 工具描述 / 当前任务，仅对历史对话进行截断并插入摘要占位，满足 W1「保留目标、已确认事实、待办、关键 ID」的最低要求。
- 当前计数器为字符近似值，M2 接入真实 tokenizer 与审计表后，将替换为真实 Token 数并补充 RAG 知识问答场景的压缩对比。
