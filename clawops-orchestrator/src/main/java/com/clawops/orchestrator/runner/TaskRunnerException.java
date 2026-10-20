package com.clawops.orchestrator.runner;

/** 任务运行时异常，映射为 ApiErrorCode（2002 TASK_NOT_RESUMABLE / 2003 APPROVAL_STATE_CONFLICT）。 */
public class TaskRunnerException extends RuntimeException {

  public TaskRunnerException(String message) {
    super(message);
  }
}
