package com.clawops.harness.task;

import com.clawops.harness.spi.ToolCall;
import java.time.Instant;
import java.util.Map;

/**
 * 任务单步检查点（落盘用）。
 *
 * <p>记录一次工具调用的入参出参摘要，用于断点恢复与全链路审计。{@link ToolCall#arguments()} 对齐入参； resultSummary 由 orchestrator 对
 * ToolResult.content 截断后写入。
 *
 * @param taskId 所属任务 ID
 * @param seq 步序（同一任务内单调递增）
 * @param toolCallId 工具调用 ID（与 AgentMessage.TOOL 消息对应）
 * @param toolName 工具名
 * @param arguments 工具入参（与 ToolCall 对齐）
 * @param resultStatus 工具结果状态（ToolResult.status）
 * @param resultSummary 结果摘要（截断）
 * @param createdAt 落盘时间
 */
public record StepCheckpoint(
    String taskId,
    int seq,
    String toolCallId,
    String toolName,
    Map<String, Object> arguments,
    String resultStatus,
    String resultSummary,
    Instant createdAt) {

  public StepCheckpoint {
    java.util.Objects.requireNonNull(taskId, "taskId must not be null");
    java.util.Objects.requireNonNull(toolName, "toolName must not be null");
    if (taskId.isBlank()) {
      throw new IllegalArgumentException("taskId must not be blank");
    }
    if (seq < 1) {
      throw new IllegalArgumentException("seq must be >= 1");
    }
    arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
  }
}
