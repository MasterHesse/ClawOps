package com.clawops.harness.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** StepCheckpoint 构造校验。 */
@DisplayName("StepCheckpoint 检查点记录")
class StepCheckpointTest {

  @Test
  @DisplayName("构造合法检查点：参数默认值与字段对齐")
  void shouldConstructValidCheckpoint() {
    Instant now = Instant.now();
    StepCheckpoint cp =
        new StepCheckpoint(
            "t1", 1, "call-1", "metrics.query", Map.of("q", "up"), "success", "up=1", now);
    assertThat(cp.taskId()).isEqualTo("t1");
    assertThat(cp.seq()).isEqualTo(1);
    assertThat(cp.toolCallId()).isEqualTo("call-1");
    assertThat(cp.arguments()).containsEntry("q", "up");
    assertThat(cp.resultStatus()).isEqualTo("success");
  }

  @Test
  @DisplayName("taskId 不可为空，seq 不可小于 1，toolName 不可为 null")
  void shouldRejectInvalidFields() {
    assertThatThrownBy(
            () -> new StepCheckpoint("  ", 1, "c", "t", Map.of(), "success", "x", Instant.now()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new StepCheckpoint("t", 0, "c", "t", Map.of(), "success", "x", Instant.now()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new StepCheckpoint("t", 1, "c", null, Map.of(), "success", "x", Instant.now()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("null arguments 归一为空 Map")
  void shouldNormalizeNullArguments() {
    StepCheckpoint cp = new StepCheckpoint("t", 1, "c", "t", null, "success", "x", Instant.now());
    assertThat(cp.arguments()).isEmpty();
  }
}
