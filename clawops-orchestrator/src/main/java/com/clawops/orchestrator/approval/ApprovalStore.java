package com.clawops.orchestrator.approval;

import java.util.List;
import java.util.Optional;

/**
 * 审批单持久化（orchestrator 落 PG approval 表）。
 *
 * <p>{@link #decide} 校验状态机合法性（经 {@link ApprovalStatus#decide}），并乐观更新（AND status='PENDING'）防并发重复决策。
 */
public interface ApprovalStore {

  ApprovalRecord create(ApprovalRecord approval);

  Optional<ApprovalRecord> find(String approvalId);

  List<ApprovalRecord> findPendingByTask(String taskId);

  /** 应用决策；非 PENDING 状态再决策抛 {@link ApprovalStateException}。 */
  ApprovalRecord decide(
      String approvalId, ApprovalStatus decision, String reason, String decidedBy);
}
