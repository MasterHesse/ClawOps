# 测试策略与 TDD 规约

> 细则文档，与 TASK_BOOK §6 同等效力，冲突时以 TASK_BOOK 为准。
> 适用范围：全部 Java 模块与 testbed；前端与 CLI 参照执行、从宽。

## 1. 测试金字塔与比例

| 层级 | 占比（近似） | 运行环境 | 触发方式 |
| --- | --- | --- | --- |
| 单元测试 | ~70% | 纯 JVM，无外部依赖 | 每次提交（mvn test，surefire） |
| 集成测试 | ~25% | Testcontainers（PG17+pgvector、Redis 7） | 每次提交（mvn verify，failsafe） |
| 端到端评测 | ~5% | 真实模型 API + testbed | CI 冒烟子集（≤5 条）；全量仅手动 / 上线前 |

硬规则：

- 单元测试禁止访问网络、禁止连接数据库、禁止起 Docker；需要外部依赖的一律归入集成测试。
- 集成测试一律 Testcontainers，禁止连接 dev / prod 实例；测试数据用完即弃。
- 真实 LLM 调用只允许出现在：eval run、手动验证脚本、CI 评测冒烟子集。单测与集测一律使用 Fake 模型。

## 2. LLM Mock 策略（关键）

自研 `FakeChatModel`（clawops-harness 测试公共模块），实现 LangChain4j 的 `ChatLanguageModel` 接口：

```java
// 按轮次弹出预设回复，记录全部入参供断言
fake.enqueueText("已定位到 inventory-service 连接池耗尽");          // 纯文本回复
fake.enqueueToolCall("metrics.query", Map.of("query", "db_pool_active")); // 下一轮要求调用工具
// 断言
assertThat(fake.requests()).hasSize(2);
assertThat(fake.requests().get(1).tools()).contains("metrics.query");
```

- 规则一：Fake 返回模型层对象（AiMessage 等），禁止在单测里 mock 到 JSON 字符串级别再自行解析——那测的是 mock 而不是代码。
- 规则二：工具调用的结果回填路径（tool result → 下一轮请求）必须有专项测试，这是 AgentLoop 的核心行为。
- 规则三：token 计数、压缩触发等与用量相关的逻辑，Fake 需支持注入 usage 值。
- 录制回放（P2 可选）：若需贴近真实行为的回归，用录制文件回放，文件入仓并脱敏。

## 3. TDD 强制范围

| 范围 | 模块与类 |
| --- | --- |
| 强制 TDD（红-绿-重构，测试先行） | harness 全部核心类：AgentLoop、ToolRegistry、ContextManager、PermissionManager、RetryPolicy、LoopGuard（死循环检测）；orchestrator 的 TaskStore、Checkpoint、审批状态机；workers 的消费幂等与死信路由 |
| 事后补测可接受（覆盖率仍须达标） | connector 各实现、skills、mcp、cli、前端、testbed |

合规判定：核心模块 PR 中，测试与实现须同包提交且覆盖主路径；纯实现无测试的核心类 PR 一律打回。

## 4. 覆盖率矩阵（Jacoco 行覆盖）

| 模块 | 起步门槛 | 最终门槛（M2 结束时冻结） |
| --- | --- | --- |
| clawops-harness | 60%（W1） | 80% |
| clawops-orchestrator | 50% | 70% |
| clawops-workers | 50% | 70% |
| clawops-rag | 50% | 65% |
| clawops-connector | 50% | 65% |
| clawops-gateway | 40% | 60% |
| clawops-skills / clawops-mcp | 40% | 60% |
| clawops-cli | 不设线 | 不设线（以 E2E 冒烟为准） |

- 门槛经 Jacoco check 强制，未达标 CI 失败。
- 门槛只升不降：一经冻结，任何下调须走 ADR。
- 覆盖率不是唯一标准：重试、压缩触发、审批拦截、死信路由等核心分支必须有针对性断言，禁止为凑数字写空测试。

## 5. 命名与写法规范

- 测试类命名：`XxxTest`（单测）/ `XxxIT`（集成，failsafe 插件执行）。
- 方法名：`should<Expect>When<Condition>`；`@DisplayName` 用中文描述行为。
- 结构：given / when / then 三段；一个测试方法只验证一个行为。
- 测试数据：fixture 工厂方法集中管理；随机数据固定种子；测试间禁止顺序依赖（surefire 并行可开）。
- 断言库：AssertJ；异步等待用 Awaitility，禁止 Thread.sleep。
- 集成测试类须加 `@Tag("it")`，本地可 `-DskipITs` 快速跳过。

## 6. 测试与评测的边界

- 评测集（eval/，≥30 条样本）不是测试套件，不参与 mvn verify；由 CI 独立 job（冒烟）与 `clawops-cli eval run`（全量）执行。
- 评测基准线与回归门禁（指标低于上一 tag 阻断合并）属于 LLM 工程约定，见 docs/conventions/llm.md（P1 待补）。
