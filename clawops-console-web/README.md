# clawops-console-web（前端控制台，W2 起接入）

技术栈：Vue 3 + Vite；构建产物由 Nginx 托管。

页面规划（TASK_BOOK §5.4）：对话台（SSE 流式）、任务中心、知识库管理、技能管理、审计、评测。

质量-gates §2 边界：console-web 独立，仅消费 HTTP/WS 契约（docs/openapi/）。
