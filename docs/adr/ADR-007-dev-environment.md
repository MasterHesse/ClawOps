# ADR-007：开发环境选型——Windows 原生 + Docker Desktop

> 状态：已接受
> 日期：2026-10-13
> 关联：TASK_BOOK §5.1（容器）、§5.3（服务器）；PROGRESS.md M0

## 背景

M0 起采用本地 Docker Compose 容器化开发（PG17+pgvector / Redis 7 / Prometheus / node_exporter / nginx），最终经 Compose 打包推送至 Ubuntu 24.04 云服务器上线。开发机为 Windows 11，需决定是否将开发环境迁移至 WSL。

## 选项

| 选项 | 说明 |
| --- | --- |
| A. Windows 原生 + Docker Desktop | 仓库留 NTFS（D:\Dev\Projects\ClawOps），IDE / JDK / Maven 跑 Windows 侧；Docker Desktop 本身以 WSL2 后端运行容器 |
| B. 整体迁移 WSL2（Ubuntu + ext4） | 仓库迁至 WSL 文件系统，JDK / Maven / IDE 全部在 WSL 侧 |

关键事实：Docker Desktop for Windows 的容器本就运行于 WSL2 轻量 VM。两选项的容器层与最终镜像**完全一致**，差异仅在宿主侧工具链与仓库位置。

## 决定

采用**选项 A**：Windows 原生 + Docker Desktop（WSL2 后端）。依据：

1. 本项目开发模式为「应用跑宿主机 JVM（IDE 启动），容器仅承载基础设施」——不存在源码 bind mount 进容器的需求，Windows 跨文件系统 I/O 的主要痛点不成立。
2. IntelliJ + JDK 25 + Maven 在 Windows 原生支持成熟，零迁移成本；迁移 WSL 需重建工具链且 IDE 跨 `\\wsl$` 索引性能差。
3. 生产一致性由镜像 + compose 声明保证；CI（GitHub Actions Ubuntu runner）构建镜像，本地 OS 差异由 CI 兜底。

### 配套强制约定

1. **PG / Redis 等容器数据一律使用 named volume**，禁止 bind mount 到 Windows 路径（gRPC-FUSE 共享层 I/O 性能与 fsync 语义损耗）。
2. **`.gitattributes` 强制 `*.sh` / `*.sql` / `*.yml` LF 行尾**，防止 CRLF 脚本在 Ubuntu 服务器报错。
3. `deploy/` 下 bash 脚本本地仅做语法检查（`bash -n`），真实执行验证在云服务器完成。

## 影响

- M0 验收口径（compose 容器全部 healthy + mvn verify）在 Windows 侧无阻碍。
- 云服务器初始化（Ubuntu 24.04 / swap / 安全组 / certbot）不受影响，属独立手动任务。
- 若未来切换为「源码 bind mount 热更新」开发模式，需重开 ADR 评估迁移 WSL。
