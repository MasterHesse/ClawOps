# ClawOps

> 自托管值班 / 运维常驻 Agent 平台（求职作品集：AI Infra / AI 应用工程师）

**状态：M0 准备阶段（骨架搭建中，非可用状态）**

- 任务书（强约束）：[docs/TASK_BOOK.md](docs/TASK_BOOK.md)
- 进度追踪：[docs/PROGRESS.md](docs/PROGRESS.md)
- 工程规约：[docs/conventions/](docs/conventions/)
- 决策记录：[docs/adr/](docs/adr/)

## 快速开始（M0 后补全）

```bash
cp .env.example .env   # 填入真实值
docker compose -f deploy/docker-compose.dev.yml up -d
./mvnw verify
```

## 合规声明

仅做 clean-room 实现与公开架构分析，不引用、不提交任何泄露源码产物。
