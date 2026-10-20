package com.clawops.harness.task;

import java.util.EnumSet;
import java.util.Set;

/**
 * 任务状态。
 *
 * <p>合法迁移：CREATED→RUNNING；RUNNING→{SUCCEEDED,FAILED,PAUSED,CANCELLED}；PAUSED→{RUNNING,FAILED,CANCELLED}；
 * SUCCEEDED/FAILED/CANCELLED 为终态。
 */
public enum TaskState {
  CREATED,
  RUNNING,
  PAUSED,
  SUCCEEDED,
  FAILED,
  CANCELLED;

  private static final Set<TaskState> TERMINAL = EnumSet.of(SUCCEEDED, FAILED, CANCELLED);

  /** 当前状态是否可迁移到目标状态。 */
  public boolean canTransitionTo(TaskState target) {
    if (target == null) {
      return false;
    }
    return switch (this) {
      case CREATED -> target == RUNNING;
      case RUNNING -> target != RUNNING && target != CREATED;
      case PAUSED ->
          target == RUNNING || target == SUCCEEDED || target == FAILED || target == CANCELLED;
      default -> false; // 终态不可迁移
    };
  }

  /** 是否为终态。 */
  public boolean isTerminal() {
    return TERMINAL.contains(this);
  }
}
