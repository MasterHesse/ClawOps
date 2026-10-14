package com.clawops.harness.loop;

import com.clawops.harness.context.ContextConfig;
import com.clawops.harness.context.ContextManager;
import com.clawops.harness.context.WindowCompressionStrategy;
import com.clawops.harness.permission.PermissionCheckResult;
import com.clawops.harness.permission.PermissionManager;
import com.clawops.harness.retry.CircuitBreaker;
import com.clawops.harness.retry.RetryConfig;
import com.clawops.harness.retry.RetryExecutor;
import com.clawops.harness.spi.AgentEventListener;
import com.clawops.harness.spi.ModelClient;
import com.clawops.harness.spi.ModelResponse;
import com.clawops.harness.spi.TokenCounter;
import com.clawops.harness.spi.TokenUsage;
import com.clawops.harness.spi.ToolCall;
import com.clawops.harness.spi.ToolDefinition;
import com.clawops.harness.spi.ToolResult;
import com.clawops.harness.tool.ToolRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 自研 Agent 循环：感知-推理-行动-反馈。
 *
 * <p>集成 ToolRegistry、PermissionManager、RetryExecutor、LoopGuard、ContextManager，提供步数/时间上限与事件回调。
 */
public class AgentLoop {

  private final ModelClient modelClient;
  private final ToolRegistry toolRegistry;
  private final PermissionManager permissionManager;
  private final RetryExecutor retryExecutor;
  private final RetryConfig retryConfig;
  private final LoopGuard loopGuard;
  private final TokenCounter tokenCounter;
  private final AgentEventListener listener;
  private final LoopConfig config;
  private final ContextConfig contextConfig;

  private final Map<String, CircuitBreaker> circuitBreakers = new HashMap<>();

  public AgentLoop(
      ModelClient modelClient,
      ToolRegistry toolRegistry,
      PermissionManager permissionManager,
      RetryExecutor retryExecutor,
      RetryConfig retryConfig,
      LoopGuard loopGuard,
      TokenCounter tokenCounter,
      LoopConfig config,
      ContextConfig contextConfig) {
    this(
        modelClient,
        toolRegistry,
        permissionManager,
        retryExecutor,
        retryConfig,
        loopGuard,
        tokenCounter,
        null,
        config,
        contextConfig);
  }

  public AgentLoop(
      ModelClient modelClient,
      ToolRegistry toolRegistry,
      PermissionManager permissionManager,
      RetryExecutor retryExecutor,
      RetryConfig retryConfig,
      LoopGuard loopGuard,
      TokenCounter tokenCounter,
      AgentEventListener listener,
      LoopConfig config,
      ContextConfig contextConfig) {
    this.modelClient = Objects.requireNonNull(modelClient);
    this.toolRegistry = Objects.requireNonNull(toolRegistry);
    this.permissionManager = Objects.requireNonNull(permissionManager);
    this.retryExecutor = Objects.requireNonNull(retryExecutor);
    this.retryConfig = Objects.requireNonNull(retryConfig);
    this.loopGuard = Objects.requireNonNull(loopGuard);
    this.tokenCounter = Objects.requireNonNull(tokenCounter);
    this.listener = listener == null ? new AgentEventListener() {} : listener;
    this.config = Objects.requireNonNull(config);
    this.contextConfig = Objects.requireNonNull(contextConfig);
  }

  /** 返回一个使用新监听器的新 AgentLoop 实例。 */
  public AgentLoop withListener(AgentEventListener listener) {
    return new AgentLoop(
        modelClient,
        toolRegistry,
        permissionManager,
        retryExecutor,
        retryConfig,
        loopGuard,
        tokenCounter,
        listener,
        config,
        contextConfig);
  }

  /** 执行一次任务。 */
  public LoopResult run(String task) {
    ContextManager context =
        new ContextManager(contextConfig, tokenCounter, new WindowCompressionStrategy())
            .withSystemPrompt(config.systemPrompt())
            .withRolePrompt(config.rolePrompt())
            .withTools(toolRegistry.all())
            .withTask(task)
            .addUserMessage(task);

    Instant start = Instant.now();
    int steps = 0;
    TokenUsage totalUsage = TokenUsage.zero();

    try {
      while (steps < config.maxSteps()) {
        if (Duration.between(start, Instant.now()).getSeconds() >= config.maxDurationSeconds()) {
          return LoopResult.timeout(steps, totalUsage);
        }

        steps++;
        ModelResponse response = modelClient.chat(context.build(), toolRegistry.all());
        totalUsage = totalUsage.plus(response.tokenUsage());

        if (!response.hasToolCalls()) {
          String answer = response.text() == null ? "" : response.text();
          listener.onDelta(answer);
          listener.onDone(answer, steps, totalUsage);
          return LoopResult.success(answer, steps, totalUsage);
        }

        context.addAssistantMessage(response);
        for (ToolCall call : response.toolCalls()) {
          listener.onToolCall(call);

          if (!loopGuard.check(call)) {
            ToolResult result = ToolResult.loopDetected("检测到重复调用，终止循环");
            listener.onToolResult(call.id(), result);
            context.addToolResult(call, result);
            String answer = "检测到死循环：工具 " + call.name() + " 被重复调用";
            listener.onDone(answer, steps, totalUsage);
            return LoopResult.loopDetected(answer, steps, totalUsage);
          }

          ToolResult result = executeTool(call);
          listener.onToolResult(call.id(), result);
          context.addToolResult(call, result);
        }
      }

      return LoopResult.maxSteps(steps, totalUsage);
    } catch (Exception e) {
      listener.onError(e);
      return LoopResult.error(e.getMessage(), steps, totalUsage);
    }
  }

  private ToolResult executeTool(ToolCall call) {
    ToolDefinition tool = toolRegistry.get(call.name()).orElse(null);
    if (tool == null) {
      return ToolResult.error("未知工具: " + call.name());
    }

    PermissionCheckResult permission = permissionManager.check(tool);
    if (!permission.allowed()) {
      return ToolResult.forbidden(permission.reason());
    }

    CircuitBreaker breaker =
        circuitBreakers.computeIfAbsent(call.name(), k -> new CircuitBreaker());
    return retryExecutor.execute(
        () -> tool.executor().execute(call), breaker, retryConfig, call.name());
  }
}
