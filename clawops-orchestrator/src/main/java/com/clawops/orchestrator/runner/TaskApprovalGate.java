package com.clawops.orchestrator.runner;

import com.clawops.harness.spi.Permission;
import com.clawops.harness.spi.ToolCall;
import com.clawops.harness.spi.ToolDefinition;
import com.clawops.harness.task.ApprovalGate;
import com.clawops.harness.tool.ToolRegistry;
import com.clawops.orchestrator.approval.ApprovalRecord;
import com.clawops.orchestrator.approval.ApprovalStore;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/** 任务级审批闸门：DANGEROUS 工具创建 PENDING 审批单并暂停；断点恢复重跑时已批准的调用（按 toolName + 规范化入参匹配）直接放行（ADR-008）。 */
public class TaskApprovalGate implements ApprovalGate {

  private final ApprovalStore approvalStore;
  private final ToolRegistry toolRegistry;
  private final String taskId;

  public TaskApprovalGate(ApprovalStore approvalStore, ToolRegistry toolRegistry, String taskId) {
    this.approvalStore = approvalStore;
    this.toolRegistry = toolRegistry;
    this.taskId = taskId;
  }

  @Override
  public Optional<String> requireApproval(ToolCall call) {
    String callKey = canonicalKey(call.name(), call.arguments());
    for (ApprovalRecord approved : approvalStore.findApprovedByTask(taskId)) {
      if (canonicalKey(approved.toolName(), approved.arguments()).equals(callKey)) {
        return Optional.empty(); // 已批准，放行
      }
    }

    ToolDefinition tool = toolRegistry.get(call.name()).orElse(null);
    if (tool != null && tool.permission() == Permission.DANGEROUS) {
      String approvalId = UUID.randomUUID().toString();
      approvalStore.create(
          ApprovalRecord.pending(approvalId, taskId, call, Permission.DANGEROUS, Instant.now()));
      return Optional.of(approvalId);
    }
    return Optional.empty();
  }

  private static String canonicalKey(String toolName, Map<String, Object> args) {
    return toolName + "|" + new TreeMap<>(args == null ? Map.of() : args).toString();
  }
}
