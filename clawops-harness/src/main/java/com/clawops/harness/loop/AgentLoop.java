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
import com.clawops.harness.spi.AgentMessage;
import com.clawops.harness.spi.ModelClient;
import com.clawops.harness.spi.ModelResponse;
import com.clawops.harness.spi.TokenCounter;
import com.clawops.harness.spi.TokenUsage;
import com.clawops.harness.spi.ToolCall;
import com.clawops.harness.spi.ToolDefinition;
import com.clawops.harness.spi.ToolResult;
import com.clawops.harness.task.ApprovalGate;
import com.clawops.harness.tool.ToolRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
  private final ApprovalGate approvalGate;

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
        contextConfig,
        null);
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
    this(
        modelClient,
        toolRegistry,
        permissionManager,
        retryExecutor,
        retryConfig,
        loopGuard,
        tokenCounter,
        listener,
        config,
        contextConfig,
        null);
  }

  private AgentLoop(
      ModelClient modelClient,
      ToolRegistry toolRegistry,
      PermissionManager permissionManager,
      RetryExecutor retryExecutor,
      RetryConfig retryConfig,
      LoopGuard loopGuard,
      TokenCounter tokenCounter,
      AgentEventListener listener,
      LoopConfig config,
      ContextConfig contextConfig,
      ApprovalGate approvalGate) {
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
    this.approvalGate = approvalGate;
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
        contextConfig,
        approvalGate);
  }

  /** 返回一个使用新审批闸门的新 AgentLoop 实例（断点恢复重跑时注入 ApprovedCallGate）。 */
  public AgentLoop withApprovalGate(ApprovalGate gate) {
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
        contextConfig,
        gate);
  }

  /** 执行一次任务。 */
  public LoopResult run(String task) {
    return run(task, List.of());
  }

  /**
   * 执行一次任务，可选注入历史上下文（断点恢复重建历史用）。
   *
   * <p>恢复时传入上次快照的 {@link AgentMessage} 列表；AgentLoop 会重建上下文后续跑。 详见 ADR-008。
   */
  public LoopResult run(String task, List<AgentMessage> resumedHistory) {
    ContextManager context =
        new ContextManager(contextConfig, tokenCounter, new WindowCompressionStrategy())
            .withSystemPrompt(config.systemPrompt())
            .withRolePrompt(config.rolePrompt())
            .withTools(toolRegistry.all())
            .withTask(task)
            .withHistory(resumedHistory)
            .addUserMessage(task);
    return runInternal(context);
  }

  private LoopResult runInternal(ContextManager context) {
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
    } catch (ApprovalRequiredException e) {
      listener.onApprovalRequired(e.call(), e.approvalId());
      return LoopResult.paused(e.call(), e.approvalId(), steps, totalUsage);
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

    // 审批闸门优先于权限校验：需审批时暂停循环，等待 HITL 决策（ADR-008）。
    if (approvalGate != null) {
      Optional<String> approvalId = approvalGate.requireApproval(call);
      if (approvalId.isPresent()) {
        throw new ApprovalRequiredException(call, approvalId.get());
      }
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

  /** executeTool 中检测到需审批时抛出，由 runInternal 捕获转为 PAUSED_FOR_APPROVAL。 */
  private static final class ApprovalRequiredException extends RuntimeException {

    private final ToolCall call;
    private final String approvalId;

    ApprovalRequiredException(ToolCall call, String approvalId) {
      super("approval required: " + call.name());
      this.call = call;
      this.approvalId = approvalId;
    }

    ToolCall call() {
      return call;
    }

    String approvalId() {
      return approvalId;
    }
  }
}
