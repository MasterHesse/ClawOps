package com.clawops.harness.spi;

import java.util.List;
import java.util.Map;

/**
 * Agent 对话消息。
 *
 * @param role 消息角色
 * @param content 文本内容
 * @param toolCalls 模型返回的工具调用列表（assistant 消息可能携带）
 * @param toolCallId 工具结果消息对应的调用 ID（tool 角色使用）
 */
public record AgentMessage(Role role, String content, List<ToolCall> toolCalls, String toolCallId) {

  public enum Role {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL
  }

  public AgentMessage {
    toolCalls = List.copyOf(toolCalls == null ? List.of() : toolCalls);
  }

  public static AgentMessage system(String content) {
    return new AgentMessage(Role.SYSTEM, content, null, null);
  }

  public static AgentMessage user(String content) {
    return new AgentMessage(Role.USER, content, null, null);
  }

  public static AgentMessage assistant(String content) {
    return new AgentMessage(Role.ASSISTANT, content, null, null);
  }

  public static AgentMessage assistant(ToolCall... calls) {
    return new AgentMessage(Role.ASSISTANT, null, List.of(calls), null);
  }

  public static AgentMessage tool(String toolCallId, String content) {
    return new AgentMessage(Role.TOOL, content, null, toolCallId);
  }

  public boolean hasToolCalls() {
    return !toolCalls.isEmpty();
  }

  public Map<String, Object> toMap() {
    return Map.of("role", role.name().toLowerCase(), "content", content == null ? "" : content);
  }
}
