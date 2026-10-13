# testbed（靶场，W3 落地）

原则：指标要真、业务要假、故障可控（TASK_BOOK §7）。

- 真实部分：order-service、inventory-service（极简 Spring Boot，/actuator/prometheus）+ Prometheus + node_exporter + Nginx
- Mock 部分：mock-ops-system（告警/工单/发布记录/服务拓扑/值班人 5 张表 + REST + MCP 暴露）
- 故障注入：F1~F5 一键脚本 + ground truth（eval/ground_truth.yaml）

禁止引入 Online Boutique / Train-Shuttle / Sock Shop 等重型开源靶场。
