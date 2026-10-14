package com.clawops.harness.context;

import com.clawops.harness.spi.AgentMessage;
import com.clawops.harness.spi.ModelResponse;
import com.clawops.harness.spi.TokenCounter;
import com.clawops.harness.spi.ToolCall;
import com.clawops.harness.spi.ToolDefinition;
import com.clawops.harness.spi.ToolResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
