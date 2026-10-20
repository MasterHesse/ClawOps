package com.clawops.harness.task;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 任务状态机校验（conventions/testing.md §3 TDD 强制范围）。 */
@DisplayName("TaskState 任务状态机")
class TaskStateTest {

  @Test
  @DisplayName("CREATED 仅可迁移至 RUNNING")
  void shouldAllowCreatedOnlyToRunning() {
    assertThat(TaskState.CREATED.canTransitionTo(TaskState.RUNNING)).isTrue();
    assertThat(TaskState.CREATED.canTransitionTo(TaskState.PAUSED)).isFalse();
    assertThat(TaskState.CREATED.canTransitionTo(TaskState.SUCCEEDED)).isFalse();
  }

  @Test
  @DisplayName("RUNNING 可迁移至 SUCCEEDED/FAILED/PAUSED/CANCELLED，不可自迁移")
  void shouldAllowRunningToTerminalOrPause() {
    assertThat(TaskState.RUNNING.canTransitionTo(TaskState.SUCCEEDED)).isTrue();
    assertThat(TaskState.RUNNING.canTransitionTo(TaskState.FAILED)).isTrue();
    assertThat(TaskState.RUNNING.canTransitionTo(TaskState.PAUSED)).isTrue();
    assertThat(TaskState.RUNNING.canTransitionTo(TaskState.CANCELLED)).isTrue();
    assertThat(TaskState.RUNNING.canTransitionTo(TaskState.RUNNING)).isFalse();
  }

  @Test
  @DisplayName("PAUSED 可恢复至 RUNNING 或转 FAILED/CANCELLED，不可保持 PAUSED")
  void shouldAllowPausedResumeOrCancel() {
    assertThat(TaskState.PAUSED.canTransitionTo(TaskState.RUNNING)).isTrue();
    assertThat(TaskState.PAUSED.canTransitionTo(TaskState.FAILED)).isTrue();
    assertThat(TaskState.PAUSED.canTransitionTo(TaskState.CANCELLED)).isTrue();
    assertThat(TaskState.PAUSED.canTransitionTo(TaskState.PAUSED)).isFalse();
  }

  @Test
  @DisplayName("终态不可迁移且 isTerminal 为真")
  void shouldNotAllowTransitionFromTerminal() {
    assertThat(TaskState.SUCCEEDED.canTransitionTo(TaskState.RUNNING)).isFalse();
    assertThat(TaskState.FAILED.canTransitionTo(TaskState.RUNNING)).isFalse();
    assertThat(TaskState.CANCELLED.canTransitionTo(TaskState.RUNNING)).isFalse();
    assertThat(TaskState.SUCCEEDED.isTerminal()).isTrue();
    assertThat(TaskState.FAILED.isTerminal()).isTrue();
    assertThat(TaskState.CANCELLED.isTerminal()).isTrue();
    assertThat(TaskState.RUNNING.isTerminal()).isFalse();
    assertThat(TaskState.PAUSED.isTerminal()).isFalse();
  }
}
