package com.clawops.harness.context;

import com.clawops.harness.spi.AgentMessage;
import com.clawops.harness.spi.ModelResponse;
import com.clawops.harness.spi.TokenCounter;
import com.clawops.harness.spi.ToolCall;
import com.clawops.harness.spi.ToolDefinition;
import com.clawops.harness.spi.ToolResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 上下文管理器。
 *
 * <p>负责分层 Prompt 组装、token 计数与阈值压缩。保留内容优先级：System > Role > 工具描述 > 当前任务 > 最近对话。
 */
public class ContextManager {

  private final ContextConfig config;
  private final TokenCounter tokenCounter;
  private final CompressionStrategy compressionStrategy;

  private String systemPrompt = "";
  private String rolePrompt = "";
  private String taskPrompt = "";
  private List<ToolDefinition> tools = List.of();
  private final List<AgentMessage> history = new ArrayList<>();

  public ContextManager() {
    this(new ContextConfig(), new DefaultTokenCounter(), new WindowCompressionStrategy());
  }

  public ContextManager(
      ContextConfig config, TokenCounter tokenCounter, CompressionStrategy compressionStrategy) {
    this.config = config;
    this.tokenCounter = tokenCounter;
    this.compressionStrategy = compressionStrategy;
  }

  public ContextManager withSystemPrompt(String prompt) {
    this.systemPrompt = prompt == null ? "" : prompt;
    return this;
  }

  public ContextManager withRolePrompt(String prompt) {
    this.rolePrompt = prompt == null ? "" : prompt;
    return this;
  }

  public ContextManager withTools(Collection<ToolDefinition> tools) {
    this.tools = tools == null ? List.of() : List.copyOf(tools);
    return this;
  }

  public ContextManager withTask(String task) {
    this.taskPrompt = task == null ? "" : task;
    return this;
  }

  /** 注入历史消息（替换现有历史，断点恢复重建上下文用）。 */
  public ContextManager withHistory(List<AgentMessage> history) {
    this.history.clear();
    if (history != null) {
      this.history.addAll(history);
    }
    return this;
  }

  public ContextManager addUserMessage(String content) {
    history.add(AgentMessage.user(content));
    return this;
  }

  public ContextManager addAssistantMessage(ModelResponse response) {
    if (response.hasToolCalls()) {
      history.add(AgentMessage.assistant(response.toolCalls().toArray(new ToolCall[0])));
    } else if (response.text() != null) {
      history.add(AgentMessage.assistant(response.text()));
    }
    return this;
  }

  public ContextManager addToolResult(ToolCall call, ToolResult result) {
    history.add(AgentMessage.tool(call.id(), "[" + call.name() + "] " + result.content()));
    return this;
  }

  /** 构建最终 Prompt 消息列表，必要时触发压缩。 */
  public List<AgentMessage> build() {
    List<AgentMessage> reserved = buildReservedMessages();
    int reservedTokens = tokenCounter.countMessages(reserved);
    int budget = config.compressionThresholdTokens() - reservedTokens;

    List<AgentMessage> compressedHistory =
        compressionStrategy.compress(history, tokenCounter, Math.max(0, budget));

    List<AgentMessage> result = new ArrayList<>(reserved);
    result.addAll(compressedHistory);
    return result;
  }

  /** 当前消息总 token 数（含压缩后）。 */
  public int currentTokenCount() {
    return tokenCounter.countMessages(build());
  }

  public int historySize() {
    return history.size();
  }

  /**
   * 移除尾部未配对 tool result 的 assistant(toolCalls) 消息。
   *
   * <p>审批暂停时，模型已请求调用 DANGEROUS 工具（assistant toolCalls 消息入栈）但结果未回填； 断点恢复重建上下文后模型会重新发起调用，此时
   * ApprovalGate 按已批准的 approval 放行。 已配对的（assistant toolCalls + 对应 tool result 均在历史中）保留。详见 ADR-008。
   */
  public ContextManager trimUnresolvedToolCalls() {
    Set<String> resolvedIds = new HashSet<>();
    for (AgentMessage m : history) {
      if (m.role() == AgentMessage.Role.TOOL && m.toolCallId() != null) {
        resolvedIds.add(m.toolCallId());
      }
    }
    int i = history.size() - 1;
    while (i >= 0) {
      AgentMessage m = history.get(i);
      if (m.role() == AgentMessage.Role.ASSISTANT
          && m.hasToolCalls()
          && m.toolCalls().stream().noneMatch(c -> resolvedIds.contains(c.id()))) {
        history.remove(i);
        i--;
      } else {
        break;
      }
    }
    return this;
  }

  private List<AgentMessage> buildReservedMessages() {
    List<AgentMessage> reserved = new ArrayList<>();
    if (!systemPrompt.isEmpty()) {
      reserved.add(AgentMessage.system(systemPrompt));
    }
    if (!rolePrompt.isEmpty()) {
      reserved.add(AgentMessage.system(rolePrompt));
    }
    if (!tools.isEmpty()) {
      reserved.add(AgentMessage.system(buildToolDescriptions()));
    }
    if (!taskPrompt.isEmpty()) {
      reserved.add(AgentMessage.user("当前任务：" + taskPrompt));
    }
    return reserved;
  }

  private String buildToolDescriptions() {
    StringBuilder sb = new StringBuilder("可用工具：\n");
    for (ToolDefinition tool : tools) {
      sb.append("- ").append(tool.name()).append(": ").append(tool.description());
      sb.append(" [权限:").append(tool.permission()).append("]\n");
    }
    return sb.toString();
  }
}
