# ClawOps 进度追踪表

> 关联文档：TASK_BOOK.md v1.2
> 更新频率：每日收工前更新一次
> 状态标记：`[ ]` 未开始 ｜ `[~]` 进行中 ｜ `[x]` 已完成 ｜ `[!]` 阻塞 ｜ `[-]` 已取消/移入 Roadmap

## 使用说明

- 完成一项立即勾选，并在行尾补 ｜ commit: xxxxx ｜ 耗时: x.xh。
- 任何偏离任务书的决定，先在「决策记录（ADR）」登记，再改代码。
- 每日在「每日日志」写三行：今日完成 / 明日计划 / 阻塞点。
- Vibe Coding 约定：AI 生成 → 人工 Review → 补单测 → 跑 CI → 提交，四步缺一不算完成。
- 工程规约：TDD 范围、覆盖率矩阵、门禁清单、API 契约见 docs/conventions/（testing / quality-gates / api），与任务书同等效力。

## M0 准备（D0–D1）

- [x] 创建仓库、初始化 Maven 多模块骨架（10 个模块空壳可编译）｜ commit: M0-init ｜ 耗时: 0.8h
- [x] 锁定版本：JDK / Spring Boot / LangChain4j / PG / Redis，产出 versions.lock 说明段｜ commit: M0-init ｜ 耗时: 0.2h
- [x] 兼容性 spike：Boot 4.1 + JDK 25 + LangChain4j 1.20 最小 Demo 跑通（不通过则降级并记 ADR-000）｜ commit: M0-init ｜ 耗时: 0.3h
- [x] MySQL 禁用校验：CI grep（范围 pom / compose / Flyway 脚本 / yml / properties / Dockerfile / Java 源码）命中即失败；Flyway 脚本全部 PG 方言｜ commit: M0-init ｜ 耗时: 0.1h
- [~] docker-compose.dev.yml：postgres17+pgvector、redis7、prometheus、node_exporter、nginx｜ 文件已就绪，待 Docker daemon 就绪后验证 healthy
- [ ] 云服务器初始化：Ubuntu 24.04、2G swap、安全组、Docker、域名解析、certbot
- [x] GitHub Actions：compile + test + build image（先不部署）｜ commit: M0-init ｜ 耗时: 0.2h
- [x] 测试基建：FakeChatModel + Testcontainers 集成测试模板（PG+Redis）+ Jacoco 覆盖率门槛矩阵接入 CI｜ commit: M0-init ｜ 耗时: 0.3h
- [x] 静态门禁：Spotless + Checkstyle + SpotBugs + ArchUnit 三条硬规则接入 CI｜ commit: M0-init ｜ 耗时: 0.2h
- [x] API 契约：统一响应体 + error-codes.yaml 骨架 + springdoc 生成 openapi.json 入仓与 CI diff 校验｜ commit: M0-init ｜ 耗时: 0.2h
- [x] .gitignore / .env.example / Issue 与 PR 模板 / docs/adr/ 目录｜ commit: M0-init ｜ 耗时: 0.1h
- [x] 写 ADR-001：Harness 与 LangChain4j 的职责边界；ADR-007：开发环境选型｜ commit: M0-init ｜ 耗时: 0.2h

## W1 内核（M1）

- [ ] clawops-harness：AgentLoop（感知-推理-行动-反馈）+ 步数/时间上限
- [ ] ToolRegistry：注解注册、JSON Schema 生成、参数校验
- [ ] 工具 1：metrics.query（Prometheus HTTP，真实数据）
- [ ] 工具 2：log.search（Mock 日志接口，可注入 ERROR）
- [ ] 重试与熔断：指数退避（≤3 次）、超时隔离、失败降级路径 + 单测
- [ ] Permission：READ / WRITE / DANGEROUS 三级标注与拦截 + 单测
- [ ] ContextManager：分层 Prompt 组装 + token 计数 + 阈值压缩（默认 80%，可配置）
- [ ] 压缩效果验证：同一任务压缩前后轮数/Token 对比，写入 docs/metrics/
- [ ] 死循环防护：重复动作检测 + 单测
- [ ] 最小对话接口（REST + SSE 流式）+ 极简前端对话页
- [ ] 单测覆盖率：harness ≥ 60%（按 conventions/testing.md 矩阵爬坡至 80%），CI 绿灯
- [ ] 打 tag v0.1.0

## W2 持久化 + RAG（M2）

- [ ] Flyway 迁移脚本：task / task_step / audit / approval / kb_doc / kb_chunk / eval_case
- [ ] TaskStore：任务状态落库 + 断点恢复（kill 进程后重启可续跑，附验证记录）
- [ ] Checkpoint 机制（每步落盘，含工具入参出参摘要）
- [ ] HITL 审批：approval 表 + 审批接口 + 前端审批卡片
- [ ] RAG：文档解析（md/pdf/txt）→ 切分（可调 chunk/overlap）→ Embedding → pgvector HNSW
- [ ] 混合检索：向量 + tsvector 全文 + 应用层 RRF 融合 + Top-K 可配
- [ ] 引用溯源：回答携带 chunk 来源与跳转
- [ ] 知识库灌入真实文档（自选一个真实项目/课程的文档 + 复盘记录），记录文档数量与切分参数
- [ ] 知识库管理页：上传、切分参数、检索测试（输入问题看召回片段）
- [ ] RAG 开/关对比评测（≥10 条问答样本），结果入 docs/metrics/
- [ ] 打 tag v0.2.0

## W3 接入 + 异步 + 靶场（M3）

- [ ] clawops-gateway：单端口 HTTP + WebSocket、鉴权（token）、会话隔离、消息幂等键
- [ ] 通道适配器接口 + 飞书（或钉钉）一个实现：收消息 → 回复卡片 → 审批按钮回调
- [ ] 告警 Webhook 入口（签名校验 + 去重）
- [ ] clawops-connector：MetricsConnector（Prometheus 真实实现）+ Log/Ticket/CMDB/Change（Mock 实现）
- [ ] testbed：order-service、inventory-service（各 ≤128M，2~3 接口 + 库）
- [ ] mock-ops-system：告警/工单/发布记录/拓扑/值班人 5 表 + REST
- [ ] 故障注入脚本：F1~F5 一键注入 + 自动恢复 + 注入日志留痕
- [ ] 故障 ground truth 表（对应任务书 §7），落成 eval/ground_truth.yaml
- [ ] Redis Stream：长任务异步化 + 消费组 + 死信队列 + 幂等消费 + 失败重试演示
- [ ] Cron 巡检任务（每 5 分钟拉指标，异常自动建诊断任务）
- [ ] clawops-cli（Picocli）：ask / task list / task show / skill ls / eval run
- [ ] 端到端跑通演示剧本 7 步，记录一次完整执行日志
- [ ] 打 tag v0.3.0

## W4 扩展 + 上线 + 评测（M4）

- [ ] clawops-skills：SKILL.md 规范 + 加载 + 启停 + 渐进式披露（先描述后正文）
- [ ] 量化 Skills 省 Token 效果（开/关对比，写入指标表）
- [ ] 自研 MCP Server ×1（工单或监控查询），并完成连通性检测页
- [ ] MCP 接入说明：与 Function Calling 的差异写入文档
- [ ] 审计页 + 简易看板（成功率、轮数、Token、时延分位）
- [ ] 可观测：Micrometer 指标 + Prometheus 抓取 + 关键面板截图
- [ ] deploy/prod：Dockerfile（多阶段、JVM 内存参数）、compose override、nginx + HTTPS、健康检查、优雅停机、PG 备份脚本（cron）
- [ ] CI 扩展：构建镜像 + 推送 + 跑评测集（CLI eval run）
- [ ] 公网部署 + Demo 只读账号 + 快速开始文档
- [ ] 评测：≥30 条样本全量跑，产出报告（命中率/轮数/Token/p50-p95/恢复成功率）
- [ ] 压测（轻量）：并发 5~10 会话，记录内存与错误率，确认 4G 不 OOM
- [ ] 《Agent 运行时架构分析与实现对照》文档（借鉴机制 ↔ 本地实现 ↔ 改动理由）
- [ ] README 重写：定位、架构图、快速开始、指标表、Demo、合规声明、Roadmap
- [ ] 5 分钟录屏 + 上传
- [ ] 简历项目描述段落（Infra 版 / 应用版各一份）
- [ ] 打 tag v1.0.0，冻结范围

## 可选任务（P3，仅在主线全部完成后启动）

- [ ] clawops-cli-rs（Rust + clap，仅调用 Gateway，不侵入主链路）—— 满足任务书 5.6 启用条件才做
- [ ] Python FastAPI + LangGraph 编排侧车（内存允许时）
- [ ] 第二个 IM 通道（验证适配器扩展成本）
- [ ] LangSmith/OTel 全链路追踪
- [ ] K8s manifests（仅文档化，不实际部署）

## 决策记录（ADR 索引）

| 编号 | 标题 | 状态 | 日期 |
| --- | --- | --- | --- |
| ADR-000 | JDK / Boot / LangChain4j 版本锁定 | 已完成 | 2026-10-13 |
| ADR-001 | Harness 与 LangChain4j 职责边界 | 已完成 | 2026-10-13 |
| ADR-002 | 上下文压缩阈值与保留策略 | 待填 |  |
| ADR-003 | 队列选型：Redis Stream vs 外部 MQ | 待填 |  |
| ADR-004 | 向量方案：pgvector HNSW | 待填 |  |
| ADR-005 | 是否引入 LangGraph 侧车 | 待填 |  |
| ADR-006 | 是否启用 Rust CLI 模块 | 待填 |  |
| ADR-007 | 开发环境：Windows 原生 + Docker Desktop + named volume | 已完成 | 2026-10-13 |

## 问题与阻塞日志

| 日期 | 问题 | 影响 | 处理 | 状态 |
| --- | --- | --- | --- | --- |
| 2026-10-13 | Docker Desktop daemon 未就绪（npipe 连接失败） | 无法本地验证 compose healthy / 无法跑 Testcontainers IT | 已在 PROGRESS 标记为阻塞；compose 文件已就绪，待手动启动 Docker Desktop 后执行 `docker compose -f deploy/docker-compose.dev.yml up -d` 验证 | 阻塞 |
| 2026-10-13 | Spring Boot 4.1 中 TestRestTemplate 移入 `spring-boot-resttestclient` | OpenAPI 契约 IT 原写法编译失败 | 改用 `org.springframework.test.web.servlet.client.RestTestClient`（spring-test 7 原生） | 已解决 |
| 2026-10-13 | Testcontainers 2.0.5 模块化重命名 | `EnabledIfDockerAvailable` 包路径、`PostgreSQLContainer` 泛型签名变化 | 修正为 `org.testcontainers.junit.jupiter.EnabledIfDockerAvailable` 与原生非泛型 `PostgreSQLContainer` | 已解决 |

## 每日日志（模板）

### 2026-10-13

- 完成：
  - 补 ADR-007（开发环境选型）、ADR-001（Harness 边界）、ADR-000（版本锁定）
  - 仓库骨架：.gitignore / .gitattributes / .env.example / .editorconfig / Issue & PR 模板 / docs/adr / docs/ai-log / docs/metrics
  - Maven 多模块：parent + 10 模块 + testbed，Spotless/Checkstyle/SpotBugs/Jacoco/failsafe 接入
  - 测试基建：FakeChatModel（可编程响应 + usage 注入）、ArchUnit 三条硬规则、PG/Redis Testcontainers 模板
  - API 契约：统一响应体、error-codes.yaml、springdoc 生成 docs/openapi/openapi.json 入仓
  - CI workflow：七阶段门禁（format-lint → build-test → integration → arch-coverage → mysql-guard → package → eval-smoke）
  - 兼容性 spike 通过：`./mvnw verify -DskipITs` BUILD SUCCESS（JDK 25.0.4 + Boot 4.1.1 + LangChain4j 1.20.2）
- 明日：
  - 手动启动 Docker Desktop，验证 `docker compose -f deploy/docker-compose.dev.yml up -d` 全部 healthy
  - 跑通 `./mvnw verify`（含 IT）
  - 准备云服务器初始化（Ubuntu 24.04 + Docker + swap）
- 阻塞：Docker Desktop daemon 在自动启动后仍未就绪，需人工确认 GUI 启动与 WSL backend 状态。
- AI 辅助记录：使用 CodeBuddy 生成全部骨架文件，人工 Review 修正了 Boot 4.1/Testcontainers 2.0 API 变化点；单测补充：harness FakeChatModel 单测、gateway 响应体与错误码契约测试、ArchUnit 规则测试。
- 资源观测：内存峰值约 2.1G（Maven 全量构建）/ API 调用 0 次 / 成本 ¥0

## 指标登记表（跑完填真实数字，禁止估算）

| 指标 | 基线（无优化/无 RAG） | 优化后 | 采集方式 |
| --- | --- | --- | --- |
| 根因命中率 |  |  | eval run |
| 服务定位准确率 |  |  | eval run |
| 平均工具调用轮数 |  |  | 任务表统计 |
| p50 / p95 时延 |  |  | 任务表统计 |
| 单任务 Token 消耗 |  |  | 审计表统计 |
| 上下文压缩比 |  |  | 审计表统计 |
| 失败恢复成功率 |  |  | 人工注入验证 |

## 完成度汇总

| 里程碑 | 计划 | 实际 | 状态 |
| --- | --- | --- | --- |
| M0 | D0–D1 | D0 主体完成，compose healthy 验证阻塞于 Docker daemon | [~] 进行中 |
