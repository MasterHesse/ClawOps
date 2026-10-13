# ClawOps 项目备忘

- 定位：自托管值班/运维常驻 Agent 平台，求职作品集（AI Infra / AI 应用工程师）。
- 核心文档：docs/TASK_BOOK.md（任务书 v1.0，强约束）、docs/PROGRESS.md（进度追踪，每日更新）。
- 关键红线：仅 PostgreSQL（禁 MySQL，CI grep 校验）；服务器 2C4G + 2G swap；API 成本 ≤¥100/月。
- 技术栈：Java 25 + Spring Boot 4.1.1 + LangChain4j 1.20.2 + PG17/pgvector + Redis 7 Stream；前端 Vue 3。
- 开发模式：Vibe Coding 四步闭环（AI 生成 → 人工 Review → 补单测 → CI 绿灯才算完成）；决策必须先记 ADR 再改代码。
- 模块：gateway / harness / orchestrator / rag / skills / mcp / connector / workers / cli / console-web + testbed + deploy（Maven 多模块）。
- 开发环境：Windows 原生 + Docker Desktop，容器数据全部使用 named volume，禁止源码 bind mount（ADR-007）。
- 构建命令：`./mvnw verify` 全量（含 IT，需 Docker）；`./mvnw verify -DskipITs` 本地无 Docker 门禁；`./mvnw spotless:apply` 自动格式化。
- Boot 4.1 已知迁移点：`TestRestTemplate` 迁出 `spring-boot-starter-test`，M0 使用 `RestTestClient`（spring-test 7）；`@WebMvcTest` 等测试切片也拆分到独立模块。
- Testcontainers 2.0.5 已知迁移点：模块拆分为 `testcontainers`、`testcontainers-postgresql`、`testcontainers-junit-jupiter`；`EnabledIfDockerAvailable` 位于 `org.testcontainers.junit.jupiter`；`PostgreSQLContainer` 不再带泛型参数。
- 2026-10-13：完成工程约束差距分析（8 项待补：测试策略 TDD、静态门禁、API 契约、配置与密钥、日志规范、异常降级、Prompt 资产、开发工作流），逐项补充中。
