package com.clawops.harness.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.clawops.harness.spi.AgentMessage;
import com.clawops.harness.spi.TokenUsage;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 任务记录构造与不可变迁移（conventions/testing.md §3 TDD 强制范围）。 */
@DisplayName("TaskRecord 任务记录")
class TaskRecordTest {

  @Test
  @DisplayName("新建任务：默认 steps=0、token 零、上下文空、暂停原因为空")
  void shouldCreateWithDefaults() {
    Instant now = Instant.now();
    TaskRecord task = TaskRecord.create("task-1", "诊断 inventory CPU 飙高", now);
    assertThat(task.id()).isEqualTo("task-1");
    assertThat(task.status()).isEqualTo(TaskState.CREATED);
    assertThat(task.steps()).isZero();
    assertThat(task.tokenUsage()).isEqualTo(TokenUsage.zero());
    assertThat(task.contextMessages()).isEmpty();
    assertThat(task.pauseReason()).isNull();
    assertThat(task.result()).isNull();
    assertThat(task.createdAt()).isEqualTo(now);
    assertThat(task.updatedAt()).isEqualTo(now);
  }

  @Test
  @DisplayName("id / prompt / status 不可为 null 或空串")
  void shouldRejectBlankFields() {
    Instant now = Instant.now();
    assertThatThrownBy(() -> TaskRecord.create(null, "p", now))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> TaskRecord.create("  ", "p", now))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> TaskRecord.create("id", "", now))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> TaskRecord.create("id", "p", null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("withStatus 迁移时 steps 不可为负")
  void shouldRejectNegativeStepsOnTransition() {
    TaskRecord task = TaskRecord.create("t", "p", Instant.EPOCH);
    assertThatThrownBy(() -> task.withStatus(TaskState.RUNNING, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("withStatus 完成迁移：更新状态、步数、时间戳")
  void shouldTransitionStatus() {
    TaskRecord task = TaskRecord.create("t", "p", Instant.EPOCH);
    TaskRecord running = task.withStatus(TaskState.RUNNING, 0);
    assertThat(running.status()).isEqualTo(TaskState.RUNNING);
    assertThat(running.steps()).isZero();
    assertThat(running.updatedAt()).isAfterOrEqualTo(task.updatedAt());
  }

  @Test
  @DisplayName("pause 记录暂停原因，complete/fail 记录结果与用量及上下文快照")
  void shouldPauseCompleteAndFail() {
    TaskRecord task = TaskRecord.create("t", "p", Instant.EPOCH).withStatus(TaskState.RUNNING, 0);
    TaskRecord paused = task.pause("等待审批 DANGEROUS 工具");
    assertThat(paused.status()).isEqualTo(TaskState.PAUSED);
    assertThat(paused.pauseReason()).contains("审批");

    TokenUsage usage = new TokenUsage(10, 5, 15);
    List<AgentMessage> context = List.of(AgentMessage.user("诊断"), AgentMessage.assistant("已调用"));
    TaskRecord done = paused.complete("根因：连接池耗尽", 3, usage, context);
    assertThat(done.status()).isEqualTo(TaskState.SUCCEEDED);
    assertThat(done.result()).isEqualTo("根因：连接池耗尽");
    assertThat(done.steps()).isEqualTo(3);
    assertThat(done.tokenUsage()).isEqualTo(usage);
    assertThat(done.contextMessages()).hasSize(2);
    assertThat(done.pauseReason()).isNull();
  }

  @Test
  @DisplayName("fail 迁移至 FAILED 并记录原因")
  void shouldFailTask() {
    TaskRecord task = TaskRecord.create("t", "p", Instant.EPOCH).withStatus(TaskState.RUNNING, 2);
    TaskRecord failed = task.fail("工具熔断", 2, new TokenUsage(5, 2, 7), List.of());
    assertThat(failed.status()).isEqualTo(TaskState.FAILED);
    assertThat(failed.result()).isEqualTo("工具熔断");
    assertThat(failed.status().isTerminal()).isTrue();
  }

  @Test
  @DisplayName("cancel 可从 RUNNING 或 PAUSED 转移，终态不可取消")
  void shouldCancelFromNonTerminal() {
    TaskRecord running =
        TaskRecord.create("t", "p", Instant.EPOCH).withStatus(TaskState.RUNNING, 0);
    TaskRecord cancelled = running.cancel();
    assertThat(cancelled.status()).isEqualTo(TaskState.CANCELLED);

    assertThatThrownBy(() -> cancelled.cancel()).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("非法迁移抛 IllegalStateException")
  void shouldRejectIllegalTransition() {
    TaskRecord created = TaskRecord.create("t", "p", Instant.EPOCH);
    assertThatThrownBy(() -> created.withStatus(TaskState.SUCCEEDED, 0))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> created.pause("x")).isInstanceOf(IllegalStateException.class);
  }
}
