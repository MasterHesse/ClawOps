/**
 * ClawOps 自研 Agent 内核（ADR-001：纯内核）。
 *
 * <p>AgentLoop / ToolRegistry / ContextManager / Permission / SubAgent / TaskStore / RetryPolicy
 * 将在本包及子包实现。主代码禁止依赖任何兄弟模块、Spring Web、Servlet、HTTP 客户端与 dev.langchain4j（由 ArchUnit 机器化校验，见
 * com.clawops.harness.arch.HarnessArchTest）。
 */
package com.clawops.harness;
