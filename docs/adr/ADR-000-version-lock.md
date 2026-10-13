# ADR-000：JDK / Spring Boot / LangChain4j / 中间件版本锁定

> 状态：已接受（spike 结论见文末验证记录）
> 日期：2026-10-13
> 关联：TASK_BOOK §5.1；PROGRESS.md M0「版本锁定」「兼容性 spike」

## 背景

任务书主选 Java 25 + Spring Boot 4.1 + LangChain4j 1.20，要求 D0 实测依赖兼容性，不通过则降级（JDK 21 / Boot 4.0）并记 ADR。2026-10-13 经 Maven Central 元数据实测确认以下版本均已 GA。

## 决定（versions.lock）

| 组件 | 锁定版本 | 说明 |
| --- | --- | --- |
| JDK | **Temurin 25.0.4.1 LTS** | 本地 `.tools/jdk-25.0.4.1+1`（gitignored），CI 用 setup-java temurin 25；`maven.compiler.release=25` |
| Spring Boot | **4.1.1** | 父 POM `spring-boot-starter-parent`；基线 Java 17（实测其 POM `java.version=17`），25 兼容 |
| LangChain4j | **1.20.2** | BOM `dev.langchain4j:langchain4j-bom`；不追 1.21/1.22，锁定任务书指定 1.20.x 线 |
| PostgreSQL | **17 + pgvector（pgvector/pgvector:pg17 镜像）** | 云服务器与本地 compose 同版本 |
| Redis | **7（redis:7 镜像）** | Stream 队列 + 死信 |
| Maven | 3.9.9（本地）/ 3.9.x（CI wrapper） | 引入 mvnw wrapper 固定 CI 侧版本 |

版本升级规则：仅安全补丁（patch）可跟随；minor 升级须重跑本 ADR 的验证清单并更新本文。

## 验证记录（spike）

- [x] Maven Central 元数据：spring-boot-starter-parent 4.1.1 GA 存在（4.2.0 尚在 M2，不采用）
- [x] Maven Central 元数据：langchain4j 1.20.2 GA 存在（latest 1.22.0，不追新）
- [x] Boot 4.1.1 父 POM `java.version=17`，与 JDK 25 兼容（17 ≤ 25）
- [x] 本地 JDK 25.0.4.1（Temurin）`mvn verify` 多模块骨架 + LangChain4j 最小 Demo 测试通过（结果见 PROGRESS.md M0 日志；若未通过，本 ADR 状态回退为「草案」并按任务书降级）

## 影响

- 全模块 `maven.compiler.release=25`：本机若无 JDK 25，需安装或改用 `.tools/` 内分发（不入库）。
- Boot 4.1 的 Starter 命名 / 自动配置与 3.x 差异由骨架期暴露，问题记录至 PROGRESS.md 阻塞日志。
- LangChain4j 1.20 的 `ChatLanguageModel` 接口签名与 FakeChatModel 实现强绑定，升级需同步检查。
