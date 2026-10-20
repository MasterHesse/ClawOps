package com.clawops.orchestrator.approval;

import com.clawops.harness.spi.Permission;
import com.clawops.harness.spi.ToolCall;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 审批单（不可变）。
 *
 * @param id 审批单 ID（UUID 字符串）
 * @param taskId 所属任务 ID
 * @param toolCallId 触发审批的工具调用 ID
 * @param toolName 工具名
 * @param arguments 工具入参（与 ToolCall 对齐）
 * @param requiredPermission 触发审批所需权限等级
 * @param status 审批状态
 * @param reason 决策理由（决策后填写）
 * @param decidedBy 决策人（决策后填写）
 * @param createdAt 创建时间
 * @param decidedAt 决策时间
 */
public record ApprovalRecord(
    String id,
    String taskId,
    String toolCallId,
    String toolName,
    Map<String, Object> arguments,
    Permission requiredPermission,
    ApprovalStatus status,
    String reason,
    String decidedBy,
    Instant createdAt,
    Instant decidedAt) {

  public ApprovalRecord {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(taskId, "taskId must not be null");
    Objects.requireNonNull(toolCallId, "toolCallId must not be null");
    Objects.requireNonNull(toolName, "toolName must not be null");
    Objects.requireNonNull(requiredPermission, "requiredPermission must not be null");
    Objects.requireNonNull(status, "status must not be null");
    arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
  }

  /** 由工具调用创建 PENDING 审批单。 */
  public static ApprovalRecord pending(
      String id, String taskId, ToolCall call, Permission requiredPermission, Instant now) {
    return new ApprovalRecord(
        id,
        taskId,
        call.id(),
        call.name(),
        call.arguments(),
        requiredPermission,
        ApprovalStatus.PENDING,
        null,
        null,
        now,
        null);
  }

  /** 应用决策结果，生成新记录。 */
  public ApprovalRecord withDecision(
      ApprovalStatus decision, String reason, String decidedBy, Instant now) {
    ApprovalStatus next = status.decide(decision);
    return new ApprovalRecord(
        id,
        taskId,
        toolCallId,
        toolName,
        arguments,
        requiredPermission,
        next,
        reason,
        decidedBy,
        createdAt,
        now);
  }
}
