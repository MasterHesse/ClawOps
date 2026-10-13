# ClawOps 项目任务书

> 项目代号：ClawOps
> 文档版本：v1.2
> 编制日期：2026-10-13
> 负责人：本人（独立开发）
> 开发模式：Vibe Coding（智能体编码工具为主）+ 人工 Review
> 目标岗位：AI Infra 工程师 / AI 应用工程师（次选：AI 智能体工程师）

## 1. 项目定位

面向 20~50 人规模团队值班 / 运维（DevOps）场景的自托管常驻 Agent 平台，具备自研 Harness 内核、LangChain 生态能力接入、Claw 式 Gateway 多通道接入、RAG 知识库、故障演练式评测与容器化上线能力。

为什么做这个：需要一个能同时承载「Harness / CC 架构设计参照 / LangChain / Claw 设计」四项必选要素，且具备真实数据源、可控失败场景、可量化指标的工程载体，用于支撑 AI Infra 与应用工程师方向的求职作品集。

产品形态：Web 控制台（主）+ IM 机器人（副）+ CLI（副）。不是 C 端通用对话产品，不是编码 CLI，不是桌面助手。

## 2. 目标（Goals）

| 编号 | 目标 | 验收口径 |
| --- | --- | --- |
| G1 | 自研 Agent Harness 内核 | Loop / 工具注册表 / 上下文压缩 / 权限分级 / 子 Agent / 任务持久化与断点恢复，均有代码 + 单测 |
| G2 | 落地 CC 架构设计参照（clean-room） | 产出《Agent 运行时架构分析与实现对照》文档，逐条说明借鉴机制与本地改动 |
| G3 | 使用 LangChain 生态 | LangChain4j（主）接入模型 / 工具 / RAG / MCP；可选 LangGraph4j 或 Python LangGraph 侧车做图编排 |
| G4 | 落地 Claw 式设计 | Gateway 单端口 HTTP+WebSocket、通道适配器、Skills 加载、MCP 接入、常驻 + 定时任务 |
| G5 | 可上线 | Docker Compose 一键部署到云服务器，HTTPS、健康检查、日志、备份、CI |
| G6 | 可评测 | ≥30 条带标注样本，输出根因命中率、平均轮数、Token 消耗、p50/p95 时延 |
| G7 | 可演示 | 5 分钟录屏 + README 架构图 + 在线 Demo 账号 |

## 3. 非目标（Out of Scope，写进 README 的 Roadmap）

多租户 SaaS、K8s 生产编排、本地模型推理、桌面客户端、多模型智能路由、复杂前端动效、真实企业客户接入、Web 端多语言。

## 4. 用户与场景

角色：值班/运维工程师（主）、团队负责人/管理员（配置与审计）、普通同事（知识问答）。

- 场景 A｜告警值守诊断：告警 Webhook → Agent 查指标/日志/发布记录/知识库 → 输出根因 + 置信度 + 处置建议 → 高危动作走审批 → 结论回写工单。
- 场景 B｜内部知识问答：规范/发布流程/故障复盘/API 文档入 RAG，回答带引用溯源。
- 场景 C｜流程类任务：跨工具串联生成结构化报告并落库。

演示剧本（固定 7 步，用于录屏）：注入故障 → 告警进入 → 任务中心出现诊断任务 → 对话页流式展示工具调用链 → 输出根因结论 → 审批卡片批准 → 回写工单 + 打开评测页看指标。

## 5. 技术选型（强约束，变更需记 ADR）

### 5.1 运行时与框架

| 项 | 选型 | 说明 |
| --- | --- | --- |
| 语言 | Java（主）/ Python（可选侧车）/ Rust（可选，见 5.6） |  |
| JDK | Java 25 LTS（备选 Java 21 LTS） | Day 0 需实测依赖兼容性，不兼容则降 21 并记 ADR |
| 主框架 | Spring Boot 4.1.x（备选 4.0.x） | 3.5.x 开源支持已于 2026-06 结束，不采用 |
| AI 能力层 | LangChain4j 1.20.x | 模型接入、@Tool、ChatMemory、PgVector 混合检索、MCP Client、Skills 加载 |
| AI 能力层（备选） | Spring AI 2.0.x | 若 LangChain4j 与 Boot 4.1 集成摩擦大则切换，二选一，不并存 |
| 图编排（可选） | LangGraph4j 或 Python FastAPI + LangGraph 侧车 | 仅在内存允许时启用，二选一 |
| 持久层 | Spring Data JPA + Flyway（迁移脚本版本化） |  |
| 数据库 | PostgreSQL 17 + pgvector（强制） | 禁用 MySQL，见 5.2 |
| 缓存/队列 | Redis 7（Redis Stream 做任务队列 + 死信）；2C2G 降级方案：Caffeine 本地缓存 + DB 轮询 | 不引入 Kafka / RocketMQ / RabbitMQ |
| 可观测 | Micrometer + Prometheus（自埋点）+ 结构化日志（JSON）；OTel 可选 |  |
| 容器 | Docker + Docker Compose（dev / prod 两份 override） | 多模块独立镜像，同机编排 |
| 反向代理 | Nginx + Let's Encrypt HTTPS |  |
| 前端 | Vue 3 + Vite（或 React），构建产物由 Nginx 托管 | 追求能用即可，不做复杂动效 |
| CLI | Java（Picocli）为主；Rust 版为可选增强 |  |
| 构建 | Maven 多模块（或 Gradle，二选一并全程统一） |  |
| CI | GitHub Actions 七阶段 fail-fast 门禁（见 §6.2）：格式/静态分析 → 单测 → 集成 → 架构与覆盖率 → MySQL 校验 → 镜像构建 → 评测冒烟 |  |

### 5.2 数据库强制约束（红线）

仅允许 PostgreSQL，全项目禁止出现 MySQL 相关依赖、配置、镜像、SQL 方言。

检查手段：① pom.xml 禁止 mysql-connector-j 与 mariadb-connector-j（MySQL 协议家族驱动一律禁入）；② docker-compose.yml 禁止 mysql / mariadb 镜像；③ CI grep 校验：模式 `(?i)(mysql|mariadb|jdbc:mysql|jdbc:mariadb)`，范围为全部 pom、compose、Flyway 脚本、application*.yml / properties、Dockerfile 与 src/ 下 Java 源码，命中即构建失败（docs/ 内的禁令性表述属合法提及，排除在外）；④ 所有 SQL 使用 PG 方言（jsonb、gen_random_uuid()、tsvector、pgvector 算子）。

向量方案：pgvector + HNSW（vector_cosine_ops）；全文检索用 PG tsvector + GIN，混合检索在应用层做 RRF 融合。

PG 方言基线：主键 uuid 类型 + 默认 gen_random_uuid()；时间列统一 timestamptz；半结构化字段一律 jsonb；禁止应用层拼接 SQL 字符串，一律参数化（JPA / PreparedStatement）。

### 5.3 服务器选型

| 项 | 规格 |
| --- | --- |
| 配置 | 2 vCPU / 4 GiB（低于 4G 不启动本项目，2C2G 需按降级方案砍组件） |
| 系统 | Ubuntu 24.04 LTS x86_64（或 22.04 LTS） |
| 磁盘 | ≥40 GiB SSD（镜像 + PG 数据 + 日志） |
| Swap | 2 GiB（必须配置，防 OOM） |
| 网络 | 固定公网 IP + 域名 + 80/443；安全组仅放行 22/80/443 |
| 模型 | 全部走外部 API（Chat + Embedding），服务器不做任何本地推理 |
| 预算 | 记录每月 API 调用成本上限（建议 ≤ ¥100/月），CLI 侧做调用计数与熔断 |

内存预算（4 GiB）：ClawOps 主应用 512M（-Xmx512m）｜PG17+pgvector 300M｜Prometheus+node_exporter 300M｜靶场 2 服务 2×128M｜Mock 工单/CMDB 128M｜Redis 128M｜Nginx 20M｜系统预留 ~1G。

### 5.4 模块与目录（Maven 多模块）

```text
clawops/
├── clawops-gateway/        # 接入层：WS+HTTP、鉴权、通道适配器（飞书/钉钉）、Webhook
├── clawops-harness/        # 内核：AgentLoop、ToolRegistry、ContextManager、
│                           #       Permission、SubAgent、TaskStore、RetryPolicy
├── clawops-orchestrator/   # 图编排 / 路由 / Checkpoint / HITL 审批节点
├── clawops-rag/            # 解析→切分→向量化→混合检索→重排→引用溯源
├── clawops-skills/         # SKILL.md 加载、渐进式披露、启停管理
├── clawops-mcp/            # MCP Client + 自研 MCP Server（工单、监控）
├── clawops-connector/      # Metrics/Log/Ticket/CMDB/Change 抽象 + Mock + Prometheus 实现
├── clawops-workers/        # Redis Stream 消费者：长任务、Cron 巡检、通知、死信
├── clawops-cli/            # Picocli 命令行：ask / task / skill / eval / deploy
├── clawops-console-web/    # 前端（对话台、任务中心、知识库、技能、审计、评测）
├── testbed/                # 靶场：order-service、inventory-service、mock-ops-system
├── deploy/                 # Dockerfile、compose、nginx、备份脚本、CI 配置
└── docs/                   # 架构图、ADR、评测报告、架构对照文档、演示脚本
```

### 5.5 关键设计约束

- **Harness 与框架边界**：高频工具执行、上下文压缩、权限判定、任务持久化由自研 Harness 承担；LangChain4j 仅作为模型/工具/RAG/MCP 的能力适配层。此边界须写入 ADR-001。
- **上下文管理**：分层 Prompt 组装（System / 角色 / 工具说明 / 记忆 / 当前任务）；token 阈值触发压缩（可配置，默认 80%），压缩保留「目标、已确认事实、待办、关键 ID」；压缩前后写入审计表以便对比。
- **工具执行三阶防护**：参数 Schema 校验 → 超时 + 指数退避重试（上限 3 次）→ 熔断/降级/转人工；步数上限、时间上限、重复动作检测防死循环。
- **权限与审批**：工具标注 READ / WRITE / DANGEROUS；WRITE 以上必须 HITL 审批；全链路审计留痕（谁、何时、调了什么、参数、结果）。
- **可靠性**：任务状态落 PG，进程重启可断点续跑；长任务经 Redis Stream 异步化，失败进死信队列并告警；消费端幂等（业务幂等键）。
- **安全**：默认只读；密钥仅存 .env（不入库、不入 Git）；对外接口鉴权 + 限流；命令/SQL 白名单。

### 5.6 Rust 模块（可选，默认关闭）

默认不做。启用条件（满足任一才开）：① W1~W3 全部按期完成且有余力；② 需要一个跨平台单二进制 CLI 作为差异化亮点。

若启用，范围严格限定为一个模块：clawops-cli-rs（Rust + clap，调用 Gateway HTTP/WS），不得侵入 Java 主链路。

明确不做：Rust 写核心 Harness、Rust 写 RAG、Rust 与 JVM 通过 JNI 通信。

## 6. 工程约束（测试 / 门禁 / 契约）

细则文档（与本章同等效力，冲突时以本章为准）：`docs/conventions/testing.md`、`docs/conventions/quality-gates.md`、`docs/conventions/api.md`。

### 6.1 测试策略（摘要）

- 三层金字塔：单测（纯 JVM，~70%）／集成（Testcontainers：PG17+pgvector、Redis 7）／E2E 评测（唯一允许真实 LLM 调用的环节）。
- LLM 一律 Fake：单测与集测使用自研可编程 FakeChatModel，禁止真实 API；评测冒烟子集 ≤5 条进 CI，全量仅手动 / 上线前。
- TDD 强制范围：harness 全部核心类（Loop / ToolRegistry / Context / Permission / Retry / LoopGuard）、TaskStore、Checkpoint、审批状态机、消费幂等；其余模块事后补测即可。
- 覆盖率门槛（Jacoco 强制，只升不降，矩阵见 conventions/testing.md）：harness 80%（W1 起步 60%）｜orchestrator 70%｜workers 70%｜rag / connector 65%｜gateway / skills / mcp 60%。

### 6.2 静态门禁与架构约束（摘要）

- 工具链：Spotless（格式）+ Checkstyle + SpotBugs（High 阻断）+ ArchUnit + Jacoco + MySQL grep，全部接 CI。
- ArchUnit 三条硬规则：① 任一模块切片间禁止循环依赖；② harness 纯内核——禁止依赖任何兄弟模块、spring-web、servlet、HTTP 客户端，与外界只经自研 SPI；③ LangChain4j 类型不得出现在 harness / orchestrator 的 public API 签名（ADR-001 边界的机器化校验）。
- PR 合并门禁：CI 全绿 + 核心模块测试先行 + 接口变更须显式提交 openapi.json + AI 生成注明 `Assisted-by` 并留档 docs/ai-log/ + 偏离任务书须先记 ADR。清单见 conventions/quality-gates.md §4。

### 6.3 API 与错误码契约（摘要）

- 统一响应体 `{ code, message, data, traceId }`；错误码分段：1xxx 通用 / 2xxx 任务域 / 3xxx 工具执行 / 4xxx RAG / 5xxx 集成 / 9xxx 系统内部，一经发布只增不改不复用。
- 错误码附带 Agent 侧语义（可重试 / 降级 / 转人工），是 5.5 节三阶防护降级判断的落地依据；机器可读版本维护于 error-codes.yaml，禁止硬编码散落。
- WS 消息信封 `{ type, seq, taskId, payload, ts }`，按 `(sessionId, seq)` 幂等去重；SSE 事件 delta / tool_call / tool_result / done / error。
- OpenAPI 产物（openapi.json）入仓 + CI diff 校验，接口变更必须显式提交并经 Review——防止 AI 静默改接口。

## 7. 靶场（Testbed）与评测

原则：指标要真、业务要假、故障可控。

- 真实部分：2 个极简 Spring Boot 服务（/actuator/prometheus）+ Prometheus + node_exporter + Nginx。
- Mock 部分：mock-ops-system（告警、工单、发布记录、服务拓扑、值班人 5 张表 + REST + MCP 暴露）。
- 禁止引入 Online Boutique / Train-Ticket / Sock Shop 等重型开源靶场。

### 7.1 五类故障注入（含 ground truth）

| ID | 故障 | 期望根因类别 | 期望定位服务 | 期望建议动作 |
| --- | --- | --- | --- | --- |
| F1 | 数据库连接池耗尽 | 资源耗尽 | inventory-service | 扩容连接池 / 排查慢查询 |
| F2 | 下游注入 3s 延迟 | 依赖劣化 | order-service（上游受影响） | 加超时与熔断 |
| F3 | 发布错误版本（500） | 变更引入 | 对应服务 | 回滚至上一版本 |
| F4 | 内存泄漏 / OOM | 资源泄漏 | 对应服务 | 重启 + 抓 heap |
| F5 | 缓存开关误关 | 配置错误 | 对应服务 | 回滚配置 |

评测集：≥30 条（5 类故障 × 多次采样 + 10 条知识问答样本），字段含输入、ground truth、命中判定规则。

指标：根因命中率、服务定位准确率、建议动作匹配率、平均工具调用轮数、p50/p95 端到端时延、单任务 Token 消耗与压缩比、失败恢复成功率。评测脚本随仓库提供，可一键复现。

## 8. 里程碑（4 周）

| 阶段 | 时间 | 交付 | 验收 |
| --- | --- | --- | --- |
| M0 准备 | D0–D1 | 仓库、CI 骨架、版本锁定、compose 起 PG/Redis/Prometheus | mvn verify 通过，容器全部 healthy |
| M1 内核 | W1 | Harness 最小 Loop + 2 工具 + 权限分级 + 上下文压缩 | 单测覆盖 Loop 与重试；能完成一次真实工具调用链 |
| M2 编排与 RAG | W2 | 任务持久化 + 断点恢复 + RAG（真实文档）+ 对话台流式 | 重启进程可续跑；RAG 回答带引用 |
| M3 接入与异步 | W3 | Gateway + 1 个 IM 通道 + 审批卡片 + Redis Stream 长任务 + CLI | 从 IM 发消息可完成一次带审批的诊断 |
| M4 上线与评测 | W4 | Skills + 1 个自研 MCP Server + Compose 上线 + HTTPS + 评测报告 + 录屏 | 公网可访问；评测报告有真实数字；录屏可播 |

## 9. 风险与应对

| 风险 | 应对 |
| --- | --- |
| 4G 内存 OOM | 严格 -Xmx 预算 + 2G swap + 监控；超预算先砍 Redis 或 Prometheus |
| API 成本失控 | 调用计数 + 每日配额熔断 + 用便宜模型跑评测批处理 |
| Vibe Coding 产出质量不稳 | 每个 AI 生成模块必须：人工 Review + 补单测 + 跑 CI；Prompt 与生成记录归档到 docs/ai-log/ |
| 依赖版本不兼容（Boot 4.1 / LangChain4j） | D0 做兼容性 spike，不通过则降级 JDK/Boot 并记 ADR，不硬扛 |
| 范围蔓延 | 非目标清单为准，新增需求一律进 Roadmap，不进本期 |
| 合规 | 仅做 clean-room 实现与公开架构分析，不引用、不提交任何泄露源码产物，README 明确声明 |

## 10. Git 与协作规范

- **分支**：main（可发布）/ dev（日常）/ feat、fix/；main 受保护，走 PR 自审。
- **提交**：Conventional Commits（feat|fix|docs|test|chore|refactor），一次提交一个可验证单元；AI 辅助生成的提交在 body 注明 Assisted-by: <tool>。
- **标签**：v0.1.0（M1）、v0.2.0（M2）、v0.3.0（M3）、v1.0.0（M4 上线）。
- **忽略**：.env、*.pem、本地数据目录；密钥用环境变量注入。
- **文档**：每个关键技术决策写 docs/adr/ADR-xxx.md（背景/选项/决定/影响）。

## 11. 交付物清单（DoD）

代码仓库（含 CI 绿灯）、README（含架构图与快速开始）、docs/ 下：架构图、ADR 集、工程规约（conventions/：testing、quality-gates、api，P1 追加 config / observability / errors / llm）、《Agent 运行时架构分析与实现对照》、评测报告、部署手册；deploy/ 可一键起停；公网 Demo + 演示账号；5 分钟录屏；简历用项目描述段落。

## 12. 变更记录

| 版本 | 日期 | 变更 |
| --- | --- | --- |
| v1.0 | 2026-10-13 | 初版 |
| v1.1 | 2026-10-13 | 新增 §6 工程约束（测试策略 / 静态门禁 / API 契约），细则落 docs/conventions/；修正 5.2 笔误；原 §6–§11 顺延为 §7–§12 |
| v1.2 | 2026-10-13 | PG 红线强化：grep 模式扩至 mariadb 家族、明确校验范围与 docs/ 排除；新增 PG 方言基线（uuid 主键 / timestamptz / jsonb / 参数化 SQL） |
