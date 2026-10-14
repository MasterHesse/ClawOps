package com.clawops.harness.loop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.clawops.harness.spi.ToolCall;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LoopGuard 死循环检测")
class LoopGuardTest {

  @Test
  @DisplayName("新调用默认允许")
  void shouldAllowNewCalls() {
    LoopGuard guard = new LoopGuard(2);

    assertThat(guard.check(new ToolCall("c1", "metrics.query", Map.of("q", "up")))).isTrue();
  }

  @Test
  @DisplayName("同一签名连续达到阈值后拦截")
  void shouldBlockRepeatedCalls() {
    LoopGuard guard = new LoopGuard(2);
    ToolCall call = new ToolCall("c1", "metrics.query", Map.of("q", "up"));

    assertThat(guard.check(call)).isTrue();
    assertThat(guard.check(call)).isTrue();
    assertThat(guard.check(call)).isFalse();
  }

  @Test
  @DisplayName("不同签名重置连续计数")
  void shouldResetCountForDifferentCalls() {
    LoopGuard guard = new LoopGuard(2);
    ToolCall callA = new ToolCall("c1", "a", Map.of());

    assertThat(guard.check(callA)).isTrue();
    assertThat(guard.check(new ToolCall("c2", "b", Map.of()))).isTrue();
    assertThat(guard.check(callA)).isTrue();
    assertThat(guard.check(callA)).isTrue();
    assertThat(guard.check(callA)).isFalse();
  }

  @Test
  @DisplayName("reset 后重新开始计数")
  void shouldReset() {
    LoopGuard guard = new LoopGuard(1);
    ToolCall call = new ToolCall("c1", "a", Map.of());

    assertThat(guard.check(call)).isTrue();
    assertThat(guard.check(call)).isFalse();

    guard.reset();
    assertThat(guard.check(call)).isTrue();
  }

  @Test
  @DisplayName("构造参数非法时抛出异常")
  void shouldRejectInvalidConfig() {
    assertThatThrownBy(() -> new LoopGuard(0)).isInstanceOf(IllegalArgumentException.class);
  }
}
