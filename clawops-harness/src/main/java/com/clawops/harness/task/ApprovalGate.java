package com.clawops.harness.task;

import com.clawops.harness.spi.ToolCall;
import java.util.Optional;

/**
 * 审批闸门 SPI（harness 纯内核）。
 *
 * <p>AgentLoop 在执行工具前询问本闸门：返回 empty 表示直接执行；返回审批单号则暂停循环等待 HITL 决策。 orchestrator 的实现根据
 * PermissionManager 与 ApprovalStore 决定是否拦截；恢复重跑时由 ApprovedCallGate 放行已批准的调用（按
 * taskId+toolName+arguments 匹配）。
 */
public interface ApprovalGate {

  /** 若该调用需审批，返回审批单号；否则返回 empty。 */
  Optional<String> requireApproval(ToolCall call);
}
