# API 与错误码契约

> 细则文档，与 TASK_BOOK §6 同等效力，冲突时以 TASK_BOOK 为准。
> 消费方：console-web（前端）、IM 通道适配器、clawops-cli、评测脚本。

## 1. REST 规范

- 路径统一前缀 `/api/v1/`；资源复数、kebab-case；动词不进 URL（动作类用子资源，如 `POST /tasks/{id}/approvals`）。
- HTTP 语义：200/201 成功；400 参数错；401 未认证；403 无权限（含工具 DANGEROUS 拦截）；404 不存在；409 状态/幂等冲突；429 限流；5xx 系统错。
- 时间一律 ISO-8601 带时区（`2026-10-13T20:00:00+08:00`）；ID 一律字符串（UUID）。

## 2. 统一响应体

```json
{ "code": 0, "message": "ok", "data": {}, "traceId": "8f3a..." }
```

- `code=0` 成功；非 0 见错误码表。HTTP 状态码照常设置，前端与 CLI 统一按 `code` 分支。
- 分页：请求 `page` / `pageSize`（默认 1/20，max 100）；响应 `data: { "items": [], "total": 0, "page": 1, "pageSize": 20 }`。

## 3. 错误码表（初版骨架，随实现追加）

分段：**1xxx 通用｜2xxx 任务域｜3xxx 工具执行｜4xxx RAG｜5xxx 集成（Gateway/IM/Webhook）｜9xxx 系统内部**。

| code | 名称 | HTTP | Agent 侧语义 |
| --- | --- | --- | --- |
| 0 | OK | 200 | — |
| 1001 | INVALID_PARAM | 400 | 不可重试 |
| 1002 | UNAUTHORIZED | 401 | 不可重试 |
| 1003 | FORBIDDEN | 403 | 转人工 / 换路径 |
| 1004 | NOT_FOUND | 404 | 不可重试 |
| 1005 | RATE_LIMITED | 429 | 等待后可重试 |
| 2001 | TASK_STATE_CONFLICT | 409 | 不可重试 |
| 3001 | TOOL_PARAM_INVALID | 400 | 修正参数后可重试 |
| 3002 | TOOL_TIMEOUT | 504 | 可重试（计入重试次数） |
| 3003 | TOOL_RETRY_EXHAUSTED | 502 | 降级 / 换工具 / 转人工 |
| 3004 | TOOL_CIRCUIT_OPEN | 503 | 跳过该工具 |
| 4001 | KB_PARSE_FAILED | 400 | 不可重试 |
| 5001 | WEBHOOK_SIGNATURE_INVALID | 401 | 丢弃消息 |
| 9001 | INTERNAL | 500 | 不可重试 |

维护规则：

- 错误码一经发布**只增不改不复用**；废弃标记 deprecated，不得改义。
- 机器可读版本维护在 `src/main/resources/error-codes.yaml`（code / name / http / retryable / degradeAction），Agent 三阶防护的降级判断、前端提示、CLI 输出共用此文件，禁止各处硬编码。
- `Agent 侧语义` 列是任务书 5.5 三阶防护的落地依据：可重试 → 计数重试；降级 → 换工具或降级路径；转人工 → 触发审批/通知。

## 4. WebSocket 消息协议（Gateway）

信封结构：

```json
{ "type": "user.message", "seq": 3, "taskId": "uuid", "payload": {}, "ts": "ISO-8601" }
```

- `type` 取值（初版）：`user.message` / `agent.delta` / `agent.tool_call` / `agent.tool_result` / `agent.done` / `approval.request` / `approval.result` / `pong` / `error`。
- `seq` 客户端单调递增；服务端按 `(sessionId, seq)` 幂等去重（对应任务书消息幂等键）。
- 心跳：客户端 30s 发 `ping`，服务端回 `pong`；60s 无心跳断开。

## 5. SSE 流式（对话页）

- `event`：`delta`（增量文本）| `tool_call` | `tool_result` | `done` | `error`；`data` 为 JSON。
- `done` 事件携带 `taskId` 与 `usage`（token 统计），前端据此更新指标展示。

## 6. OpenAPI 契约管理

- springdoc-openapi 生成 `openapi.json`，产物提交入仓（`docs/openapi/openapi.json`）。
- CI diff 校验：CI 内重新生成后与仓内版本比对，不一致即失败——**防止 AI 静默改接口**；任何接口变更必须显式提交并经 Review。
- WS / SSE 消息 schema 以 JSON Schema 定义于 `docs/openapi/ws-messages.json`（M3 前落地）。
