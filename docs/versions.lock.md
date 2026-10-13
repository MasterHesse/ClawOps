# versions.lock（版本锁定说明）

> 与 [ADR-000](adr/ADR-000-version-lock.md) 同步维护；升级规则：patch 可跟随，minor 须重跑验证清单并更新本文与 ADR。

## 运行时与框架

| 组件 | 锁定版本 | 锁定位置 |
| --- | --- | --- |
| JDK | Temurin 25.0.4.1 LTS | 父 POM `java.version=25`；CI setup-java temurin 25 |
| Spring Boot | 4.1.1 | 父 POM `spring-boot-starter-parent` |
| LangChain4j | 1.20.2（BOM） | 父 POM `langchain4j.version` |
| springdoc-openapi | 3.1.1 | 父 POM `springdoc.version` |
| Picocli | 4.7.7 | 父 POM `picocli.version` |

## 中间件（compose 与云服务器一致）

| 组件 | 镜像 / 版本 |
| --- | --- |
| PostgreSQL 17 + pgvector | `pgvector/pgvector:0.8.7-pg17-trixie` |
| Redis 7 | `redis:7.4.11` |
| Prometheus | `prom/prometheus:v3.13.4` |
| node_exporter | `prom/node-exporter:v1` |
| Nginx | `nginx:1.30` |

## 测试基建

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| Testcontainers | 2.0.5（`testcontainers` / `testcontainers-postgresql` / `testcontainers-junit-jupiter`） | 2.x 起 artifact 前缀为 `testcontainers-`；PG 容器类在 `org.testcontainers.postgresql` 包 |
| ArchUnit | 1.5.1 | 三条硬规则（quality-gates §2） |
| Maven | 3.9.x（wrapper 固定） | `./mvnw` |

## 构建插件

| 插件 | 版本 |
| --- | --- |
| spotless-maven-plugin | 3.10.4 |
| maven-checkstyle-plugin | 3.6.0 |
| spotbugs-maven-plugin | 4.10.5.0 |
| jacoco-maven-plugin | 0.8.15 |

## 验证记录（2026-10-13）

- Maven Central 元数据实测：上述全部 GA 版本存在
- Boot 4.1.1 父 POM `java.version=17`（基线），JDK 25 兼容
- LangChain4j 1.20.2 接口实测（javap）：`ChatModel#doChat(ChatRequest)` 为实现点；
  `ToolExecutionRequest.builder().id/name/arguments`、`ChatResponse.builder().aiMessage/metadata`
