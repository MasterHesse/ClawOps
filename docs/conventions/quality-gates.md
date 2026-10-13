# 静态质量门禁与架构约束

> 细则文档，与 TASK_BOOK §6 同等效力，冲突时以 TASK_BOOK 为准。

## 1. 工具链

| 环节 | 工具 | 阻断级别 |
| --- | --- | --- |
| 格式化 | Spotless（google-java-format） | 违规即 CI 失败 |
| 编码规范 | Checkstyle（精简规则集：命名、导入、行宽 120） | error 阻断；warning 仅记录 |
| 缺陷分析 | SpotBugs | High 阻断；Medium 记录 |
| 架构约束 | ArchUnit（以测试形式随单测运行） | 违规即失败 |
| 覆盖率 | Jacoco check | 低于门槛即失败（矩阵见 conventions/testing.md） |
| 红线校验 | grep `(?i)(mysql\|mariadb\|jdbc:mysql\|jdbc:mariadb)`（范围见 §5） | 命中即失败 |

## 2. ArchUnit 规则（首版）

模块依赖方向（→ 表示"允许依赖"）：

```text
gateway     → harness, connector, workers
orchestrator → harness, connector
rag         → 仅自身 + langchain4j
workers     → harness, connector
skills/mcp/cli → harness（经其 SPI）
console-web → 独立，仅消费 HTTP/WS 契约
```

三条硬规则（落地为 ArchUnit 测试，任何一条违反即构建失败）：

1. **无循环依赖**：任一模块切片间禁止循环。
2. **harness 纯内核**：clawops-harness 禁止依赖任何兄弟模块、`org.springframework.web.*`、`jakarta.servlet.*`、任何 HTTP 客户端。与外界的交互只经自研 SPI（ToolDefinition、ModelClient 等接口）。
3. **LangChain4j 不出边界**：langchain4j 类型不得出现在 harness 与 orchestrator 的 public API 签名中，只允许存在于 adapter 实现类内部。这是 ADR-001（Harness 与框架职责边界）的机器化校验。

## 3. CI 阶段划分（GitHub Actions，fail-fast）

| # | Job | 内容 | 触发 |
| --- | --- | --- | --- |
| 1 | format-lint | spotless:check + checkstyle | 每次 push / PR |
| 2 | build-test | compile + 单测（surefire）+ SpotBugs | 同上 |
| 3 | integration | failsafe + Testcontainers（需 docker） | 同上 |
| 4 | arch-coverage | ArchUnit（随单测）+ jacoco:check | 同上 |
| 5 | mysql-guard | MySQL 家族红线 grep 校验（模式与范围见 §5） | 同上 |
| 6 | package | 镜像构建（+ 推送） | 仅 main / dev |
| 7 | eval-smoke | CLI eval run 冒烟子集 ≤5 条 | 仅 tag / 上线前 |

## 4. PR 合并门禁清单（写入 PR 模板，逐项自查）

- [ ] CI 全绿（Job 1–5）
- [ ] 新增 / 变更行为有对应测试；核心模块（testing.md §3 范围）测试先行
- [ ] 模块覆盖率不低于当前门槛
- [ ] 涉及接口变更：openapi.json 已重新生成并有意提交（见 conventions/api.md §6）
- [ ] 涉及 prompt 变更：已按 conventions/llm.md 登记（P1 待补，先在 PR 描述注明）
- [ ] AI 辅助生成：body 注明 `Assisted-by: <tool>`，人工 Review 修改点记录到 docs/ai-log/
- [ ] 偏离任务书的决定：ADR 已登记，PR 关联 ADR 编号

## 5. MySQL 红线校验细则（对应 TASK_BOOK §5.2）

- 匹配模式（不区分大小写）：`mysql|mariadb|jdbc:mysql|jdbc:mariadb`。
- 校验范围：全部 pom.xml、docker-compose*.yml、Flyway 脚本（db/migration）、application*.yml / *.properties、Dockerfile、src/ 下 Java 源码。
- 排除：docs/ 目录（任务书与规约中对 MySQL 的禁令性表述属合法提及，不参与校验）。
- 命中即 Job 5 失败；唯一修复方式是移除 MySQL 家族依赖 / 配置，**禁止任何形式的白名单豁免**。
- SQL 方言由 review 保障 + 参数化基线：主键 uuid + gen_random_uuid()、时间 timestamptz、半结构化 jsonb、全量参数化（见 TASK_BOOK §5.2 PG 方言基线）。
