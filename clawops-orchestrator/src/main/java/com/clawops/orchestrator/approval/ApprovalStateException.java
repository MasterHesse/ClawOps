package com.clawops.orchestrator.approval;

/** 审批状态机非法决策异常（运行时）。 */
public class ApprovalStateException extends RuntimeException {

  public ApprovalStateException(String message) {
    super(message);
  }
}
