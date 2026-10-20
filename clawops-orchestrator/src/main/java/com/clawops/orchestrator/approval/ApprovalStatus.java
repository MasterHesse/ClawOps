package com.clawops.orchestrator.approval;

/**
 * 审批状态机：PENDING → {APPROVED, REJECTED}（一次性决策，不可重复）。
 *
 * <p>{@link #decide(ApprovalStatus)} 校验迁移合法性：仅 PENDING 可决策，目标只能是 APPROVED/REJECTED。
 */
public enum ApprovalStatus {
  PENDING,
  APPROVED,
  REJECTED;

  /** 是否已决策（非 PENDING）。 */
  public boolean isDecided() {
    return this != PENDING;
  }

  /**
   * 校验并返回决策结果。
   *
   * @throws IllegalArgumentException 目标非 APPROVED/REJECTED
   * @throws ApprovalStateException 当前状态非 PENDING（已决策）
   */
  public ApprovalStatus decide(ApprovalStatus decision) {
    if (decision == null || decision == PENDING) {
      throw new IllegalArgumentException("决策目标只能是 APPROVED 或 REJECTED");
    }
    if (this != PENDING) {
      throw new ApprovalStateException("审批单已决策: " + this);
    }
    return decision;
  }
}
